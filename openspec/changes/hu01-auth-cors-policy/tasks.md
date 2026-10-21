# Tasks

Checklist de implementacion del cambio `hu01-auth-cors-policy`. La unidad de producto sigue siendo el [Issue #10](https://github.com/user-bcp-student05/workshop-auth-backend/issues/10); este archivo no crea historias ni amplia su alcance.

Fuentes aprobadas: [proposal.md](proposal.md), [auth-cors-policy](specs/auth-cors-policy/spec.md), [auth-http-contract](specs/auth-http-contract/spec.md) y [design.md](design.md). Baseline protegido: [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md).

Ejecutar en orden de dependencia, exclusivamente en este backend y en `feature/HU01`. Las tareas estan pendientes; crear este checklist no acredita ejecucion ni autoriza implementar en esta etapa. No modificar comportamiento funcional hasta completar la caracterizacion del grupo 1 y la aprobacion del grupo 2. La lista autorizada sigue pendiente: no sustituirla por origenes de prueba. Usar el JDK compatible con el POM actual y fixtures de laboratorio, sin datos ni secretos reales.

## 1. Caracterizacion previa al cambio funcional

- [ ] 1.1 Ejecutar AuthResourceTest con `mvn -Dtest=AuthResourceTest test` antes de tocar la politica; verificar respuesta completa 200, validaciones y su orden, espacios, client opcional, propiedades desconocidas, errores 401/500, JDBC capturado y salud, conservando comando, revision y reporte de resultados.
- [ ] 1.2 Ejecutar AuthServiceTest, PasswordStorageTest y TokenServiceTest con `mvn -Dtest=AuthServiceTest,PasswordStorageTest,TokenServiceTest test`; verificar coordinacion, persistencia/hashing y firma/claims/expiracion sin modificar esas capas, y conservar los reportes.
- [ ] 1.3 Ejecutar `mvn -Dtest=AuthResourceTest -Dit.test=AuthProtocolIT verify`; verificar en el empaquetado prod los 400 por JSON/tipo, 415, 406 y 405 sin cuerpo, sin cambiar expectativas para acomodar diagnosticos de modo test.
- [ ] 1.4 Capturar por HTTP el login y preflight actuales con dos origenes ficticios de laboratorio bajo el fallback universal; registrar origen, metodo, ruta, headers, status y autorizacion observada en docs/TESTING.md, distinguiendo esta caracterizacion de las expectativas restrictivas futuras y sin guardar tokens o credenciales.
- [ ] 1.5 Contrastar los resultados con todos los escenarios de auth-http-contract y completar solo caracterizaciones faltantes en las suites existentes; verificar que pasan antes del cambio funcional y que su cobertura queda identificada en docs/TESTING.md. No conservar la apertura universal como requisito permanente ni ampliar el contrato de salud.

## 2. Aprobacion y preparacion de la politica

- [ ] 2.1 Obtener y documentar en la documentacion existente del backend la lista exacta de origenes autorizados y la referencia de quien la aprueba; verificar que contiene origenes literales de esquema, host y puerto cuando aplique, sin comodines, regex ni valores inventados. Si falta aprobacion, registrar el bloqueo y no avanzar al grupo 3.
- [ ] 2.2 Identificar el punto de suministro de AUTH_CORS_ORIGINS en la ejecucion existente y registrar revision del artefacto y configuracion previa para rollback; verificar que la configuracion de destino refleja la lista aprobada y que no se requiere infraestructura nueva ni incluir secretos en el repositorio.
- [ ] 2.3 Preparar los overrides de laboratorio reutilizando TestSigningKeys y los fixtures existentes, con dos origenes permitidos y uno ajeno a la lista; verificar que llegan tanto al proceso de pruebas como a la aplicacion empaquetada, y documentar su ejecucion en docs/TESTING.md. Mantener esos valores separados de la aprobacion operativa y no paralelizar pruebas que comparten mocks, SQLite o registro de salud.

## 3. Pruebas de la nueva politica e implementacion incremental

- [ ] 3.1 Agregar pruebas HTTP de origen permitido para login exitoso y credenciales invalidas; verificar 200/401 y cuerpos del baseline junto con Access-Control-Allow-Origin exactamente igual al origen solicitado, nunca `*`, reutilizando las suites existentes.
- [ ] 3.2 Agregar pruebas HTTP de origen no permitido con peticion de negocio valida; verificar ausencia de Access-Control-Allow-Origin que autorice al solicitante, por eco o `*`, y registrar el status observado sin imponer 401/403 ni cuerpo del handler. Confirmar que esta prueba detecta la apertura anterior antes de aplicar 3.5.
- [ ] 3.3 Agregar pruebas preflight OPTIONS /auth/login para origen permitido y no permitido, solicitando POST y content-type; verificar autorizacion exacta y metodo/header permitidos en el primer caso, ausencia de autorizacion en el segundo, sin fijar status o cuerpo nuevos para OPTIONS.
- [ ] 3.4 Agregar comprobaciones aisladas de configuracion/arranque con clave y SQLite de laboratorio validas: variable de origenes ausente y lista explicita; verificar que detectan el fallback anterior y que no confunden un fallo de clave o base de datos con el de configuracion. Incluir correspondencia de dos origenes permitidos y rechazo CORS de variantes de esquema, host o puerto no incluidas.
- [ ] 3.5 Retirar exclusivamente el fallback `:/.*/` de AUTH_CORS_ORIGINS en src/main/resources/application.properties, conservando CORS habilitado y el resto de parametrizacion; verificar inmediatamente las pruebas 3.1-3.4, incluido fallo de arranque sin la entrada obligatoria y funcionamiento con lista explicita. Si Quarkus no cumple la resolucion prevista, detenerse y revisar el diseno, sin agregar filtros o fallback permisivo.
- [ ] 3.6 Suministrar AUTH_CORS_ORIGINS con la lista aprobada mediante el mecanismo de configuracion existente; verificar concordancia exacta entre aprobacion y valor efectivo, sin lista vacia, comodines ni regex, y confirmar que AUTH_CORS_METHODS, AUTH_CORS_HEADERS, credenciales CORS y cache permanecen sin cambios.
- [ ] 3.7 Documentar en README.md la entrada obligatoria, formato, comportamiento sin variable y procedimiento de arranque/rollback existente; verificar los pasos con configuracion de laboratorio y registrar en docs/TESTING.md los comandos y resultados de 3.1-3.6, sin presentar origenes ficticios como aprobados.

## 4. Regresion e integracion del cambio

- [ ] 4.1 Ejecutar la matriz de auth-http-contract sin Origin y con origen permitido, incluida salud, validaciones, cuerpos 200/400/401/500 y errores de protocolo en prod; verificar ausencia de bearer obligatorio, conservacion de JWT/DTO y ninguna garantia nueva de salud. Reutilizar las caracterizaciones del grupo 1 y registrar los resultados.
- [ ] 4.2 Ejecutar `mvn verify` incluyendo las pruebas CORS y los overrides de laboratorio para los procesos correspondientes; verificar Surefire, Failsafe y JaCoCo sin fallos ni omisiones injustificadas, manteniendo el minimo 0.85 por clase en lineas e instrucciones. Conservar reportes y comandos, sin atribuir a cobertura Java la verificacion de ramas del framework CORS.
- [ ] 4.3 Revisar el diff y los resultados de los checks de CI existentes; verificar que no hay cambios de negocio, dependencias o infraestructura, ni alteraciones de CodeQL, Dependency Review o JaCoCo. Registrar evidencia real de esos checks y dejar pendiente cualquier gate no ejecutado, sin declararlo aprobado.

## 5. Activacion controlada y evidencia de aceptacion

- [ ] 5.1 Comprobar antes de activar que existe la configuracion de retorno conocida y autorizada de 2.2, y reiniciar en laboratorio mediante el procedimiento existente con la lista aprobada; verificar arranque, salud y login permitido sin desplegar infraestructura ni migrar datos.
- [ ] 5.2 Repetir peticion directa y preflight para origen aprobado y no aprobado contra esa configuracion efectiva; adjuntar a docs/TESTING.md revision, perfil, comando, origen, metodo, ruta, headers relevantes, status y resultado, contrastando los criterios del Issue #10 sin registrar secretos.
- [ ] 5.3 Consolidar la evidencia de lista aprobada, configuracion actualizada y pruebas de ambos casos para revision del Issue #10; verificar trazabilidad con ambas specs, compatibilidad del contrato y gates reales. Registrar el resultado de activacion y, si hubo rollback, su autorizacion y comprobaciones de arranque/salud/login; si restaura la apertura anterior, declarar H-04 reabierto y no presentar el Issue como cumplido.

## Condiciones de rollback

Aplicar el procedimiento de [design.md](design.md) solo si la activacion falla o causa regresiones: priorizar la lista aprobada corregida; restaurar artefacto/configuracion anteriores solo con autorizacion y evidencia. No ampliar automaticamente a `*` o `/.*/`, no usar reset destructivo ni revertir datos, claves o cambios ajenos. Esta condicion no agrega una tarea de reversion obligatoria cuando la activacion es correcta.