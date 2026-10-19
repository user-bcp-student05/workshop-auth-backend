# Plan de correccion de items pendientes

Fecha: 19/10/2026. Items seleccionados expresamente por el usuario: H-04 y H-06, ambos Pendiente en [HALLAZGOS.md](HALLAZGOS.md). Se prioriza primero H-04 por severidad alta y despues H-06 por severidad media. Cada apartado tiene alcance independiente; no incluye trabajos de los items ya resueltos.

Estado de esta revision: propuesta pendiente de aprobacion para implementar. La seleccion de items no autoriza modificar el contrato. Este encargo solo actualiza PLAN.md; no cambia codigo, pruebas, configuracion, estados del backlog ni [docs/ENDPOINTS.md](docs/ENDPOINTS.md).

Trazabilidad: el plan anterior y su aprobacion de Ruben Vilca del 19/10/2026 se conservan en Git, recuperables con `git show 3360485:PLAN.md` desde este repositorio. Esa aprobacion correspondia a la revision anterior y no se traslada a esta propuesta. Las decisiones historicas y fuentes SCA citadas por el backlog siguen consultables en esa version; no se reabren aqui.

## Item: H-04 - CORS permisivo por defecto

### Identificacion y alcance

Item H-04 de [HALLAZGOS.md](HALLAZGOS.md), Vulnerabilidad, severidad alta, Pendiente. El secreto fijo ya fue eliminado y la clave externa es obligatoria: no se vuelve a planificar esa parte resuelta ni se cambia el JWT. La deuda restante es el origen permisivo por defecto y la falta de una politica de origenes/metodos/cabeceras acordada.

Evidencia actual: [application.properties:5](src/main/resources/application.properties#L5) usa AUTH_CORS_ORIGINS con fallback /.*/; [metodos y cabeceras](src/main/resources/application.properties#L6) tambien tienen defaults amplios. Una pagina de cualquier origen puede obtener autorizacion CORS en la configuracion por defecto; CORS no sustituye autenticacion ni impide peticiones de clientes no navegador.

### Cambio propuesto

1. Antes de tocar configuracion, obtener de los responsables la lista exacta de origenes autorizados por ambiente, metodos y cabeceras requeridos. No asumir URLs de frontend, puertos, cookies ni necesidad de permitir credenciales. Coordinar sin leer ni modificar otros componentes del workspace.
2. Aprovechar AUTH_CORS_ORIGINS, AUTH_CORS_METHODS y AUTH_CORS_HEADERS, ya implementadas, para operar con listas explicitas. Probar primero una politica acotada mediante perfiles/variables de prueba, conservando las respuestas de POST /auth/login y GET /q/health. No agregar dependencias ni implementar un filtro CORS propio.
3. La correccion definitiva propuesta es retirar el fallback permisivo y adoptar un valor cerrado o una lista explicita aprobada. La decision exacta sobre ausencia de origenes y aislamiento del laboratorio debe quedar acordada antes de implementar; no introducir un fallo de arranque o una lista nueva silenciosamente.

Limite contractual: [docs/ENDPOINTS.md](docs/ENDPOINTS.md) documenta tanto las variables configurables como el default permisivo. Usar una lista explicita en un entorno ya configurado es compatible con esa variabilidad, pero no elimina el default inseguro. Cambiarlo globalmente afecta al contrato documentado. H-04 no pide explicitamente cambiar ese contrato: este plan no autoriza retirar el fallback ni editar el contrato. Para cerrar completamente el item se requiere una decision expresa de alcance/compatibilidad y una revision del plan que autorice ese cambio. Mientras tanto solo son viables pruebas y documentacion operativa compatibles; no se declara el item resuelto.

### Archivos a tocar

- [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java): ampliar pruebas CORS reutilizando characterizesHealthAndCors y corsHonorsConfiguredOrigins; no mezclar pruebas de filtracion de errores.
- [TestSigningKeys.java](src/test/java/com/bcp/workshop/auth/TestSigningKeys.java): solo si se necesita reutilizar el fixture aislado para una configuracion CORS de prueba; mantener SQLite y claves efimeras en target. No introducir configuracion de CORS en el emisor.
- [README.md](README.md): documentar valores aprobados y procedimiento operativo, sin inventar origenes de consumidores reales.
- [application.properties](src/main/resources/application.properties): candidato exclusivo para la correccion del default, bloqueado hasta autorizacion contractual; no es necesario volver a parametrizar valores ya externalizados.
- [HALLAZGOS.md](HALLAZGOS.md): nota de evidencia al implementar; conservar Pendiente si solo hay mitigacion por entorno.

No se tocan AuthResource, AuthService, TokenService, hashes, SQL, esquema de datos, dependencias ni respuestas de error por este item. No se modifica docs/ENDPOINTS.md en el alcance vigente.

### Prueba previa a tocar configuracion

Ampliar y ejecutar primero las pruebas existentes con dos configuraciones aisladas:

- Default actual: preflight OPTIONS y POST de login con un Origin de laboratorio reciben la autorizacion CORS actual; el cuerpo/status de login y el GET de salud permanecen como en el contrato.
- Lista explicita suministrada por configuracion de prueba: origen permitido, origen ajeno y origen parecido pero diferente; comprobar coincidencia exacta del origen permitido. Verificar preflight con metodo/cabeceras admitidos y no admitidos, peticion real y peticion sin Origin. No convertir ausencia de autorizacion CORS automaticamente en una expectativa de HTTP 403.

Registrar los resultados antes del cambio. Caracterizar con configuracion acotada no demuestra que el fallback ya este corregido. Solo tras la autorizacion correspondiente, escribir una regresion que exija que la ausencia de configuracion no autorice origenes arbitrarios; esa regresion debe fallar con el default actual y pasar tras corregirlo, sin rebajar la expectativa.

### Validacion y cierre

Repetir las pruebas CORS con valores autorizados y las pruebas de contrato HTTP existentes. No cambiar la semantica de errores ni la disponibilidad de salud. El cierre requiere ausencia de default permisivo, compatibilidad confirmada con los origenes acordados y evidencia en el backlog; una prueba de lista configurada por si sola no basta. Si la decision contractual falta, registrar ese bloqueo y mantener Pendiente.

## Item: H-06 - Exposicion de detalles internos en errores

### Identificacion y alcance

Item H-06 de [HALLAZGOS.md](HALLAZGOS.md), Vulnerabilidad, severidad media, Pendiente. [AuthResource.java:37](src/main/java/com/bcp/workshop/auth/AuthResource.java#L37) devuelve el mensaje y la clase completa de una excepcion en el HTTP 500. Puede revelar informacion interna. Las convenciones exigen mensajes genericos y logging controlado, pero el contrato vigente describe expresamente ese cuerpo detallado.

El alcance es solo la exposicion de detalles y su registro interno. No se cambia CORS, JWT, persistencia, DTO de exito, validaciones 400, mensaje 401 ni la traduccion heredada de errores JDBC a 401. Distinguir fallos JDBC mediante nuevos codigos HTTP seria otro cambio de alcance y no forma parte de esta propuesta.

### Cambio propuesto

1. Conservar y ampliar la caracterizacion de ambos 500, por invocacion directa y HTTP, antes de tocar el handler. No envolver excepciones ni sustituirlas por otras para aparentar compatibilidad.
2. La solucion definitiva propuesta es un mapeo HTTP de errores con mensaje publico generico y sin el nombre de clase ni el mensaje original de la excepcion. El detalle tecnico se registra solo en el servidor, controlando que no incluya password, token o claves; no agregar correlationId u otros campos sin contrato aprobado.
3. Usar el logging ya disponible en Quarkus y un metodo de mapeo de una sola responsabilidad, con constante de mensaje publico y sin duplicar bloques. No introducir un ExceptionMapper global que modifique errores de framework, salud u otras respuestas del contrato. No agregar una biblioteca nueva para logging.

Limite contractual: borrar exception o volver generico message cambia precisamente lo que [docs/ENDPOINTS.md](docs/ENDPOINTS.md) garantiza hoy. El item identifica la vulnerabilidad pero no pide explicitamente autorizar ese cambio de contrato. Por tanto la solucion definitiva esta bloqueada: no se aprueba un nuevo texto ni un nuevo JSON aqui, ni se edita el contrato. Hace falta una decision expresa sobre contenido y compatibilidad antes de revisar el plan para implementar el mapeo. Solo mejorar logging interno manteniendo el 500 detallado no corrige la filtracion y no permite cerrar H-06.

### Archivos a tocar

- [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java): reutilizar preservesExceptionDetailsAndFallback, sus dobles y QuarkusMock; separar aserciones contractuales actuales de la regresion futura de seguridad. No mezclar los casos CORS.
- [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java): candidato para un metodo de mapeo y logging controlado; el cambio del cuerpo HTTP queda bloqueado hasta autorizacion. No se tocan las otras ramas.
- [HALLAZGOS.md](HALLAZGOS.md): registrar evidencia y mantener Pendiente hasta eliminar la exposicion con un contrato aprobado.

No se toca LegacyDatabase para cambiar codigos ni excepciones, no se modifica el servicio de autenticacion y no se introduce otro esquema de respuestas. No se modifica docs/ENDPOINTS.md en el alcance vigente.

### Prueba previa a tocar el handler

Ejecutar primero preservesExceptionDetailsAndFallback y preservesJdbcFailureAsUnauthorized. Mantener los asserts de 500 con mensaje original, fallback Unknown error y clase completa, y de JDBC capturado como 401. Ampliar las entradas de excepcion con texto de laboratorio que simule un detalle interno, sin secretos reales, para demostrar la exposicion existente por HTTP.

Una vez aprobada expresamente la revision contractual, escribir antes del cambio una regresion que exija exactamente el cuerpo generico aprobado y ausencia de clase, traza y texto interno en la respuesta. Debe fallar contra el handler actual y pasar tras la correccion. No alterar esa expectativa para que pase conservando la filtracion. Agregar una comprobacion de logging con un Handler temporal de java.util.logging del JDK, retirado en finally, sin dependencia nueva, para asegurar que no se registran credenciales, tokens ni claves; acordar antes la politica de detalle registrada si los mensajes de excepcion pueden contenerlos.

### Validacion y cierre

Conservar los tests de todos los status no afectados, incluidos los errores de protocolo del empaquetado prod. Repetir la regresion aprobada del 500 y la comprobacion de logging. El cierre requiere no exponer detalles internos y una decision contractual explicita; una mejora de logs que mantenga el cuerpo actual no basta. Si esa decision no existe, solo hay caracterizacion y H-06 permanece Pendiente.

## Validacion prevista y condiciones generales

Durante una futura implementacion, ejecutar desde este repositorio con JDK 21:

```bash
mvn -Dtest=AuthResourceTest test
mvn verify
```

El primer comando es la comprobacion enfocada; el segundo incluye integracion con el empaquetado prod y el gate JaCoCo. Seguir el procedimiento documentado en [docs/TESTING.md](docs/TESTING.md), sin reutilizar SQLite ni claves reales. No se ejecutaron estos comandos para redactar esta revision del plan ni se atribuyen resultados futuros.

Respetar [docs/GOOD_PRACTICES.md](docs/GOOD_PRACTICES.md) y [REGLAS.md](REGLAS.md): una responsabilidad por metodo, retornos anticipados, nombres explicativos, sin duplicacion ni valores magicos. Mantener al menos 85% de cobertura del codigo modificado y el gate actual de lineas e instrucciones por clase, con reportes JaCoCo; no reducir CodeQL, Dependency Review ni cobertura. No agregar dependencias para ninguno de estos items.

El unico seguimiento de estados continua en [HALLAZGOS.md](HALLAZGOS.md). Los items se implementarian y verificarian por separado; no se consideran resueltos por estar planificados. No se leen ni modifican componentes externos. No hay cambios de contrato autorizados por esta revision.

## Fuentes y decisiones pendientes

Consultados antes de proponer cambios: [contrato vigente](docs/ENDPOINTS.md), [convenciones](docs/GOOD_PRACTICES.md), [backlog](HALLAZGOS.md), plan anterior y fuentes actuales [application.properties](src/main/resources/application.properties) y [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java). Las referencias de pruebas y validacion reutilizan las suites existentes documentadas en [docs/TESTING.md](docs/TESTING.md).

Decisiones que faltan: para H-04, origenes/metodos/cabeceras autorizados y permiso explicito para retirar el fallback contractual; para H-06, autorizacion de compatibilidad y cuerpo generico de error, junto con politica de logging. Son decisiones independientes: aprobar una no habilita la otra. Ninguna se asume resuelta ni autoriza implementacion en este encargo.