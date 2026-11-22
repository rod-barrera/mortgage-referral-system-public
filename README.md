# Sistema web de derivación y seguimiento de consultas hipotecarias

Proyecto académico individual de Rodrigo Ignacio Barrera Pino. Permite registrar interesados y seguir sus consultas hipotecarias en una oficina bancaria ficticia. Utiliza datos de demostración.

**[Abrir la aplicación](https://mortgage-referral-system.onrender.com)**

- **Ejecutivo:** registra o busca interesados por RUT, crea derivaciones y las corrige antes de su recepción.
- **Supervisora:** administra las cuentas de ejecutivos, recibe los casos, registra gestiones y actualiza su estado.
- **Ambos perfiles:** consultan su bandeja y el historial de sus casos y pueden editar su perfil y contraseña.

El sistema registra el resultado informado de una evaluación crediticia externa. La evaluación y aprobación de créditos se realizan fuera de la aplicación. Los nombres técnicos están en inglés y la interfaz está en español; el diccionario de datos incluye las equivalencias.

## Tecnologías

| Componente | Tecnología |
|---|---|
| Servidor | Java 21 y Spring Boot 4.1.2 |
| Seguridad y persistencia | Spring Security y Spring Data JPA |
| Interfaz | Thymeleaf, HTML y CSS propio responsive |
| Interacciones del navegador | TypeScript 5.9.3 compilado a JavaScript |
| Construcción | Gradle Wrapper 8.14.2 y Node.js 22 con npm |
| Base de datos | PostgreSQL 17.x, verificado con 17.11 |
| Alojamiento | Docker en Render Free y PostgreSQL en Neon Free |

El esquema tiene ocho tablas, 41 campos, 14 claves foráneas y nueve transiciones de estado permitidas. Cada actuación conserva su autor. PostgreSQL valida las transiciones y mantiene el estado del caso junto con su historial.

## Estructura del proyecto

```text
src/main/java/                    Controladores, servicios, seguridad y persistencia
src/main/resources/templates/    Pantallas y formularios Thymeleaf
src/main/resources/static/css/   Estilos responsive
frontend/                        Interacciones escritas en TypeScript
database/                        Esquema, catálogos, permisos y migración
config/                          Ejemplo de configuración local
scripts/                         Arranque local
docs/                            Modelo de datos, arquitectura y plan de pruebas
```

## Documentación

- [Modelo y diccionario de datos](docs/data-model.md).
- [Normalización paso a paso](docs/normalization.md).
- [Arquitectura y elección de tecnologías](docs/architecture.md).
- [Instalación y consulta de PostgreSQL](docs/postgresql-setup.md).
- [Despliegue en Render y Neon](docs/deployment.md).
- [Plan de pruebas](docs/test-plan.md).

## Preparación de la base de datos

Los pasos siguientes preparan una instalación nueva en Windows con PostgreSQL 17. Ejecuta los comandos desde la carpeta `mortgage-referral-system`, ajustando `$pgBin` si PostgreSQL está instalado en otra ruta. Se utiliza el propietario de la base para instalar las tablas y un rol independiente para conectar la aplicación.

### 1. Crear la base y cargar el esquema inicial

```powershell
$pgBin = 'C:\Program Files\PostgreSQL\17\bin'
& "$pgBin\createdb.exe" -h 127.0.0.1 -p 5432 -U postgres -T template0 -E UTF8 mortgage_referrals_eval2
& "$pgBin\psql.exe" -h 127.0.0.1 -p 5432 -U postgres -d mortgage_referrals_eval2 -v ON_ERROR_STOP=1 -f database/01_schema.sql
& "$pgBin\psql.exe" -h 127.0.0.1 -p 5432 -U postgres -d mortgage_referrals_eval2 -v ON_ERROR_STOP=1 -f database/02_seed_catalogs.sql
```

### 2. Crear el rol de aplicación y asignarle una contraseña

Crea el rol una sola vez. El segundo comando solicita su contraseña en `psql`:

```powershell
& "$pgBin\psql.exe" -h 127.0.0.1 -p 5432 -U postgres -d mortgage_referrals_eval2 -v ON_ERROR_STOP=1 -c 'CREATE ROLE mortgage_referrals_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;'
& "$pgBin\psql.exe" -h 127.0.0.1 -p 5432 -U postgres -d mortgage_referrals_eval2 -v ON_ERROR_STOP=1 -c '\password mortgage_referrals_app'
```

### 3. Aplicar los permisos y la migración

```powershell
& "$pgBin\psql.exe" -h 127.0.0.1 -p 5432 -U postgres -d mortgage_referrals_eval2 -v ON_ERROR_STOP=1 -f database/05_application_permissions.sql
& "$pgBin\psql.exe" -h 127.0.0.1 -p 5432 -U postgres -d mortgage_referrals_eval2 -v ON_ERROR_STOP=1 -f database/07_feedback_integrity.sql
```

La secuencia `01 → 02 → crear rol → 05 → 07` instala el esquema que utiliza la aplicación. El [detalle de PostgreSQL](docs/postgresql-setup.md) explica las tablas, los permisos y las consultas de inspección.

## Ejecutar la aplicación local

Se necesitan Java 21, Node.js 22 con npm y la base preparada. Gradle Wrapper descarga la versión de Gradle definida en el proyecto.

### 1. Preparar la configuración

Para una instalación nueva, copia el ejemplo a una carpeta externa al repositorio y edítalo:

```powershell
New-Item -ItemType Directory -Path '..\.local' -Force | Out-Null
Copy-Item -LiteralPath 'config/local.example.json' -Destination '..\.local\mortgage-referrals-config.json'
notepad.exe '..\.local\mortgage-referrals-config.json'
```

Completa `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` con los datos de la base y del rol `mortgage_referrals_app`. Configura también el correo, nombre y contraseña de la supervisora inicial en los campos `BOOTSTRAP_SUPERVISOR_*`. Su contraseña debe tener al menos diez caracteres y hasta 72 bytes UTF-8.

### 2. Compilar e iniciar

```powershell
npm.cmd ci
.\gradlew.bat --no-daemon bootJar
.\scripts\Start-Local.ps1 -ConfigPath '..\.local\mortgage-referrals-config.json'
```

Abre [http://127.0.0.1:8080/login](http://127.0.0.1:8080/login) e inicia sesión con la supervisora configurada. El arranque crea esa cuenta solo si todavía no existe una supervisora. Desde **Cuentas de ejecutivos** puedes crear las cuentas ejecutivas y controlar su acceso.

El proceso permanece en la consola y se detiene con `Ctrl+C`. El puerto 8080 debe estar disponible. JPA valida las tablas al arrancar; su instalación se realiza con los comandos anteriores.

## Plan de pruebas

El plan reúne doce casos sobre acceso, cuentas y perfil, interesados, derivaciones, permisos, seguimiento, persistencia e interfaz. Cada caso documenta las condiciones iniciales, los pasos, los resultados esperados y los resultados obtenidos. Las comprobaciones de seguridad e integridad de datos se presentan como verificaciones técnicas complementarias.

El [plan de pruebas](docs/test-plan.md) describe el alcance y los criterios de evaluación de los casos.
