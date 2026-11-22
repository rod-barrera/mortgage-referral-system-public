# Despliegue en Render y Neon

La aplicación de demostración está publicada en [mortgage-referral-system.onrender.com](https://mortgage-referral-system.onrender.com). Render ejecuta el contenedor Docker y Neon conserva la base de datos PostgreSQL. Esta guía describe cómo preparar otra instalación con los archivos del proyecto.

## Componentes del despliegue

| Componente | Configuración del proyecto |
|---|---|
| Aplicación | Web Service de Render con runtime Docker y plan Free |
| Base de datos | PostgreSQL 17 en Neon Free |
| Nombre de la base | `mortgage_referrals_eval2` |
| Rol de conexión | `mortgage_referrals_app`, con permisos limitados |
| Perfil de Spring | `prod` |
| Comprobación de disponibilidad | `/login` |
| Entrada HTTP del contenedor | Puerto indicado por la variable `PORT` de Render |

El Dockerfile compila TypeScript con Node.js 22 y empaqueta Java con JDK 21 y Gradle Wrapper. La imagen final contiene el JRE y el ejecutable y utiliza un usuario sin privilegios administrativos. El archivo `.dockerignore` limita el contexto a los archivos necesarios para construir la aplicación.

## Preparar una base nueva en Neon

1. Crear un proyecto con PostgreSQL 17 y una base llamada `mortgage_referrals_eval2`.
2. Conectarse mediante `psql` con el rol propietario y los parámetros TLS del proveedor. La contraseña se proporciona fuera de los archivos del proyecto.
3. Ejecutar [01_schema.sql](../database/01_schema.sql) y [02_seed_catalogs.sql](../database/02_seed_catalogs.sql), en ese orden y con `ON_ERROR_STOP=1`.
4. Crear el rol de aplicación mediante SQL:

```sql
CREATE ROLE mortgage_referrals_app
    LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
```

5. Asignar su contraseña desde `psql` con `\password mortgage_referrals_app`.
6. Ejecutar [05_application_permissions.sql](../database/05_application_permissions.sql) y luego [07_feedback_integrity.sql](../database/07_feedback_integrity.sql) como propietario de las tablas.
7. Consultar las ocho tablas y los catálogos instalados. El rol de aplicación debe poder consultar los catálogos e insertar las actuaciones que corresponden a sus funciones, sin atributos administrativos ni propiedad sobre las tablas.

La base utiliza ocho tablas, 41 campos, 14 claves foráneas y nueve transiciones de estado. La [guía de PostgreSQL](postgresql-setup.md) incluye las consultas de inspección del esquema.

## Variables de Render

El archivo `render.yaml` define el servicio y solicita las variables siguientes. `.env.example` documenta sus nombres; Spring no carga ese archivo por sí mismo.

| Variable | Valor o propósito |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod`, fijado en el Blueprint |
| `DB_URL` | URL JDBC de Neon con TLS, sin contraseña en la URL |
| `DB_USERNAME` | `mortgage_referrals_app` |
| `DB_PASSWORD` | Contraseña del rol de aplicación |
| `BOOTSTRAP_SUPERVISOR_EMAIL` | Correo de la supervisora inicial |
| `BOOTSTRAP_SUPERVISOR_NAME` | Nombre visible de la supervisora |
| `BOOTSTRAP_SUPERVISOR_PASSWORD` | Contraseña inicial de esa cuenta |

Formato de la URL JDBC, usando un nombre de servidor de ejemplo:

```text
jdbc:postgresql://YOUR_NEON_HOST:5432/mortgage_referrals_eval2?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&channelBinding=require
```

`verify-full` valida el certificado y el nombre del servidor. `DefaultJavaSSLFactory` utiliza el almacén de confianza del JRE. Las credenciales se introducen en las variables del servicio, separadas de la URL y del código.

La contraseña de provisión debe tener al menos diez caracteres y hasta 72 bytes UTF-8. La aplicación crea la supervisora solo cuando no existe ninguna; reiniciar el servicio no cambia una contraseña existente. Desde esa cuenta se crean los ejecutivos y se administra su acceso.

## Crear el servicio en Render

1. Dar a Render acceso al repositorio de la aplicación.
2. Crear un Web Service con runtime **Docker**, usando el Dockerfile y el contexto de la raíz. También puede utilizarse `render.yaml` como Blueprint.
3. Seleccionar el plan Free y completar las variables anteriores.
4. Conservar el perfil `prod`, la comprobación de disponibilidad `/login` y la configuración de despliegue definida en `render.yaml`.
5. Esperar a que termine la construcción y abrir la URL HTTPS asignada.
6. Iniciar sesión con la cuenta configurada y recorrer las funciones de ambos perfiles según el [plan de pruebas](test-plan.md).

La configuración del repositorio permite publicar las actualizaciones desde el panel de Render. Para construir la imagen localmente se puede ejecutar `docker build -t mortgage-referral-system .`; su ejecución necesita las variables externas y la base preparada.

## Configuración de ejecución

El perfil `prod` escucha en `0.0.0.0` y utiliza el puerto asignado por Render. Reconoce el HTTPS terminado por el proxy y mantiene las cookies de sesión con los atributos `Secure`, `HttpOnly` y `SameSite=Lax`. JPA valida el esquema existente al arrancar; los scripts de instalación se ejecutan por separado.

La JVM tiene un heap máximo de 256 MiB, metaspace de 160 MiB, caché de código de 32 MiB y recolector Serial. Tomcat admite hasta 20 hilos y Hikari hasta dos conexiones. Estos valores corresponden a la configuración de demostración y se deben revisar si cambia la carga o el plan de alojamiento.

Los datos persistentes se guardan en Neon. El contenedor mantiene las sesiones en memoria, por lo que un reinicio puede requerir volver a iniciar sesión. Los planes gratuitos pueden suspender recursos por inactividad y están sujetos a las cuotas de cada proveedor.

## Documentación de referencia

- [Render y Docker](https://render.com/docs/docker).
- [Render Blueprint YAML](https://render.com/docs/blueprint-spec).
- [Conexión de Neon desde Java](https://neon.com/docs/guides/java).
- [Validación TLS de pgJDBC](https://jdbc.postgresql.org/documentation/ssl/).
