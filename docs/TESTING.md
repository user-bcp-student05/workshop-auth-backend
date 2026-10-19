# Pruebas del contrato HTTP

Ejecucion del 19/10/2026 contra el contrato de [ENDPOINTS.md](ENDPOINTS.md). Se reutilizan las pruebas existentes y se completan los casos que faltaban. No se modifico codigo de produccion ni el contrato; solo pruebas y configuracion Maven necesaria para ejecutar la integracion. No se agregaron dependencias.

## Matriz de casos

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| POST /auth/login | student y trainer con credenciales validas; preservesCompleteSuccessResponse | Camino feliz | 200 JSON con token, expiresIn=3600, user con username/displayName/role y debug fijo; sin id, password ni hash. |
| POST /auth/login | Password de 100 caracteres y credenciales Unicode; preservesCompleteSuccessResponse | Borde | 200 sin truncar las credenciales validas. No se inventa un limite HTTP. |
| POST /auth/login | Espacios significativos y displayName con caracteres HTML; preservesCompleteSuccessResponse | Borde | Conserva credenciales y devuelve displayName escapado como HTML4. |
| POST /auth/login | client ausente, null, vacio o informado; acceptsOptionalClientValues y logsInWithWorkshopUser | Borde | 200; client no afecta la autenticacion ni se agrega a la salida. |
| POST /auth/login | Propiedad JSON desconocida; preservesCompleteSuccessResponse | Borde | Se ignora; 200 y misma estructura. |
| POST /auth/login | Usuario legitimo con apostrofo; authenticatesLiteralApostrophe | Borde | 200; el apostrofo es un dato, no sintaxis SQL. |
| POST /auth/login | Cuerpo ausente o literal null por HTTP; rejectsNullAndAbsentHttpBodies | Error | 400, {"message":"Request is null"}. |
| POST /auth/login | username ausente, null, vacio o solo espacios; preservesValidationOrderAndMessages | Error | 400, {"message":"Username is required"}. Si tambien falta password, prevalece username. |
| POST /auth/login | password ausente, null, vacio o solo espacios con username presente; preservesValidationOrderAndMessages | Error | 400, {"message":"Password is required"}. |
| POST /auth/login | Usuario inexistente o password incorrecto; preservesInvalidCredentialsAndWhitespace | Error | 401 y mensaje con el username recibido. |
| POST /auth/login | Agregar espacios a credenciales que no los tienen; preservesInvalidCredentialsAndWhitespace | Borde | 401; no normalizar silenciosamente los valores. |
| POST /auth/login | Entrada que intenta alterar SQL; rejectsSqlAsCredentials | Error | 401 sin autenticar otro usuario. |
| POST /auth/login | Fallo JDBC capturado; preservesJdbcFailureAsUnauthorized | Error | 401 heredado y mensaje esperado; la tabla temporalmente renombrada se restaura en finally. No equivale a un fallo de credenciales real. |
| POST /auth/login | Excepcion con mensaje; preservesExceptionDetailsAndFallback | Error | Por invocacion directa y HTTP: 500, {"message":"controlled failure","exception":"java.lang.IllegalStateException"}. |
| POST /auth/login | Excepcion sin mensaje; preservesExceptionDetailsAndFallback | Error | Por invocacion directa y HTTP: 500, {"message":"Unknown error","exception":"java.lang.IllegalStateException"}. |
| POST /auth/login | JSON mal formado; AuthProtocolIT.rejectsMalformedJsonAndIncompatibleTypes | Error | En empaquetado prod: 400 y cuerpo de 0 bytes. |
| POST /auth/login | username es un objeto JSON; AuthProtocolIT.rejectsMalformedJsonAndIncompatibleTypes | Error | En empaquetado prod: 400 y cuerpo de 0 bytes. |
| POST /auth/login | Content-Type: text/plain; AuthProtocolIT.rejectsUnsupportedContentType | Error | 415 y cuerpo de 0 bytes. |
| POST /auth/login | Accept: application/xml; AuthProtocolIT.rejectsUnacceptableResponseType | Error | 406 y cuerpo de 0 bytes. |
| POST /auth/login | Metodo GET; AuthProtocolIT.rejectsWrongLoginMethod | Error | 405 y cuerpo de 0 bytes. No se crea un endpoint GET de login. |
| GET /q/health | Sin credenciales y sin cuerpo; reportsHealthyWithoutAuthentication | Camino feliz | 200 JSON exacto en campos: {"status":"UP","checks":[]}. |
| GET /q/health | Cuerpo opcional vacio {}; acceptsEmptyHealthBodyAndUnmatchedAccept | Borde | 200 y mismo cuerpo de salud. No se inventan campos de request. |
| GET /q/health | Accept: application/xml; acceptsEmptyHealthBodyAndUnmatchedAccept | Borde | 200 application/json, no 406, como documenta el contrato de esta extension. |
| GET /q/health | Check fallido registrado solo durante el test; reportsUnavailableForTemporaryFailedCheck | Error | 503 JSON con status=DOWN y checks=[{"name":"testing-only","status":"DOWN"}]. Al retirarlo, vuelve a 200 UP con checks vacio. |

Cada fila corresponde a aserciones ejecutadas. Hay 35 metodos JUnit en total; los bucles de entradas de un metodo no se cuentan como tests independientes. Los tests de seguridad adicionales no crean endpoints nuevos.

## Codigo de los tests

Fuentes ejecutables completas:

- [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java): contrato HTTP, bordes, validaciones, respuestas 500, JDBC y salud.
- [AuthProtocolIT.java](../src/test/java/com/bcp/workshop/auth/AuthProtocolIT.java): errores de protocolo del empaquetado prod.
- [TestSigningKeys.java](../src/test/java/com/bcp/workshop/auth/TestSigningKeys.java): fixture existente de SQLite y clave efimera en target, retirando la clave al terminar.
- [AuthServiceTest.java](../src/test/java/com/bcp/workshop/auth/AuthServiceTest.java), [PasswordStorageTest.java](../src/test/java/com/bcp/workshop/auth/PasswordStorageTest.java) y [TokenServiceTest.java](../src/test/java/com/bcp/workshop/auth/TokenServiceTest.java): coordinacion, hashing/migracion, firma y expiracion.

Codigo completo de la nueva suite de protocolo. Sus expectativas se conservan al ejecutarla en prod; no se cambia el cuerpo esperado para aceptar detalles diagnosticos del modo test:

```java
package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusIntegrationTest
@QuarkusTestResource(TestSigningKeys.class)
class AuthProtocolIT {
    @Test
    void rejectsMalformedJsonAndIncompatibleTypes() {
        for (String body : new String[]{"{", "{\"username\":{},\"password\":\"x\"}"}) {
            given().contentType("application/json").accept("application/json").body(body)
                    .when().post("/auth/login").then().statusCode(400).body(is(""));
        }
    }

    @Test
    void rejectsUnsupportedContentType() {
        given().contentType("text/plain").accept("application/json").body("text")
                .when().post("/auth/login").then().statusCode(415).body(is(""));
    }

    @Test
    void rejectsUnacceptableResponseType() {
        given().contentType("application/json").accept("application/xml").body("{}")
                .when().post("/auth/login").then().statusCode(406).body(is(""));
    }

    @Test
    void rejectsWrongLoginMethod() {
        given().accept("application/json")
                .when().get("/auth/login").then().statusCode(405).body(is(""));
    }
}
```

Extracto literal del test de salud en la suite existente. La clase completa enlazada arriba contiene imports, inyeccion de healthReporter y helper assertHealthy. No se declara un bean de salud que altere permanentemente el cuerpo normal del endpoint:

```java
@Test
void reportsUnavailableForTemporaryFailedCheck() {
    HealthCheck failedCheck = () -> HealthCheckResponse.down("testing-only");
    healthReporter.addHealthCheck(failedCheck);
    try {
        given().accept("application/json").when().get("/q/health").then()
                .statusCode(503).contentType("application/json")
                .body("", aMapWithSize(2)).body("status", is("DOWN"))
                .body("checks", hasSize(1))
                .body("checks[0].name", is("testing-only"))
                .body("checks[0].status", is("DOWN"));
    } finally {
        healthReporter.removeHealthCheck(failedCheck);
    }
    assertHealthy(given().accept("application/json").when().get("/q/health").then());
}
```

Extractos literales de los otros casos nuevos del Resource; se ejecutan dentro de AuthResourceTest con sus helpers existentes:

```java
@Test
void rejectsNullAndAbsentHttpBodies() {
    for (String body : new String[]{"null", ""}) {
        given().contentType("application/json").accept("application/json").body(body)
                .when().post("/auth/login").then().statusCode(400)
                .body("", aMapWithSize(1)).body("message", is("Request is null"));
    }
}

@Test
void acceptsOptionalClientValues() {
    for (String client : new String[]{null, "", "angular-workshop"}) {
        LoginRequest request = request("student", "Workshop123!");
        request.client = client;
        given().contentType("application/json").accept("application/json").body(request)
                .when().post("/auth/login").then().statusCode(200)
                .body("user.username", is("student"))
                .body("client", is((Object) null));
    }
}

@Test
void reportsHealthyWithoutAuthentication() {
    assertHealthy(given().accept("application/json").when().get("/q/health").then());
}

@Test
void acceptsEmptyHealthBodyAndUnmatchedAccept() {
    for (String accept : new String[]{"application/json", "application/xml"}) {
        assertHealthy(given().contentType("application/json").accept(accept).body("{}")
                .when().get("/q/health").then());
    }
}

private void assertHealthy(ValidatableResponse response) {
    response.statusCode(200).contentType("application/json")
            .body("", aMapWithSize(2)).body("status", is("UP"))
            .body("checks", empty());
}
```

Para los 500 se conserva el doble manual existente que fuerza la excepcion, se instala ese servicio con QuarkusMock.installMockForType durante el metodo de test y se verifica tambien la llamada HTTP. Quarkus restaura el mock al finalizar ese metodo. Se prueba el contrato heredado, no se califica la exposicion de detalles como segura. No se agrego Mockito.

## Comandos y resultados ejecutados

Directorio para todos los comandos: raiz del repositorio workshop-auth-backend-1, en reqflow-workspace. Entorno observado: Windows 11, PowerShell, Maven 3.10.0 y JDK Temurin 21.0.12.1. Ejecutar desde la raiz con JAVA_HOME apuntando a JDK 21; no se requiere una clave real ni la SQLite del operador.

| comando | codigo de salida | resultado real |
|---|---|---|
| mvn -Dtest=AuthResourceTest test (primera incorporacion) | 1 | 23 tests, 1 fallo, 0 errores, 0 omitidos. Diferencia de modo test/prod descrita abajo. |
| mvn -Dtest=AuthResourceTest -Dit.test=AuthProtocolIT verify | 0 | 19 tests del Resource y 4 de protocolo aprobados; empaquetado en prod y gate satisfecho. |
| mvn -Dtest=AuthResourceTest#preservesExceptionDetailsAndFallback test | 0 | 1 test aprobado; comprueba las dos variantes 500 directa y por HTTP. |
| mvn -q clean | 0 | Compilacion y datos de cobertura anteriores eliminados. Se conservaron los logs de evidencia, no jacoco.exec. |
| mvn verify | 0 | Ejecucion final limpia: 31 tests Surefire y 4 Failsafe aprobados, sin fallos, errores ni omisiones; BUILD SUCCESS. |

La ejecucion final termino a las 2026-10-19T17:04:12-05:00. Duracion total Maven: 59.241 segundos.

| suite | motor | tests | fallos | errores | omitidos | tiempo de suite (s) |
|---|---|---|---|---|---|---|
| AuthResourceTest | Surefire | 19 | 0 | 0 | 0 | 9.493 |
| AuthServiceTest | Surefire | 1 | 0 | 0 | 0 | 0.592 |
| PasswordStorageTest | Surefire | 6 | 0 | 0 | 0 | 2.276 |
| TokenServiceTest | Surefire | 5 | 0 | 0 | 0 | 0.201 |
| AuthProtocolIT | Failsafe, aplicacion prod | 4 | 0 | 0 | 0 | 11.67 |
| Total | Ambos | 35 | 0 | 0 | 0 | No equivale al tiempo total de Maven |

Configuracion en [pom.xml](../pom.xml): Quarkus genera el ejecutable durante package; Failsafe ejecuta los IT durante integration-test y valida el resultado en verify. Surefire sigue ejecutando las pruebas existentes. Por ello mvn test solo no sustituye a mvn verify. Se conserva CodeQL, Dependency Review y el gate JaCoCo. No se ejecutaron los dos primeros localmente.

Logs: [fallo inicial](../target/testing-endpoints-initial-failure.log), [ejecucion enfocada](../target/testing-focused.log), [500 por HTTP](../target/testing-http500.log) y [verificacion final](../target/testing-verify.log). Informes estructurados: target/surefire-reports/TEST-*.xml y target/failsafe-reports/TEST-*.xml. target es regenerable y se elimina con mvn clean.

## Cobertura y metodo de medicion

Meta de [REGLAS.md](../REGLAS.md): al menos 85% del codigo modificado. No se modificaron fuentes de produccion; se reporta y valida ademas toda la produccion del repositorio. No se calcula la cobertura de los propios archivos de tests como si fuera cobertura de negocio.

JaCoCo 0.8.12 instrumenta el JVM de Surefire con prepare-agent y genera el reporte en verify. Su check exige por cada clase de produccion al menos 0.85 de LINE/COVEREDRATIO y 0.85 de INSTRUCTION/COVEREDRATIO, sin nuevas exclusiones. Los IT se ejecutan en un proceso de aplicacion separado: el reporte no se presenta como cobertura de las ramas internas del framework atendidas por esas peticiones.

Medicion final desde limpio: sumar *_COVERED y *_MISSED del [CSV](../target/site/jacoco/jacoco.csv), y calcular porcentaje = 100 * covered / (covered + missed). Se contabilizan solo las filas de clase del reporte, no resultados JUnit.

| metrica | cubierto / total | porcentaje |
|---|---|---|
| Lineas | 169 / 171 | 98.83% |
| Instrucciones | 740 / 747 | 99.06% |
| Ramas | 63 / 68 | 92.65% |

Minimo por clase: PasswordHasher, 22/24 lineas (91.67%) y 109/116 instrucciones (93.97%). Las otras diez clases del reporte alcanzan 100% de lineas e instrucciones. No se fuerza la indisponibilidad del proveedor criptografico JDK ni se afirma cobertura total. Resultado real del check: All coverage checks have been met.

Comando PowerShell equivalente para volver a medir tras mvn verify:

```powershell
$rows = Import-Csv target/site/jacoco/jacoco.csv
foreach ($metric in @('LINE', 'INSTRUCTION', 'BRANCH')) {
    $covered = ($rows | Measure-Object -Property ($metric + '_COVERED') -Sum).Sum
    $missed = ($rows | Measure-Object -Property ($metric + '_MISSED') -Sum).Sum
    '{0}: {1}/{2} = {3:N2}%' -f $metric, $covered, ($covered + $missed), (100 * $covered / ($covered + $missed))
}
```

Reportes completos: [HTML JaCoCo](../target/site/jacoco/index.html) y [CSV JaCoCo](../target/site/jacoco/jacoco.csv). No basta un BUILD SUCCESS de mvn test: verify es el comando que aplica el check configurado.

## Fallos

Fallos actuales: ninguno en la ejecucion final. El primer intento encontro una diferencia de entorno: BuiltinMismatchedInputExceptionMapper devuelve detalles objectName/attributeName en LaunchMode TEST, pero cuerpo vacio en el empaquetado prod, que es el modo documentado. Se conservo el fallo inicial y se movieron las mismas aserciones a AuthProtocolIT; no se cambio el resultado esperado ni se desactivo el mapper para ocultarlo.

No se detecto un bug nuevo de produccion que requiera modificarla en este encargo. Las limitaciones conocidas de exposicion de detalles 500, errores JDBC traducidos a 401 y CORS permisivo se reportan aparte en [HALLAZGOS.md](../HALLAZGOS.md). Caracterizarlas no implica aprobarlas como practicas seguras ni cambiar su estado.

## Pendientes

- No hay respuestas de error documentadas sin una prueba: el 503 se verifica mediante un fixture temporal de la extension. Eso no agrega un check JDBC o de negocio en produccion ni prueba que SQLite caida implique 503. La pregunta abierta del contrato sobre un check real de negocio sigue vigente.
- Tamano maximo, timeouts, errores de proxy y otros fallos internos de salud siguen sin una expectativa contractual definida. No se inventan codigos o cuerpos para probarlos.
- No se probo integracion con consumidores externos de JWT ni ejecucion remota de CodeQL/Dependency Review. Las pruebas de firma/expiracion son locales.

## Pruebas propuestas, no ejecutadas

No se deja una prueba propuesta en lugar de una respuesta de error definida. Solo se propone ampliar la matriz cuando se acuerden las expectativas abiertas de limites/proxy o se incorpore un check de salud de negocio real. No se propone un endpoint nuevo ni se adelanta un resultado para esos escenarios. No ejecutar estas pruebas con paralelismo de metodos sin aislar antes los fixtures que modifican temporalmente el registro de salud, el mock CDI o el esquema SQLite.

## Documentos consultados

- [ENDPOINTS.md](ENDPOINTS.md): endpoints, ejemplos, validaciones, errores y limites abiertos.
- [REGLAS.md](../REGLAS.md): cobertura minima y reglas de pruebas/refactor.
- [pom.xml](../pom.xml): versiones, Surefire, empaquetado Quarkus, Failsafe y gate JaCoCo.
- Fuentes de prueba enlazadas en la seccion de codigo y API publica de SmallRyeHealthReporter; para diagnosticar el primer fallo se inspecciono la condicion LaunchMode del mapper de la dependencia Quarkus resuelta.
- Logs, informes JUnit y JaCoCo de las ejecuciones descritas: evidencia de resultados, no comportamientos supuestos.