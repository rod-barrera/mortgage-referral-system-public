# Plan de pruebas y resultados

El plan reúne doce casos que abarcan el acceso, las cuentas y el perfil, los interesados, las derivaciones, los permisos, el seguimiento, la persistencia y la interfaz. La ronda del 17 de noviembre de 2026 registró los doce casos como aprobados. P12 corresponde a una comprobación técnica complementaria de seguridad e integridad.

Cada caso identifica su entorno, condición inicial, pasos principales, resultado esperado y resultado obtenido. Los datos utilizados son ficticios.

## Entornos y alcance

| Grupo | Entorno |
|---|---|
| P01, P02 y P04–P09 | Servicio publicado en Render y base de datos en Neon. |
| P03 | Laboratorio local aislado con cuentas ficticias de ambos perfiles. |
| P10 | Render y Neon, con sesiones nuevas después de reiniciar el servicio. |
| P11 | Interfaz publicada en Render y revisión del código local correspondiente a esa versión. |
| P12 | Aplicación y PostgreSQL en un laboratorio local aislado. |

La versión de aplicación publicada utilizada en la ronda corresponde a `df35b07`. Los resultados acreditan los escenarios y entornos descritos; las mediciones de carga, la operación de una entidad bancaria real y la evaluación crediticia quedan fuera de este alcance.

## Criterios de evaluación

Un caso se aprueba cuando sus resultados coinciden con lo esperado, los permisos se respetan y los cambios válidos conservan la coherencia de los datos. Se registra como fallo una operación válida que no puede completarse, una operación prohibida que se acepta o una diferencia entre el estado y su historial.

La ejecución se suspende cuando el entorno no está disponible o no pueden garantizarse los datos iniciales del caso. Se reanuda al recuperar el servicio y restablecer esas condiciones, registrando el resultado del recorrido completo.

## P01 Acceso y cierre de sesión

**Entorno:** servicio publicado. **Condición inicial:** cuentas activas de ejecutivo y supervisora.

**Pasos:** abrir una ruta protegida sin sesión, intentar un ingreso con contraseña incorrecta, ingresar con cada perfil y cerrar cada sesión. Volver a abrir una ruta protegida después del cierre.

**Resultado esperado:** el acceso protegido requiere autenticación, las credenciales incorrectas se rechazan, cada perfil obtiene su menú y el cierre invalida la sesión.

**Resultado obtenido:** se comprobaron el rechazo del acceso anónimo, el enmascaramiento de contraseña, los menús por perfil y la exigencia de autenticación después de salir. **Estado: aprobado.**

## P02 Administración de cuentas ejecutivas

**Entorno:** servicio publicado. **Condición inicial:** sesión de supervisora y una cuenta ejecutiva ficticia.

**Pasos:** crear una cuenta ejecutiva, intentar repetir su correo, editar sus datos, comprobar el acceso del ejecutivo a administración y desactivar la cuenta con una sesión abierta. Reactivarla y comprobar el ingreso con los datos vigentes.

**Resultado esperado:** el rol se asigna en el servidor, el correo es único, la edición conserva la identidad de la cuenta y la desactivación bloquea tanto sesiones existentes como nuevos ingresos.

**Resultado obtenido:** se rechazó el correo duplicado; la edición conservó identificador y perfil. El ejecutivo recibió HTTP 403 en administración y la desactivación revocó su acceso. La cuenta reactivada pudo ingresar. **Estado: aprobado.**

## P03 Edición del perfil propio

**Entorno:** laboratorio local aislado. **Condición inicial:** cuentas ficticias de ambos perfiles con sesiones abiertas.

**Pasos:** editar nombre y correo de cada perfil, intentar un cambio de contraseña con la actual incorrecta y realizar el cambio válido. Comprobar las sesiones anteriores y volver a ingresar con las credenciales vigentes.

**Resultado esperado:** los datos se guardan sin cambiar el rol; una contraseña actual incorrecta impide el cambio y las credenciales modificadas invalidan las sesiones anteriores.

**Resultado obtenido:** ambos perfiles editaron sus datos y cambiaron su contraseña conservando el rol. Se rechazó la contraseña actual incorrecta, se invalidaron dos sesiones por perfil y se comprobó el nuevo ingreso. **Estado: aprobado.**

## P04 Registro y recuperación de interesados

**Entorno:** servicio publicado. **Condición inicial:** sesión ejecutiva y datos ficticios de un interesado.

**Pasos:** intentar registrar un RUT inválido y una ficha sin contacto; registrar una ficha válida, buscarla con formato de RUT con puntos e intentar repetir el alta.

**Resultado esperado:** se rechazan el RUT inválido y la ausencia de contacto. La ficha existente se recupera y el alta duplicada conserva sus datos originales.

**Resultado obtenido:** las entradas inválidas se rechazaron y la búsqueda y el alta duplicada devolvieron la misma ficha, conservando su nombre original. **Estado: aprobado.**

## P05 Creación y corrección de derivaciones

**Entorno:** servicio publicado. **Condición inicial:** ejecutivo con una ficha de interesado seleccionada.

**Pasos:** intentar crear sin resumen, crear un caso con resumen válido y valores UF vacíos, consultar su detalle y corregir sus antecedentes antes de la recepción.

**Resultado esperado:** el resumen es obligatorio, los importes opcionales pueden omitirse y el caso queda pendiente con responsables, fecha e historial inicial. La corrección conserva el mismo caso.

**Resultado obtenido:** se exigió resumen y se aceptaron importes vacíos. La derivación quedó pendiente con sus antecedentes e historial inicial y conservó su identidad al editar el resumen y un valor UF. **Estado: aprobado.**

## P06 Bandejas y aislamiento entre ejecutivos

**Entorno:** servicio publicado. **Condición inicial:** dos ejecutivos con casos propios y una supervisora destinataria.

**Pasos:** buscar casos por identificador y RUT desde cada ejecutivo, intentar abrir directamente el detalle de un caso ajeno y consultar la bandeja supervisora con filtros.

**Resultado esperado:** cada ejecutivo accede únicamente a sus casos y una ruta directa tampoco permite consultar casos ajenos. La supervisora obtiene sus casos y puede filtrarlos.

**Resultado obtenido:** cada ejecutivo encontró su propio caso. El acceso ajeno recibió HTTP 404 y la supervisora consultó los dos casos, su orden y los filtros. **Estado: aprobado.**

## P07 Recepción y bloqueo de corrección

**Entorno:** servicio publicado. **Condición inicial:** derivación pendiente, visible para su ejecutivo y supervisora.

**Pasos:** recibir la derivación desde supervisión, recargar el detalle y volver a intentar la corrección desde el ejecutivo.

**Resultado esperado:** la recepción genera una actuación con fecha y autora, conserva los antecedentes y bloquea posteriores correcciones del ejecutivo.

**Resultado obtenido:** el caso quedó recibido y mantuvo esa situación al recargar. El ejecutivo conservó la consulta de antecedentes e historial y obtuvo el bloqueo de corrección. **Estado: aprobado.**

## P08 Registro de gestiones

**Entorno:** servicio publicado. **Condición inicial:** caso recibido y abierto.

**Pasos:** comprobar que la gestión exige recepción y contenido, agregar una gestión válida y consultar el historial desde ambos perfiles.

**Resultado esperado:** la actuación registra contenido, fecha y autora, se incorpora al historial y no cambia el estado del caso.

**Resultado obtenido:** se exigieron recepción y descripción. La gestión válida conservó el estado recibido y ambos perfiles consultaron el mismo historial cronológico. **Estado: aprobado.**

## P09 Transiciones y cierre

**Entorno:** servicio publicado. **Condición inicial:** caso abierto con actuaciones previas.

**Pasos:** consultar los cambios disponibles, recorrer las transiciones previstas incluido un retorno permitido, intentar cerrar sin observación y realizar un cierre válido. Revisar las acciones y el historial del caso cerrado.

**Resultado esperado:** solo se ofrecen y aceptan transiciones permitidas, el cierre exige una observación y el caso cerrado impide nuevas gestiones.

**Resultado obtenido:** se comprobó el catálogo y el retorno permitido. El cierre sin motivo se rechazó y el cierre válido dejó el caso rechazado con su observación y ocho actuaciones visibles en el historial. Las acciones posteriores quedaron bloqueadas. **Estado: aprobado.**

## P10 Persistencia después del reinicio

**Entorno:** Render y Neon. **Condición inicial:** caso cerrado con ficha, gestiones e historial registrados.

**Pasos:** reiniciar el servicio, abrir sesiones nuevas de ambos perfiles y recuperar el mismo caso por HTTPS.

**Resultado esperado:** los antecedentes, el estado y el historial permanecen disponibles en la base de datos después del reinicio.

**Resultado obtenido:** las sesiones nuevas recuperaron el mismo caso, con su ficha, el estado rechazado y las ocho actuaciones del historial. **Estado: aprobado.**

## P11 Interfaz y compatibilidad

**Entorno:** interfaz publicada en Render y revisión del código local correspondiente. **Condición inicial:** acceso a los dos perfiles y a las pantallas principales.

**Pasos:** revisar siete pantallas en Chrome y Edge a 360, 768 y 1366 píxeles de ancho; comprobar foco, navegación por teclado, etiquetas, mensajes de error y desplazamiento de tablas. Revisar la carga de recursos y la configuración de las plantillas, estilos y TypeScript.

**Resultado esperado:** los controles y mensajes permanecen accesibles, la distribución se adapta al ancho y se cargan los recursos definidos para el proyecto.

**Resultado obtenido:** se completaron las 42 combinaciones. Se comprobaron el foco visible, el error de RUT asociado al campo, el desplazamiento interno de la tabla y la carga de CSS y JavaScript. Los recursos de interfaz revisados coincidían con la versión publicada. **Estado: aprobado.**

## P12 Seguridad e integridad de datos

**Clasificación:** comprobación técnica complementaria. **Entorno:** laboratorio local aislado con aplicación y PostgreSQL. **Condición inicial:** esquema instalado con datos ficticios dedicados a la comprobación.

**Pasos:** comprobar el enmascaramiento de contraseña, su almacenamiento como hash, la protección CSRF, las restricciones de identidad, las relaciones y los permisos sobre las actuaciones. Comprobar la continuidad del estado y su historial y el rechazo de operaciones no permitidas.

**Resultado esperado:** las contraseñas no se almacenan como texto, las modificaciones protegidas requieren CSRF y la base conserva sus restricciones y la trazabilidad.

**Resultado obtenido:** se comprobaron formato BCrypt, rechazo CSRF con HTTP 403, unicidad de correo y RUT, 14 claves foráneas y protección de la bitácora. Se aprobaron 28 comprobaciones de integridad SQL, incluidas las de estado e historial. **Estado: aprobado.**

## Resultado de la ronda

Los doce casos quedaron aprobados dentro de su alcance. Los resultados relacionan las funciones del sistema con permisos, persistencia, presentación e integridad de datos. Las condiciones de infraestructura, los datos ficticios y los entornos indicados forman parte de la interpretación de estos resultados.
