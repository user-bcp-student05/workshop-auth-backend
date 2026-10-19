# Resultado de la ejecucion de tests

- Comando ejecutado: `mvn verify`.
- Repositorio: workshop-auth-backend-1.
- Fin de ejecucion: 2026-10-19T16:14:59-05:00.
- Tiempo total de Maven: 24.907 segundos.
- Resultado: **BUILD SUCCESS**.

## Pruebas

| Suite | Pruebas | Fallos | Errores | Omitidas | Tiempo (s) |
|---|---|---|---|---|---|
| AuthResourceTest | 14 | 0 | 0 | 0 | 8.373 |
| AuthServiceTest | 1 | 0 | 0 | 0 | 0.608 |
| PasswordStorageTest | 6 | 0 | 0 | 0 | 2.291 |
| TokenServiceTest | 5 | 0 | 0 | 0 | 0.182 |
| **Total** | **26** | **0** | **0** | **0** | |

Las 26 pruebas finalizaron correctamente, sin fallos, errores ni omisiones.

## Cobertura

JaCoCo informo: `All coverage checks have been met.` El gate configurado de al menos 85% de lineas e instrucciones por clase de produccion fue satisfecho.

Este resultado no implica que CodeQL o Dependency Review se hayan ejecutado localmente ni modifica los estados registrados en [HALLAZGOS.md](HALLAZGOS.md).

## Evidencia

- [Log completo de la ejecucion](target/latest-test-run.log).
- [Reporte HTML de JaCoCo](target/site/jacoco/index.html).
- [Reporte CSV de JaCoCo](target/site/jacoco/jacoco.csv).

Los artefactos de target son regenerables y pueden eliminarse con `mvn clean`. Este informe resume la ejecucion indicada; no se repitieron los tests para redactarlo.