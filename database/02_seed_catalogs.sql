-- Catalogos del alcance comprometido. Ejecutar despues de 01_schema.sql.
-- Los identificadores son estables: el estado inicial es el numero 1.
BEGIN;

INSERT INTO public.role (role_id, name) VALUES
    (1, 'EXECUTIVE'),
    (2, 'SUPERVISOR');

INSERT INTO public.status (status_id, name, is_final) VALUES
    (1, 'Pendiente de recepción', FALSE),
    (2, 'Recibida', FALSE),
    (3, 'En gestión', FALSE),
    (4, 'En evaluación', FALSE),
    (5, 'Aprobada', TRUE),
    (6, 'Rechazada', TRUE),
    (7, 'Desistida', TRUE);

COMMIT;
