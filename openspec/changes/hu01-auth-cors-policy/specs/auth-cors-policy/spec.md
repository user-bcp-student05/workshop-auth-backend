# Spec Delta

## Purpose

Definir la autorizacion CORS observable por origen para cumplir el Issue #10 sin inventar la lista autorizada, alterar el contrato de endpoints ni cambiar infraestructura.

## ADDED Requirements

### Requirement: Politica limitada a origenes aprobados

El servicio SHALL autorizar por CORS exclusivamente los origenes de una lista aprobada y documentada, utilizando la configuracion parametrizable existente. La configuracion efectiva SHALL reflejar esa lista y no conservar una autorizacion universal que permita origenes ajenos a ella. Esta spec no fija origenes reales ni concede aprobacion; la lista sigue pendiente segun el Issue #10.

#### Scenario: Configuracion concordante con la aprobacion
- **GIVEN** una lista de origenes con evidencia documentada de aprobacion
- **WHEN** se contrasta la politica CORS efectiva con esa lista
- **THEN** los origenes que reciben autorizacion CORS corresponden a la lista aprobada
- **AND** la evidencia identifica la lista, su aprobacion y la configuracion existente utilizada, sin cambios de infraestructura

#### Scenario: Falta de aprobacion de origenes
- **WHEN** no existe una lista aprobada y documentada
- **THEN** la aprobacion y la configuracion autorizada permanecen pendientes
- **AND** valores de laboratorio empleados en pruebas no se presentan como origenes aprobados ni como cumplimiento completo del Issue

### Requirement: Autorizacion observable del origen permitido

Para una peticion de un origen aprobado que cumpla los metodos y headers de la politica existente, el servicio SHALL devolver `Access-Control-Allow-Origin` con ese origen, sin sustituirlo por una autorizacion universal. La autorizacion CORS SHALL ser independiente del exito o error de negocio del login.

#### Scenario: Login exitoso desde origen aprobado
- **GIVEN** un origen incluido en la lista aprobada
- **WHEN** se envia `POST /auth/login` con ese `Origin`, JSON valido y credenciales validas de laboratorio
- **THEN** la respuesta incluye `Access-Control-Allow-Origin` igual al origen solicitado
- **AND** conserva el status 200 y el cuerpo exitoso del baseline `docs/ENDPOINTS.md`

#### Scenario: Login con credenciales invalidas desde origen aprobado
- **GIVEN** un origen incluido en la lista aprobada
- **WHEN** se envia `POST /auth/login` con ese `Origin`, username `student` y password incorrecto no vacio
- **THEN** la respuesta incluye `Access-Control-Allow-Origin` igual al origen solicitado
- **AND** conserva el status 401 y el cuerpo `{"message":"Invalid user student or password"}`

### Requirement: Ausencia de autorizacion para origen no permitido

Para un origen no incluido en la lista aprobada, el servicio SHALL NOT devolver un `Access-Control-Allow-Origin` que autorice ese origen, ni mediante eco del origen ni mediante `*`. No se prescribe un status HTTP de rechazo: ausencia de autorizacion CORS no equivale a rechazo de credenciales ni garantiza bloqueo de clientes fuera del navegador.

#### Scenario: Peticion directa desde origen no aprobado
- **GIVEN** un origen no incluido en la lista aprobada
- **WHEN** se envia `POST /auth/login` con ese `Origin` y una peticion de negocio valida
- **THEN** la respuesta no incluye autorizacion CORS para ese origen
- **AND** el resultado no se interpreta como un nuevo contrato de error 401 o 403

### Requirement: Preflight coherente con la politica por origen

El servicio SHALL aplicar la misma restriccion de origen a las consultas preflight de login, conservando el alcance de metodos y headers de la configuracion existente. Esta exigencia observa la politica CORS del Issue #10 y no crea un nuevo endpoint de negocio ni fija un status o cuerpo para `OPTIONS`.

#### Scenario: Preflight para origen aprobado
- **GIVEN** un origen aprobado y `POST` y `content-type` permitidos por la configuracion existente
- **WHEN** se envia `OPTIONS /auth/login` con ese `Origin`, `Access-Control-Request-Method: POST` y `Access-Control-Request-Headers: content-type`
- **THEN** la respuesta incluye `Access-Control-Allow-Origin` igual a ese origen
- **AND** los headers CORS de respuesta permiten el metodo y el header solicitados

#### Scenario: Preflight para origen no aprobado
- **GIVEN** un origen ajeno a la lista aprobada y los mismos metodo y header solicitados
- **WHEN** se envia el preflight de login con ese `Origin`
- **THEN** la respuesta no incluye autorizacion CORS para ese origen

### Requirement: Evidencia funcional trazable

La aceptacion del cambio SHALL contar con resultados funcionales reproducibles para origen permitido y no permitido, usando la configuracion existente sin cambios de infraestructura, y con la lista aprobada y la configuracion actualizada exigidas por el Issue #10. Las pruebas con origenes ficticios solo acreditan comportamiento, no la aprobacion de la lista real.

#### Scenario: Revision de evidencia de aceptacion
- **WHEN** se revisa el cumplimiento del Issue #10
- **THEN** se dispone de la lista aprobada, la configuracion efectiva correspondiente y los resultados de ambos casos
- **AND** cada resultado identifica origen, metodo, ruta, headers relevantes de solicitud, status y headers CORS recibidos
- **AND** la evidencia permite contrastar los escenarios de compatibilidad de `auth-http-contract`
- **AND** no se adjuntan secretos reales ni se declara ejecutada una prueba solo por existir este escenario