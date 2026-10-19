# Contrato HTTP del backend refactorizado

Referencia de integracion verificada el 19/10/2026 con fuentes, pruebas existentes y peticiones al empaquetado Quarkus 3.33.2.1 en perfil prod, usando SQLite y clave RSA exclusivamente de laboratorio. Esta actualizacion es solo documental.

URL local por defecto: `http://127.0.0.1:8080`. Puerto y host proceden de [application.properties:1](../src/main/resources/application.properties#L1); en prod el host cambia a `0.0.0.0`. La comprobacion documental uso loopback y puerto 18081 para aislarla. El orden de las propiedades JSON no forma parte del contrato.

## POST /auth/login

### 1. Que hace

Comprueba las credenciales de un usuario y entrega sus datos y un token firmado para que el cliente inicie sesion.

### 2. Autenticacion y headers

No requiere autenticacion previa ni header `Authorization`. No hay una politica de autenticacion entrante para esta ruta. El 401 descrito abajo corresponde a las credenciales del cuerpo, no a la ausencia de un bearer token.

```http
Content-Type: application/json
Accept: application/json
```

`Content-Type` identifica el cuerpo JSON. `Accept` es recomendable; puede omitirse si el cliente admite cualquier representacion. Las respuestas JSON observadas usan `Content-Type: application/json; charset=UTF-8`.

Evidencia: [anotaciones y ruta](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L12), [configuracion](../src/main/resources/application.properties) y [dependencias](../pom.xml). La extension JWT declarada es de emision, no un mecanismo de autenticacion entrante.

### 3. Request

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| username | string | Si | Nombre de usuario; no admite ausencia, null, cadena vacia o solo espacios segun String.trim(). En los valores no vacios se conservan los espacios al autenticar. |
| password | string | Si | Password con las mismas comprobaciones de presencia. Se verifica el valor original, sin recortarlo. El DTO no declara un limite de longitud. |
| client | string | No | Identificador enviado por el cliente. No se consulta durante el login; puede omitirse o ser null. |

Ejemplo JSON completo, ejecutable con el usuario de laboratorio sembrado:

```json
{
	"username": "student",
	"password": "Workshop123!",
	"client": "angular-workshop"
}
```

El cuerpo debe representar un objeto. Primero se valida username y despues password: si faltan ambos, se informa username. Las propiedades JSON desconocidas se ignoran. Usar strings, sin depender de coerciones de tipos de Jackson. El literal JSON null o la ausencia de cuerpo producen el 400 documentado abajo.

Evidencia: [LoginRequest.java:3](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L3), [validacion](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L45), [autenticacion](../src/main/java/com/bcp/workshop/auth/AuthService.java#L19), [Jackson](../src/main/resources/application.properties#L8) y [sembrado de usuarios](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java).

### 4. Response exitosa

**HTTP 200 OK**, cuerpo JSON:

| campo | tipo | descripcion |
|---|---|---|
| token | string | JWT compacto firmado con RS256; no esta cifrado y cambia entre emisiones. |
| expiresIn | number, entero de 64 bits | Duracion en segundos, actualmente 3600; coincide con exp menos iat del token. |
| user | object | Datos del usuario autenticado; no incluye id, password ni hash. |
| user.username | string | Nombre recuperado de la base. |
| user.displayName | string | Nombre visible escapado como HTML4 con escapeHtml4; puede contener entidades HTML. |
| user.role | string | Rol de la base. Los usuarios sembrados usan STUDENT o TRAINER; no hay un enum cerrado en el codigo. |
| debug | string | Valor fijo authenticated-with-legacy-flow conservado por compatibilidad; no describe el algoritmo actual del token. |

Ejemplo completo capturado de una respuesta real para la peticion anterior, con clave efimera de laboratorio:

```json
{
	"token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJzdHVkZW50Iiwicm9sZSI6IlNUVURFTlQiLCJpYXQiOjE3OTI0NDYzNjAsImV4cCI6MTc5MjQ0OTk2MCwianRpIjoiODc1YWIzZGUtZjZiMS00Nzc0LWI4ZTQtNmQzNmVlYjlmMGQyIn0.M0ukmpD0XMHQW9wqYImbZeqlbMlhNkcdnQozDkV0GDTphbbwSBEeJdEVFPVjpS_iEdum7CR2dX30MR9Kzd1n_113ywbbxgv1ufK2U5montPGX2vaJfenH8Yrk-R-lcnuyfI7uI3bWWL7bx56f6uMaNCyOV9WjLXWmS7Sddlr_nogBYC9TJPwpn85tk6rWCZMDkNa2Vr3vdnAEDksCUXxwDqE_sO65FmvEEPdi3JyH30ubwNtOPF6mjxUo6Jh5-UpTtw-m1RWyquI7NGXZZk1NAD7iGv9WrCUM0p7J0oadO4aJAt37hUmPo1Kl1AjRDtP57Idpyqvh8gBG19_Vccdtw",
	"expiresIn": 3600,
	"user": {
		"username": "student",
		"displayName": "Workshop Student",
		"role": "STUDENT"
	},
	"debug": "authenticated-with-legacy-flow"
}
```

Es una muestra historica, no una credencial reutilizable: su exp es 1792449960 y la clave de prueba no se distribuye. El cliente debe usar el token de su propia respuesta.

Evidencia: [DTO y mapeo](../src/main/java/com/bcp/workshop/auth/LoginResponse.java#L5), [emision](../src/main/java/com/bcp/workshop/auth/TokenService.java#L39) y [prueba del cuerpo completo](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java).

### 5. Respuestas de error

Respuestas JSON construidas por el Resource:

| codigo | cuando ocurre | ejemplo completo de cuerpo |
|---|---|---|
| 400 Bad Request | Cuerpo ausente o literal JSON null; el handler recibe request null. | `{"message":"Request is null"}` |
| 400 Bad Request | username ausente, null, vacio o solo espacios. Tiene prioridad sobre password. | `{"message":"Username is required"}` |
| 400 Bad Request | username supera la comprobacion de presencia, pero password esta ausente, null, vacio o solo tiene espacios. | `{"message":"Password is required"}` |
| 401 Unauthorized | Usuario inexistente o password incorrecto. Tambien cuando el repositorio captura un fallo JDBC y devuelve null; el cliente no distingue ese caso. | `{"message":"Invalid user student or password"}` |
| 500 Internal Server Error | Una excepcion llega al catch del Resource y tiene mensaje no null. Ejemplo de la prueba que fuerza IllegalStateException. | `{"message":"controlled failure","exception":"java.lang.IllegalStateException"}` |
| 500 Internal Server Error | La excepcion capturada tiene mensaje null; se usa Unknown error. Ejemplo de la misma prueba. | `{"message":"Unknown error","exception":"java.lang.IllegalStateException"}` |

En el 401, student se sustituye por el username exacto recibido, incluidos espacios. En el 500, exception es la clase completa de la excepcion real, no siempre IllegalStateException. Los ejemplos 500 proceden del doble controlado de la prueba, no de una peticion que el integrador deba provocar.

Errores anteriores al handler, comprobados en el empaquetado actual en perfil prod:

| codigo | cuando ocurre | ejemplo de cuerpo real |
|---|---|---|
| 400 Bad Request | JSON mal formado, como un cuerpo `{`, o tipo incompatible, como `{"username":{},"password":"x"}`. | Sin cuerpo: 0 bytes. No hay JSON ni message. |
| 415 Unsupported Media Type | El cuerpo se envia como text/plain, en lugar de application/json. | Sin cuerpo: 0 bytes. |
| 406 Not Acceptable | Se solicita solo una representacion no producida, por ejemplo Accept: application/xml. | Sin cuerpo: 0 bytes. |
| 405 Method Not Allowed | Se usa GET en /auth/login en lugar de POST. | Sin cuerpo: 0 bytes. |

El cliente debe comprobar status y presencia de cuerpo antes de deserializar JSON. No todos los errores tienen message. Los cuerpos generados por el framework pueden variar con otro perfil o configuracion.

Evidencia: [handler y catch](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L26), [captura JDBC](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L56) y [pruebas de excepciones y JDBC](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java). Los errores de protocolo pertenecen a quarkus-rest-jackson, declarado en [pom.xml:36](../pom.xml#L36), y se observaron por HTTP sin modificar fuentes.

Pregunta abierta: no hay contrato propio para limites de tamano, timeouts ni errores de un proxy o fallos fuera del Resource. No se asignan codigos ni cuerpos JSON a esos casos sin verificar la configuracion del despliegue.

### 6. Notas de seguridad

- El token ya no incluye un secreto codificado en Base64: es un JWT firmado con RS256 y clave RSA externa de al menos 2048 bits. Su contenido no esta cifrado. Nunca distribuir la clave privada al cliente.
- El emisor fija sub como username, role como el rol, iat como epoch de emision en segundos y exp como iat + 3600. La biblioteca agrego ademas un jti string al token observado; no es un campo adicional del JSON externo de login. Este servicio no fija issuer ni audience expresamente.
- La expiracion esta protegida por la firma. El consumidor debe verificar firma, algoritmo y exp con la clave publica correcta para rechazar tokens vencidos. Este backend emite tokens, pero no implementa un endpoint verificador ni autentica rutas con ellos. Las pruebas locales verifican expiracion, alteracion, clave equivocada y algoritmo no admitido; no acreditan la integracion externa.
- AUTH_TOKEN_KEY_LOCATION debe indicar la clave externa. Si falta o es invalida, el backend no arranca: no es una respuesta HTTP 500 de login. La distribucion de clave publica y activacion integrada deben coordinarse con los consumidores.
- Se verifica el password con PBKDF2-HMAC-SHA256 y salt. El repositorio consulta username con parametros. No se devuelve password ni hash.
- Persisten errores 500 con detalles internos y CORS permisivo por defecto. AUTH_CORS_ORIGINS, AUTH_CORS_METHODS y AUTH_CORS_HEADERS permiten configurarlo; CORS no es autenticacion. Es un laboratorio aislado: no desplegar en Internet ni transmitir credenciales reales sin TLS.

Evidencia: [TokenService.java](../src/main/java/com/bcp/workshop/auth/TokenService.java), [TokenServiceTest.java](../src/test/java/com/bcp/workshop/auth/TokenServiceTest.java), [PasswordHasher.java](../src/main/java/com/bcp/workshop/auth/PasswordHasher.java), [configuracion](../src/main/resources/application.properties#L4) y [advertencia operativa](../README.md).

## GET /q/health

### 1. Que hace

Informa al operador si el servicio reporta un estado de salud agregado favorable para su supervision.

Lo expone quarkus-smallrye-health, no un Resource propio. No hay checks de negocio o JDBC definidos por el proyecto: UP no demuestra que un usuario pueda iniciar sesion.

### 2. Autenticacion y headers

No requiere autenticacion ni header `Authorization` en la configuracion actual.

```http
Accept: application/json
```

No se necesita Content-Type cuando no se envia cuerpo. La respuesta observada usa `Content-Type: application/json; charset=UTF-8`. La extension devolvio JSON incluso con Accept: application/xml; no aplicar aqui el 406 de /auth/login.

Evidencia: [extension de salud](../pom.xml#L40), [configuracion sin politica de autenticacion](../src/main/resources/application.properties) y [prueba GET sin credenciales](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java).

### 3. Request

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| Ninguno | No aplica | No | No se definen parametros de ruta, query ni campos JSON de negocio para esta consulta. |

Peticion habitual completa, sin cuerpo:

```http
GET /q/health HTTP/1.1
Host: 127.0.0.1:8080
Accept: application/json
```

Ejemplo JSON completo de cuerpo opcional comprobado con GET: el objeto vacio tambien fue aceptado y no aporta parametros. No hace falta enviarlo para integrar; la peticion sin cuerpo es la recomendada.

```json
{}
```

Si se envia ese objeto opcional, usar Content-Type: application/json. No enviar credenciales ni token en el cuerpo. La extension no usa un DTO de request del proyecto.

### 4. Response exitosa

**HTTP 200 OK**, cuerpo JSON real observado:

| campo | tipo | descripcion |
|---|---|---|
| status | string | Estado agregado; el valor de exito observado es UP. |
| checks | array | Resultados individuales. En este repositorio/configuracion es un array vacio; no hay campos de elemento propios que documentar. |

```json
{
	"status": "UP",
	"checks": []
}
```

La prueba existente comprueba el 200. El cuerpo se confirmo ejecutando la extension indicada en [pom.xml:40](../pom.xml#L40). No se incluyen checks inventados de SQLite, usuarios, version ni tokens.

### 5. Respuestas de error

El proyecto no implementa errores de negocio ni un cuerpo message/exception propio para esta ruta. No se observo un 401 o 403 por ausencia de token.

| codigo | cuando ocurre | cuerpo y grado de verificacion |
|---|---|---|
| 503 Service Unavailable | La extension contempla este status cuando el agregado esta DOWN. Se verifico la rama en SmallRyeHealthHandlerBase de la dependencia resuelta. | Pregunta abierta: no existe un check fallido del repositorio con el que reproducir y verificar un ejemplo JSON real de 503. No se inventa un cuerpo ni un nombre de check ficticio. |

No se cambia artificialmente UP a DOWN en el ejemplo de exito: checks vacio no demuestra un check fallido. El cuerpo JSON completo de 503 y los campos de sus checks quedan pendientes de identificar un check que falle y reproducirlo con esta configuracion. Esta limitacion es expresa, no se presenta como una respuesta ejecutada.

Pregunta abierta adicional: el proyecto no fija el cuerpo de errores internos inesperados del manejador de salud. Un proceso que no arranca o un fallo de transporte puede no producir respuesta HTTP; no se documenta automaticamente como un JSON 500/503. Una clave de firma invalida puede impedir que este endpoint este disponible.

La extension respondio 200 tambien al probar POST /q/health: no se promete 405 para otros metodos. Esta referencia cubre GET y no crea endpoints de negocio adicionales.

### 6. Notas de seguridad

- Es publico en la configuracion actual. El cuerpo observado no incluye secretos, tokens ni usuarios.
- UP con checks vacio no acredita disponibilidad de SQLite, migracion correcta ni autenticacion funcional. Los fallos capturados de inicializacion/persistencia no tienen un health check propio que los publique.
- No sustituye un login de prueba ni autoriza operaciones. CORS y host siguen la configuracion HTTP general; en prod el host por defecto de este proyecto es 0.0.0.0.

Evidencia: [captura de fallos de SQLite](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java), [dependencia de salud](../pom.xml#L40), [configuracion HTTP](../src/main/resources/application.properties#L1) y [limitaciones operativas](../README.md).

## Documentos y fuentes consultados

- [README.md](../README.md): arranque, credenciales de laboratorio, variables, migracion y limites de integracion.
- Version anterior de este documento: contrato legacy contrastado y reemplazado, no usado como prueba del comportamiento nuevo.
- [pom.xml](../pom.xml) y [application.properties](../src/main/resources/application.properties): versiones, extensiones, configuracion HTTP, CORS y clave externa.
- [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java), [LoginRequest.java](../src/main/java/com/bcp/workshop/auth/LoginRequest.java) y [LoginResponse.java](../src/main/java/com/bcp/workshop/auth/LoginResponse.java): entrada, salida, validaciones y errores propios.
- [AuthService.java](../src/main/java/com/bcp/workshop/auth/AuthService.java), [LegacyDatabase.java](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java), [PasswordHasher.java](../src/main/java/com/bcp/workshop/auth/PasswordHasher.java) y [TokenService.java](../src/main/java/com/bcp/workshop/auth/TokenService.java): autenticacion, persistencia y emision.
- [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java) y [TokenServiceTest.java](../src/test/java/com/bcp/workshop/auth/TokenServiceTest.java): caracterizacion y verificacion de errores, JWT y expiracion.

Evidencia complementaria local: respuestas HTTP en target/docs-http-examples.json y bytecode de handlers en target/docs-health-handler.txt. Son artefactos temporales eliminables con mvn clean; el cliente no los necesita para usar esta referencia. No se modificaron fuentes, pruebas, configuracion ni otros documentos.