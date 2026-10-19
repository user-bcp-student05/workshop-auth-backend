# Plan de correccion del backend de autenticacion
## Aprobador: Rubén Vilca
## Fecha: 19/10/2026

## Alcance y seleccion

Se incluyen H-01, H-02, H-03, H-04, H-05, H-06 y H-07, seleccionados expresamente por el usuario y actualmente Pendiente en [HALLAZGOS.md](HALLAZGOS.md). Motivos de priorizacion propuestos: riesgo alto para SQL, contrasenas, token y configuracion; revision de dependencias y filtracion de errores por riesgo medio; separacion de responsabilidades para facilitar cambios verificables.

Este documento es una propuesta, no una implementacion ni una segunda matriz de seguimiento. No modifica codigo, dependencias, estados de hallazgos ni [docs/ENDPOINTS.md](docs/ENDPOINTS.md). Todos los futuros cambios y comandos se limitan al repositorio workshop-auth-backend-1, autorizado en la conversacion. No se leen ni modifican consumidores externos.

Fuentes consultadas: [contrato](docs/ENDPOINTS.md), [convenciones](docs/GOOD_PRACTICES.md), [reglas](REGLAS.md), [matriz](HALLAZGOS.md), implementacion y [prueba existente](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java). Solo existe una prueba de login satisfactorio parcial; no se presupone cobertura de las demas ramas.

## Invariantes y pruebas comunes previas

Antes de cambiar produccion, ampliar [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java) y ejecutar las pruebas contra la implementacion actual:

- POST /auth/login sigue consumiendo y produciendo JSON. Login de student y trainer: HTTP 200, token string no vacio, expiresIn numerico igual a 3600, username/displayName/role correctos y debug igual a authenticated-with-legacy-flow. Comprobar escape HTML4 con un usuario de prueba de displayName especial.
- Conservar nombres, tipos y contenido de los campos de salida; no exponer id ni contrasena. No comparar el orden de propiedades JSON ni fijar un token que depende del tiempo.
- Caracterizar request null y campos username/password null, vacios y solo espacios: 400 y mensajes exactos del contrato. Para request null, usar una invocacion directa del Resource si el framework no lo entrega asi por HTTP; distinguir ambos niveles.
- Caracterizar usuario inexistente y password incorrecto: 401 y mensaje exacto con el username recibido. Comprobar prioridad de validacion cuando faltan ambos campos. Los valores originales llegan a la busqueda; no agregar trim o normalizacion a credenciales.
- Caracterizar client ausente/presente y propiedades JSON desconocidas: no cambian el resultado del login. No agregar validaciones nuevas para ellos.
- Forzar excepciones controladas del colaborador para cubrir 500, mensaje original, fallback Unknown error cuando es null y nombre completo de excepcion. Usar dobles manuales en el mismo paquete y la inyeccion disponible; no agregar Mockito u otra dependencia sin necesidad demostrada.
- Usar una SQLite temporal mediante AUTH_DB_URL antes de arrancar Quarkus, nunca la base local habitual. Verificar tambien que un error JDBC capturado actualmente se traduce a null/401. No convertirlo silenciosamente en 500 durante estos cambios.

Reutilizar JUnit 5, RestAssured, SQLite JDBC y utilidades existentes. Si se requiere una prueba especializada de persistencia, token o migracion, crear una clase de prueba por responsabilidad en el mismo paquete, sin duplicar fixtures. Los nombres de clases nuevas de este plan son propuestos, no convenciones adicionales del repositorio.

Las pruebas de caracterizacion describen respuestas legitimas y comportamiento actual. Las pruebas que exigen dejar de aceptar entradas SQL manipuladas o tokens alterados son regresiones de seguridad del cambio, no requisitos que ya pasen en la version vulnerable. No congelar como contrato la explotabilidad ni el formato Base64 interno.

## H-01 - Parametrizar SQL

### Hallazgo y problema

H-01, Vulnerabilidad de severidad alta, Pendiente. [LegacyDatabase.java:32](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L32) concatena username y password y ejecuta el resultado mediante Statement. Los valores pueden modificar el filtro de autenticacion.

### Cambio propuesto

Reemplazar solamente la consulta de credenciales por PreparedStatement con placeholders para ambos valores y setString, conservando try-with-resources, el mapeo a UserRecord y el comportamiento de ausencia/error actual. No concatenar valores de usuario en ningun SQL. Tras H-02, la consulta sera por username parametrizado y la verificacion de password saldra del repositorio.

Mantener separadas construccion/ejecucion de consulta y mapeo si extraer el mapeo reduce complejidad real; no introducir una jerarquia para una unica consulta. No cambiar inicializacion o manejo de errores en este paso. Usar constantes con nombre para SQL y nombres explicativos para parametros.

### Archivos a tocar

[LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java) y [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java); una prueba especializada de repositorio solo si el fixture existente no permite cubrir la consulta de forma aislada. Sin cambios en dependencias.

### Prueba de caracterizacion previa

Con SQLite temporal, comprobar ambos usuarios validos, password incorrecto, usuario inexistente, valores con espacios y cierre de recursos sin alterar resultados. Registrar el 401 de un fallo JDBC controlado. Ejecutar estas pruebas antes de sustituir Statement.

### Validacion posterior

Agregar regresiones donde comillas y entradas que parecen expresiones SQL se tratan como datos: no autentican a otro usuario ni amplian resultados. Verificar un username legitimo con apostrofo. Ejecutar pruebas de persistencia y contrato; no registrar contrasenas ni payloads en logs.

## H-02 - Hash lento con salt y migracion

### Decision de algoritmo durante la implementacion

La caracterizacion conserva passwords de 100 caracteres, ademas de entradas UTF-8. Se descarta BCrypt para evitar su limite de 72 bytes sin truncamiento ni prehashing ad hoc. Alternativa elegida conforme a la condicion del plan: PBKDF2WithHmacSHA256 provisto por JCA del JDK 21, sin dependencia nueva. Usar 600000 iteraciones, salt aleatorio de 16 bytes y resultado de 32 bytes, guardando algoritmo, iteraciones, salt y hash juntos y comparando en tiempo constante. La configuracion de formato y coste tiene constantes con nombre; no se agrega limite HTTP de longitud. Probar especialmente passwords largos distintos despues del byte 72, UTF-8, salts distintos y formatos invalidos. Referencia de parametros: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#pbkdf2

### Hallazgo y problema

H-02, Vulnerabilidad de severidad alta, Pendiente. [LegacyDatabase.java:22](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L22) crea la columna password; las lineas siguientes insertan literales y [la consulta](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L32) los compara directamente. Una copia de SQLite revela credenciales reutilizables.

### Cambio propuesto

Separar busqueda y verificacion: el repositorio consulta por username con parametro y entrega un registro interno con passwordHash; un servicio verifica el password original con un hash lento y salt aleatorio por usuario. Ese registro interno nunca se serializa ni se utiliza como DTO HTTP. El Resource no debe hacer hashing ni SQL.

Proponer BCrypt mediante io.quarkus:quarkus-elytron-security-common, sujeto a confirmar su API de hashing y version gestionada por el BOM elegido. Se justifica para reutilizar una implementacion criptografica mantenida e integrada con Quarkus, en lugar de programar el algoritmo. Si ya esta disponible transitivamente, declarar la dependencia directa cuando se use su API. Validar limites de longitud, tratamiento UTF-8 y coste con la biblioteca elegida: no truncar passwords silenciosamente ni agregar un rechazo HTTP no autorizado. Si no preserva las entradas admitidas, decidir una alternativa mantenida antes de implementar; no improvisar prehashing.

Migrar instalaciones existentes, no solo las nuevas: con el servicio detenido y acceso exclusivo, ejecutar una migracion JDBC transaccional, versionada y repetible. Crear el esquema con password_hash, transformar cada password heredado una sola vez, conservar id/username/displayName/role y eliminar la columna en claro al completar. Usar una version de esquema explicita, no inferir migracion por prefijos del password. Rollback ante fallo; no mantener un fallback permanente de comparacion en claro. La copia de respaldo local previa es sensible: limitar acceso y acordar su eliminacion segura tras verificar; la migracion por si sola no elimina restos en backups, WAL o paginas libres.

En nuevas bases, sembrar solo usuarios de laboratorio y guardar su hash, no una contrasena en claro. La creacion del esquema, migracion y sembrado son operaciones separadas con metodos pequenos. Las credenciales de fixture no se mezclan con secretos reales ni se imprimen. Mantener las credenciales locales documentadas y los valores originales de entrada. El coste del hash se define una sola vez mediante configuracion o constante con nombre, documentado y medido en el entorno aislado.

### Archivos a tocar

[LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java), [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java), [pom.xml](pom.xml), [application.properties](src/main/resources/application.properties) y [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java). Nuevos archivos para las clases PasswordHasher, AuthService y un registro interno de credenciales en com.bcp.workshop.auth, reutilizando los creados por H-07. Mantener [UserRecord.java](src/main/java/com/bcp/workshop/auth/UserRecord.java) sin hash de salida; no cambiarlo salvo necesidad interna justificada. Pruebas especializadas para hashing y migracion.

### Prueba de caracterizacion previa

Antes de cambiar persistencia, ejecutar login de ambos usuarios y fallo de password. Preparar una base heredada temporal con usuarios adicionales y comprobar resultados y campos HTTP actuales, incluyendo espacios significativos y caracteres no ASCII en credenciales de prueba. Registrar el comportamiento con entradas largas para evaluar compatibilidad de la biblioteca.

### Validacion posterior

Comprobar hash diferente del password, salts distintos para passwords iguales, verificacion correcta y rechazo del incorrecto. Probar base nueva, migracion de base existente, segundo arranque sin doble hash, rollback de migracion y conservacion de usuarios/roles. Confirmar ausencia de columna en claro en el esquema final, ausencia de hashes en HTTP/logs y login sin cambios contractuales. H-01 debe seguir pasando tras cambiar la consulta.

## H-03 - Token firmado con expiracion

### Hallazgo y problema

H-03, Vulnerabilidad de severidad alta, Pendiente. [LegacyTokenUtil.java:12](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L12) codifica username, role, expiracion y secreto con Base64, sin integridad criptografica.

### Cambio propuesto

Sustituir la construccion heredada por un TokenService que emita JWT firmado usando una biblioteca mantenida. Proponer io.quarkus:quarkus-smallrye-jwt-build, con version gestionada por el BOM y API confirmada antes de incorporarla. Se justifica por firma y construccion de claims estandar, sin criptografia artesanal. No agregar una extension de autenticacion entrante para un endpoint que solo emite tokens si no se necesita.

Proponer firma asimetrica con algoritmo fijado y admitido por consumidores; la clave privada se suministra desde configuracion externa y nunca se incluye en el token. Claims propuestos: sub para username, role conservando su valor, iat y exp; cualquier issuer/audience se acuerda antes con consumidores, sin inventar una obligacion externa. El servicio obtiene el tiempo de Clock inyectable y usa una unica duracion con nombre equivalente a 3600 segundos, compartida con expiresIn. DTO, debug, roles y escape de displayName se mantienen.

Coordinar fuera del codigo de este repositorio el algoritmo, distribucion de clave publica, claims y momento de activacion. No se inspeccionan ni modifican frontend/API externos; pedir a sus responsables validacion de firma, expiracion y compatibilidad. No desplegar el nuevo formato hasta obtener esa confirmacion; no afirmar que este backend verifica tokens que no consume. No ofrecer fallback inseguro ni incluir el secreto anterior en el JWT.

### Archivos a tocar

[LegacyTokenUtil.java](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java) para retirarlo o sustituir su responsabilidad, [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java), [pom.xml](pom.xml), [application.properties](src/main/resources/application.properties) y [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java). Nuevos archivos para TokenService y pruebas de token en el mismo paquete; reutilizar AuthService de H-07/H-02. No editar consumidores externos ni el contrato.

### Prueba de caracterizacion previa

Antes de tocar el emisor, ejecutar el 200 completo y comprobar token string no vacio, role y expiresIn igual a 3600. Una prueba temporal del emisor puede registrar username/role y expiracion aproximadamente una hora posterior al inicio de la llamada; no perpetuar Base64 con secreto como contrato del endpoint. No usar esperas para probar el tiempo.

### Validacion posterior

Con Clock fijo y claves exclusivamente de prueba, verificar firma con la clave publica, claims y exp - iat igual a expiresIn. Comprobar fallo de verificacion de token alterado, clave equivocada y algoritmo no permitido; usar el verificador de la biblioteca en pruebas, no un endpoint nuevo. Probar expiracion adelantando el reloj del verificador y ausencia del secreto/clave privada en el contenido. Ejecutar caracterizacion HTTP. La validacion de los consumidores queda como dependencia externa, no como prueba local inventada.

## H-04 - Configuracion externa y CORS acotado

### Hallazgo y problema

H-04, Vulnerabilidad de severidad alta, Pendiente. El [secreto](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L8) esta fijado en Java y [CORS](src/main/resources/application.properties#L4) permite cualquier origen. CORS ya esta en propiedades: el cambio es externalizar valores y restringirlos, no moverlo desde Java.

### Cambio propuesto

Usar configuracion Quarkus/MicroProfile ya disponible para las claves o su ubicacion y los origenes permitidos, suministrada por variables de entorno. No agregar una biblioteca de configuracion. No mantener el secreto heredado como fallback, ni entregar secretos reales como parte del plan. Valores y claves de prueba solo en perfil de test aislado; faltar configuracion de firma debe impedir un arranque inseguro con un diagnostico que no exponga secretos.

Coordinar la retirada del secreto embebido con H-03: externalizarlo sin cambiar el token que lo revela seria solo una mitigacion parcial, no el cierre de H-04. Validar y cargar configuracion en una responsabilidad separada; no leer variables dispersas en el Resource. No colocar claves privadas en archivos versionados.

Para CORS, proponer lista explicita de origenes autorizados y solo los metodos/cabeceras requeridos por el login, manteniendo el acceso de salud documentado. Obtener esos origenes y cabeceras de los responsables; no asumir puertos de frontend ni habilitar credenciales por defecto. El contrato actual tambien documenta la politica permisiva: parametrizar con valores equivalentes preserva el comportamiento pero no corrige esa parte del riesgo. Bajo la exigencia estricta de no cambiar lo documentado, la activacion de una politica mas restrictiva queda condicionada a una decision explicita de alcance/compatibilidad. Este plan no la autoriza ni cambia docs/ENDPOINTS.md; H-04 no se cierra mientras persista esa limitacion.

### Archivos a tocar

[application.properties](src/main/resources/application.properties), [LegacyTokenUtil.java](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java) hasta su retirada, [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java) solo si cambia la inyeccion, y [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java). Configuracion de TokenService reutilizada de H-03. [README.md](README.md) para documentar variables, provision local y coordinacion, sin secretos reales. Ninguna dependencia nueva.

### Prueba de caracterizacion previa

Con el codigo actual, registrar POST de login y preflight OPTIONS desde un origen de prueba con las cabeceras/metodos declarados; comprobar el comportamiento CORS real sin inferir valores concretos no ejecutados. Caracterizar tambien expiracion y cuerpo HTTP antes de introducir configuracion inyectada.

### Validacion posterior

Verificar carga desde entorno y aislamiento de perfiles, fallo seguro por clave ausente o invalida y mantenimiento de expiresIn igual a 3600. Tras autorizar la restriccion CORS, comprobar origen permitido y rechazo de origen ajeno mediante ausencia de autorizacion CORS, no asumir que toda peticion recibe HTTP 403. Comprobar preflight y cabeceras realmente necesarias sin modificar el JSON de login. No afirmar cierre completo mientras siga revelandose un secreto en tokens o no se apruebe la politica CORS.

## H-05 - Revision SCA y actualizacion justificada

### Evidencia y versiones elegidas durante la implementacion

Consulta realizada el 19/10/2026. El arbol resuelto previo se conserva en target/dependencies-before.txt. Apache confirma CVE-2022-42889 en Commons Text anterior a 1.10.0; aqui solo se usa escapeHtml4, no StringSubstitutor, por lo que no se afirma explotabilidad de ese CVE en el flujo. Se elige Commons Text 1.15.0, release publicado el 04/12/2025, compatible con Java 21 y con escapeHtml4, evitando sustituir el algoritmo de escape. Fuentes: https://commons.apache.org/proper/commons-text/security.html y https://commons.apache.org/proper/commons-text/changes.html.

La API publica de avisos devuelve GHSA-qcxp-gm7m-4j5v / CVE-2026-50559 para io.quarkus:quarkus-vertx-http:3.27.4, que esta en el arbol resuelto. El rango incluye >= 3.21.0.CR1, < 3.27.4.1, y otra rama afectada termina antes de 3.33.2.1. Se elige el BOM Quarkus 3.33.2.1 de la rama LTS 3.33 soportada, confirmado en Maven Central; plugin y extensiones permanecen alineados mediante la propiedad del BOM. La aplicabilidad a mecanismos de autorizacion concretos no se equipara a una explotacion probada del login publico. Fuentes: https://github.com/advisories/GHSA-qcxp-gm7m-4j5v, https://quarkus.io/security/ y https://repo.maven.apache.org/maven2/io/quarkus/platform/quarkus-bom/maven-metadata.xml.

La comprobacion de correccion contrasta las versiones cargadas, no explota los CVEs. Se repite la caracterizacion HTTP, escape, salud y pruebas de seguridad despues de actualizar, y se obtiene un nuevo arbol. Esta revision dirigida no equivale a un escaneo SCA exhaustivo ni sustituye Dependency Review.

### Hallazgo y problema

H-05, Sugerencia de severidad media provisional, Pendiente. [pom.xml:13](pom.xml#L13) declara Quarkus 3.27.4 y [pom.xml:50](pom.xml#L50) Commons Text 1.9. [AuthResource.java:45](src/main/java/com/bcp/workshop/auth/AuthResource.java#L45) usa escapeHtml4. Las versiones y ese uso no demuestran por si solos un CVE explotable.

### Cambio propuesto

Antes de elegir versiones, obtener el arbol de dependencias resueltas y contrastar advisories oficiales para coordenadas, rango afectado, dependencias transitivas y condiciones de explotacion. Guardar en este apartado del plan la justificacion tecnica de la version candidata y referencias a avisos; el estado del hallazgo se mantiene solo en HALLAZGOS.md. No inventar CVEs ni elegir una version como segura por ser la mas reciente.

Proponer actualizar el BOM Quarkus a una version compatible soportada que corrija avisos aplicables confirmados, manteniendo alineado el plugin mediante la propiedad existente. Actualizar Commons Text a una version corregida comprobada y compatible con escapeHtml4. Preferir esta actualizacion a sustituir manualmente el escape HTML, que puede cambiar displayName. Solo remover la dependencia si se demuestra equivalencia de salida y que no se necesita. No agregar dependencias o exclusiones especulativas; no silenciar CodeQL ni Dependency Review.

### Archivos a tocar

[pom.xml](pom.xml) y [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java). [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java) solo si una incompatibilidad demostrada lo exige, sin cambiar salida. Actualizar este apartado de PLAN.md con evidencia de version antes de implementar; no crear otra matriz de seguimiento.

### Prueba de caracterizacion previa

Ejecutar el contrato completo con las versiones actuales y registrar las salidas de displayName con ampersand, comillas, apostrofos, angulos y caracteres no ASCII. Registrar compilacion, arranque y estado de salud con el framework actual para comparar sin inventar un esquema de salud.

### Validacion posterior

Repetir contrato, escape y arranque con las versiones candidatas, verificar arbol efectivo y avisos que motivaron el cambio. Si no se confirma un aviso aplicable, registrar la conclusion sin reclasificarlo como vulnerabilidad demostrada ni forzar una actualizacion arbitraria. Respetar las comprobaciones CI existentes y la compatibilidad con Java 21.

## H-06 - Filtracion de detalles internos y limite contractual

### Hallazgo y problema

H-06, Vulnerabilidad de severidad media, Pendiente. [AuthResource.java:54](src/main/java/com/bcp/workshop/auth/AuthResource.java#L54) retorna mensaje y clase de excepcion. Las convenciones exigen mensajes genericos, pero el [contrato actual](docs/ENDPOINTS.md) exige justamente esos detalles en el HTTP 500.

### Cambio propuesto

La solucion tecnica seria mapear errores a un mensaje generico y registrar detalles solo en el servidor mediante logging controlado, sin credenciales, tokens ni secretos; ademas distinguir internamente fallo de persistencia de credenciales incorrectas. Sin embargo, sustituir el contenido o quitar exception cambiaria el contrato. Tampoco se debe cambiar el actual error JDBC de 401 a 500 sin autorizacion.

Por tanto, no hay una correccion completa autorizada para H-06 bajo las restricciones actuales. No implementar la sustitucion, no envolver todas las excepciones para disimular el cambio contractual y no marcar el hallazgo como resuelto. Solo se puede preparar caracterizacion y, si se implementa despues, logging interno controlado sin alterar respuestas. La solucion completa queda bloqueada hasta una decision explicita que resuelva la contradiccion; no se propone editar el contrato en este encargo.

### Archivos a tocar

[AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java) para caracterizacion. [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java) y [LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java) unicamente para logging interno compatible, en un cambio futuro y sin atribuirle el cierre. El mapeo generico no tiene archivos de produccion autorizados mientras subsista el bloqueo. No requiere dependencias nuevas para logging Quarkus.

### Prueba de caracterizacion previa

Inyectar un doble manual de LegacyDatabase que lance una excepcion de mensaje conocido y otra sin mensaje. Verificar HTTP 500, los mensajes exactos y exception con la clase completa. Separadamente, un fallo JDBC capturado por la implementacion real debe conservar el 401 observado. No usar secretos en las excepciones de prueba.

### Validacion posterior

Si se incorpora logging compatible, repetir estas pruebas y verificar que los logs agregados no contienen passwords/tokens ni duplican innecesariamente trazas. La prueba de mensaje generico quedara para una correccion expresamente autorizada: no se presenta como aprobada ni como compatible con el contrato actual.

## H-07 - Separacion de capas y responsabilidades

### Hallazgo y problema

H-07, Refactor de severidad baja, Pendiente. [AuthResource.java:24](src/main/java/com/bcp/workshop/auth/AuthResource.java#L24) valida con anidamiento profundo, llama directamente a persistencia y token, y arma la respuesta. El SQL ya reside en LegacyDatabase, pero falta una capa de servicio.

### Cambio propuesto

Mantener Resource como adaptador HTTP: validar forma de entrada con retornos anticipados, invocar AuthService y mapear el resultado a DTO de respuesta. AuthService coordina autenticacion; el repositorio busca datos; PasswordHasher verifica; TokenService emite. El mapeo a DTO conserva escapeHtml4, debug y estructura actual, sin serializar la entidad de persistencia. No duplicar esas clases si H-02/H-03 ya las incorporaron.

Separar validacion, autenticacion, consulta, hashing, emision y mapeo en metodos con una responsabilidad. Usar nombres de dominio y extraer helpers solo cuando eliminen duplicacion o complejidad concreta. Evitar cadenas de if anidados y retornos ambiguos; conservar la traduccion HTTP existente incluso al introducir resultados internos mas expresivos. Definir constantes con nombre para mensajes/valores fijos y una unica duracion de token compartida, sin un 3600 separado en Resource y emisor. No crear interfaces y factories sin necesidad local demostrada.

La extraccion inicial debe conservar incluso las respuestas heredadas de error; las mejoras de seguridad se realizan en sus cambios especificos y H-06 sigue bloqueado. No usar este refactor para normalizar entradas, quitar debug, modificar DTO o endurecer silenciosamente CORS.

### Archivos a tocar

[AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java), [LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java), [LegacyTokenUtil.java](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java) y [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java). Nuevos archivos de AuthService, TokenService y DTO de respuesta en com.bcp.workshop.auth; reutilizar PasswordHasher y el registro interno de H-02. Pruebas de servicio si permiten aislar reglas sin repetir caracterizacion HTTP. Sin dependencia adicional por la separacion en si.

### Prueba de caracterizacion previa

Ejecutar todas las invariantes comunes antes de mover responsabilidades. Verificar especialmente orden de validacion, valores originales con espacios, 401 con username recibido, displayName escapado, debug, expiracion y ambas variantes del 500. Mantener pruebas HTTP como barrera de contrato y pruebas directas para las ramas que no controla el framework.

### Validacion posterior

Repetir la misma suite sin cambiar sus expectativas para que pase el refactor. Agregar pruebas unitarias de coordinacion sin SQL ni token reales cuando sea util. Revisar que Resource no haga SQL/hashing y que ningun DTO exponga el hash. Comprobar ausencia de duplicacion, anidamiento profundo y duraciones magicas divergentes.

## Secuencia y puertas de validacion

1. Escribir y ejecutar caracterizacion del comportamiento actual antes de tocar codigo; aislar SQLite y valores de prueba. Ningun comando de Maven o migracion se ejecuta como parte de este documento.
2. Implementar H-01 como cambio minimo y comprobar persistencia/HTTP. Extraer H-07 conservando comportamiento como cambio separado para dar un hogar a servicio, seguridad y DTO; no mezclar la extraccion con el cambio de algoritmo.
3. Implementar H-02 con migracion y regresiones. No usar SQL concatenado como paso intermedio ni introducir un segundo flujo de autenticacion en claro.
4. Preparar H-04 y H-03 en cambios revisables coordinados, con claves de prueba, configuracion externa y pruebas de firma antes de activar el nuevo emisor. Externalizar el secreto heredado no basta para cerrar ninguno. Obtener confirmacion de consumidores y resolver la compatibilidad CORS antes de activarlos.
5. Realizar H-05 en un cambio independiente, registrando advisories/versiones verificadas y repitiendo contrato tras actualizar. Elegir dependencias de hashing/firma compatibles con el BOM que resulte, sin mezclar una actualizacion amplia con el refactor de capas.
6. Mantener H-06 sin correccion completa hasta resolver su incompatibilidad contractual. El logging compatible no la elimina. Todos los estados y evidencias de avance se actualizan exclusivamente en HALLAZGOS.md cuando existan pruebas, no por haber escrito este plan.

Durante la implementacion futura, usar `mvn -Dtest=AuthResourceTest test` para la barrera HTTP, pruebas especializadas para cada responsabilidad y `mvn verify` para integracion y reporte JaCoCo, desde esta carpeta con JDK 21 y Maven 3.9+. Aislar AUTH_DB_URL y configuracion de test antes del arranque; no usar la base de laboratorio habitual. No se afirma que esos comandos hayan pasado ahora.

La cobertura del codigo modificado debe ser al menos 85%, con evidencia del reporte JaCoCo; la configuracion actual genera reporte pero no declara una regla automatica de ese umbral. No afirmar que un build exitoso prueba esa meta. No silenciar ni alterar CodeQL, Dependency Review o JaCoCo para pasar las comprobaciones. Si la nueva instrumentacion requerida no funciona, resolverlo antes de cerrar el hallazgo.

Ninguna propuesta autoriza cambios en docs/ENDPOINTS.md. Las dependencias externas se coordinan con sus responsables sin leer otros componentes del workspace. Los bloqueos identificados se comunican, no se sortean cambiando silenciosamente respuestas o politicas documentadas.