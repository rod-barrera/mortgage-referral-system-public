# Normalización del modelo de datos

**Proyecto:** sistema web de derivación y seguimiento de consultas hipotecarias.  
**Modelo lógico en 3FN:** siete relaciones y 35 atributos.  
**Esquema físico vigente:** ocho tablas y 41 campos tras aplicar la migración 07.

Las secciones siguientes muestran la transformación del modelo de partida a 1FN, 2FN y 3FN. Después se explican las decisiones de auditoría e integridad que incorporó el esquema físico. El [diccionario vigente](data-model.md) y sus [ocho fichas estructuradas](data-dictionary.json) describen el resultado implementado.

## Reglas del dominio utilizadas

- Cada derivación tiene una cuenta ejecutiva creadora y una supervisora destinataria distintas. Ambos responsables son fijos: no se reasignan.
- Todas las gestiones pertenecen a la supervisora destinataria del caso.
- El evento inicial del historial pertenece al ejecutivo creador. Se reconoce por `previous_status_id IS NULL`; los demás eventos pertenecen a la supervisora.
- El sistema tiene estos dos perfiles y no incorpora otros actores.
- Una ficha de interesado se reconoce por un RUT único y puede participar en varias derivaciones.
- Los nombres de los catálogos Rol y Estado y el correo de acceso de Usuario son únicos. El correo de contacto del interesado no es único.
- Cada gestión y evento tiene su propio identificador; varias actuaciones o eventos pueden compartir una fecha.

Estas reglas determinan qué datos se almacenan una vez y cuáles se obtienen mediante relaciones. En el modelo lógico de partida, la autoría de una actuación o evento se obtiene a través de los responsables del caso. El esquema físico conserva además la cuenta autora de forma explícita; esta decisión se explica después del desarrollo de 3FN. El trigger `preserve_referral_responsibles` impide cambiar `executive_id` o `supervisor_id` y rechaza la operación con SQLSTATE 23514; el mapeo Java usa `updatable=false` para ambos campos. El rol de la aplicación tampoco tiene UPDATE ni DELETE sobre las gestiones o los eventos.

## Punto de partida

Una ficha conceptual puede reunir los datos del caso, de la persona interesada y del estado, además de listas de gestiones y eventos. Una cuenta puede incluir el identificador y la descripción de su perfil.

```text
CaseDraft(
  referral_id,
  lead_id, rut, lead_name, phone, contact_email,
  executive_id, supervisor_id,
  current_status_id, status_name, is_final,
  summary, created_at, property_value_uf, down_payment_uf,
  follow_ups[{follow_up_id, author_id, description, created_at}],
  status_changes[{status_history_id, previous_status_id, new_status_id,
                  author_id, changed_at, comment}]
)

UserDraft(user_id, role_id, role_name, name, email, password_hash, active)
```

Aquí `author_id` es un dato redundante del esquema conceptual que se examina durante la normalización. Las listas representan grupos repetitivos. Los datos del interesado, del estado y del perfil también pueden repetirse entre casos o cuentas.

## Paso 1. Primera forma normal

### Antes

Se parte de `CaseDraft` y `UserDraft`, representados arriba. En `CaseDraft`, un mismo caso puede contener varias gestiones y varios eventos dentro de sus listas. Esa organización no representa una fila por actuación ni una fila por evento.

### Dependencias y problema identificado

```text
referral_id → datos del caso, lead_id, executive_id,
              supervisor_id, current_status_id
follow_up_id → datos de una gestión y caso al que pertenece
status_history_id → datos de un evento y caso al que pertenece
user_id → datos de la cuenta
```

La cantidad de gestiones y eventos puede variar de forma independiente. Cada identificador de gestión o evento determina una actuación individual, por lo que sus datos se separan en filas relacionadas con el caso. Cada campo contiene un valor de su dominio; no se almacenan listas separadas por comas ni columnas numeradas por actuación.

### Después

```text
Referral1NF(
  referral_id, lead_id, rut, lead_name, phone, contact_email,
  executive_id, supervisor_id, current_status_id, status_name, is_final,
  summary, created_at, property_value_uf, down_payment_uf
)

FollowUp1NF(
  follow_up_id, referral_id, author_id, description, created_at
)

StatusHistory1NF(
  status_history_id, referral_id, previous_status_id, new_status_id,
  author_id, changed_at, comment
)

User1NF(
  user_id, role_id, role_name, name, email, password_hash, active
)
```

**Resultado:** cuatro relaciones con valores atómicos y una fila por cuenta, caso, gestión o evento. El teléfono y el correo son campos distintos; cada uno contiene como máximo un dato de ese tipo. El nombre completo se mantiene en un campo porque el alcance no requiere operar por separado sus componentes. La ausencia de un contacto se representa con `NULL` y se exige al menos teléfono o correo.

Las relaciones todavía contienen descripciones y autores redundantes. Cumplir 1FN no resuelve esas dependencias.

## Paso 2. Segunda forma normal

### Antes

Se revisan las cuatro relaciones obtenidas en 1FN:

```text
Referral1NF(referral_id, lead_id, rut, lead_name, phone, contact_email,
             executive_id, supervisor_id, current_status_id,
             status_name, is_final, summary, created_at,
             property_value_uf, down_payment_uf)

FollowUp1NF(follow_up_id, referral_id, author_id, description, created_at)

StatusHistory1NF(status_history_id, referral_id, previous_status_id,
                  new_status_id, author_id, changed_at, comment)

User1NF(user_id, role_id, role_name, name, email, password_hash, active)
```

### Claves y dependencias

| Relación | Claves candidatas | Dependencia completa respecto de la clave |
|---|---|---|
| Referral1NF | referral_id | referral_id determina todos los atributos de la fila. El RUT no identifica el caso: una persona puede originar varios. |
| FollowUp1NF | follow_up_id | follow_up_id determina referral_id, author_id, description y created_at. |
| StatusHistory1NF | status_history_id | status_history_id determina referral_id, previous_status_id, new_status_id, author_id, changed_at y comment. |
| User1NF | user_id; email | Cada una de estas claves determina todos los datos de la cuenta; el correo de acceso es obligatorio y único. |

Todas las claves candidatas identificadas son simples. Por tanto, no existe una parte propia de una clave compuesta de la que pueda depender un atributo no primo: no hay dependencias parciales. La fecha de una actuación o evento no es clave candidata. El índice que limitará el evento inicial por caso es parcial y tampoco convierte `referral_id` en clave de todos los eventos.

### Después

La comprobación de 2FN conserva las relaciones y sus atributos:

```text
Referral2NF(referral_id, lead_id, rut, lead_name, phone, contact_email,
             executive_id, supervisor_id, current_status_id,
             status_name, is_final, summary, created_at,
             property_value_uf, down_payment_uf)

FollowUp2NF(follow_up_id, referral_id, author_id, description, created_at)

StatusHistory2NF(status_history_id, referral_id, previous_status_id,
                  new_status_id, author_id, changed_at, comment)

User2NF(user_id, role_id, role_name, name, email, password_hash, active)
```

**Resultado:** las cuatro relaciones cumplen 2FN sin una descomposición adicional. Se mantienen los atributos para revisar sus dependencias transitivas en 3FN.

## Paso 3. Tercera forma normal

### Antes

El punto de partida son las cuatro relaciones obtenidas en 2FN. Aún se almacenan los datos descriptivos del interesado, estado y rol, y se repite el autor en cada gestión y evento:

```text
Referral2NF(referral_id, lead_id, rut, lead_name, phone, contact_email,
             executive_id, supervisor_id, current_status_id,
             status_name, is_final, summary, created_at,
             property_value_uf, down_payment_uf)

FollowUp2NF(follow_up_id, referral_id, author_id, description, created_at)

StatusHistory2NF(status_history_id, referral_id, previous_status_id,
                  new_status_id, author_id, changed_at, comment)

User2NF(user_id, role_id, role_name, name, email, password_hash, active)
```

### Dependencias y descomposición

#### Datos del interesado

```text
referral_id → lead_id
lead_id → rut, lead_name, phone, contact_email
rut → lead_id, lead_name, phone, contact_email
```

Los datos personales corresponden a la ficha, no a cada caso que la utiliza. Se crea `lead` y `referral` conserva `lead_id`. Así, varias derivaciones reutilizan la misma ficha y no almacenan copias del nombre y los contactos. Se mantiene una sola fila por RUT.

#### Descripción del estado

```text
referral_id → current_status_id
current_status_id → status_name, is_final
```

La descripción y la condición final se separan en `status`. El caso conserva `current_status_id`; los eventos referencian por separado el estado previo y el nuevo.

#### Descripción del perfil

```text
user_id → role_id
role_id → role_name
```

Se crea `role` y la cuenta conserva `role_id`. El nombre del perfil se consulta desde el catálogo. La relación resultante de cuentas se denomina `user_account`.

#### Autor de la gestión

```text
follow_up_id → referral_id
referral_id → supervisor_id
author_id de la gestión = supervisor_id del caso
```

En `FollowUp2NF`, la regla de supervisora fija implica `referral_id → author_id`. Un caso puede tener varias gestiones, por lo que `referral_id` no es superclave de esa relación y `author_id` no es atributo primo. Se elimina el autor almacenado en cada gestión y se obtiene mediante `follow_up.referral_id → referral.supervisor_id → user_account.user_id`.

#### Autor del evento del historial

El autor depende del caso y de si el evento es inicial. La condición de inicio se obtiene de `previous_status_id IS NULL`.

```text
Si previous_status_id IS NULL:
  author_id del evento = referral.executive_id

Si previous_status_id IS NOT NULL:
  author_id del evento = referral.supervisor_id
```

Para expresar la dependencia se puede llamar `event_kind` a la clasificación calculada INICIAL/POSTERIOR. Se utiliza solo como expresión de análisis, derivada de la condición de estado previo:

```text
status_history_id → referral_id, previous_status_id
previous_status_id IS NULL → clasificación INICIAL
previous_status_id IS NOT NULL → clasificación POSTERIOR
(referral_id, event_kind) → author_id
```

La combinación caso/clasificación no identifica todos los eventos: un caso puede tener varios eventos posteriores. El autor almacenado resulta redundante porque se reconstruye de los responsables fijos y de una condición ya disponible en cada evento. Se elimina `author_id`; `event_kind` tampoco se almacena como una columna adicional.

La consulta del autor utiliza:

```sql
CASE
  WHEN status_history.previous_status_id IS NULL
    THEN referral.executive_id
  ELSE referral.supervisor_id
END
```

La expresión se aplica tras unir `status_history.referral_id` con `referral.referral_id`. El identificador resultante referencia la cuenta del autor. La inmutabilidad de los responsables y los dos perfiles definidos mantienen la interpretación de todos los eventos.

### Después: modelo lógico en 3FN

```text
role(
  role_id PK, name UQ
)

user_account(
  user_id PK, role_id FK, name, email UQ, password_hash, active
)

lead(
  lead_id PK, rut UQ, name, phone, contact_email
)

status(
  status_id PK, name UQ, is_final
)

referral(
  referral_id PK, lead_id FK, executive_id FK, supervisor_id FK,
  current_status_id FK, summary, created_at,
  property_value_uf, down_payment_uf
)

follow_up(
  follow_up_id PK, referral_id FK, description, created_at
)

status_history(
  status_history_id PK, referral_id FK, previous_status_id FK,
  new_status_id FK, changed_at, comment
)
```

| Tabla | Campos | Claves candidatas |
|---|---:|---|
| role | 2 | role_id; name |
| user_account | 6 | user_id; email |
| lead | 5 | lead_id; rut |
| status | 3 | status_id; name |
| referral | 9 | referral_id |
| follow_up | 4 | follow_up_id |
| status_history | 6 | status_history_id |
| **Total** | **35** | **Siete tablas** |

El [diccionario vigente](data-model.md#diccionario-de-datos) detalla tipos, longitudes, nulabilidad, valores predeterminados y restricciones del esquema físico que amplía este modelo lógico.

## Dependencias funcionales del modelo lógico

```text
role_id → name
name de role → role_id

user_id → role_id, name, email, password_hash, active
email de user_account → user_id, role_id, name, password_hash, active

lead_id → rut, name, phone, contact_email
rut de lead → lead_id, name, phone, contact_email

status_id → name, is_final
name de status → status_id, is_final

referral_id → lead_id, executive_id, supervisor_id, current_status_id,
              summary, created_at, property_value_uf, down_payment_uf

follow_up_id → referral_id, description, created_at

status_history_id → referral_id, previous_status_id, new_status_id,
                    changed_at, comment
```

En las dependencias funcionales no triviales identificadas del modelo lógico, los determinantes son claves candidatas de sus respectivas tablas. Las descripciones de los catálogos, los datos de contacto y los autores derivados no se repiten como atributos dependientes de campos no clave dentro de otra tabla. Con las reglas de este proyecto, las siete relaciones cumplen 3FN.

## Conservación de la información y las relaciones

- Los datos del interesado, del estado y del perfil se recuperan al unir las FK con las claves únicas de sus tablas. Cada separación conserva el identificador de referencia.
- Los datos de una gestión o evento se recuperan junto con el caso al que pertenecen. Los responsables fijos y la condición de evento inicial permiten reconstruir el autor.
- El nombre y el correo de los responsables pertenecen a `user_account`; no se copian en `referral`, `follow_up` o `status_history`.
- Las relaciones mantienen los objetivos y RF01–RF14 y RNF01–RNF08. El alcance no incorpora reasignaciones ni otros actores.

## Esquema físico vigente y decisiones de auditoría

La [migración 07](../database/07_feedback_integrity.sql) guarda el autor de cada gestión y evento con una FK a la cuenta. Los autores existentes se recuperan con la regla previa de responsables fijos. Los nuevos registros guardan el autor explícito y PostgreSQL verifica su correspondencia con el responsable del caso.

Bajo la regla vigente, `referral_id → author_id` se mantiene para las gestiones. En el historial el autor depende del caso y de si el evento es inicial. Guardar el autor es una redundancia deliberada de auditoría. La descripción del esquema físico reconoce esa decisión en lugar de atribuirle el mismo resultado estricto de 3FN de la preentrega. Las columnas generadas de perfil en `referral` son constantes técnicas de integridad y no agregan hechos editables al modelo conceptual.

La tabla `status_transition` conserva nueve pares. Su clave es `(from_status_id, to_status_id)` y el historial la referencia con una FK compuesta. El estado anterior nulo permite el evento inicial, sujeto a su propia regla de estado. El evento posterior sincroniza el estado vigente bajo un bloqueo por caso. Una comprobación diferida exige el evento inicial y la coincidencia del caso con el último evento al cerrar la transacción.

### Tablas del esquema vigente

```text
role(role_id PK, name UQ)

user_account(user_id PK, role_id FK, name, email UQ, password_hash, active)

lead(lead_id PK, rut UQ, name, phone, contact_email)

status(status_id PK, name UQ, is_final)

referral(
  referral_id PK, lead_id FK, executive_id FK, supervisor_id FK,
  current_status_id FK, summary, created_at,
  property_value_uf, down_payment_uf,
  executive_role generado, supervisor_role generado
)

follow_up(
  follow_up_id PK, referral_id FK, description, created_at, author_id FK
)

status_history(
  status_history_id PK, referral_id FK, previous_status_id FK,
  new_status_id FK, changed_at, comment, author_id FK
)

status_transition(from_status_id PK/FK, to_status_id PK/FK)
```

| Tabla | Campos | Decisión del esquema físico |
|---|---:|---|
| role | 2 | Catálogo de perfiles. |
| user_account | 6 | Cuenta y perfil; unicidad adicional de `(user_id, role_id)` para las FK compuestas. |
| lead | 5 | Ficha reutilizable por RUT. |
| status | 3 | Catálogo de estados y condición de cierre. |
| referral | 11 | Nueve atributos del modelo lógico y dos columnas generadas para comprobar perfiles. |
| follow_up | 5 | Cuatro atributos del modelo lógico y autoría explícita. |
| status_history | 7 | Seis atributos del modelo lógico y autoría explícita. |
| status_transition | 2 | Clave compuesta con las nueve transiciones permitidas. |
| **Total** | **41** | **Ocho tablas y 14 claves foráneas.** |

La adición de `status_transition` no repite descripciones: cada par identifica una transición permitida y carece de atributos no clave. Las columnas generadas de perfil son constantes técnicas. La autoría persistida conserva una decisión de auditoría que, con los responsables fijos del alcance actual, admite derivación desde otras relaciones. Por eso el análisis distingue el modelo lógico normalizado y las redundancias controladas del esquema físico.

## Estado actual e historial

`referral.current_status_id` conserva el estado vigente para consultar las bandejas. Ese dato también se obtiene del último evento válido: existe redundancia controlada entre relaciones.

No se duplica la descripción del estado dentro del caso. La relación `referral` conserva la dependencia `referral_id → current_status_id`; esta decisión no introduce por sí sola una dependencia transitiva entre sus atributos. La coherencia entre caso e historial exige el siguiente invariante:

> El estado actual de cada derivación coincide con el estado nuevo del último evento válido de su historial.

La implementación mantiene este invariante mediante el siguiente procedimiento:

1. Crear el caso en Pendiente de recepción y su evento inicial con estado previo NULL dentro de una misma transacción. El autor del evento se obtiene del ejecutivo creador.
2. Obtener y bloquear la fila del caso antes de validar una transición o registrar una gestión.
3. Comprobar la sesión, el responsable autorizado, el estado vigente, la transición y la observación del cierre. Las gestiones y los eventos posteriores al inicial corresponden a la supervisora fija.
4. Insertar el evento; el trigger de PostgreSQL sincroniza el estado del caso dentro de la misma transacción. Un fallo revierte ambas escrituras.
5. Mantener el bloqueo hasta finalizar la transacción y ordenar los eventos del caso por `status_history_id`, asignado tras obtener ese bloqueo. Los saltos de la secuencia de identidad no representan eventos perdidos.
6. Conservar los eventos y los responsables del caso sin edición ni reasignación desde la aplicación.

El índice único parcial limita a uno los eventos iniciales por caso. Los triggers diferidos exigen su existencia y la coincidencia del estado vigente con el último evento al confirmar la transacción. El trigger que registra el cambio valida al autor y el estado anterior bajo un bloqueo del caso. Las FK hacia `status_transition` y el CHECK de cierre completan las reglas de transición y observación.

## Referencias de diseño

- [Modelo y diccionario de datos](data-model.md): esquema final, campos, claves y restricciones.
- Iplacex, `ME_3.pdf`, páginas 10–15: atomicidad, dependencias y normalización.
- Iplacex, `Mat_Comp_S3_MODREL.pdf`: claves y relaciones del modelo relacional.
- Informe final de la Evaluación 1: entidades y reglas del seguimiento.
