# Contrato HTTP actual

Contrato reconstruido desde el codigo, sin cambiar su comportamiento. Describe el estado de partida que deben preservar las correcciones internas; no propone un contrato nuevo.

## POST /auth/login

- Consume y produce `application/json` ([AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L15)).
- Entrada: objeto con `username`, `password` y `client`, todos declarados como String. `client` no se consulta durante el login ([LoginRequest.java](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L3), [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L24)). Las propiedades desconocidas se ignoran ([application.properties](../src/main/resources/application.properties#L8)).

Ejemplo de entrada usado por la prueba existente:

```json
{"username":"student","password":"Workshop123!","client":"angular-workshop"}
```

### Respuestas del Resource

| HTTP | Condicion | Cuerpo JSON |
|---|---|---|
| 200 | Usuario encontrado | `{"token":"<string>","expiresIn":3600,"user":{"username":"<string>","displayName":"<string escapado como HTML4>","role":"<string>"},"debug":"authenticated-with-legacy-flow"}` |
| 400 | request recibido como null | `{"message":"Request is null"}` |
| 400 | username null o vacio despues de trim | `{"message":"Username is required"}` |
| 400 | password null o vacio despues de trim | `{"message":"Password is required"}` |
| 401 | La busqueda devuelve null | `{"message":"Invalid user <username recibido> or password"}` |
| 500 | Excepcion capturada por el Resource | `{"message":"<mensaje de excepcion o Unknown error si es null>","exception":"<nombre completo de la clase>"}` |

Evidencia: [validacion](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L26), [401](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L37), [200](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L39), [500](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L54). El orden de las propiedades JSON no forma parte del contrato. Las respuestas del framework anteriores a la invocacion del metodo (por ejemplo, JSON mal formado o un tipo de contenido incompatible) no estan definidas por este Resource y no se verificaron en ejecucion.

### Flujo completo actual

1. Quarkus inyecta `LegacyDatabase`, de ambito ApplicationScoped. Su inicializacion crea el directorio `data`, la tabla SQLite `legacy_users` y dos usuarios de laboratorio mediante `INSERT OR IGNORE`. Los fallos se imprimen y no se propagan ([LegacyDatabase.java](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L13), [inicializacion](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L17)).
2. `AuthResource.login` valida en orden request, username y password. Usa trim solo para comprobar si estan vacios; entrega los valores originales a la busqueda ([AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L26)).
3. `findByCredentials` abre una conexion JDBC, concatena las credenciales en un SELECT y devuelve el primer resultado como `UserRecord` (id, username, displayName, role). No escribe durante el login. Si no hay coincidencia o sucede una excepcion capturada, devuelve null y el Resource responde 401 ([LegacyDatabase.java](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L31), [UserRecord.java](../src/main/java/com/bcp/workshop/auth/UserRecord.java#L3)).
4. Para un usuario encontrado, `LegacyTokenUtil.create` construye un texto con username, rol, epoch de expiracion calculado como ahora mas 3600 segundos y secreto fijo, separados por el caracter vertical U+007C. Devuelve Base64 UTF-8; no hay firma ni endpoint verificador en estas fuentes ([LegacyTokenUtil.java](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L12)). Este detalle describe la implementacion heredada, no exige conservar su inseguridad al corregirla.
5. El Resource devuelve token, expiresIn numerico, user con displayName escapado mediante `escapeHtml4` y debug. Las excepciones que alcancen su catch producen el 500 descrito arriba ([AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L39)).

### Configuracion que interviene

- Base de datos: variable de entorno `AUTH_DB_URL`, con valor por defecto `jdbc:sqlite:./data/workshop.db` ([LegacyDatabase.java](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L15)).
- HTTP: puerto 8080 y host 127.0.0.1; en perfil prod el host es 0.0.0.0 ([application.properties](../src/main/resources/application.properties#L1)).
- CORS habilitado, origenes `/.*/`, metodos `GET,POST,PUT,DELETE,OPTIONS` y cabeceras `accept,authorization,content-type,x-requested-with` ([application.properties](../src/main/resources/application.properties#L4)).
- Secreto del token fijado en Java; expiracion de 3600 segundos en el generador y expiresIn de 3600 en la respuesta ([LegacyTokenUtil.java](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L8), [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L42)).
- Versiones declaradas: BOM Quarkus 3.27.4, sqlite-jdbc 3.50.3.0 y Commons Text 1.9 ([pom.xml](../pom.xml#L13), [pom.xml](../pom.xml#L45), [pom.xml](../pom.xml#L50)).

## GET /q/health

Endpoint de salud anunciado en [README.md](../README.md) y provisto por la dependencia `quarkus-smallrye-health` ([pom.xml](../pom.xml#L40)). No hay un Resource propio ni pruebas de su cuerpo/status en estas fuentes; la respuesta concreta depende del framework y del estado de salud. No se inventa un esquema JSON.

## Evidencia de pruebas existentes

[AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L13) comprueba un login de student con HTTP 200, user.username igual a student y token no nulo. No caracteriza el resto de campos ni las ramas de error. Este documento procede del analisis estatico, no de nuevas pruebas de ejecucion. El seguimiento de hallazgos se mantiene exclusivamente en [HALLAZGOS.md](../HALLAZGOS.md).