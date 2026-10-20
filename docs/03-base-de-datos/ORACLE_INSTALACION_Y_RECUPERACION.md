[Volver al ?ndice de documentaci?n](../README.md)

# Oracle 19c+: instalación, validación y recuperación

**Estado: pendiente de ejecución real.** No hay servidor Oracle ni SQLcl/SQL*Plus disponibles en el entorno de esta auditoría. La revisión de fuentes y las pruebas H2 no certifican PL/SQL, DDL, triggers, driver, bloqueos ni planes Oracle.

## Requisitos y precauciones de instalación

Usar primero un esquema de laboratorio dedicado en Oracle 19c+, SQLcl o SQL*Plus y credenciales provistas fuera de Git. El propietario necesita los privilegios de creación de tablas, índices, secuencias, triggers y procedimientos correspondientes. La cuenta runtime no debe tener DDL ni capacidad para desactivar guardas.

No instalar con escritores activos. Revisar FK, PK, valores fuera de dominio, referencias de reverso y auditorías equivalentes antes de DDL. El preflight aborta ante PK/FK obligatorias ausentes o identificadores/versiones no representables como enteros Java long. El modelo original mantiene NUMBER; la aplicación realiza conversiones exactas y no trunca identificadores.

Registrar `SELECT banner_full FROM v$version` si hay permiso, `SELECT DBTIMEZONE,SESSIONTIMEZONE,LOCALTIMESTAMP,CURRENT_TIMESTAMP FROM dual` y el inventario de objetos. Los TIMESTAMP no incluyen zona: la sesión debe seguir la convención con la que se almacenó el historial; no cambiar fechas históricas automáticamente.

## Laboratorio vacío

Desde `back/db/oracle`, abrir SQLcl/SQL*Plus y conectarse de forma interactiva:

```sql
CONNECT PAGOCLARO_LAB@//host:1521/service
-- La herramienta solicita la contraseña; no incluirla en archivos o línea de comandos.
SET ECHO ON
SPOOL oracle_demo_validation.log
@demo_install.sql
@02_detect_anomalies.sql
SPOOL OFF
```

Guardar el spool fuera del repositorio si contiene información de una base real. `demo_install.sql` es solo para esquema vacío y ejecuta exactamente:

1. `00_schema.sql`: modelo base.
2. `05_seed_demo.sql`: muestras ficticias; COMMIT del instalador.
3. `07_preflight.sql`: verificación e inventario previo.
4. `01_support_migration.sql`: clave de auditoría, índices y secuencias.
5. `08_integrity_constraints.sql`: CHECK para escrituras nuevas.
6. `03_reconcile_payment.sql`: procedimiento, sin COMMIT interno.
7. `04_ledger_immutability.sql`: rechazo de UPDATE/DELETE del ledger.
8. `09_write_guards.sql`: bloqueo del padre y relaciones de reverso.
9. Comprobación de errores de compilación.
10. `06_verify_procedure.sql`: muestras, política segura, repetición y vector SHA-256; ROLLBACK de los ensayos.
11. `10_verify_guards.sql`: restricciones e inmutabilidad; ROLLBACK.
12. `11_verify_rollback.sql`: fallo de auditoría y preservación del trabajo previo del llamador; limpieza del trigger de prueba.
13. `14_postcheck.sql`: objetos válidos, guardas y CHECK habilitados.

Los mensajes PASS de esos scripts son **resultados esperados**, todavía no observados en Oracle. Los ensayos preservan los datos de muestra, aunque consumir secuencias deja huecos normales. El paso 11 crea/elimina un trigger temporal y contiene DDL: no ejecutarlo dentro de una transacción de negocio.

La consulta debe devolver AX91, BX17 y CQ22; DK44 debe quedar excluido. BX17 debe tener neto 178000, sin multiplicarlo por sus dos eventos. El preflight debe dejar todas las violaciones de datos visibles, no corregirlas.

## Migración de una base existente

No ejecutar `00_schema.sql`, `05_seed_demo.sql` ni `demo_install.sql` sobre datos existentes.

1. Detener escritores, obtener backup/export de datos aprobado por DBA y conservar el DDL anterior. Para metadatos, `15_backup_metadata.sql` exige destino absoluto fuera del repositorio y raíz absoluta del repositorio como argumentos. El DBA verifica permisos y ubicación protegida. Ese archivo no reemplaza un backup de datos.
2. Ejecutar `07_preflight.sql` y revisar sus resultados. Corregir falta de estructura mediante una decisión revisada; no borrar/regrabar movimientos históricos para lograr que pase.
3. Revisar índices por columnas, no solo por nombre. La migración reutiliza índices válidos equivalentes y prefijos adecuados para búsquedas no únicas; no agrega una copia si ya existe esa cobertura. Un nombre ocupado con una definición distinta provoca un error que se debe investigar.
4. Ejecutar `deploy_existing.sql`. Orden: preflight → migración → CHECK → inmutabilidad → guardas de escritura → procedimiento → postcheck.
5. Inspeccionar salida, `USER_ERRORS`, `USER_OBJECTS`, `USER_TRIGGERS`, `USER_CONSTRAINTS`, `USER_INDEXES` y `USER_SEQUENCES` antes de habilitar la aplicación.

`01_support_migration.sql` es reanudable: agrega evidence_key si falta, rechaza duplicados incompatibles antes de crear unicidad y consume NEXTVAL para comprobar el valor real de secuencias existentes. LAST_NUMBER puede ser un límite de caché; no se trata como el siguiente ID efectivo. Si es necesario adelanta la secuencia y restaura su incremento positivo. Nunca la reinicia hacia atrás ni elimina filas. Todos los escritores deben compartir la estrategia de IDs acordada.

`08_integrity_constraints.sql` usa ENABLE NOVALIDATE: conserva valores históricos inválidos o evidence_key NULL, pero bloquea nuevas escrituras que violen los CHECK. No confundir ENABLED con VALIDATED. La investigación del legado sigue pendiente cuando existan filas fuera de dominio. El índice único no puede instalarse sobre referencias/auditorías equivalentes duplicadas: el script se detiene y exige revisión, sin destruir evidencia.

Las guardas se ejecutan con los privilegios del propietario y bloquean PAYMENT antes de las escrituras de eventos/ledger. El trigger compuesto valida reversos al finalizar la sentencia para evitar ORA-04091. Mantener una operación por pago; lotes con varios padres deben adquirirlos en orden ascendente y gestionar deadlocks con rollback completo.

## Política de compensación y llamadas

El llamador debe usar READ COMMITTED, igual que el servicio Java. Oracle lo usa por defecto, pero se debe comprobar que el cliente o un logon trigger no lo haya cambiado. Si se quiere declararlo explícitamente, ejecutar `SET TRANSACTION ISOLATION LEVEL READ COMMITTED` al inicio de una transacción nueva, antes de otras operaciones. El procedimiento no puede cambiar silenciosamente el aislamiento de una transacción previa del llamador. Un snapshot SERIALIZABLE antiguo queda fuera del contrato de ejecución segura.

Por defecto, Oracle y el servicio Java fuera de `local` no suponen que varios APPROVED demuestran una sola captura. Un pago con varios débitos activos devuelve MANUAL_REVIEW / DUPLICATE_DEBITS_REQUIRE_REVIEW.

El procedimiento agrega una última entrada opcional:

```text
reconcile_payment(external_ref, outcome OUT, reason OUT,
                  net_before OUT, net_after OUT,
                  allow_duplicate_compensation IN DEFAULT 0)
```

El valor 1 es exclusivo de simulación: requiere compilar el procedimiento en laboratorio con `pagoclaro_demo:TRUE`. La compilación segura FALSE lo rechaza con -20005. Java rechaza habilitar compensaciones fuera del perfil local o con oracle activo. Ni ese parámetro ni APPROVED acreditan captura externa. El instalador demo compila temporalmente TRUE, ejecuta sus pruebas y recompila FALSE antes del postcheck. Consultar [corrección final](CORRECCION_INTEGRIDAD_ORACLE.md).

```sql
SET SERVEROUTPUT ON
DECLARE
 v_out VARCHAR2(40); v_reason VARCHAR2(100); v_before NUMBER; v_after NUMBER;
BEGIN
 reconcile_payment('PAY-BX17',v_out,v_reason,v_before,v_after);
 DBMS_OUTPUT.PUT_LINE(v_out||'/'||v_reason||'/'||v_before||' -> '||v_after);
 COMMIT; -- también confirma la auditoría de revisión manual
EXCEPTION WHEN OTHERS THEN ROLLBACK; RAISE;
END;
/
```

NOT_FOUND devuelve netos NULL y no escribe. ALREADY_CONSISTENT no escribe pero conserva el bloqueo hasta que el llamador cierre la transacción. Ante cualquier excepción el procedimiento vuelve a su SAVEPOINT y propaga el error; el llamador debe ignorar todos los parámetros OUT de esa llamada. No se usa NOCOPY ni se promete que asignaciones OUT se copien al llamador si la ejecución falla. No interpretar una excepción de unicidad como éxito.

## Contrato automatizado Java/Oracle

La misma matriz de once escenarios en `back/src/test/resources/contract/reconciliation-cases.json` alimenta ReconciliationContractTest y OracleReconciliationContractTest. Incluye muestras, política segura, falta de evidencia, débito ausente/parcial, rechazo y aprobación revertida.

Preparar el esquema de laboratorio con el instalador anterior. Para probar duplicate-demo por JDBC, recompilar únicamente el laboratorio ficticio con `ALTER SESSION SET PLSQL_CCFLAGS='pagoclaro_demo:TRUE';` y `@03_reconcile_payment.sql`. Después de las pruebas recompilar FALSE y ejecutar `14_postcheck.sql`. Desde `back`, con Java/Maven disponibles:

```powershell
$env:ORACLE_TEST_URL = 'jdbc:oracle:thin:@//host:1521/service'
$env:ORACLE_TEST_USER = 'PAGOCLARO_LAB'
$env:ORACLE_TEST_PASSWORD = '<secreto proporcionado fuera del repositorio>'
.\mvnw.cmd '-Dtest=OracleReconciliationContractTest' test
```

La prueba inserta fixtures con IDs desplazados y referencias CT-*, verifica resultado, motivo, importes, versión, movimientos y SHA-256 contra Java, repite la llamada y finalmente hace ROLLBACK. No borra ni actualiza filas preexistentes. Usa un esquema dedicado y evita ejecuciones paralelas de esta prueba sobre el mismo esquema. Las secuencias pueden avanzar por las pruebas aunque las filas se reviertan.

Sin ORACLE_TEST_URL la suite queda omitida explícitamente; una omisión no cuenta como Oracle aprobado. Para comprobar NLS repetir desde una sesión/configuración que use NLS_NUMERIC_CHARACTERS `,.`; la serialización del importe debe seguir usando punto y dos decimales.

Oracle convierte explícitamente cada token a AL32UTF8 mediante UTL_I18N.STRING_TO_RAW antes de STANDARD_HASH, igual que StandardCharsets.UTF_8 en Java. Verificar acceso al paquete UTL_I18N al compilar. Los tokens del dominio normal son ASCII y conservan el vector previo; la conversión explícita evita depender del charset físico si se auditan valores de legado fuera del dominio. No se recalculan claves históricas automáticamente.

## Concurrencia entre sesiones

Usar las dos sesiones conectadas al mismo laboratorio, sin autocommit:

```sql
-- Sesión A
@12_lock_session_a.sql
-- Mantener abierta.

-- Sesión B
@13_compete_session_b.sql
```

Si A mantiene el bloqueo más de cinco segundos, B debe fallar por espera y dejar intactos estado, ledger y auditoría. Hacer ROLLBACK en A. Para probar espera exitosa, repetir y liberar A antes de cinco segundos; B debe completar y revertir el ensayo al terminar.

Para comprobar dos conciliaciones reales: A llama a BX17 con último parámetro 1 y no confirma; B llama al mismo pago. Confirmar A antes de agotar el WAIT de B: B debe responder ALREADY_CONSISTENT. Después confirmar B y verificar exactamente tres movimientos, un reverso a 5003 y una auditoría de corrección. Este ensayo confirma datos y debe ejecutarse al final en un laboratorio nuevo, no en una base operativa.

Para comprobar escrituras hijas: A bloquea CQ22; B intenta insertar un evento nuevo para payment_id=3. Debe esperar el padre o agotar el timeout, no incorporarse a una decisión a medio ejecutar. Hacer ROLLBACK en ambos. Repetir con un reverso competidor a 5003: después de que A confirme una conciliación, B no puede insertar otro reverso por el índice único. Guardar versiones, conteos y códigos Oracle reales.

Repetir el ensayo entre HTTP Java y PL/SQL para verificar driver y traducción de errores. Estos pasos no fueron ejecutados en esta auditoría.

## Consulta, índices y mediciones

La consulta considera actividad de las tres tablas en 24 horas, pero agrega todo el historial del pago. Usa agregaciones separadas y relaciones por PK para no multiplicar importes. Además informa vínculos inválidos, débitos parciales y duplicidad de reversos incluso cuando el neto coincide.

No se afirma una mejora de tiempo. El agregado de referencias de reverso añade trabajo; hace falta medir con volumen y distribución reales. Para evaluar: prefijar el SELECT de `02_detect_anomalies.sql` con `EXPLAIN PLAN SET STATEMENT_ID='PAGOCLARO_ANOMALIES' FOR`, ejecutarlo y consultar:

```sql
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL,'PAGOCLARO_ANOMALIES','BASIC +PREDICATE'));
```

Un plan estimado no sustituye tiempos ni filas reales. Revisar estadísticas y lecturas antes de proponer índices por updated_at/received_at/created_at; esos índices tienen costo de escritura/almacenamiento y no se crean automáticamente.

## Recuperación y rollback

DDL Oracle hace commits implícitos: ROLLBACK no deshace una migración parcialmente instalada. Mantener escritores detenidos si falla cualquier paso. Corregir el motivo, revisar el inventario y reanudar los scripts reentrantes; no cargar la muestra para “reparar” una base existente.

Preferir una corrección hacia adelante. Conservar evidence_key, auditorías, secuencias adelantadas y movimientos; no decrementar secuencias ni eliminar reversos. Para restaurar un procedimiento/trigger anterior, usar el DDL respaldado y revisar sus privilegios y garantías antes de habilitarlo. No ejecutar un DROP indiscriminado de restricciones o guardas, especialmente si ya existían antes de la migración.

Si es imprescindible volver a una versión anterior, documentar qué objetos creó cada paso, restaurar únicamente los objetos de código acordados y mantener suspendidas las compensaciones hasta verificar el conjunto. Un defecto en una corrección financiera exige una nueva operación contable autorizada; no borrar historia. Si falla una instalación de laboratorio a mitad de `11_verify_rollback.sql`, comprobar que no exista `TRG_TEST_AUDIT_FAILURE` antes de otras operaciones.

Fuentes de semántica consultadas: [triggers compuestos Oracle](https://docs.oracle.com/en/database/oracle/oracle-database/19/lnpls/dml-triggers.html), [ALTER TABLE y estados de restricciones](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/ALTER-TABLE.html) y [conversión explícita de charset con UTL_I18N](https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/UTL_I18N.html). Consultarlas no equivale a haber compilado los scripts.
