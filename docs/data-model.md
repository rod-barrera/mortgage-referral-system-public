# Modelo y diccionario de datos

**Proyecto:** sistema web de derivación y seguimiento de consultas hipotecarias.  
**Modelo vigente:** ocho tablas, 41 campos, 14 claves foráneas y nueve transiciones permitidas. El esquema se obtiene al aplicar `07_feedback_integrity.sql` sobre el esquema inicial y los catálogos. PostgreSQL en Neon conserva este modelo para la aplicación publicada en Render. [Guía de despliegue](deployment.md).

## Estructura del modelo

El dominio contiene siete entidades: rol, cuenta, interesado, estado, derivación, gestión e historial. El catálogo técnico `status_transition` completa las ocho tablas del esquema y define las transiciones entre los siete estados del seguimiento.

## Fundamentación de SQL frente a NoSQL

Los datos tienen una estructura conocida y relaciones estables: una cuenta pertenece a un perfil, una persona interesada puede originar varios casos y cada caso reúne responsables, un estado vigente, gestiones y eventos. El modelo relacional permite representar esas asociaciones mediante claves foráneas y conservar una sola ficha por RUT, un correo de acceso único y catálogos compartidos. Las consultas de bandeja e historial necesitan combinar estas tablas sin duplicar nombres, contactos ni descripciones de estados en cada caso.

El seguimiento requiere que la creación del caso y su evento inicial, y luego cada cambio de estado y su evento, se completen juntos o se reviertan. PostgreSQL combina transacciones, claves y triggers para comprobar estas reglas. Los formularios y servicios Java aplican estas reglas en el recorrido hipotecario.

El crecimiento previsto consiste en acumular más interesados, casos, actuaciones y eventos dentro de la misma estructura. El esquema utiliza índices para los vínculos y las bandejas; filtros, paginación y revisión de consultas permitirán adaptar su acceso al volumen observado. No se asigna una capacidad ni un tiempo de respuesta sin mediciones.

Una base documental también podría representar el dominio, pero no se identifican documentos de estructura variable ni una necesidad de acceso que justifique elegirla para este alcance. Separar las entidades relacionadas evita repetir sus datos en cada caso y facilita expresar las restricciones del proyecto. Por estos motivos el modelo utiliza PostgreSQL 17.11 y conserva la estructura relacional.

## Correspondencia de nombres

Los identificadores técnicos se expresan en inglés y las explicaciones permanecen en español. Las etiquetas de los estados que ve el usuario están en español.

| Concepto | Tabla SQL |
|---|---|
| Rol | `role` |
| Usuario | `user_account` |
| Interesado | `lead` |
| Estado | `status` |
| Derivación | `referral` |
| Gestión | `follow_up` |
| Historial de estado | `status_history` |
| Matriz de cambios permitidos | `status_transition` |

Los códigos `EXECUTIVE` y `SUPERVISOR` representan los dos perfiles. Las gestiones y los eventos conservan su cuenta autora en `author_id`. PostgreSQL comprueba al insertar que el ejecutivo del caso firma el evento inicial y que la supervisora destinataria firma las gestiones y eventos posteriores.

Con los responsables fijos del proyecto, la autoría se puede deducir del caso y, en el historial, de si el evento es el inicial. Guardarla como dato de auditoría introduce una redundancia deliberada. Las columnas generadas de perfil forman parte del esquema físico y permiten verificar las FK compuestas. El análisis de 3FN distingue estas decisiones de la estructura normalizada de partida. [Normalización del modelo](normalization.md).

## Convenciones

- Los identificadores de las cinco tablas operativas son `BIGINT GENERATED ALWAYS AS IDENTITY`; la identidad se documenta como mecanismo de generación, separado del tipo base. Los catálogos usan identificadores `SMALLINT` asignados explícitamente. Las secuencias de identidad no garantizan valores consecutivos.
- `PK`: clave primaria; `FK`: clave foránea; `UQ`: valor único.
- Los nombres SQL se escriben en inglés, en minúsculas y con separación mediante guiones bajos.
- La longitud de `VARCHAR(n)` se expresa en caracteres. `BIGINT`, `SMALLINT`, `BOOLEAN` y `TIMESTAMP WITH TIME ZONE` ocupan respectivamente 8, 2, 1 y 8 bytes para su valor; esto no representa el tamaño total de una fila. `TEXT` tiene longitud variable y `NUMERIC(12,2)` indica precisión 12 y escala 2, con almacenamiento variable.
- Las fechas son `TIMESTAMP WITH TIME ZONE` y tienen `DEFAULT clock_timestamp()`. El valor predeterminado se aplica si el campo se omite al insertar; no impide que una operación SQL proporcione otro valor ni hace inmutable la fecha. La interfaz utiliza la zona `America/Santiago`.
- Los importes referenciales usan `NUMERIC(12,2)`. Se admiten dos decimales y ausencia de valor; se rechazan importes negativos y NaN. No se impone una relación entre el pie y el valor de la propiedad, ni se realizan cálculos crediticios.
- Admite NULL describe la nulabilidad de cada campo. Sin DEFAULT declarado significa que el esquema no define un valor predeterminado; en el JSON se representa como `null` dentro de la propiedad `default`.
- Los contactos opcionales admiten `NULL` individualmente y al menos uno debe estar informado. `status_history.comment` toma la cadena vacía por defecto. Al cerrar, el campo debe contener al menos un carácter que no sea blanco.
- Las claves foráneas conservan las referencias mediante `ON DELETE RESTRICT`. Las cuentas se desactivan.

## Diccionario de datos

Las fichas registran su fecha de elaboración, **06/11/2026**, y la revisión del **10/11/2026** que incorporó las columnas de autoría, la validación de perfiles y la ficha de transiciones. [Diccionario estructurado](data-dictionary.json).

El [diagrama relacional](diagrams/relational-model.svg) muestra las ocho tablas. También se incluye el [diagrama en Mermaid](relational-model.mmd).

### Ficha 1. Rol

**Nombre del archivo/tabla:** `public.role`.  
**Fecha de elaboración:** 06/11/2026.  
**Descripción:** Catálogo de perfiles de acceso. Los datos de demostración incluyen Ejecutivo y Supervisora; sus identificadores se asignan explícitamente.

| Campo | Tipo SQL | Longitud o precisión | Admite NULL | Predeterminado | Descripción | Claves y restricciones SQL |
|---|---|---|---|---|---|---|
| `role_id` | `SMALLINT` | 2 bytes | No | Sin DEFAULT declarado | Identificador estable del perfil. Las semillas asignan 1 a Ejecutivo y 2 a Supervisora. | `PRIMARY KEY` |
| `name` | `VARCHAR(20)` | 20 caracteres | No | Sin DEFAULT declarado | Código técnico del perfil: EXECUTIVE o SUPERVISOR en los datos de demostración. | `UNIQUE`; `ck_role_name: CHECK (btrim(name) <> '')` |

**Campos llave:**

- PK: `role_id`.
- UQ: `name`.

**Relaciones:**

- public.role.role_id → public.user_account.role_id: un perfil puede estar asociado a cero o más cuentas; cada cuenta referencia exactamente un perfil.

**Reglas y alcance:**

- El catálogo se carga mediante 02_seed_catalogs.sql y no se administra desde las pantallas del prototipo.
- Los dos códigos proceden de las semillas. El SQL no contiene un CHECK que limite el catálogo exclusivamente a esos dos valores.

### Ficha 2. Usuario

**Nombre del archivo/tabla:** `public.user_account`.  
**Fecha de elaboración:** 06/11/2026.  
**Fecha de revisión:** 10/11/2026.  
**Descripción:** Cuenta autenticada y perfil asignado. La aplicación almacena la contraseña mediante BCrypt y utiliza el correo como identificador de acceso.

| Campo | Tipo SQL | Longitud o precisión | Admite NULL | Predeterminado | Descripción | Claves y restricciones SQL |
|---|---|---|---|---|---|---|
| `user_id` | `BIGINT` | 8 bytes | No | Sin DEFAULT declarado | Identificador de la cuenta, generado por PostgreSQL. | `PRIMARY KEY`; `GENERATED ALWAYS AS IDENTITY`; `UNIQUE (user_id, role_id)` |
| `role_id` | `SMALLINT` | 2 bytes | No | Sin DEFAULT declarado | Perfil de acceso asignado a la cuenta. | `FOREIGN KEY REFERENCES public.role(role_id) ON DELETE RESTRICT`; `UNIQUE (user_id, role_id)` |
| `name` | `VARCHAR(120)` | 120 caracteres | No | Sin DEFAULT declarado | Nombre visible de la cuenta. | `ck_user_account_name: CHECK (btrim(name) <> '')` |
| `email` | `VARCHAR(254)` | 254 caracteres | No | Sin DEFAULT declarado | Correo de acceso único, en minúsculas y sin espacios externos. La condición SQL sobre @ es una comprobación mínima, no una validación completa del correo. | `UNIQUE`; `ck_user_account_email_normalized: CHECK (email = lower(btrim(email)) AND position('@' IN email) > 1)` |
| `password_hash` | `VARCHAR(255)` | 255 caracteres | No | Sin DEFAULT declarado | Hash de la contraseña. Spring Security aplica BCrypt; el CHECK SQL solo exige contenido no vacío después de btrim. | `ck_user_account_hash_present: CHECK (btrim(password_hash) <> '')` |
| `active` | `BOOLEAN` | 1 byte | No | `TRUE` | Indica si la cuenta puede acceder. La aplicación impide el acceso a cuentas desactivadas y comprueba las sesiones existentes. | — |

**Campos llave:**

- PK: `user_id`.
- UQ: `email`.
- UQ: `user_id`, `role_id`.
- FK: `role_id` → `FOREIGN KEY REFERENCES public.role(role_id) ON DELETE RESTRICT`.

**Relaciones:**

- public.user_account.role_id → public.role.role_id: cada cuenta tiene exactamente un perfil; un perfil puede tener cero o más cuentas.
- public.user_account.(user_id, role_id) → public.referral.(executive_id, executive_role) y public.referral.(supervisor_id, supervisor_role). Una cuenta puede participar en cero o más derivaciones en su perfil.
- public.user_account.user_id → public.follow_up.author_id y public.status_history.author_id. Cada actuación o evento conserva una cuenta autora y cada cuenta puede figurar en cero o más registros.

**Reglas y alcance:**

- La supervisora puede crear, activar y desactivar ejecutivos. El servidor asigna EXECUTIVE al crear estas cuentas y no acepta un rol elegido libremente por el formulario.
- La desactivación conserva la cuenta y sus referencias. Las FK usan ON DELETE RESTRICT.
- El formato BCrypt, la cuenta activa y los permisos corresponden a la aplicación; el SQL no los deduce del texto password_hash ni de las referencias.
- La unicidad de (user_id, role_id) habilita las FK compuestas que verifican el perfil de los responsables. Una cuenta asignada a un caso debe conservar el perfil que referencia el caso.

### Ficha 3. Interesado

**Nombre del archivo/tabla:** `public.lead`.  
**Fecha de elaboración:** 06/11/2026.  
**Descripción:** Ficha de la persona interesada, reutilizable por varias derivaciones y reconocida por un RUT único. Los datos de contacto no son claves únicas.

| Campo | Tipo SQL | Longitud o precisión | Admite NULL | Predeterminado | Descripción | Claves y restricciones SQL |
|---|---|---|---|---|---|---|
| `lead_id` | `BIGINT` | 8 bytes | No | Sin DEFAULT declarado | Identificador de la ficha, generado por PostgreSQL. | `PRIMARY KEY`; `GENERATED ALWAYS AS IDENTITY` |
| `rut` | `VARCHAR(10)` | 10 caracteres | No | Sin DEFAULT declarado | RUT con cuerpo de uno a ocho dígitos, sin cero inicial ni puntos, guion y verificador 0–9 o K mayúscula. El patrón SQL comprueba el formato; no calcula el dígito verificador. | `UNIQUE`; `ck_lead_rut_format: CHECK (rut ~ '^[1-9][0-9]{0,7}-[0-9K]$')` |
| `name` | `VARCHAR(120)` | 120 caracteres | No | Sin DEFAULT declarado | Nombre de la persona interesada. | `ck_lead_name: CHECK (btrim(name) <> '')` |
| `phone` | `VARCHAR(30)` | 30 caracteres | Sí | Sin DEFAULT declarado | Teléfono opcional. Si se informa, debe contener texto después de btrim; la base no valida un formato telefónico específico. | `ck_lead_phone: CHECK (phone IS NULL OR btrim(phone) <> '')`; `ck_lead_contact: CHECK (phone IS NOT NULL OR contact_email IS NOT NULL)` |
| `contact_email` | `VARCHAR(254)` | 254 caracteres | Sí | Sin DEFAULT declarado | Correo opcional, sin espacios externos cuando se informa. Se admite mayúscula y no se exige unicidad. La comprobación de @ es mínima, no una validación completa. | `ck_lead_contact_email: CHECK (contact_email IS NULL OR (contact_email = btrim(contact_email) AND position('@' IN contact_email) > 1))`; `ck_lead_contact: CHECK (phone IS NOT NULL OR contact_email IS NOT NULL)` |

**Campos llave:**

- PK: `lead_id`.
- UQ: `rut`.

**Relaciones:**

- public.lead.lead_id → public.referral.lead_id: una ficha puede tener cero o más derivaciones; cada derivación referencia exactamente una ficha.

**Reglas y alcance:**

- phone y contact_email admiten NULL individualmente, pero ck_lead_contact impide que ambos sean NULL en la misma ficha. Pueden informarse ambos.
- Java normaliza el RUT y valida su dígito verificador al registrar o buscar una ficha.
- RF05 contempla recuperar la ficha por RUT exacto conservando el aislamiento de las derivaciones de cada ejecutivo. No se incorpora una función adicional de edición de contactos.

### Ficha 4. Estado

**Nombre del archivo/tabla:** `public.status`.  
**Fecha de elaboración:** 06/11/2026.  
**Fecha de revisión:** 10/11/2026.  
**Descripción:** Catálogo de los estados del seguimiento y su condición de cierre. Sus identificadores son estables y se asignan explícitamente en las semillas.

| Campo | Tipo SQL | Longitud o precisión | Admite NULL | Predeterminado | Descripción | Claves y restricciones SQL |
|---|---|---|---|---|---|---|
| `status_id` | `SMALLINT` | 2 bytes | No | Sin DEFAULT declarado | Identificador estable del estado. Los datos de demostración utilizan los valores del 1 al 7. | `PRIMARY KEY` |
| `name` | `VARCHAR(32)` | 32 caracteres | No | Sin DEFAULT declarado | Etiqueta visible del estado en español. | `UNIQUE`; `ck_status_name: CHECK (btrim(name) <> '')` |
| `is_final` | `BOOLEAN` | 1 byte | No | Sin DEFAULT declarado | Indica si el estado es final; su valor se proporciona al cargar el catálogo. | — |

**Campos llave:**

- PK: `status_id`.
- UQ: `name`.

**Relaciones:**

- public.status.status_id → public.referral.current_status_id: un estado puede estar asignado a cero o más casos; cada caso tiene exactamente un estado actual.
- public.status.status_id → public.status_history.previous_status_id: un estado puede aparecer en cero o más eventos como estado previo; la referencia de un evento es opcional según el CHECK del historial.
- public.status.status_id → public.status_history.new_status_id: un estado puede aparecer en cero o más eventos como estado nuevo; cada evento tiene exactamente un estado nuevo.
- public.status.status_id → public.status_transition.from_status_id y public.status_transition.to_status_id. Un estado puede aparecer en cero o más transiciones como origen o destino.

**Reglas y alcance:**

- Semillas: 1 Pendiente de recepción, 2 Recibida, 3 En gestión, 4 En evaluación, 5 Aprobada, 6 Rechazada y 7 Desistida. Solo 5, 6 y 7 tienen is_final = TRUE.
- Los siete valores y sus etiquetas proceden de 02_seed_catalogs.sql; no existe un CHECK que limite status_id al intervalo 1–7.
- Las aristas permitidas se conservan en status_transition. Los estados de cierre del alcance son Aprobada (5), Rechazada (6) y Desistida (7).

**Valores del catálogo:**

| Código | Nombre | Final |
|---|---|---|
| 1 | Pendiente de recepción | No |
| 2 | Recibida | No |
| 3 | En gestión | No |
| 4 | En evaluación | No |
| 5 | Aprobada | Sí |
| 6 | Rechazada | Sí |
| 7 | Desistida | Sí |

### Ficha 5. Derivación

**Nombre del archivo/tabla:** `public.referral`.  
**Fecha de elaboración:** 06/11/2026.  
**Fecha de revisión:** 10/11/2026.  
**Descripción:** Caso que vincula una ficha de interesado, una cuenta creadora, una supervisora destinataria y su estado actual. Incluye la consulta y valores referenciales opcionales en UF.

| Campo | Tipo SQL | Longitud o precisión | Admite NULL | Predeterminado | Descripción | Claves y restricciones SQL |
|---|---|---|---|---|---|---|
| `referral_id` | `BIGINT` | 8 bytes | No | Sin DEFAULT declarado | Identificador del caso, generado por PostgreSQL. | `PRIMARY KEY`; `GENERATED ALWAYS AS IDENTITY` |
| `lead_id` | `BIGINT` | 8 bytes | No | Sin DEFAULT declarado | Ficha de interesado asociada al caso. | `FOREIGN KEY REFERENCES public.lead(lead_id) ON DELETE RESTRICT` |
| `executive_id` | `BIGINT` | 8 bytes | No | Sin DEFAULT declarado | Cuenta creadora fija del caso. La FK compuesta exige una cuenta con perfil ejecutivo. | `FOREIGN KEY (executive_id, executive_role) REFERENCES public.user_account(user_id, role_id) ON DELETE RESTRICT`; `ck_referral_responsible_accounts: CHECK (executive_id <> supervisor_id)`; `preserve_referral_responsibles: TRIGGER BEFORE UPDATE OF executive_id, supervisor_id; rechaza cambios de responsables con SQLSTATE 23514` |
| `supervisor_id` | `BIGINT` | 8 bytes | No | Sin DEFAULT declarado | Cuenta destinataria fija del caso. La FK compuesta exige una cuenta con perfil de supervisión. | `FOREIGN KEY (supervisor_id, supervisor_role) REFERENCES public.user_account(user_id, role_id) ON DELETE RESTRICT`; `ck_referral_responsible_accounts: CHECK (executive_id <> supervisor_id)`; `preserve_referral_responsibles: TRIGGER BEFORE UPDATE OF executive_id, supervisor_id; rechaza cambios de responsables con SQLSTATE 23514` |
| `current_status_id` | `SMALLINT` | 2 bytes | No | `1` | Estado vigente de la bandeja. Empieza en 1 y PostgreSQL lo sincroniza con el último evento de estado en la misma transacción. | `FOREIGN KEY REFERENCES public.status(status_id) ON DELETE RESTRICT` |
| `summary` | `TEXT` | Variable | No | Sin DEFAULT declarado | Descripción de la consulta hipotecaria. | `ck_referral_summary: CHECK (btrim(summary) <> '')` |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | 8 bytes | No | `clock_timestamp()` | Fecha y hora de creación; PostgreSQL aporta la fecha si se omite el campo al insertar. | — |
| `property_value_uf` | `NUMERIC(12,2)` | Precisión 12; escala 2; almacenamiento variable | Sí | Sin DEFAULT declarado | Valor referencial de la propiedad en UF. Puede ser NULL o cero; no admite negativos ni NaN. | `ck_referral_property_value_uf: CHECK (property_value_uf IS NULL OR (property_value_uf >= 0 AND property_value_uf <> 'NaN'::numeric))` |
| `down_payment_uf` | `NUMERIC(12,2)` | Precisión 12; escala 2; almacenamiento variable | Sí | Sin DEFAULT declarado | Pie estimado en UF. Puede ser NULL o cero; no admite negativos ni NaN. | `ck_referral_down_payment_uf: CHECK (down_payment_uf IS NULL OR (down_payment_uf >= 0 AND down_payment_uf <> 'NaN'::numeric))` |
| `executive_role` | `SMALLINT` | 2 bytes | No | Sin DEFAULT declarado | Columna técnica generada con el valor fijo 1 para comprobar el perfil de ejecutivo. | `GENERATED ALWAYS AS (1::smallint) STORED`; `FOREIGN KEY (executive_id, executive_role) REFERENCES public.user_account(user_id, role_id) ON DELETE RESTRICT` |
| `supervisor_role` | `SMALLINT` | 2 bytes | No | Sin DEFAULT declarado | Columna técnica generada con el valor fijo 2 para comprobar el perfil de supervisión. | `GENERATED ALWAYS AS (2::smallint) STORED`; `FOREIGN KEY (supervisor_id, supervisor_role) REFERENCES public.user_account(user_id, role_id) ON DELETE RESTRICT` |

**Campos llave:**

- PK: `referral_id`.
- UQ: sin claves únicas adicionales de tabla completa.
- FK: `lead_id` → `FOREIGN KEY REFERENCES public.lead(lead_id) ON DELETE RESTRICT`.
- FK: `FOREIGN KEY (executive_id, executive_role) REFERENCES public.user_account(user_id, role_id) ON DELETE RESTRICT`.
- FK: `FOREIGN KEY (supervisor_id, supervisor_role) REFERENCES public.user_account(user_id, role_id) ON DELETE RESTRICT`.
- FK: `current_status_id` → `FOREIGN KEY REFERENCES public.status(status_id) ON DELETE RESTRICT`.

**Relaciones:**

- public.referral.lead_id → public.lead.lead_id: cada caso referencia exactamente una ficha; una ficha puede tener cero o más casos.
- public.referral.(executive_id, executive_role) y public.referral.(supervisor_id, supervisor_role) → public.user_account.(user_id, role_id). Cada caso tiene un creador ejecutivo y una destinataria de supervisión distintos.
- public.referral.current_status_id → public.status.status_id: cada caso tiene exactamente un estado actual; cada estado puede utilizarse en cero o más casos.
- public.referral.referral_id → public.follow_up.referral_id y public.status_history.referral_id. Cada caso admite cero o más gestiones y requiere al menos el evento inicial al confirmar la transacción.

**Reglas y alcance:**

- Las FK compuestas verifican los perfiles de los responsables. El servicio Java comprueba la identidad autenticada y la actividad de la cuenta antes de registrar una actuación.
- El creador puede corregir el resumen y los importes mientras el caso está Pendiente de recepción. El rol de aplicación puede actualizar summary, property_value_uf y down_payment_uf.
- La base no exige down_payment_uf <= property_value_uf ni efectúa cálculos o decisiones crediticias.
- La creación del caso y su evento inicial debe ejecutarse dentro de una transacción. PostgreSQL comprueba al confirmarla que el caso tiene evento inicial y que el estado vigente coincide con el último evento.
- El trigger require_initial_referral_status exige que cada caso nuevo comience en estado 1.
- PostgreSQL sincroniza el estado al registrar un evento. El rol de aplicación carece de UPDATE directo de current_status_id y Java mapea el campo como solo lectura para escrituras.
- Los responsables son inmutables. El trigger preserve_referral_responsibles rechaza cambios de executive_id o supervisor_id y el mapeo Java utiliza updatable=false.

### Ficha 6. Gestión

**Nombre del archivo/tabla:** `public.follow_up`.  
**Fecha de elaboración:** 06/11/2026.  
**Fecha de revisión:** 10/11/2026.  
**Descripción:** Actuación asociada a una derivación, con identificación persistida de la cuenta autora.

| Campo | Tipo SQL | Longitud o precisión | Admite NULL | Predeterminado | Descripción | Claves y restricciones SQL |
|---|---|---|---|---|---|---|
| `follow_up_id` | `BIGINT` | 8 bytes | No | Sin DEFAULT declarado | Identificador de la gestión, generado por PostgreSQL. | `PRIMARY KEY`; `GENERATED ALWAYS AS IDENTITY` |
| `referral_id` | `BIGINT` | 8 bytes | No | Sin DEFAULT declarado | Caso al que pertenece la gestión. | `FOREIGN KEY REFERENCES public.referral(referral_id) ON DELETE RESTRICT` |
| `description` | `TEXT` | Variable | No | Sin DEFAULT declarado | Contenido de la actuación. | `ck_follow_up_description: CHECK (btrim(description) <> '')` |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | 8 bytes | No | `clock_timestamp()` | Fecha y hora del registro; PostgreSQL aporta la fecha si se omite el campo al insertar. | — |
| `author_id` | `BIGINT` | 8 bytes | No | Sin DEFAULT declarado | Cuenta de supervisión asignada al caso que registró la gestión. | `FOREIGN KEY REFERENCES public.user_account(user_id) ON DELETE RESTRICT` |

**Campos llave:**

- PK: `follow_up_id`.
- UQ: sin claves únicas adicionales de tabla completa.
- FK: `referral_id` → `FOREIGN KEY REFERENCES public.referral(referral_id) ON DELETE RESTRICT`.
- FK: `author_id` → `FOREIGN KEY REFERENCES public.user_account(user_id) ON DELETE RESTRICT`.

**Relaciones:**

- public.follow_up.referral_id → public.referral.referral_id: cada gestión pertenece exactamente a un caso; cada caso puede tener cero o más gestiones.
- public.follow_up.author_id → public.user_account.user_id. Cada gestión conserva una autora explícita.

**Reglas y alcance:**

- Al insertar, check_follow_up_author verifica que author_id corresponde a la supervisora destinataria del caso.
- Registrar una gestión conserva el estado. El servicio Java verifica la sesión y exige un caso recibido y abierto.
- El rol de aplicación tiene permisos de consulta e inserción. El trigger prevent_follow_up_update protege las filas ya registradas. La cuenta autora permanece asociada a la actuación cuando se desactiva la cuenta.

### Ficha 7. Historial de estado

**Nombre del archivo/tabla:** `public.status_history`.  
**Fecha de elaboración:** 06/11/2026.  
**Fecha de revisión:** 10/11/2026.  
**Descripción:** Eventos de creación, recepción y cambio de estado, con estado previo, estado nuevo, fecha, observación y cuenta autora persistida.

| Campo | Tipo SQL | Longitud o precisión | Admite NULL | Predeterminado | Descripción | Claves y restricciones SQL |
|---|---|---|---|---|---|---|
| `status_history_id` | `BIGINT` | 8 bytes | No | Sin DEFAULT declarado | Identificador del evento, generado por PostgreSQL. Define el orden del historial dentro de cada caso. | `PRIMARY KEY`; `GENERATED ALWAYS AS IDENTITY` |
| `referral_id` | `BIGINT` | 8 bytes | No | Sin DEFAULT declarado | Caso al que pertenece el evento. El índice único parcial limita a uno los eventos con estado previo NULL para cada caso. | `FOREIGN KEY REFERENCES public.referral(referral_id) ON DELETE RESTRICT`; `ux_status_history_creation: UNIQUE INDEX (referral_id) WHERE previous_status_id IS NULL` |
| `previous_status_id` | `SMALLINT` | 2 bytes | Sí | Sin DEFAULT declarado | Estado previo. Puede ser NULL únicamente con new_status_id = 1; la condición identifica la forma admitida del evento inicial. | `FOREIGN KEY REFERENCES public.status(status_id) ON DELETE RESTRICT`; `ck_status_history_statuses: CHECK ((previous_status_id IS NULL AND new_status_id = 1) OR (previous_status_id IS NOT NULL AND previous_status_id <> new_status_id))`; `FOREIGN KEY (previous_status_id, new_status_id) REFERENCES public.status_transition(from_status_id, to_status_id) ON DELETE RESTRICT` |
| `new_status_id` | `SMALLINT` | 2 bytes | No | Sin DEFAULT declarado | Estado resultante. El evento inicial utiliza 1 y cada evento posterior debe corresponder a una transición del catálogo. | `FOREIGN KEY REFERENCES public.status(status_id) ON DELETE RESTRICT`; `ck_status_history_statuses: CHECK ((previous_status_id IS NULL AND new_status_id = 1) OR (previous_status_id IS NOT NULL AND previous_status_id <> new_status_id))`; `FOREIGN KEY (previous_status_id, new_status_id) REFERENCES public.status_transition(from_status_id, to_status_id) ON DELETE RESTRICT` |
| `changed_at` | `TIMESTAMP WITH TIME ZONE` | 8 bytes | No | `clock_timestamp()` | Fecha y hora del evento; PostgreSQL aporta la fecha si se omite el campo al insertar. | — |
| `comment` | `TEXT` | Variable | No | `''` | Observación del evento. Los cierres 5, 6 y 7 exigen al menos un carácter que no sea blanco. | `ck_status_history_close_comment: CHECK (new_status_id NOT IN (5, 6, 7) OR comment ~ '[^[:space:]]')` |
| `author_id` | `BIGINT` | 8 bytes | No | Sin DEFAULT declarado | Cuenta autora del evento. Para la creación se exige al ejecutivo del caso. Para los cambios se exige a la supervisora del caso. | `FOREIGN KEY REFERENCES public.user_account(user_id) ON DELETE RESTRICT` |

**Campos llave:**

- PK: `status_history_id`.
- UQ: sin claves únicas adicionales de tabla completa.
- FK: `referral_id` → `FOREIGN KEY REFERENCES public.referral(referral_id) ON DELETE RESTRICT`.
- FK: `previous_status_id` → `FOREIGN KEY REFERENCES public.status(status_id) ON DELETE RESTRICT`.
- FK: `new_status_id` → `FOREIGN KEY REFERENCES public.status(status_id) ON DELETE RESTRICT`.
- FK: `FOREIGN KEY (previous_status_id, new_status_id) REFERENCES public.status_transition(from_status_id, to_status_id) ON DELETE RESTRICT`.
- FK: `author_id` → `FOREIGN KEY REFERENCES public.user_account(user_id) ON DELETE RESTRICT`.

**Relaciones:**

- public.status_history.referral_id → public.referral.referral_id. Cada evento pertenece a un caso. Cada caso requiere un evento inicial y admite más eventos.
- public.status_history.previous_status_id → public.status.status_id: cada evento referencia cero o un estado previo, sujeto a ck_status_history_statuses.
- public.status_history.new_status_id → public.status.status_id: cada evento referencia exactamente un estado nuevo; un estado puede aparecer en cero o más eventos.
- public.status_history.author_id → public.user_account.user_id. Cada evento conserva exactamente una cuenta autora.
- public.status_history.(previous_status_id, new_status_id) → public.status_transition.(from_status_id, to_status_id). Cada evento posterior a la creación referencia un par permitido.

**Reglas y alcance:**

- ux_status_history_creation limita a uno los eventos iniciales por caso. El trigger de restricción diferido referral_history_consistency verifica al confirmar que el evento inicial existe y que current_status_id coincide con el último evento.
- El trigger sync_referral_status valida al autor, el orden y el estado previo con un bloqueo sobre el caso. El evento registrado y la actualización de current_status_id quedan en la misma transacción. Cada sentencia registra una transición por caso.
- La FK compuesta usa MATCH SIMPLE. La creación usa estado previo NULL y queda fuera de la matriz, con el estado nuevo fijado en 1 por el CHECK del historial.
- El rol de aplicación tiene permisos de consulta e inserción. El servicio registra el cambio de estado con el autor de la sesión.
- El trigger prevent_status_history_update protege los eventos de modificaciones. La restricción diferida status_history_removal permite al administrador retirar el historial junto con el caso completo en la misma transacción.

### Ficha 8. Transición de estado

**Nombre del archivo/tabla:** `public.status_transition`.  
**Fecha de elaboración:** 10/11/2026.  
**Descripción:** Catálogo dirigido de pares de estados permitidos para el seguimiento de una derivación.

| Campo | Tipo SQL | Longitud o precisión | Admite NULL | Predeterminado | Descripción | Claves y restricciones SQL |
|---|---|---|---|---|---|---|
| `from_status_id` | `SMALLINT` | 2 bytes | No | Sin DEFAULT declarado | Estado de origen del cambio. | `PRIMARY KEY (from_status_id, to_status_id)`; `FOREIGN KEY REFERENCES public.status(status_id) ON DELETE RESTRICT`; `ck_status_transition_pair: CHECK (from_status_id <> to_status_id AND from_status_id NOT IN (5, 6, 7))` |
| `to_status_id` | `SMALLINT` | 2 bytes | No | Sin DEFAULT declarado | Estado de destino del cambio. | `PRIMARY KEY (from_status_id, to_status_id)`; `FOREIGN KEY REFERENCES public.status(status_id) ON DELETE RESTRICT`; `ck_status_transition_pair: CHECK (from_status_id <> to_status_id AND from_status_id NOT IN (5, 6, 7))` |

**Campos llave:**

- PK: `from_status_id`, `to_status_id`.
- UQ: sin claves únicas adicionales de tabla completa.
- FK: `from_status_id` → `FOREIGN KEY REFERENCES public.status(status_id) ON DELETE RESTRICT`.
- FK: `to_status_id` → `FOREIGN KEY REFERENCES public.status(status_id) ON DELETE RESTRICT`.

**Relaciones:**

- public.status_transition.from_status_id y to_status_id → public.status.status_id. Cada fila tiene un origen y un destino.
- public.status_transition.(from_status_id, to_status_id) → public.status_history.(previous_status_id, new_status_id). Un par puede utilizarse en cero o más eventos posteriores a la creación.

**Reglas y alcance:**

- El catálogo conserva las nueve transiciones comprometidas. Los estados finales 5, 6 y 7 carecen de transiciones de salida.
- El rol de aplicación tiene acceso de lectura al catálogo.
- La creación se comprueba mediante el evento inicial de historial, con estado anterior NULL y nuevo estado 1.

**Transiciones del catálogo:**

| Desde | Hacia |
|---|---|
| 1 | 2 |
| 2 | 3 |
| 2 | 7 |
| 3 | 4 |
| 3 | 7 |
| 4 | 3 |
| 4 | 5 |
| 4 | 6 |
| 4 | 7 |

## Relaciones y cardinalidades

| Relación | Cardinalidad y regla |
|---|---|
| Rol → Usuario | Un rol agrupa cero o más cuentas. Cada cuenta tiene un rol. |
| Interesado → Derivación | Un interesado puede tener cero o más casos. Cada caso tiene un interesado. |
| Usuario → Derivación, como ejecutivo o supervisora | Una cuenta participa en cero o más casos en su perfil. Cada caso tiene una cuenta creadora y una destinataria distintas, verificadas por claves foráneas compuestas. |
| Estado → Derivación | Un estado puede estar asignado a cero o más casos. Cada caso tiene un estado actual. |
| Derivación → Gestión | Un caso admite cero o más actuaciones. Cada actuación pertenece a un caso. |
| Usuario → Gestión | Cada gestión conserva su autora mediante una FK. Una cuenta puede aparecer en cero o más gestiones. |
| Derivación → Historial de estado | Cada evento pertenece a un caso. Cada caso debe quedar con al menos el evento inicial al confirmar la transacción. |
| Usuario → Historial de estado | Cada evento conserva una cuenta autora mediante una FK. |
| Estado → Historial de estado | Cada evento tiene estado nuevo. El estado previo admite NULL para la creación. |
| Estado → Transición de estado | Cada transición tiene un estado de origen y uno de destino. Un estado puede participar en cero o más transiciones. |
| Transición de estado → Historial de estado | Cada evento posterior a la creación utiliza un par del catálogo. Una transición puede aparecer en cero o más eventos. |

## Integridad de la base y reglas del servidor

| Responsabilidad | Mecanismo |
|---|---|
| Identificación única, campos obligatorios y referencias válidas | PK, UQ, NOT NULL y FK del esquema PostgreSQL. |
| Al menos un contacto, textos requeridos y valores no negativos | CHECK del esquema PostgreSQL. |
| RUT válido y normalizado | Formato y unicidad en PostgreSQL. Java valida el dígito verificador en el formulario. |
| Perfil de los responsables | Dos FK compuestas comprueban que la cuenta corresponda al perfil requerido. Las columnas auxiliares se generan en PostgreSQL. |
| Responsables fijos | El trigger `preserve_referral_responsibles` impide cambiar `executive_id` o `supervisor_id`. |
| Autoría de gestiones y eventos | Cada fila conserva `author_id` y PostgreSQL lo valida frente al responsable del caso al insertar. |
| Cuenta activa y administración por perfil | Spring Security y servicios de cuentas. |
| Pertenencia de las bandejas | Consultas por creador o destinataria. La autorización de operaciones de negocio corresponde al servicio Java. |
| Solo gestionar tras recepción y antes del cierre | El servicio Java comprueba el estado bajo un bloqueo del caso. |
| Transiciones permitidas y observación de cierre | FK compuesta hacia `status_transition` y comprobación de observación con contenido para los cierres. |
| Estado actual e historial consistentes | PostgreSQL bloquea el caso al insertar su evento y actualiza el estado vigente en la misma transacción. Una comprobación diferida exige el evento inicial y la coincidencia de estados. |
| Edición acotada y conservación de actuaciones | El rol de aplicación puede actualizar el resumen y los importes del caso. Para las gestiones y el historial tiene permisos de consulta e inserción. |

## Índices iniciales

El esquema incluye índices para las bandejas, la búsqueda de casos por interesado y la consulta de gestiones e historial. Las restricciones de unicidad crean índices para RUT, correo y la combinación de cuenta con perfil. El índice parcial `ux_status_history_creation` limita a un evento inicial por caso. La comprobación diferida exige su existencia al confirmar el alta.

## Referencias de diseño

- [Normalización del modelo](normalization.md).
- Iplacex, `ME_3.pdf` y `Mat_Comp_S3_DD.pdf`.
- Informe de la Evaluación 1, objetivos, RF01–RF14, RNF01–RNF08 y matriz de estados.
- [PostgreSQL 17, restricciones](https://www.postgresql.org/docs/17/ddl-constraints.html).
- [PostgreSQL 17, triggers de restricción](https://www.postgresql.org/docs/17/sql-createtrigger.html).

## Instalación del esquema

El [script de migración](../database/07_feedback_integrity.sql) parte del [esquema inicial](../database/01_schema.sql) con los [catálogos](../database/02_seed_catalogs.sql). Se aplica después de crear el rol de aplicación y asignar sus [permisos](../database/05_application_permissions.sql). La migración rellena la autoría conforme a los responsables fijos, comprueba el historial existente y aplica las restricciones en una sola transacción. La [guía de PostgreSQL](postgresql-setup.md) detalla el orden de instalación.
