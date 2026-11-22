# Instalación y consulta de PostgreSQL

La aplicación utiliza PostgreSQL 17 y una base llamada `mortgage_referrals_eval2`. El esquema final contiene ocho tablas, 41 campos, 14 claves foráneas y nueve transiciones de estado permitidas. Los nombres técnicos y sus equivalencias se explican en el [modelo y diccionario de datos](data-model.md).

## Instalación local

Los comandos completos para una base nueva en Windows están en el [README](../README.md#preparación-de-la-base-de-datos). Se ejecutan desde la raíz del proyecto con el propietario de la base y las tablas.

| Orden | Archivo o acción | Función |
|---|---|---|
| 1 | [01_schema.sql](../database/01_schema.sql) | Crea las siete tablas iniciales, claves, restricciones, índices y reglas de responsables. |
| 2 | [02_seed_catalogs.sql](../database/02_seed_catalogs.sql) | Carga los perfiles Ejecutivo y Supervisora y los siete estados. |
| 3 | Crear `mortgage_referrals_app` | Proporciona una cuenta de conexión sin privilegios administrativos. |
| 4 | [05_application_permissions.sql](../database/05_application_permissions.sql) | Asigna los permisos de conexión y de uso de las tablas. |
| 5 | [07_feedback_integrity.sql](../database/07_feedback_integrity.sql) | Incorpora la autoría explícita, la octava tabla de transiciones y las reglas de consistencia del historial. |

El nombre de la base debe coincidir con el utilizado en el script de permisos y en la URL JDBC. Los scripts de creación se ejecutan una vez sobre una base nueva. La migración 07 utiliza una transacción y comprueba los datos existentes antes de confirmar sus cambios.

## Rol de aplicación

El rol se crea una sola vez desde una sesión administrativa de `psql`:

```sql
CREATE ROLE mortgage_referrals_app
    LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
```

Su contraseña se asigna mediante `\password mortgage_referrals_app`. Después se aplican los permisos de 05 y la migración 07. La aplicación recibe la contraseña a través de su configuración externa.

El propietario instala y modifica el esquema. El rol de aplicación consulta los catálogos, gestiona las cuentas y registra interesados, derivaciones y actuaciones. Las gestiones y los eventos del historial permiten consulta e inserción, con protección frente a modificación o eliminación de actuaciones existentes. La actualización directa de una derivación se limita a sus datos editables; PostgreSQL mantiene el estado vigente.

## Acceso desde pgAdmin

1. Conectarse al servidor local PostgreSQL 17 con host `127.0.0.1` y puerto `5432`.
2. Seleccionar la base `mortgage_referrals_eval2`.
3. Actualizar `Schemas → public → Tables` para ver las tablas instaladas.
4. Abrir el editor de consultas de esa base.

Consultar el entorno y las tablas:

```sql
SELECT current_database(), version();

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;
```

Consultar los catálogos:

```sql
SELECT role_id, name FROM public.role ORDER BY role_id;
SELECT status_id, name, is_final FROM public.status ORDER BY status_id;
SELECT from_status_id, to_status_id
FROM public.status_transition
ORDER BY from_status_id, to_status_id;
```

Las tablas finales son `role`, `user_account`, `lead`, `status`, `referral`, `follow_up`, `status_history` y `status_transition`. Los perfiles utilizan los códigos `EXECUTIVE` y `SUPERVISOR`; los estados se presentan en español.

El archivo [04_evidence_queries.sql](../database/04_evidence_queries.sql) reúne consultas adicionales para inspeccionar columnas, tipos, restricciones y catálogos.

## Autoría y consistencia del historial

Las gestiones y los eventos guardan `author_id`. PostgreSQL comprueba el perfil y el responsable de cada actuación: el ejecutivo en el evento inicial y la supervisora en la recepción y las actuaciones posteriores.

Los responsables permanecen fijos dentro de cada caso. Las claves foráneas compuestas validan sus perfiles y las transiciones deben existir en el catálogo `status_transition`. El cierre exige una observación. Al insertar un evento, la base sincroniza el estado vigente y comprueba la continuidad del historial dentro de la misma transacción.

## Conexión desde la aplicación

La configuración local parte de `config/local.example.json` y se completa fuera del repositorio. El lanzador acepta esa ruta mediante `-ConfigPath`. Spring Boot se conecta como `mortgage_referrals_app` y utiliza `ddl-auto=validate` para validar las tablas al arrancar.

La instalación de Neon sigue la misma secuencia de esquema y permisos. Su URL JDBC utiliza TLS y sus credenciales se configuran en las variables del servicio. La [guía de despliegue](deployment.md) describe esa instalación y la [arquitectura](architecture.md) explica cómo el servidor utiliza los datos.
