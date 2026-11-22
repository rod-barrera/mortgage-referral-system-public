-- Refinamiento del esquema de la preentrega. Ejecutar una vez como propietario
-- después de 01_schema.sql, 02_seed_catalogs.sql y los permisos de 05.
-- Conserva los datos. Si el historial existente no concuerda, revierte todo.
BEGIN;
SET LOCAL lock_timeout = '10s';
LOCK TABLE public.user_account, public.referral, public.follow_up, public.status_history
    IN ACCESS EXCLUSIVE MODE;

CREATE TABLE public.status_transition (
    from_status_id SMALLINT NOT NULL REFERENCES public.status(status_id) ON DELETE RESTRICT,
    to_status_id SMALLINT NOT NULL REFERENCES public.status(status_id) ON DELETE RESTRICT,
    PRIMARY KEY (from_status_id, to_status_id),
    CONSTRAINT ck_status_transition_pair CHECK (
        from_status_id <> to_status_id AND from_status_id NOT IN (5, 6, 7)
    )
);

INSERT INTO public.status_transition (from_status_id, to_status_id) VALUES
    (1, 2), (2, 3), (2, 7), (3, 4), (3, 7), (4, 3), (4, 5), (4, 6), (4, 7);

-- Las constantes generadas identifican el perfil requerido, incluso cuando
-- se intenta cambiar el perfil de una cuenta que ya es responsable de un caso.
ALTER TABLE public.user_account
    ADD CONSTRAINT uq_user_account_id_role UNIQUE (user_id, role_id);

ALTER TABLE public.referral
    ADD COLUMN executive_role SMALLINT GENERATED ALWAYS AS (1::smallint) STORED NOT NULL,
    ADD COLUMN supervisor_role SMALLINT GENERATED ALWAYS AS (2::smallint) STORED NOT NULL,
    ADD CONSTRAINT fk_referral_executive_role FOREIGN KEY (executive_id, executive_role)
        REFERENCES public.user_account(user_id, role_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_referral_supervisor_role FOREIGN KEY (supervisor_id, supervisor_role)
        REFERENCES public.user_account(user_id, role_id) ON DELETE RESTRICT,
    DROP CONSTRAINT referral_executive_id_fkey,
    DROP CONSTRAINT referral_supervisor_id_fkey;

-- La regla previa de responsables fijos permite recuperar el autor histórico.
ALTER TABLE public.follow_up ADD COLUMN author_id BIGINT;
UPDATE public.follow_up AS f
SET author_id = r.supervisor_id
FROM public.referral AS r
WHERE r.referral_id = f.referral_id;
ALTER TABLE public.follow_up
    ALTER COLUMN author_id SET NOT NULL,
    ADD CONSTRAINT fk_follow_up_author FOREIGN KEY (author_id)
        REFERENCES public.user_account(user_id) ON DELETE RESTRICT;

ALTER TABLE public.status_history ADD COLUMN author_id BIGINT;
UPDATE public.status_history AS h
SET author_id = CASE WHEN h.previous_status_id IS NULL THEN r.executive_id ELSE r.supervisor_id END
FROM public.referral AS r
WHERE r.referral_id = h.referral_id;
ALTER TABLE public.status_history
    ALTER COLUMN author_id SET NOT NULL,
    ADD CONSTRAINT fk_status_history_author FOREIGN KEY (author_id)
        REFERENCES public.user_account(user_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_status_history_transition FOREIGN KEY (previous_status_id, new_status_id)
        REFERENCES public.status_transition(from_status_id, to_status_id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_status_history_close_comment CHECK (
        new_status_id NOT IN (5, 6, 7) OR comment ~ '[^[:space:]]'
    );

-- No se infieren estados faltantes ni se corrigen cadenas silenciosamente.
DO $migration_check$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.referral AS r
        LEFT JOIN LATERAL (
            SELECT h.new_status_id
            FROM public.status_history AS h
            WHERE h.referral_id = r.referral_id
            ORDER BY h.status_history_id DESC
            LIMIT 1
        ) AS last_event ON TRUE
        WHERE r.current_status_id IS DISTINCT FROM last_event.new_status_id
    ) OR EXISTS (
        SELECT 1
        FROM (
            SELECT previous_status_id, lag(new_status_id) OVER (
                PARTITION BY referral_id ORDER BY status_history_id
            ) AS expected_previous
            FROM public.status_history
        ) AS ordered_history
        WHERE previous_status_id IS DISTINCT FROM expected_previous
    ) THEN
        RAISE EXCEPTION 'Existen casos sin historia inicial o con una cadena de estados inconsistente.';
    END IF;
END;
$migration_check$;

CREATE FUNCTION public.require_initial_referral_status()
RETURNS trigger LANGUAGE plpgsql AS $initial_status$
BEGIN
    IF NEW.current_status_id IS DISTINCT FROM 1 THEN
        RAISE EXCEPTION USING ERRCODE = '23514', CONSTRAINT = 'ck_referral_initial_status',
            MESSAGE = 'El caso debe comenzar pendiente de recepción.';
    END IF;
    RETURN NEW;
END;
$initial_status$;

CREATE TRIGGER require_initial_referral_status
BEFORE INSERT ON public.referral
FOR EACH ROW EXECUTE FUNCTION public.require_initial_referral_status();

CREATE FUNCTION public.check_follow_up_author()
RETURNS trigger LANGUAGE plpgsql AS $follow_up_author$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.referral AS r
        WHERE r.referral_id = NEW.referral_id AND r.supervisor_id = NEW.author_id
    ) THEN
        RAISE EXCEPTION USING ERRCODE = '23514', CONSTRAINT = 'ck_follow_up_author',
            MESSAGE = 'La gestión debe identificar a la supervisora asignada.';
    END IF;
    RETURN NEW;
END;
$follow_up_author$;

CREATE TRIGGER check_follow_up_author
AFTER INSERT ON public.follow_up
FOR EACH ROW EXECUTE FUNCTION public.check_follow_up_author();

-- Se ejecuta después de insertar la fila para que ON CONFLICT DO NOTHING
-- no tenga efectos sobre el caso. Una transición por caso en cada sentencia.
-- El rol de aplicación puede insertar el evento, pero no escribir el estado.
CREATE FUNCTION public.sync_referral_status()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $sync_status$
DECLARE
    v_case public.referral%ROWTYPE;
    v_author BIGINT;
    v_last_event_id BIGINT;
    v_last_event_status SMALLINT;
BEGIN
    SELECT * INTO STRICT v_case
    FROM public.referral
    WHERE referral_id = NEW.referral_id
    FOR NO KEY UPDATE;

    v_author := CASE WHEN NEW.previous_status_id IS NULL
        THEN v_case.executive_id ELSE v_case.supervisor_id END;
    IF NEW.author_id IS DISTINCT FROM v_author THEN
        RAISE EXCEPTION USING ERRCODE = '23514', CONSTRAINT = 'ck_status_history_author',
            MESSAGE = 'El autor del evento debe ser el responsable habilitado para la actuación.';
    END IF;

    SELECT h.status_history_id, h.new_status_id INTO v_last_event_id, v_last_event_status
    FROM public.status_history AS h
    WHERE h.referral_id = NEW.referral_id AND h.status_history_id <> NEW.status_history_id
    ORDER BY h.status_history_id DESC
    LIMIT 1;

    IF v_last_event_id > NEW.status_history_id THEN
        RAISE EXCEPTION USING ERRCODE = '23514', CONSTRAINT = 'ck_status_history_order',
            MESSAGE = 'El evento debe quedar después del último evento del caso.';
    END IF;

    IF NEW.previous_status_id IS DISTINCT FROM v_last_event_status
       OR COALESCE(NEW.previous_status_id, 1) <> v_case.current_status_id THEN
        RAISE EXCEPTION USING ERRCODE = '23514', CONSTRAINT = 'ck_status_history_previous_current',
            MESSAGE = 'El estado previo debe coincidir con el estado vigente y el último evento del caso.';
    END IF;

    IF NEW.previous_status_id IS NOT NULL THEN
        UPDATE public.referral
        SET current_status_id = NEW.new_status_id
        WHERE referral_id = NEW.referral_id;
    END IF;
    RETURN NULL;
END;
$sync_status$;

REVOKE ALL ON FUNCTION public.sync_referral_status() FROM PUBLIC;

CREATE TRIGGER sync_referral_status
AFTER INSERT ON public.status_history
FOR EACH ROW EXECUTE FUNCTION public.sync_referral_status();

CREATE FUNCTION public.require_referral_history_consistency()
RETURNS trigger LANGUAGE plpgsql AS $history_consistency$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.referral AS r
        WHERE r.referral_id = NEW.referral_id
        AND (
            NOT EXISTS (
                SELECT 1 FROM public.status_history AS h
                WHERE h.referral_id = r.referral_id AND h.previous_status_id IS NULL
            )
            OR r.current_status_id IS DISTINCT FROM (
                SELECT h.new_status_id
                FROM public.status_history AS h
                WHERE h.referral_id = r.referral_id
                ORDER BY h.status_history_id DESC
                LIMIT 1
            )
        )
    ) THEN
        RAISE EXCEPTION USING ERRCODE = '23514', CONSTRAINT = 'ck_referral_history_consistency',
            MESSAGE = 'El caso requiere un evento inicial y su estado debe coincidir con el último evento.';
    END IF;
    RETURN NULL;
END;
$history_consistency$;

CREATE CONSTRAINT TRIGGER referral_history_consistency
AFTER INSERT OR UPDATE OF current_status_id ON public.referral
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.require_referral_history_consistency();

CREATE FUNCTION public.prevent_actuation_update()
RETURNS trigger LANGUAGE plpgsql AS $actuation_update$
BEGIN
    RAISE EXCEPTION USING ERRCODE = '23514', CONSTRAINT = 'ck_actuation_immutable',
        MESSAGE = 'Los eventos y las gestiones ya registrados no se modifican.';
END;
$actuation_update$;

CREATE TRIGGER prevent_follow_up_update
BEFORE UPDATE ON public.follow_up
FOR EACH ROW EXECUTE FUNCTION public.prevent_actuation_update();

CREATE TRIGGER prevent_status_history_update
BEFORE UPDATE ON public.status_history
FOR EACH ROW EXECUTE FUNCTION public.prevent_actuation_update();

-- Permite al administrador retirar un caso ficticio completo en la misma
-- transacción y protege la cadena de los casos que siguen existiendo.
CREATE FUNCTION public.require_referral_removed_with_history()
RETURNS trigger LANGUAGE plpgsql AS $history_removal$
BEGIN
    IF EXISTS (SELECT 1 FROM public.referral WHERE referral_id = OLD.referral_id) THEN
        RAISE EXCEPTION USING ERRCODE = '23514', CONSTRAINT = 'ck_status_history_delete',
            MESSAGE = 'No se pueden borrar eventos de un caso que sigue registrado.';
    END IF;
    RETURN NULL;
END;
$history_removal$;

CREATE CONSTRAINT TRIGGER status_history_removal
AFTER DELETE ON public.status_history
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.require_referral_removed_with_history();

GRANT SELECT ON public.status_transition TO mortgage_referrals_app;
REVOKE UPDATE ON public.referral FROM mortgage_referrals_app;
GRANT UPDATE (summary, property_value_uf, down_payment_uf)
    ON public.referral TO mortgage_referrals_app;

COMMIT;
