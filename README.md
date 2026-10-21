# Workshop Auth Backend — Java Quarkus

> **Laboratorio intencionalmente vulnerable.** Ejecutar únicamente en local, repositorios privados y ambientes aislados. No desplegar en Internet, CERT o PROD.

Backend Quarkus que autentica usuarios de laboratorio almacenados en SQLite y entrega un token consumido por el frontend Angular y la API Python.

## Ejecución

Requisitos: JDK 25 (LTS) y Maven 3.9+. Para generar una clave de laboratorio con el ejemplo siguiente se necesita OpenSSL; tambien puede proporcionarse un archivo PEM PKCS#8 de prueba ya existente. Maven debe usar JDK 25 mediante JAVA_HOME.

Ejemplo PowerShell, exclusivamente local. La clave generada no se versiona y no debe reutilizarse en otros ambientes:

```powershell
New-Item -ItemType Directory -Path target -Force
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out target/lab-only-key.pem
$env:AUTH_TOKEN_KEY_LOCATION = (Resolve-Path target/lab-only-key.pem).Path
mvn quarkus:dev
```

La clave debe ser RSA de al menos 2048 bits. Sin clave externa valida, el backend no arranca. No existe secreto de respaldo. `mvn clean` elimina la clave de laboratorio del ejemplo; generarla de nuevo para otra sesion.

Servicio: `http://localhost:8080`

Credenciales locales:

- `student` / `Workshop123!`
- `trainer` / `Trainer123!`

## Endpoints

- `POST /auth/login`
- `GET /q/health`

## Configuracion externa

| Variable | Uso | Valor por defecto |
|---|---|---|
| AUTH_TOKEN_KEY_LOCATION | Ruta local o URI file: de la clave privada PEM PKCS#8 | Ninguno; requerida |
| AUTH_DB_URL | URL JDBC SQLite | jdbc:sqlite:./data/workshop.db |
| AUTH_CORS_ORIGINS | Lista explicita de origenes para una politica autorizada | /.*/ |
| AUTH_CORS_METHODS | Metodos permitidos | GET,POST,PUT,DELETE,OPTIONS |
| AUTH_CORS_HEADERS | Cabeceras permitidas | accept,authorization,content-type,x-requested-with |

El valor CORS permisivo se conserva por compatibilidad con el contrato congelado; no se presenta como una politica segura. No habilitar otra politica sin acordar origenes/cabeceras y compatibilidad. El JWT propuesto usa RS256, sub, role, iat y exp; expiresIn sigue siendo 3600. Antes de activar el formato en una integracion, los responsables de consumidores deben confirmar algoritmo, claims, clave publica y verificacion de expiracion. No se han inspeccionado ni modificado esos consumidores.

## Migracion y pruebas

Antes de iniciar esta version contra una SQLite heredada, detener la version anterior y todos los procesos que accedan a esa base; hacer una copia local protegida. El repositorio se inicializa antes de servir trafico y solicita bloqueo exclusivo de SQLite. La migracion transaccional de version 0 a 1 transforma las contrasenas a PBKDF2-HMAC-SHA256 con 600000 iteraciones, salt aleatorio de 16 bytes y hash de 32 bytes, conserva los usuarios y elimina la columna password. Si falla, hace rollback; no usa comparacion en claro como fallback. El manejo heredado de inicializacion registra el error y puede dejar el login sin usuarios disponibles, por lo que hay que comprobar el arranque y los logins antes de admitir uso. No interpretar solamente /q/health como prueba de migracion correcta.

No volver a ejecutar la version antigua contra una base ya migrada. Verificar la migracion antes de retirar la copia de respaldo. Backups, WAL y paginas libres pueden retener datos anteriores: la eliminacion de una columna no garantiza borrado fisico. La gestion de esas copias sensibles queda a cargo del operador; no se ha tocado la base local del usuario durante la implementacion.

```bash
mvn verify
```

Las pruebas crean SQLite y claves efimeras exclusivamente en target, sin requerir secretos reales ni una base local. JaCoCo exige al menos 85% de lineas e instrucciones por clase de produccion, sin exclusiones agregadas. CodeQL y Dependency Review permanecen intactos. El contrato original esta en [docs/ENDPOINTS.md](docs/ENDPOINTS.md) y el unico seguimiento de correcciones y limitaciones esta en [HALLAZGOS.md](HALLAZGOS.md).
