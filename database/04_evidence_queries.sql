-- Consultas de solo lectura para mostrar el modelo implementado.
-- Ejecutar conectado a mortgage_referrals_eval2.
\pset pager off
BEGIN READ ONLY;

\echo ENTORNO
SELECT current_database() AS database_name,
       current_setting('server_version') AS postgresql_version,
       inet_server_port() AS port,
       current_setting('TimeZone') AS time_zone;

\echo TABLAS Y CANTIDAD DE CAMPOS
SELECT t.table_name AS table_name, count(c.column_name) AS column_count
FROM information_schema.tables t
JOIN information_schema.columns c
  ON c.table_schema = t.table_schema AND c.table_name = t.table_name
WHERE t.table_schema = 'public' AND t.table_type = 'BASE TABLE'
GROUP BY t.table_name
ORDER BY t.table_name;

\echo TIPOS Y OBLIGATORIEDAD
SELECT table_name AS table_name, column_name AS column_name, data_type AS data_type,
       character_maximum_length AS max_length,
       numeric_precision AS precision, numeric_scale AS scale,
       is_nullable AS is_nullable, is_identity AS is_identity,
       column_default AS column_default
FROM information_schema.columns
WHERE table_schema = 'public'
ORDER BY table_name, ordinal_position;

\echo CLAVES Y RESTRICCIONES
SELECT conrelid::regclass AS table_name, conname AS constraint_name,
       pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE connamespace = 'public'::regnamespace
ORDER BY conrelid::regclass::text, conname;

\echo PERFILES
SELECT role_id, name FROM public.role ORDER BY role_id;

\echo ESTADOS DEL SEGUIMIENTO
SELECT status_id, name, is_final FROM public.status ORDER BY status_id;

COMMIT;
