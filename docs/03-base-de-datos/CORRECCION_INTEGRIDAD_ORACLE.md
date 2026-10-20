[Volver al ?ndice de documentaci?n](../README.md)

# Corrección final de integridad — 19 de octubre de 2026

Este informe sustituye las conclusiones anteriores sobre creación de débitos y autorización de compensaciones. Oracle 19c+ sigue **pendiente de ejecución real**. No se ejecutaron migraciones ni se reinició la aplicación del usuario.

## A. Hallazgos confirmados

- Java y PL/SQL creaban un DEBIT cuando había APPROVED sin ledger. La aprobación no acredita captura definitiva.
- Un indicador de configuración o parámetro 1 podía habilitar compensaciones sin separar técnicamente laboratorio de producción.
- Los CHECK existentes ya cubrían tipo, positividad y relación nula/no nula; el índice único ya impedía doble reverso. Faltaba validar DEBIT.amount contra PAYMENT.amount al insertar directamente.
- La migración reutilizaba índices por columnas, unicidad y estado, pero no descartaba expresiones ni verificaba visibilidad para búsquedas. CHECK solo se comprobaba por nombre.
- La prueba BX17 y el postcheck eran insuficientes. El respaldo DDL usaba una ruta relativa.
- No se reprodujo una falta de SAVEPOINT, inmutabilidad o idempotencia: esas protecciones ya existían y se conservaron.

## B. Correcciones y alcance

`ReconciliationEngine.java` y `03_reconcile_payment.sql` devuelven MANUAL_REVIEW / APPROVED_WITHOUT_CAPTURE_EVIDENCE, neto intacto, sin movimientos ni cambio de versión, cuando APPROVED no tiene historial. La ruta Oracle de INSERT DEBIT fue eliminada. AX91 conserva su débito y solo corrige estado.

`ReconciliationService.java` permite activar compensación únicamente con perfil local y sin perfil oracle. Configurar true fuera de esa simulación impide arrancar. El controlador recibe únicamente la referencia: ni JSON ni query parameters habilitan políticas. Una prueba HTTP verifica el intento de activación desde el cliente.

PL/SQL conserva sus seis argumentos. El parámetro 1 solo se admite si el procedimiento fue compilado en laboratorio con `PLSQL_CCFLAGS='pagoclaro_demo:TRUE'`; fuera de esa compilación lanza -20005 antes de operar. El permiso de recompilar pertenece al propietario/DBA, nunca al usuario de aplicación. `deploy_existing.sql` compila FALSE. `demo_install.sql` habilita temporalmente la simulación para sus pruebas, luego recompila FALSE y ejecuta `18_verify_safe_policy.sql`. Esta separación impide que un llamador con solo EXECUTE habilite la simulación en la compilación segura; **no verifica ninguna captura externa**.

`AccountingGuard.java` y `09_write_guards.sql` rechazan débitos cuyo importe difiere del pago. No duplican CHECK ni unicidad. Oracle mantiene validación de relaciones AFTER STATEMENT para evitar ORA-04091; se añadió INSERT ALL de débito y reverso completos en una misma sentencia al laboratorio.

`01_support_migration.sql` solo reutiliza índices normales, válidos, no particionados, ascendentes y sin expresiones. Para acceso por pago exige prefijo ordenado y visibilidad. Para unicidad exige el conjunto exacto de columnas; el orden inverso es equivalente para unicidad, no para acceso por prefijo. Un índice único invisible sigue imponiendo unicidad y puede reutilizarse para esa regla. Un nombre ocupado incompatible produce error explícito; no se elimina ni reconstruye automáticamente. Si restaurar el incremento de una secuencia falla durante recuperación, se informa el error además de propagar el fallo original.

`08_integrity_constraints.sql` y `14_postcheck.sql` comparten `16_check_contract.sql`: comparan expresión, tabla, tipo CHECK, estado, validación y no diferibilidad. Reutilizan una restricción equivalente habilitada bajo otro nombre. Una restricción esperada deshabilitada o con expresión diferente exige intervención manual; no se habilita ni sustituye silenciosamente. Aceptan VALIDATED y NOT VALIDATED: la primera ofrece una garantía adicional sobre historial, la segunda corresponde a ENABLE NOVALIDATE.

La comparación conserva literales y precedencia y normaliza espacios fuera de literales y mayúsculas de identificadores ordinarios. Es deliberadamente conservadora: expresiones lógicamente equivalentes con paréntesis adicionales, identificadores entre comillas o distinto orden de términos pueden requerir revisión DBA. No pretende demostrar equivalencia semántica general. Una expresión truncada en SEARCH_CONDITION_VC no coincide con estos contratos breves y se rechaza.

`06_verify_procedure.sql` comprueba individualmente los débitos 5002/5003, reverso completo exclusivo de 5003, ausencia de reverso de 5002, neto real 89000 y conteos tras reintento. Incluye APPROVED sin ledger con auditoría idempotente.

`14_postcheck.sql` incorpora preflight, definición de CHECK e índices, firma de seis argumentos, configuración segura de compilación, secuencias crecientes NOCYCLE, triggers habilitados y objetos sin errores de compilación. `17_verify_storage_contract.sql` contiene las comprobaciones de almacenamiento. Un error produce resultado fallido y SQLCODE; solo al terminar todas las comprobaciones se imprime APPROVED. Aquí el resultado real sigue PENDIENTE porque no se ejecutó Oracle.

`15_backup_metadata.sql` exige destino absoluto y raíz absoluta del repositorio como argumentos, rechaza destino interno y referencias `..`. El operador debe comprobar permisos y resolver enlaces/junctions antes de ejecutarlo: el servidor Oracle no puede certificar el sistema de archivos del cliente SQL*Plus.

## C. Integridad y transacciones

Se conservan SELECT FOR UPDATE WAIT 5 sobre PAYMENT, READ COMMITTED, coordinación de escritores hijos con el padre, ledger inmutable, reversos completos del mismo pago y una sola reversión por débito. CHECK e índices validan nuevas escrituras sin corregir historial.

El procedimiento no contiene COMMIT: mantiene SAVEPOINT y ROLLBACK TO ante error. El llamador confirma o revierte. Debe descartar parámetros OUT si recibe una excepción. Java conserva la transacción conjunta de ledger, estado y auditoría. La huella SHA-256 continúa usando el mismo algoritmo y vector conocido para CQ22; el nuevo motivo genera su propia evidencia idempotente.

H2 valida relaciones en trigger Java por fila; Oracle usa trigger compuesto después de sentencia. Oracle ENABLE NOVALIDATE conserva posibles infracciones históricas; el esquema H2 de pruebas se crea limpio. Un débito parcial histórico se prueba como snapshot legado en Java; el contrato Oracle comprueba que esa nueva inserción es rechazada, sin desactivar guardas ni reescribir historia.

## D. Pruebas

Resultado final: **52 pruebas registradas, 51 aprobadas, cero fallos/errores y una suite Oracle omitida**. Frontend: **17 pruebas unitarias/proxy y 14 de navegador aprobadas**, cero fallos.

Comandos y resultados definitivos se registran en `evidencia/integridad-final-pruebas.json`. Maven se ejecuta desde back con `mvnw.cmd -Dmaven.repo.local=.m2 -Dartifact.suffix=-audit -B verify`; el sufijo evita reemplazar el JAR activo. Frontend: `npm test` y `npm run test:e2e` desde front.

La cobertura conserva AX91, BX17 seguro/demo, CQ22, DK44, reintentos, escritores concurrentes, fallo de auditoría y rollback, rechazo de reversos parciales/cruzados/dobles y huellas SHA-256. Añade bloqueo de configuración fuera de local, intento HTTP de activar compensación, aprobación sin ledger y atomicidad de inserción múltiple inválida. No se cuenta la suite Oracle omitida como aprobada.

## E. Oracle pendiente

1. Preparar una instancia 19c+ y un esquema de laboratorio vacío con respaldo y permisos revisados; ejecutar `demo_install.sql`, guardar salida y USER_ERRORS. Este instalador no se utiliza sobre datos existentes.
2. Verificar `06`, `10`, `11`, `18` y `14`, incluyendo la inserción múltiple y el rechazo de autorización en compilación segura.
3. Para el contrato JDBC de once escenarios, recompilar **solo el laboratorio ficticio** con TRUE y `03`; proporcionar ORACLE_TEST_URL/USER/PASSWORD por entorno y ejecutar `mvnw.cmd -Dtest=OracleReconciliationContractTest test`. Finalmente recompilar FALSE y repetir `14`. Ningún flag acredita capturas reales.
4. Ejecutar `12`/`13` en dos sesiones READ COMMITTED. `13` usa ahora política segura y comprueba espera/timeout sin habilitar compensación. Para estudiar doble compensación, repetir en laboratorio compilado TRUE con llamadores explícitos, confirmar una sesión y verificar que la otra no añade reversos ni auditorías equivalentes.
5. Probar reanudación de migración en copias de laboratorio: índices equivalentes con otro nombre/orden, índices inválidos/invisibles/funcionales, nombre ocupado, CHECK equivalente alternativo, deshabilitado o distinto, legado inválido, secuencias atrasadas y fallo parcial de DDL. Repetir instaladores pertinentes e inventarios; no inferir compatibilidad solo de H2.
6. Repetir contratos con NLS alternativo, verificar permisos UTL_I18N, firma, concurrencia entre procedimiento y escritor directo y planes de acceso.

## F. Riesgos y recuperación

La selección de 5003 es únicamente una regla determinista de la demostración ficticia. Para producción se necesitan identificador verificable de captura/liquidación, importe, moneda, relación con el pago y evidencia de cuántas capturas efectivas existieron; además, autorización auditable del ajuste. Ese mecanismo externo no está implementado. La compensación automática productiva permanece deshabilitada.

Lotes con varios pagos deben bloquear padres en orden ascendente antes del DML. Los triggers no garantizan ese orden entre filas de un INSERT ALL arbitrario: pueden producirse deadlocks y el llamador debe revertir y reintentar la unidad completa. La ejecución con dos sesiones Oracle sigue pendiente; no se afirma ausencia de deadlocks.

Metadatos DDL permiten comparar/restaurar definiciones; no contienen respaldo de filas. El DBA debe preparar backup físico o exportación lógica consistente, probar restauración y proteger los archivos fuera del repositorio. Por ejemplo: `@15_backup_metadata.sql D:/ProtectedBackups/PagoClaro/metadata-before.sql "C:/PRUEBA TECNICA IAS"`. No guardar backups ni credenciales en Git.

Oracle DDL confirma implícitamente. ROLLBACK no revierte una migración parcial. Ante fallo: detener escritores, guardar el error e inventario, comparar DDL respaldado, resolver el conflicto bajo control DBA y reanudar desde scripts reentrantes. No borrar restricciones ni historial automáticamente. Revisar incremento de secuencias si una ejecución se interrumpió antes de restaurarlo. LAST_NUMBER es un límite de caché, no prueba del siguiente valor efectivo: el postcheck verifica metadatos y límites plausibles; el alineamiento de 01 consume NEXTVAL con escritores detenidos. Certificar comportamiento real exige pruebas Oracle.

Historial con varias auditorías de evidence_key NULL para el mismo pago puede impedir crear el índice único compuesto de Oracle; no se elimina ni se rellena automáticamente. Debe investigarse antes de continuar. Índices especiales/particionados o equivalencias no reconocidas pueden requerir evaluación manual y evitar índices redundantes antes del despliegue.

## G. Estado final

Correcciones y pruebas locales completadas; migración, compilación y comportamiento Oracle pendientes de certificación real. No hubo cambios en Spring Security, nuevas tablas, dependencias ni operaciones sobre Oracle productivo. El archivo preexistente `back/db/oracle.zip` se preservó. La aplicación abierta conserva su versión anterior y sus datos H2: adoptar el nuevo JAR requiere un reinicio planificado que perdería los datos en memoria si no se preservan antes.

Referencias primarias: [compilación condicional Oracle](https://docs.oracle.com/en/database/oracle/oracle-database/19/lnpls/conditional-compilation1.html), [PLSQL_CCFLAGS](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/PLSQL_CCFLAGS.html), [gestión de índices](https://docs.oracle.com/en/database/oracle/oracle-database/19/admin/managing-indexes.html).
