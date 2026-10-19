# PagoClaro — conciliación segura de pagos

Solución de soporte N3 para diagnosticar y conciliar un pago usando evidencia persistida. Corrige estados y débitos duplicados mediante movimientos compensatorios; conserva los movimientos originales y deriva los casos ambiguos a revisión manual.

Java 21 · Spring Boot 3.5.16 · Spring JDBC · H2 local · Oracle 19c+ · JUnit 5.

## Ejecutar y probar

Requisito: JDK 21 y acceso a Maven Central en la primera ejecución. Maven Wrapper descarga Maven 3.9.11. No requiere instalar Oracle para ejecutar la demostración.

```powershell
# Windows, desde la raíz
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

```bash
# Linux/macOS
./mvnw clean verify
./mvnw spring-boot:run
```

El perfil predeterminado `local` crea H2 en memoria con los cuatro pagos del enunciado. Los datos se reinician al detener la aplicación. La API escucha en `127.0.0.1:8080`. Credenciales exclusivas de demostración: `n3` / `demo-change-me`; se pueden cambiar con `SUPPORT_USER` y `SUPPORT_PASSWORD`.

En este workspace se descargaron JDK y Maven portátiles dentro de `.tools`, excluidos de Git. Para aprovecharlos sin configurar el sistema: `powershell -ExecutionPolicy Bypass -File .\scripts\dev.ps1 verify` y, para iniciar, cambiar `verify` por `run`. El script también admite un JAVA_HOME previamente configurado y usa cachés dentro del proyecto.

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

## Política implementada

| Evidencia e historial | Acción |
|---|---|
| Solo APPROVED, un débito completo activo | Confirmar estado si hace falta; conservar el débito. |
| Solo APPROVED, varios débitos completos activos | Conservar el de menor ID activo; agregar reverso completo por cada sobrante. |
| Solo APPROVED, sin historial contable | Crear un débito por el importe del pago y confirmar. |
| Solo APPROVED, todos los débitos ya revertidos | Revisión manual: podría existir una devolución intencional. |
| Solo DECLINED, sin ledger | Pasar a REJECTED si hace falta. |
| Solo DECLINED, con historial contable | Revisión manual; no inferir autorización de devolución. |
| Sin eventos, eventos contradictorios/desconocidos | Revisión manual. |
| Débitos parciales, reversos inválidos, importes no positivos | Revisión manual, sin compensar. |
| Estado y ledger ya consistentes | Sin escrituras, incluso en auditoría. |

`netDebit = SUM(DEBIT.amount) - SUM(REVERSAL.amount)`, calculado con `BigDecimal`. Un reverso válido referencia un débito del mismo pago por su importe completo; cada débito admite un único reverso. Los reversos parciales quedan fuera del alcance seguro.

Supuestos críticos: APPROVED es evidencia final suficiente para confirmar; los débitos completos repetidos corresponden al mismo pago; existe una sola moneda por pago y todas las filas usan esa moneda. Si el contrato del proveedor permite estados posteriores, capturas múltiples o pagos parciales, **no aplicar esta política automáticamente** sin ampliarla. Un REJECTED con evidencia exclusivamente APPROVED puede pasar a CONFIRMED bajo este supuesto; revisar el contrato antes de producción.

## Consistencia y estructura

```text
api/          HTTP, validación, autorización y errores
service/      Transacción, coordinación y huella de evidencia
domain/       Motor de decisión puro, sin dependencias Spring
persistence/  SQL parametrizado y bloqueo de PAYMENT
db/oracle/    DDL, detección, PL/SQL, protección del ledger y validación
```

Cada conciliación bloquea PAYMENT con `SELECT ... FOR UPDATE`, luego lee eventos y ledger, decide y persiste movimientos, estado y auditoría en una única transacción. Un fallo revierte la unidad completa. `row_version` se incrementa una sola vez por corrección, incluso cuando el cambio fue solo contable.

La segunda solicitud espera el bloqueo y evalúa el estado ya confirmado por la primera. Esto funciona entre instancias que comparten base de datos. **Todos los escritores de eventos, ledger y estado deben tomar primero ese mismo bloqueo**; el bloqueo del padre no impide por sí solo inserciones concurrentes en tablas hijas. Si los procesos actuales no cumplen este protocolo, detenerlos o adaptarlos antes de usar la corrección. Se asume aislamiento READ COMMITTED.

La auditoría lleva SHA-256 incremental de la política, motivo, estado, importe, versión, IDs y estados de eventos, e IDs/tipos/importes/relaciones del ledger. Orden por ID y formato decimal fijo de dos posiciones; Java y PL/SQL comparten el formato. No se incluyen timestamps: cambiar una fecha no es evidencia nueva para esta política. Restricción única `(payment_id,evidence_key)` evita auditorías equivalentes. Nueva evidencia puede generar una nueva revisión. Los casos sanos no se auditan en tabla; los logs conservan el intento.

Los logs incluyen ID interno del pago, resultado, motivo y cantidad de movimientos. No registran Authorization, credenciales, cuerpos ni datos personales. La autenticación Basic es suficiente para la demostración local; el despliegue real requiere TLS y credenciales gestionadas. CSRF está desactivado para esta API de comandos; no se entrega una interfaz web ni sesiones.

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

Sobre un esquema existente: no ejecutar `00_schema.sql` ni `05_seed_demo.sql`. Revisar y aplicar `01_support_migration.sql`, `03_reconcile_payment.sql` y `04_ledger_immutability.sql` con DBA. El DDL hace commits implícitos. Detener escritores durante la migración para inicializar las secuencias por encima de los IDs existentes, o reutilizar secuencias compartidas. Revisar reversos ya duplicados antes de crear el índice único. Las auditorías históricas conservan evidence_key NULL. El trigger bloquea UPDATE/DELETE del ledger; la cuenta operativa no debe poder desactivarlo.

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

H2 demuestra el comportamiento local de Java y la consulta, **no compila PL/SQL ni certifica bloqueo/driver Oracle**. La compilación y ejecución Oracle quedan pendientes en este entorno sin servidor Oracle. `06_verify_procedure.sql` contiene aserciones reproducibles de muestra e idempotencia; `DOCS/VALIDACION_ORACLE.md` detalla concurrencia, rollback y paridad entre interfaces.

## Diagnóstico y entrega

- [Diagnóstico y decisiones](DOCS/DECISIONES.md).
- [Guía operativa N3](DOCS/OPERACION_N3.md).
- [Validación Oracle pendiente](DOCS/VALIDACION_ORACLE.md).
- [Resultados locales: 23 pruebas y HTTP real](DOCS/VALIDACION_LOCAL.md).
- [Trazabilidad de IA](DOCS/USO_IA.md).
- [Análisis inicial del PDF](DOCS/ANALISIS_PRUEBA.md).

El historial Git registra la evolución real. No se requiere una cuenta de proveedor ni infraestructura adicional. El alcance de evaluación no incluye integración con el proveedor, captura/devolución real de fondos, frontend ni despliegue productivo.
