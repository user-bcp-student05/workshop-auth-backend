# Spec Delta

## Purpose

Formalizar el contrato existente de login y salud documentado en `docs/ENDPOINTS.md` como proteccion frente al cambio CORS del Issue #10. Estos requisitos caracterizan comportamiento actual, no nuevas funcionalidades ni recomendaciones de endurecimiento.

## ADDED Requirements

### Requirement: Superficie HTTP y acceso existentes

El servicio SHALL conservar `POST /auth/login` y `GET /q/health`, sus representaciones JSON y su acceso sin autenticacion bearer previa. Los escenarios de compatibilidad SHALL evaluarse sin `Origin` o con un origen aprobado y una politica CORS compatible con la peticion. Para origenes no aprobados solo cambia la autorizacion CORS definida en `auth-cors-policy`; no se promete que esas peticiones alcancen el handler.

#### Scenario: Login sin bearer previo
- **WHEN** se envia `POST /auth/login` sin `Authorization`, con `Content-Type: application/json`, `Accept: application/json` y credenciales validas de laboratorio
- **THEN** se obtiene 200 con el cuerpo de login exitoso
- **AND** la falta de bearer no produce 401 ni 403

#### Scenario: Solicitud sin Origin
- **WHEN** un cliente sin `Origin` envia un login con credenciales validas de laboratorio o consulta `GET /q/health`
- **THEN** conserva la respuesta de negocio documentada para esa operacion
- **AND** la restriccion CORS no se convierte en autenticacion entrante

### Requirement: Entrada y orden de validacion de login

El login SHALL aceptar un objeto JSON con `username` y `password` strings obligatorios, validar primero la presencia de username y despues password, y considerar ausente un valor omitido, null, vacio o solo espacios segun `String.trim()`. SHALL autenticar con los valores originales sin recortarlos, ignorar propiedades desconocidas y conservar `client` como opcional, nullable y no utilizado durante el login. No se agrega un limite de longitud ni se promete coercion de tipos.

#### Scenario: Cuerpo ausente o null
- **WHEN** el handler recibe request null por ausencia de cuerpo o literal JSON `null`
- **THEN** devuelve 400 con `{"message":"Request is null"}`

#### Scenario: Username ausente tiene prioridad
- **WHEN** username esta omitido, es null, vacio o solo espacios, incluso si password tambien falta
- **THEN** devuelve 400 con `{"message":"Username is required"}`

#### Scenario: Password ausente con username presente
- **WHEN** username supera la validacion de presencia y password esta omitido, es null, vacio o solo espacios
- **THEN** devuelve 400 con `{"message":"Password is required"}`

#### Scenario: Espacios conservados al autenticar
- **GIVEN** un usuario de laboratorio conocido y valores con espacios que no coinciden con sus credenciales almacenadas
- **WHEN** se envia un username no vacio con espacios adicionales o un password no vacio con espacios adicionales
- **THEN** la autenticacion no recorta esos valores para convertirlos en credenciales validas
- **AND** el 401 incluye el username exacto recibido, incluidos sus espacios

#### Scenario: Client opcional y propiedades desconocidas
- **WHEN** se envian credenciales validas omitiendo `client`, usando `client: null` o un client string y agregando una propiedad desconocida
- **THEN** el login sigue devolviendo 200 con el mismo contrato de respuesta

### Requirement: Respuesta exitosa de login

El login exitoso SHALL conservar HTTP 200 y los campos externos `token`, `expiresIn`, `user` y `debug`. `user` SHALL contener `username`, `displayName` escapado como HTML4 y `role` procedentes del usuario autenticado, sin id, password ni hash. `debug` SHALL conservar `authenticated-with-legacy-flow`; el rol no se restringe a un enum nuevo. El orden de propiedades JSON no es contractual.

#### Scenario: Forma y contenido de la respuesta exitosa
- **WHEN** se autentica correctamente un usuario de laboratorio conocido
- **THEN** el cuerpo contiene un token string, `expiresIn` entero de 64 bits con valor 3600 y el objeto `user` descrito
- **AND** `user.username` y `user.role` corresponden a ese usuario, sin exponer credenciales ni identificador interno
- **AND** `debug` es `authenticated-with-legacy-flow`

#### Scenario: Nombre visible con caracteres HTML
- **GIVEN** un usuario valido de laboratorio cuyo nombre visible contiene caracteres especiales HTML
- **WHEN** inicia sesion correctamente
- **THEN** `user.displayName` conserva el resultado de escape HTML4 documentado, incluidas las entidades aplicables

#### Scenario: Contrato del token emitido
- **WHEN** se obtiene un login exitoso
- **THEN** `token` es un JWT compacto firmado con RS256, no cifrado, verificable con la clave publica correspondiente
- **AND** `sub` corresponde al username, `role` al rol y `iat` al instante de emision en segundos epoch
- **AND** `exp - iat` coincide con `expiresIn`, cuyo valor es 3600
- **AND** no se exige un token literal estable ni se agregan requisitos de issuer, audience o endpoint verificador; el `jti` string observado no es un campo externo nuevo del login

### Requirement: Errores del handler de login

El servicio SHALL conservar los status, campos y mensajes documentados del handler, incluso los detalles internos existentes del 500. El Issue #10 no autoriza modificar esos errores.

#### Scenario: Usuario inexistente o password incorrecto
- **WHEN** el username no existe o el password no coincide
- **THEN** devuelve 401 con un unico campo `message` cuyo valor es `Invalid user <username> or password`, sustituyendo `<username>` por el valor exacto recibido

#### Scenario: Fallo de persistencia que el contrato caracteriza como usuario ausente
- **GIVEN** un fallo JDBC capturado que produce el resultado de usuario ausente documentado en el baseline
- **WHEN** se procesa el login con username `student`
- **THEN** devuelve 401 con `{"message":"Invalid user student or password"}`, sin distinguirlo de credenciales invalidas

#### Scenario: Excepcion capturada con mensaje
- **WHEN** una excepcion controlada `java.lang.IllegalStateException` con mensaje `controlled failure` llega al catch del Resource
- **THEN** devuelve 500 con `{"message":"controlled failure","exception":"java.lang.IllegalStateException"}`
- **AND** para otra excepcion capturada el campo `exception` identifica su clase completa real

#### Scenario: Excepcion capturada sin mensaje
- **WHEN** una excepcion controlada `java.lang.IllegalStateException` sin mensaje llega al catch del Resource
- **THEN** devuelve 500 con `{"message":"Unknown error","exception":"java.lang.IllegalStateException"}`

### Requirement: Errores anteriores al handler

En el empaquetado y perfil prod del baseline, el servicio SHALL conservar los errores de protocolo observados sin inventar un cuerpo JSON `message`. No se extienden esas observaciones a perfiles o configuraciones distintos sin verificacion.

#### Scenario: JSON mal formado o tipo incompatible
- **WHEN** se envia a login el cuerpo `{` o `{"username":{},"password":"x"}` como JSON
- **THEN** devuelve 400 sin cuerpo, de 0 bytes

#### Scenario: Content-Type no soportado
- **WHEN** se envia el cuerpo de login como `text/plain`
- **THEN** devuelve 415 sin cuerpo

#### Scenario: Representacion no aceptada para login
- **WHEN** se solicita login con `Accept: application/xml` como unica representacion aceptada
- **THEN** devuelve 406 sin cuerpo

#### Scenario: Metodo no soportado para login
- **WHEN** se usa `GET /auth/login`
- **THEN** devuelve 405 sin cuerpo

### Requirement: Salud publica y limites del contrato

El servicio SHALL conservar el comportamiento documentado de `GET /q/health`: sin bearer obligatorio, sin parametros de negocio y, en la configuracion observada sin checks propios, HTTP 200 con `{"status":"UP","checks":[]}`. SHALL NOT presentar ese UP como prueba de disponibilidad JDBC o de login exitoso, ni introducir checks nuevos por este Issue.

#### Scenario: Consulta de salud sin credenciales
- **WHEN** se consulta `GET /q/health` sin cuerpo ni `Authorization` en la configuracion observada
- **THEN** devuelve 200 con `{"status":"UP","checks":[]}`

#### Scenario: Negociacion y cuerpo opcional de salud
- **WHEN** se consulta `GET /q/health` con `Accept: application/xml` o con el cuerpo opcional `{}` y `Content-Type: application/json`
- **THEN** mantiene la respuesta JSON exitosa observada, sin aplicar el 406 de login ni usar el objeto como parametros de negocio

#### Scenario: Limites de la evidencia de salud y arranque
- **WHEN** se revisa la compatibilidad de salud frente al baseline
- **THEN** se conserva la limitacion de que no hay un check fallido del proyecto con el que reproducir un cuerpo real 503
- **AND** no se inventan cuerpos 503, checks JDBC ni garantia 405 para otros metodos de salud
- **AND** un proceso que no arranca, incluida una clave de firma ausente o invalida, no se representa automaticamente como respuesta HTTP 500 o 503