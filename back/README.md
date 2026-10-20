# PagoClaro — conciliación segura de pagos

Solución de soporte N3 para diagnosticar y conciliar un pago usando evidencia persistida. Corrige estados y débitos duplicados mediante movimientos compensatorios; conserva los movimientos originales y deriva los casos ambiguos a revisión manual.

Java 21 · Spring Boot 3.5.16 · Spring JDBC · H2 local · Oracle 19c+ · JUnit 5.

## Ejecutar y probar

Requisito: JDK 21 y acceso a Maven Central en la primera ejecución. Maven Wrapper descarga Maven 3.9.11. No requiere instalar Oracle para ejecutar la demostración.

```powershell
# Windows, desde la carpeta back/
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

```bash
# Linux/macOS, desde back/
./mvnw clean verify
./mvnw spring-boot:run
```

El perfil predeterminado `local` crea H2 en memoria con los cuatro pagos del enunciado. Los datos se reinician al detener la aplicación. La API escucha en `127.0.0.1:8080`. Credenciales exclusivas de demostración: `n3` / `demo-change-me`; se pueden cambiar con `SUPPORT_USER` y `SUPPORT_PASSWORD`.

Ejecute estos comandos desde `back/`. En este workspace se descargaron JDK y Maven portátiles dentro de `.tools`, excluidos de Git. Para aprovecharlos sin configurar el sistema: `powershell -ExecutionPolicy Bypass -File .\scripts\dev.ps1 verify` y, para iniciar, cambiar `verify` por `run`. El script también admite un JAVA_HOME previamente configurado y usa cachés dentro del proyecto.

```powershell
curl.exe -u n3:demo-change-me -X POST http://127.0.0.1:8080/api/support/payments/PAY-BX17/reconcile
# Repetir: ALREADY_CONSISTENT, 0 movimientos y auditCreated=false
curl.exe -u n3:demo-change-me -X POST http://127.0.0.1:8080/api/support/payments/PAY-BX17/reconcile
curl.exe -u n3:demo-change-me -X POST http://127.0.0.1:8080/api/support/payments/PAY-CQ22/reconcile
```

Primera respuesta de BX17:

```json
{
  "reference": "PAY-BX17",
  "outcome": "RESOLVED",
  "reason": "APPROVED_RECONCILED",
  "paymentStatus": "CONFIRMED",
  "netDebitBefore": 178000.00,
  "netDebitAfter": 89000.00,
  "movementsCreated": 1,
  "auditCreated": true
}
```

`POST /api/support/payments/{reference}/reconcile` acepta entre 1 y 40 letras ASCII, números, `_` o `-`. No requiere cuerpo. Revisión manual es un resultado de negocio HTTP 200; **no significa que el pago haya sido corregido**. Errores: 400 referencia inválida, 401 sin autenticación, 403 sin rol SUPPORT, 404 pago inexistente, 409 bloqueo ocupado, 503 fallo transaccional/persistencia. Las respuestas de error usan Problem Detail y no incluyen SQL ni stack traces.

`GET /api/support/session` valida credenciales Basic y rol SUPPORT para el login del frontend. Devuelve únicamente `{ "username": "...", "role": "SUPPORT" }` con `Cache-Control: no-store`; no consulta ni modifica pagos ni crea una sesión de servidor. Sin credenciales o con contraseña inválida: 401; con un rol sin acceso: 403. Todos los comandos siguen validando la autorización por separado.

## Política implementada

| Evidencia e historial | Acción |
|---|---|
| Solo APPROVED, un débito completo activo | Confirmar estado si hace falta; conservar el débito. |
| Solo APPROVED, varios débitos completos activos | Revisión manual por defecto; solo en simulación local conservar menor ID y reversar sobrantes. |
| Solo APPROVED, sin historial contable | Revisión manual: no crear débitos sin evidencia de captura. |
| Solo APPROVED, todos los débitos ya revertidos | Revisión manual: podría existir una devolución intencional. |
| Solo DECLINED, sin ledger | Pasar a REJECTED si hace falta. |
| Solo DECLINED, con historial contable | Revisión manual; no inferir autorización de devolución. |
| Sin eventos, eventos contradictorios/desconocidos | Revisión manual. |
| Débitos parciales, reversos inválidos, importes no positivos | Revisión manual, sin compensar. |
| Estado y ledger ya consistentes | Sin escrituras, incluso en auditoría. |

**Política de duplicados:** la opción `reconciliation.allow-duplicate-compensation=true` solo se permite en simulación local, sin perfil oracle. Fuera de esa simulación se rechaza al arrancar. Oracle mantiene el parámetro opcional predeterminado en 0; valor 1 requiere compilación específica de laboratorio. La compilación segura rechaza el valor 1. El menor ID activo aporta determinismo, pero no demuestra captura externa. Consultar [corrección final](../docs/03-base-de-datos/CORRECCION_INTEGRIDAD_ORACLE.md).

`netDebit = SUM(DEBIT.amount) - SUM(REVERSAL.amount)`, calculado con `BigDecimal`. Un reverso válido referencia un débito del mismo pago por su importe completo; cada débito admite un único reverso. Los reversos parciales quedan fuera del alcance seguro.

Supuestos críticos: APPROVED solo permite corregir estado cuando existe un débito completo activo válido; no permite crear débitos. Existe una sola moneda por pago y todas las filas usan esa moneda. Si el contrato permite estados posteriores, capturas múltiples o pagos parciales, revisar el contrato antes de aplicar correcciones. La compensación automática productiva permanece deshabilitada.

## Consistencia y estructura

```text
api/          HTTP, validación, autorización y errores
service/      Transacción, coordinación y huella de evidencia
domain/       Motor de decisión puro, sin dependencias Spring
persistence/  SQL parametrizado y bloqueo de PAYMENT
db/oracle/    DDL, detección, PL/SQL, protección del ledger y validación
```

Cada conciliación usa READ COMMITTED explícito y bloquea PAYMENT con `SELECT ... FOR UPDATE WAIT 5`, luego lee eventos y ledger, decide y persiste movimientos, estado y auditoría en una única transacción. Un fallo revierte la unidad completa. `row_version` se incrementa una sola vez por corrección, incluso cuando el cambio fue solo contable.

La segunda solicitud espera el bloqueo y evalúa el estado ya confirmado por la primera. Esto funciona entre instancias que comparten base de datos. El bloqueo del padre por sí solo no protege inserciones hijas; por eso el esquema H2 incorpora AccountingGuard y Oracle entrega guardas que bloquean el padre al escribir eventos/ledger. Se comprobaron escritores directos concurrentes en H2. La ejecución equivalente en Oracle sigue pendiente. Los usuarios runtime no deben poder desactivar esas protecciones; operaciones con varios pagos necesitan un orden de bloqueo acordado.

La auditoría lleva SHA-256 incremental de la política, motivo, estado, importe, versión, IDs y estados de eventos, e IDs/tipos/importes/relaciones del ledger. Snapshot ordena sus listas por ID independientemente del repositorio y el formato decimal usa dos posiciones. Java y PL/SQL definen la misma serialización; se verificaron permutaciones y el vector Java, mientras la paridad real Oracle queda pendiente en su prueba de contrato. No se incluyen timestamps ni cambios de metadatos del ID externo de una misma fila de evento: para esta política, evidencia nueva implica nuevas filas o cambios de estado/importe/versión/relaciones evaluados. Restricción única `(payment_id,evidence_key)` evita auditorías equivalentes. Los casos sanos no se auditan en tabla; los logs conservan el intento después del commit.

Los logs incluyen ID interno del pago, resultado, motivo y cantidad de movimientos. No registran Authorization, credenciales, cuerpos ni datos personales. La autenticación Basic es suficiente para la demostración local; el despliegue real requiere TLS y credenciales gestionadas. CSRF está desactivado para esta API de comandos sin sesiones. La consola web usa autorización explícita en cada petición y un proxy local que rechaza comandos de otro origen; no almacena credenciales en cookies.

## Oracle

El perfil Oracle utiliza el servicio Java con SQL JDBC; el procedimiento PL/SQL es una interfaz alternativa para operación directa. Ambos deben producir las mismas decisiones y huellas. No se encadenan uno sobre otro.

Laboratorio vacío, usando SQLcl o SQL*Plus:

```sql
-- Desde db/oracle, conectado a un esquema de laboratorio vacío:
@demo_install.sql
-- Ejecuta DDL, muestra, migración, procedimiento, trigger y aserciones.
-- Las aserciones hacen ROLLBACK; permanecen los datos de muestra originales.
@02_detect_anomalies.sql
```

Sobre un esquema existente: no ejecutar `00_schema.sql` ni `05_seed_demo.sql`. Respaldar datos/DDL, detener escritores, revisar `07_preflight.sql` y ejecutar `deploy_existing.sql` con DBA. Incluye migración reanudable, CHECK para nuevas escrituras, guardas y verificación de objetos. El DDL hace commits implícitos; no se elimina ni reescribe historia. Las auditorías históricas conservan evidence_key NULL. Seguir la [guía Oracle completa](../docs/03-base-de-datos/ORACLE_INSTALACION_Y_RECUPERACION.md).

```powershell
$env:SPRING_PROFILES_ACTIVE = 'oracle'
$env:ORACLE_URL = 'jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
$env:ORACLE_USER = '<usuario>'
$env:ORACLE_PASSWORD = '<secreto>'
$env:SUPPORT_PASSWORD = '<secreto-soporte>'
.\mvnw.cmd spring-boot:run
```

Las variables de ejemplo son marcadores; no almacenarlas en Git. Oracle no inicializa ni siembra tablas al arrancar Spring.

Invocación PL/SQL:

```sql
SET SERVEROUTPUT ON
DECLARE
 v_out VARCHAR2(40); v_reason VARCHAR2(100); v_before NUMBER; v_after NUMBER;
BEGIN
 reconcile_payment('PAY-BX17',v_out,v_reason,v_before,v_after);
 DBMS_OUTPUT.PUT_LINE(v_out||' / '||v_reason||' / '||v_before||' -> '||v_after);
 COMMIT; -- también confirma una auditoría de revisión manual
EXCEPTION WHEN OTHERS THEN ROLLBACK; RAISE;
END;
/
```

El procedimiento no hace COMMIT y revierte a un SAVEPOINT si falla. El llamador debe cerrar la transacción también en NOT_FOUND o ALREADY_CONSISTENT. Espera hasta 5 segundos por el pago; errores técnicos se propagan. La API usa timeout transaccional de 15 segundos y H2 tiene LOCK_TIMEOUT de 5 segundos. Las secuencias pueden dejar huecos tras rollback; es normal.

La consulta de detección selecciona pagos con actividad en cualquiera de las tres tablas durante las últimas 24 horas y calcula su saldo con **todo** el historial. Usa agregaciones independientes para evitar el producto eventos × movimientos. Reporta diferencia de neto, estado incompatible, contradicción y ausencia de evidencia. `LOCALTIMESTAMP` supone que las columnas TIMESTAMP se guardan en la misma zona de sesión; establecer la zona acordada al conectar. No es una autorización para corregir ni una validación exhaustiva de vínculos del ledger.

## Pruebas y límites de validación

`clean verify` ejecuta pruebas del motor, integración JDBC/Spring y consulta SQL real de entrega. Incluye los cuatro casos, repetición con conteos persistidos, evidencia nueva, dos hilos/transacciones concurrentes, decimales, inexistencia, falta de evidencia, DECLINED, ledger ambiguo, reversos cruzados/duplicados/parciales y rollback por fallo de auditoría. También valida autenticación, rol, HTTP y errores.

H2 demuestra el comportamiento local de Java y la consulta, **no compila PL/SQL ni certifica bloqueo/driver Oracle**. La compilación y ejecución Oracle quedan pendientes en este entorno sin servidor Oracle. `06_verify_procedure.sql` contiene aserciones reproducibles de muestra e idempotencia; `../docs/03-base-de-datos/VALIDACION_ORACLE.md` detalla concurrencia, rollback y paridad entre interfaces.

Validación final: 54 pruebas registradas por Maven, 53 aprobadas y una suite Oracle omitida. Incluye guardas, concurrencia, rollback, configuración de simulación, aprobación sin ledger y rechazo de contraseña demo fuera de local. Once escenarios comunes alimentan el contrato Java y el contrato Oracle opcional. Consultar [informe vigente](../docs/04-validaciones/VALIDACION_FINAL_ENTREGA.md).

Los errores 401 y 403 de Spring Security también usan application/problem+json y Cache-Control: no-store. El endpoint de acceso conserva HTTP Basic y el rol SUPPORT; no se añadieron usuarios en base, JWT ni sesiones. El arranque no local rechaza la contraseña demo o una contraseña vacía.

Para una compilación realmente limpia sin eliminar el target de una aplicación abierta, desde back/:

```powershell
$env:DEBUG = 'false'
$deliveryBuild = Join-Path (Get-Location) '.tools\delivery-build'
.\mvnw.cmd '-Dmaven.repo.local=.m2' '-Dartifact.suffix=-audit' "-Dbuild.output-directory=$deliveryBuild" -B clean verify
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\audit-smoke.ps1 -JarPath "$deliveryBuild\support-reconciliation-1.0.0-audit.jar"
```

La propiedad build.output-directory conserva target como valor habitual. La validación de entrega usa únicamente .tools/delivery-build; el JAR de ese directorio es el que se probó por HTTP y navegador. No confundirlo con un JAR anterior en target/.

Para verificar sin tocar el JAR ni los datos H2 de una aplicación abierta, desde `back` con JAVA_HOME configurado:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=.m2' '-Dartifact.suffix=-audit' -B verify
powershell -ExecutionPolicy Bypass -File .\scripts\audit-smoke.ps1
```

Genera `target/support-reconciliation-1.0.0-audit.jar`; el smoke inicia y detiene una instancia aislada en 18082, verifica los cuatro casos, repetición, política segura y reinicialización H2. Para adoptar los cambios en la instancia habitual, cerrar esa instancia y ejecutar el iniciador con `-Build`, considerando que H2 en memoria no conserva datos al detenerse.

## Diagnóstico y entrega

- [Diagnóstico y decisiones](../docs/02-guias/DECISIONES.md).
- [Guía operativa N3](../docs/02-guias/OPERACION_N3.md).
- [Validación Oracle pendiente](../docs/03-base-de-datos/VALIDACION_ORACLE.md).
- [Primera validación local (histórica)](../docs/04-validaciones/VALIDACION_LOCAL.md).
- [Auditoría final y resultados vigentes](../docs/04-validaciones/AUDITORIA_FINAL.md).
- [Trazabilidad de IA](../docs/05-entrega/USO_IA.md).
- [Análisis inicial del PDF](../docs/01-enunciado/ANALISIS_PRUEBA.md).

El historial Git registra la evolución real. No se requiere una cuenta de proveedor ni infraestructura adicional. La interfaz de soporte se encuentra en [front](../front/README.md). El alcance no incluye integración con el proveedor, captura/devolución real de fondos ni despliegue productivo.

## Swagger UI / OpenAPI

Integración con springdoc-openapi 2.8.17 para Spring Boot 3.5 (compatibilidad: https://springdoc.org/v2/).

En el perfil local:

1. Abre http://127.0.0.1:8080/swagger-ui/index.html (también /swagger-ui.html).
   El navegador solicitar? primero las credenciales Basic de soporte para abrir la documentaci?n.
2. Pulsa **Authorize** y usa las credenciales del laboratorio: `n3` / `demo-change-me`, salvo que hayas configurado otras.
3. Prueba primero `GET /api/support/session` con **Try it out → Execute**.
4. Para conciliar, abre `POST /api/support/payments/{reference}/reconcile`, escribe una referencia como `PAY-AX91` y ejecuta sin cuerpo. Esta operación sí puede modificar pagos, auditoría y ledger; `MANUAL_REVIEW` devuelve 200 y requiere revisión humana.

Contrato JSON: http://127.0.0.1:8080/v3/api-docs. La documentación local se puede leer sin credenciales; los endpoints de negocio mantienen Basic Auth y rol SUPPORT. Swagger no persiste la autorización entre recargas. No hay contraseñas preconfiguradas en el contrato.

Fuera del perfil local, `springdoc.api-docs.enabled` y `springdoc.swagger-ui.enabled` están deshabilitados por defecto. No habilites el perfil local en un entorno real. Los endpoints y activos de documentación solo admiten lectura GET en la política de seguridad.

Si ya existe un backend activo, los cambios requieren un nuevo arranque para estar disponibles en 8080. No es necesario detenerlo para probar esta versión: con Java 21, desde `back`, ejecutar en otra terminal:

```powershell
./mvnw.cmd '-Dbuild.output-directory=.tools/swagger-build' package
java -jar .tools/swagger-build/support-reconciliation-1.0.0.jar --server.port=8081 '--spring.datasource.url=jdbc:h2:mem:pagoclaro-swagger;MODE=Oracle;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000'
```

En ese caso abre http://127.0.0.1:8081/swagger-ui/index.html. Esa instancia usa su propia H2 en memoria y no comparte datos con la instancia original. Detenerla elimina únicamente sus datos temporales. El comando de validación completo es `./mvnw.cmd '-Dbuild.output-directory=.tools/swagger-build' verify`; incluye contrato OpenAPI, recursos de UI y regresión de autenticación/autorización.

Validación de esta integración (19/10/2026): Maven verify aprobó 58 pruebas, sin errores ni fallos; una suite Oracle quedó omitida por falta de entorno. Incluye cinco pruebas nuevas de documentación/seguridad. En Chrome real se verificaron carga de Swagger, Authorize con Basic y Try it out de GET /api/support/session (200, usuario n3/rol SUPPORT); sin credenciales la API devolvió 401. Se usó una instancia independiente en 8081 y no se alteró la aplicación original de 8080.
