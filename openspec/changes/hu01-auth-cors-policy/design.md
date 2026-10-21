# Design

## Context

La motivacion y los limites funcionales estan en [proposal.md](proposal.md); los requisitos aprobados estan en [auth-cors-policy](specs/auth-cors-policy/spec.md) y [auth-http-contract](specs/auth-http-contract/spec.md). Este documento desarrolla solamente su solucion tecnica incremental para el [Issue #10](https://github.com/user-bcp-student05/workshop-auth-backend/issues/10).

El backend usa Java 25 y Quarkus 3.33.2.1 segun [pom.xml](../../../pom.xml), con REST Jackson, SmallRye Health, emision JWT y SQLite. CORS ya lo controla la capa HTTP de Quarkus en [application.properties](../../../src/main/resources/application.properties): esta habilitado y `quarkus.http.cors.origins` toma `AUTH_CORS_ORIGINS`, con fallback universal `/.*/`. Los metodos y headers tambien estan parametrizados mediante `AUTH_CORS_METHODS` y `AUTH_CORS_HEADERS`. No hace falta introducir una nueva capa de autorizacion.

El baseline protegido es [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md). [docs/TESTING.md](../../../docs/TESTING.md) identifica las caracterizaciones existentes de Resource, servicio, persistencia y JWT, y la suite de protocolo sobre el empaquetado prod. Su evidencia es historica, no una ejecucion de esta etapa. Para versiones de herramientas prevalece el POM actual sobre las versiones historicas citadas en esa documentacion.

La aprobacion del cambio OpenSpec no equivale a aprobacion de una lista concreta de origenes. La propuesta y las specs mantienen esa lista como pendiente. Se puede revisar este diseno sin elegir dominios; no se puede aplicar ni aceptar la politica operativa sin la lista y su aprobacion documentadas.

## Goals / Non-Goals

**Goals:**

- Resolver la restriccion en la configuracion CORS existente, con lista explicita y sin bypass universal por omision.
- Mantener fuera del cambio los componentes de negocio; limitar el impacto a configuracion, pruebas HTTP y evidencia documental.
- Obtener resultados reproducibles para peticiones directas y preflight, distinguiendo autorizacion CORS, status HTTP y respuesta de negocio.
- Hacer reversible la activacion sin migraciones de datos ni cambios de infraestructura.

**Non-Goals:**

- Crear filtros propios, endpoints, un proxy, un mecanismo de autenticacion entrante o dependencias nuevas.
- Cambiar metodos, headers, credenciales CORS, cache de preflight, puertos, hosts o perfiles por conveniencia de la prueba.
- Refactorizar Resource, servicio, persistencia o seguridad, ni corregir los errores heredados caracterizados.
- Cambiar proposal o specs aprobadas, crear tasks.md o implementar codigo en esta etapa.

## Protected Contracts

La matriz detallada de entradas y salidas sigue siendo la de `auth-http-contract` y el baseline; este diseno no la sustituye.

| Superficie | Proteccion requerida |
|---|---|
| `POST /auth/login` sin Origin o con origen aprobado | Sin bearer obligatorio; mismos DTO, validaciones y orden, espacios significativos, client opcional y propiedades desconocidas ignoradas. |
| Login exitoso | 200; token, expiresIn=3600, user y debug existentes; escape HTML4; sin secretos ni campos internos adicionales. JWT RS256 y claims existentes sin cambios. |
| Errores del handler | Mismos 400, 401 y 500, mensajes y cuerpos; incluido JDBC capturado como 401 y detalles del 500. |
| Errores previos al handler | En empaquetado prod, 400/415/406/405 sin cuerpo segun los casos documentados; no adaptar expectativas al modo test. |
| `GET /q/health` | Acceso publico, 200 con UP y checks vacio en configuracion normal; sin nuevos checks ni garantias sobre JDBC. |
| Origen no aprobado | Sin autorizacion CORS para ese origen; no se fija status, cuerpo ni llegada al handler. |

Las pruebas de salud que registran temporalmente un check fallido, documentadas en TESTING, no agregan un check real de negocio ni resuelven la pregunta abierta del baseline sobre su 503. No se agregan requisitos de disponibilidad por este cambio.

## Affected Components

| Componente | Intervencion futura prevista |
|---|---|
| `src/main/resources/application.properties` | Conservar CORS habilitado y los parametros existentes; eliminar el fallback universal de origenes. |
| Configuracion de ejecucion existente | Asignar AUTH_CORS_ORIGINS con la lista aprobada y conservar evidencia de su valor efectivo, sin nuevas piezas de infraestructura. |
| Pruebas HTTP y fixtures existentes | Reutilizar AuthResourceTest, AuthProtocolIT y TestSigningKeys; agregar solo la cobertura CORS y los overrides de laboratorio necesarios. |
| Documentacion del backend | Registrar lista aprobada, referencia de aprobacion, configuracion y resultados reproducibles; describir el nuevo requisito de arranque sin alterar el contrato de negocio. |
| Resource, servicio, repositorio, hashing y JWT | Sin modificaciones; sus suites siguen como regresion. |
| POM y gates existentes | Sin nuevas dependencias ni cambios de gates; reutilizar Surefire, Failsafe y JaCoCo. |

## Decisions

### 1. Mantener CORS en la capa HTTP de Quarkus

**Decision:** usar `quarkus.http.cors.*` y las variables existentes. La restriccion por origen pertenece a esa capa, antes del Resource, y debe cubrir tanto la respuesta de login como su preflight. Al ser configuracion HTTP general, comprobar tambien que no se rompe la consulta de salud.

**Alternativas:** un filtro REST propio duplicaria la politica y podria no cubrir preflight o errores anteriores al handler; un proxy agregaria infraestructura prohibida por el alcance. Cambiar autenticacion para rechazar origenes confundiria CORS con control de acceso. Se descartan las tres.

**Consecuencia:** no prometer un status para origen denegado ni forzar un cuerpo JSON desde la aplicacion. La asercion decisiva es la ausencia de un Access-Control-Allow-Origin que autorice al solicitante.

### 2. Lista explicita y ausencia de fallback universal

**Decision:** representar los origenes aprobados como valores literales completos de esquema, host y puerto cuando corresponda, separados mediante el formato de lista admitido por la configuracion existente. No introducir comodines ni regex universales. No se fija aqui ningun dominio, puerto de consumidor ni origen real.

Retirar `:/.*/` de la expresion de AUTH_CORS_ORIGINS y mantener la variable como entrada obligatoria, sin inventar una lista por defecto. La falta de esa entrada debe impedir arrancar con una politica universal; caracterizar la resolucion de la variable en la version actual de Quarkus antes de aceptar el cambio. Un valor vacio, comodin, regex o lista distinta de la aprobada no es una configuracion operativa aceptable: debe detectarse en la revision y comprobacion previa a activacion, no interpretarse como aprobacion implicita. No se propone un parser ni un validador Java nuevo.

**Alternativas:** conservar el fallback y depender de que todos los operadores lo sobrescriban deja una apertura silenciosa; una lista de localhost por defecto inventa autorizaciones; una lista vacia por defecto dependeria de semantica aun no caracterizada. Se elige configuracion explicita obligatoria.

**Trade-off:** exigir la entrada cambia un prerrequisito operativo de arranque, no los endpoints. Debe documentarse y probarse; los entornos de prueba necesitan sus propios valores aislados. No se modifica el requisito existente de clave de firma externa.

### 3. No ampliar la politica restante

**Decision:** conservar los valores y mecanismos de AUTH_CORS_METHODS y AUTH_CORS_HEADERS, sin alterar politica de credenciales o cache. Para probar preflight usar POST y content-type ya permitidos por la configuracion actual.

**Alternativas:** limitar metodos a POST o cambiar credenciales en la misma entrega ampliaria el compromiso del Issue y complicaria atribuir regresiones. Se difiere cualquier endurecimiento adicional a otro cambio aprobado.

### 4. Pruebas funcionales sobre HTTP real y configuracion aislada

**Decision:** reutilizar JUnit, RestAssured y los fixtures ya documentados. Configurar la lista de laboratorio antes de iniciar la aplicacion de prueba; no mutarla en caliente ni suponer que un cambio de variable afecta a un proceso ya iniciado. Usar origenes ficticios reservados de prueba, uno permitido y otro no incluido, sin presentarlos como la lista operativa aprobada.

En las pruebas de configuracion obligatoria, aislar la variable del entorno del operador y aportar una clave y SQLite de laboratorio validas para que un fallo de firma o persistencia no oculte la causa. Reutilizar TestSigningKeys donde corresponda. En pruebas del artefacto prod, asegurar que los valores lleguen al proceso de aplicacion, no solo al JVM de JUnit.

**Alternativas:** mocks del Resource no atraviesan CORS; pruebas unitarias de comparacion de strings no verifican Quarkus; usar el navegador como unica evidencia oculta status y headers y agrega dependencia de otro proyecto. Se eligen peticiones HTTP reproducibles sin acceder a consumidores externos.

## Characterization And Validation

Antes de modificar la politica, ejecutar y conservar una linea base de las suites actuales. No cambiar autenticacion, persistencia o JWT para hacer pasar casos CORS. No ejecutar en paralelo metodos que comparten mocks, tablas temporales o registro de salud, segun las restricciones de TESTING.

| Momento | Verificacion y criterio |
|---|---|
| Antes del cambio | AuthResourceTest: respuesta completa, validaciones, credenciales y espacios, errores 401/500, fallo JDBC y salud. Reutilizar AuthServiceTest, PasswordStorageTest y TokenServiceTest para la regresion del flujo critico. |
| Antes del cambio | AuthProtocolIT sobre empaquetado prod: 400 por JSON/tipo, 415, 406 y 405 con cuerpo vacio. No trasladar esos asserts al modo test, cuyos mappers pueden devolver diagnosticos. |
| Antes y despues | Capturar peticion directa y preflight con Origin en la politica actual y en la restringida. La captura inicial caracteriza el permiso universal; no convertir esa apertura en un requisito permanente de la suite final. |
| Despues, origen permitido | Login 200 y login 401 mantienen sus cuerpos; Access-Control-Allow-Origin coincide exactamente con el origen, nunca con `*`. Preflight autoriza ese origen, POST y content-type. |
| Despues, origen no permitido | Peticion directa y preflight no devuelven autorizacion para ese origen ni universal. Registrar status observado sin fijarlo como 401/403 ni exigir cuerpo de negocio. |
| Despues, compatibilidad | Repetir casos protegidos sin Origin y con origen permitido, incluida salud. Un cliente sin Origin no queda sujeto a autenticacion nueva. |
| Despues, configuracion | Sin AUTH_CORS_ORIGINS el arranque no puede quedar abierto; con lista explicita de laboratorio arranca y limita origenes; la configuracion efectiva coincide con la entrada. Verificar tambien separacion entre dos origenes permitidos, y un origen de distinto esquema, host o puerto no incluido. |
| Antes de aceptar | Repetir permitido/no permitido contra la configuracion autorizada en laboratorio y adjuntar aprobacion, valor efectivo y resultados. Un test con origenes ficticios no cierra esta evidencia. |

Para la implementacion posterior, `mvn -Dtest=AuthResourceTest test` sirve como comprobacion enfocada del Resource; `mvn -Dtest=AuthResourceTest -Dit.test=AuthProtocolIT verify` incluye protocolo prod. La suite CORS que se incorpore debera incluirse tambien en las selecciones enfocadas. El gate completo sigue siendo `mvn verify`, con los overrides de origenes de laboratorio configurados para los procesos correspondientes y el JDK compatible con el POM actual. No desactivar CodeQL, Dependency Review ni JaCoCo, ni reducir el minimo 0.85 por clase de lineas e instrucciones. La cobertura Java no demuestra por si sola cobertura de la politica del framework.

La evidencia futura debe identificar revision, perfil, lista utilizada, comando, origen, metodo, ruta, headers relevantes, status y headers de respuesta; comprobar cuerpos de negocio sin almacenar tokens utilizables, passwords ni claves privadas. No se han ejecutado estas pruebas funcionales al redactar el diseno; la validacion OpenSpec solo valida los artefactos.

## Migration Plan

1. **Preparacion:** obtener la lista aprobada y su referencia, identificar la configuracion existente donde se suministra y conservar una copia de la configuracion anterior. No activar la politica sin aprobacion; no modificar infraestructura.
2. **Caracterizacion:** ejecutar las suites anteriores y capturar el comportamiento CORS actual en laboratorio. Completar los casos faltantes antes de retirar el fallback; separar expectativas del contrato estable y del nuevo permiso por origen.
3. **Cambio minimo posterior:** retirar el fallback universal, suministrar la lista autorizada y adaptar solo fixtures/configuracion de prueba necesarios. Mantener sin cambios codigo de negocio, metodos, headers y dependencias.
4. **Validacion:** ejecutar la matriz CORS y de compatibilidad, verificar la configuracion efectiva y pasar los gates existentes. Si la variable obligatoria no produce el comportamiento previsto, detener la activacion y revisar esta decision, sin introducir silenciosamente un filtro propio o un fallback permisivo.
5. **Activacion controlada:** reiniciar con el mecanismo de ejecucion ya existente y comprobar salud, login permitido y origen no autorizado desde el laboratorio. Registrar evidencia antes de declarar aceptado el Issue; no hay migracion de datos.

### Rollback

Conservar antes de activar la revision del artefacto y los valores de configuracion anteriores, sin copiar secretos al repositorio. Si una lista incorrecta excluye un consumidor aprobado, priorizar corregir la entrada con la lista aprobada y reiniciar; no ampliar a `*` ni a `/.*/` como respuesta automatica.

Si la aplicacion del cambio impide arrancar o provoca regresiones de contrato, restaurar el artefacto y la configuracion previamente registrados mediante el procedimiento existente, con revision y autorizacion del responsable. Si esa restauracion repone la politica universal anterior, registrar explicitamente la reapertura de H-04 y no presentar el rollback como cumplimiento del Issue #10. Un rollback versionado futuro debera ser un cambio revisable, no un reset destructivo de la rama.

Tras restaurar, verificar arranque, salud y login sin Origin, y registrar de nuevo el comportamiento CORS. No revertir datos, claves, cambios ajenos o pruebas de caracterizacion utiles. Detener la activacion si no existe una configuracion de retorno conocida y autorizada.

## Risks / Trade-offs

- **Lista incompleta o formato incorrecto:** puede interrumpir acceso desde navegadores o el arranque. Mitigar con aprobacion trazable, revision del formato literal, prueba de configuracion efectiva y activacion reversible.
- **Variable omitida en algun perfil o proceso de prueba:** se elige fallo de arranque antes que permiso universal. Documentar la entrada obligatoria y probarla con fixtures aislados antes del despliegue.
- **Confundir CORS con autenticacion:** clientes fuera del navegador pueden seguir invocando endpoints. Mantener autentificacion actual y no atribuir al cambio bloqueo general ni nuevos 401/403.
- **Headers en errores o preflight distintos de lo supuesto:** Quarkus puede responder antes del Resource. Validar sobre HTTP real y empaquetado prod, sin fijar codigos no comprometidos.
- **Alcance HTTP global:** la politica tambien puede afectar salud desde un navegador. Verificar salud sin Origin y con origen permitido; no crear excepciones de ruta por defecto.
- **Diferencias entre documentacion historica y herramientas actuales:** usar el POM para el JDK y gates; no reutilizar porcentajes o resultados anteriores como evidencia nueva.
- **Reversion a configuracion permisiva:** restaura disponibilidad a costa de reabrir el riesgo. Exigir decision explicita y evidencia del estado restaurado.

## Approval Dependencies

La lista exacta, quien la aprueba y donde se registra su aprobacion siguen pendientes segun proposal y specs. Son condiciones bloqueantes de aplicacion y aceptacion, no preguntas tecnicas resueltas por este documento. Ningun origen de prueba concede autorizacion operativa. El diseno queda para revision humana; no se crea tasks.md ni se inicia implementacion.