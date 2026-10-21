# Proposal

## Why

El [Issue #10: HU-01 - Politica CORS para autenticacion](https://github.com/user-bcp-student05/workshop-auth-backend/issues/10) compromete acotar la politica CORS permisiva registrada en H-04. La parametrizacion ya existe, pero falta aprobar la lista de origenes: restringir la autorizacion de lectura desde navegadores reduce la exposicion sin cambiar el contrato HTTP.

## What Changes

### Alcance

- Aplicar una politica CORS limitada a la lista aprobada mediante la configuracion parametrizable existente, sin cambios de infraestructura.
- Autorizar un origen incluido en esa lista y no autorizar uno ajeno a ella, con resultados observables en los headers CORS, tanto en respuestas de negocio como en preflight aplicable.
- Exigir evidencia de la lista aprobada, configuracion que la refleje y pruebas funcionales de ambos casos.
- Formalizar la compatibilidad con [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md), baseline del Dia 1 indicado para este trabajo. Se usa su version presente en esta rama, cuya referencia de verificacion es 19/10/2026; no se reconstruye una version historica.

### Fuera de alcance

- Inventar o aprobar origenes, redefinir metodos o headers permitidos, o incorporar infraestructura.
- Cambiar autenticacion, JWT, persistencia, CORS como mecanismo de autenticacion, endpoints, DTO o errores de negocio/protocolo.
- Resolver otros hallazgos, endurecer errores 500 o ampliar health checks.
- Disenar la implementacion, crear design.md o tasks.md, modificar codigo o ejecutar la implementacion en esta entrega.

### Comportamiento a preservar

Se preservan rutas, metodos, acceso sin bearer previo, validaciones, estados, cuerpos y semantica de `POST /auth/login` y `GET /q/health` documentados en el baseline. La unica diferencia intencional es la autorizacion CORS para origenes no aprobados; no se promete un nuevo status 401/403 ni se confunde CORS con autorizacion del lado servidor. Las limitaciones y preguntas abiertas del contrato no se convierten en garantias nuevas.

## Capabilities

### New Capabilities

- `auth-cors-policy`: autorizacion CORS por origen aprobado, preflight y evidencia funcional trazable al Issue #10.
- `auth-http-contract`: proteccion observable del contrato de login y salud frente al cambio CORS; formaliza comportamiento existente, no agrega endpoints.

### Modified Capabilities

Ninguna: el inventario principal de OpenSpec no contiene capacidades existentes.

## Impact

La futura aplicacion afecta la politica HTTP CORS existente (`AUTH_CORS_ORIGINS`); `AUTH_CORS_METHODS` y `AUTH_CORS_HEADERS` conservan su alcance actual. No se proponen dependencias, infraestructura ni cambios del contrato de negocio. Los consumidores de navegador cuyo origen no sea aprobado dejaran de recibir autorizacion CORS.

### Aceptacion y evidencia

- Lista de origenes con aprobacion documentada y configuracion efectiva concordante.
- Pruebas funcionales reproducibles de origen permitido y no permitido, registrando origen, peticion, status y headers de respuesta, sin cambios de infraestructura.
- Escenarios de compatibilidad de login y salud coherentes con el baseline; los errores de protocolo no se confunden con errores del handler.

### Riesgos y preguntas abiertas

- **Pendiente bloqueante:** el Issue y sus comentarios consultados no contienen una lista aprobada. Debe identificarse quien la aprueba y adjuntarse evidencia antes de aplicar o aceptar la politica; esta propuesta no selecciona origenes reales.
- Una lista incorrecta puede interrumpir clientes legitimos: contrastar su aprobacion con la configuracion y los resultados funcionales.
- La ausencia de autorizacion CORS no impide clientes fuera del navegador: no presentar este cambio como autenticacion o bloqueo general de acceso.
- Las pruebas funcionales y la actualizacion de configuracion quedan pendientes de la fase autorizada posterior. Esta entrega acredita solo propuesta y specs para revision humana, no la finalizacion del Issue.