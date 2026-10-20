[Volver al ?ndice de documentaci?n](../README.md)

# Evidencia de validación local

Este documento conserva la primera validación. Los resultados vigentes de la auditoría posterior están en [AUDITORIA_FINAL.md](AUDITORIA_FINAL.md): 48 pruebas Java/H2 aprobadas, una suite Oracle omitida y 31 pruebas del frontend aprobadas.

Ejecutada el 19 de octubre de 2026, zona America/Bogota. Estado: Java y demostración HTTP verificados; servidor Oracle no disponible.

## Compilación final

Comando equivalente con la organización actual, desde la raíz:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\back\scripts\dev.ps1 verify
```

El script utilizó JDK Temurin 21.0.12.1, Maven Wrapper 3.3.4 y Maven 3.9.11, con caché local. Se ejecutó `clean verify`, generando `back/target/support-reconciliation-1.0.0.jar` ejecutable.

Resultado final registrado a las 12:00:24:

```text
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Suite | Pruebas | Evidencia principal |
|---|---:|---|
| ReconciliationIntegrationTest | 17 | Casos del PDF, idempotencia, nueva evidencia, dos transacciones concurrentes, rollback, autenticación, rol y errores HTTP sanitizados. |
| ReconciliationEngineTest | 4 | Reversos duplicados/parciales, elección determinista y tipos/importes inválidos. |
| AnomalyQueryTest | 2 | SQL del archivo entregado, netos sin multiplicación y ventana de actividad con historial completo. |

Reportes regenerables: `back/target/surefire-reports/`. Logs de esta ejecución en `back/.tools/final-verify.log`, excluidos de Git junto con herramientas y dependencias.

## JAR y HTTP real

Se inició el JAR generado en loopback, puerto 18080, con perfil local. Se hicieron nueve solicitudes reales mediante Node fetch con aserciones de códigos, resultados e idempotencia. Se detuvo el proceso de verificación al terminar; para usar la aplicación iniciar una nueva instancia con los comandos del README.

| Solicitud | Resultado observado |
|---|---|
| BX17 sin autenticación | HTTP 401. |
| AX91 | RESOLVED, CONFIRMED, cero movimientos. |
| BX17 primera vez | RESOLVED, neto 178000 → 89000, un reverso y una auditoría. |
| BX17 repetida | ALREADY_CONSISTENT, cero movimientos, auditCreated=false. |
| CQ22 primera vez | MANUAL_REVIEW, PENDING, cero movimientos, una auditoría. |
| CQ22 repetida | MANUAL_REVIEW, cero movimientos, auditCreated=false. |
| DK44 | ALREADY_CONSISTENT, sin escrituras. |
| Pago inexistente | HTTP 404 PAYMENT_NOT_FOUND. |
| Referencia inválida | HTTP 400 INVALID_REFERENCE. |

Respuestas completas conservadas en [evidencia/http-local.json](../evidencia/inicial/http-local.json). Los logs del servidor confirmaron registro de conciliaciones después del commit. No se incluyen cabeceras de autorización en la evidencia.

## Pendiente de infraestructura

No se ejecutaron el procedimiento PL/SQL, trigger ni migración en un servidor Oracle. No se verificaron bloqueos ni paridad de huellas entre procesos Java y PL/SQL reales en Oracle. La consulta sí se ejecutó con H2 Oracle mode, lo que no certifica el motor Oracle.

Se priorizó una solución local ejecutable y probada; los scripts y el protocolo reproducible están en `back/db/oracle` y VALIDACION_ORACLE.md. El siguiente paso de certificación es ejecutar `demo_install.sql` sobre Oracle, resolver cualquier diferencia del motor y probar paridad/concurrencia entre ambas interfaces.

Despu?s de reorganizar el repositorio en back/front/docs se volvi? a ejecutar la verificaci?n completa desde la ra?z con el comando anterior. Las reglas de negocio y los escenarios de prueba se conservan.
