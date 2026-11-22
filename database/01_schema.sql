-- Sistema web de derivacion y seguimiento de consultas hipotecarias.
-- Verificado en PostgreSQL 17.11. Ejecutar sobre una base nueva.
-- La aplicación valida los permisos y PostgreSQL protege
-- la integridad de los datos y la continuidad del historial.
BEGIN;

CREATE TABLE public.role (
    role_id SMALLINT PRIMARY KEY,
    name VARCHAR(20) NOT NULL UNIQUE,
    CONSTRAINT ck_role_name CHECK (btrim(name) <> '')
);

CREATE TABLE public.user_account (
    user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_id SMALLINT NOT NULL REFERENCES public.role(role_id) ON DELETE RESTRICT,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_user_account_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_user_account_email_normalized CHECK (
        email = lower(btrim(email)) AND position('@' IN email) > 1
    ),
    CONSTRAINT ck_user_account_hash_present CHECK (btrim(password_hash) <> '')
);

CREATE TABLE public.lead (
    lead_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rut VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    phone VARCHAR(30),
    contact_email VARCHAR(254),
    CONSTRAINT ck_lead_rut_format CHECK (rut ~ '^[1-9][0-9]{0,7}-[0-9K]$'),
    CONSTRAINT ck_lead_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_lead_phone CHECK (phone IS NULL OR btrim(phone) <> ''),
    CONSTRAINT ck_lead_contact_email CHECK (
        contact_email IS NULL OR (
            contact_email = btrim(contact_email)
            AND position('@' IN contact_email) > 1
        )
    ),
    CONSTRAINT ck_lead_contact CHECK (phone IS NOT NULL OR contact_email IS NOT NULL)
);

CREATE TABLE public.status (
    status_id SMALLINT PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    is_final BOOLEAN NOT NULL,
    CONSTRAINT ck_status_name CHECK (btrim(name) <> '')
);

CREATE TABLE public.referral (
    referral_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lead_id BIGINT NOT NULL REFERENCES public.lead(lead_id) ON DELETE RESTRICT,
    executive_id BIGINT NOT NULL REFERENCES public.user_account(user_id) ON DELETE RESTRICT,
    supervisor_id BIGINT NOT NULL REFERENCES public.user_account(user_id) ON DELETE RESTRICT,
    current_status_id SMALLINT NOT NULL DEFAULT 1 REFERENCES public.status(status_id) ON DELETE RESTRICT,
    summary TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT clock_timestamp(),
    property_value_uf NUMERIC(12,2),
    down_payment_uf NUMERIC(12,2),
    CONSTRAINT ck_referral_summary CHECK (btrim(summary) <> ''),
    CONSTRAINT ck_referral_responsible_accounts CHECK (executive_id <> supervisor_id),
    CONSTRAINT ck_referral_property_value_uf CHECK (
        property_value_uf IS NULL OR (property_value_uf >= 0 AND property_value_uf <> 'NaN'::numeric)
    ),
    CONSTRAINT ck_referral_down_payment_uf CHECK (
        down_payment_uf IS NULL OR (down_payment_uf >= 0 AND down_payment_uf <> 'NaN'::numeric)
    )
);

CREATE TABLE public.follow_up (
    follow_up_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    referral_id BIGINT NOT NULL REFERENCES public.referral(referral_id) ON DELETE RESTRICT,
    description TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_follow_up_description CHECK (btrim(description) <> '')
);

CREATE TABLE public.status_history (
    status_history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    referral_id BIGINT NOT NULL REFERENCES public.referral(referral_id) ON DELETE RESTRICT,
    previous_status_id SMALLINT REFERENCES public.status(status_id) ON DELETE RESTRICT,
    new_status_id SMALLINT NOT NULL REFERENCES public.status(status_id) ON DELETE RESTRICT,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT clock_timestamp(),
    comment TEXT NOT NULL DEFAULT '',
    CONSTRAINT ck_status_history_statuses CHECK (
        (previous_status_id IS NULL AND new_status_id = 1)
        OR (previous_status_id IS NOT NULL AND previous_status_id <> new_status_id)
    )
);

CREATE INDEX ix_user_account_role ON public.user_account(role_id);
CREATE INDEX ix_referral_lead ON public.referral(lead_id);
CREATE INDEX ix_referral_executive_inbox
    ON public.referral(executive_id, current_status_id, created_at, referral_id);
CREATE INDEX ix_referral_supervisor_inbox
    ON public.referral(supervisor_id, current_status_id, created_at, referral_id);
CREATE INDEX ix_follow_up_referral_created_at ON public.follow_up(referral_id, created_at, follow_up_id);
CREATE INDEX ix_status_history_referral_order ON public.status_history(referral_id, status_history_id);
CREATE UNIQUE INDEX ux_status_history_creation ON public.status_history(referral_id)
    WHERE previous_status_id IS NULL;

CREATE FUNCTION public.preserve_referral_responsibles()
RETURNS trigger LANGUAGE plpgsql AS $ownership$
BEGIN
    IF NEW.executive_id IS DISTINCT FROM OLD.executive_id
       OR NEW.supervisor_id IS DISTINCT FROM OLD.supervisor_id THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            CONSTRAINT = 'ck_referral_responsibles_immutable',
            MESSAGE = 'Los responsables del caso no se pueden reasignar.';
    END IF;
    RETURN NEW;
END;
$ownership$;

CREATE TRIGGER preserve_referral_responsibles
BEFORE UPDATE OF executive_id, supervisor_id ON public.referral
FOR EACH ROW EXECUTE FUNCTION public.preserve_referral_responsibles();

COMMIT;
