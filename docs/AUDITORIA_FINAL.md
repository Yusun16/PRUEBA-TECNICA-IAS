<!-- La correcci?n posterior y resultados vigentes est?n en CORRECCION_INTEGRIDAD_ORACLE.md. -->
# Auditoría y finalización de PagoClaro

Fecha: 19 de octubre de 2026, America/Bogota. Se trabajó sobre el repositorio existente, conservando back/front/docs, Java 21, Spring Boot, JDBC, el motor de decisión y Spring Security. No se agregó infraestructura ni tablas de usuarios.

## A. Diagnóstico inicial

La inspección se completó antes de editar. La línea base tenía 25 pruebas aprobadas, pero faltaban garantías de base de datos y escenarios de error relevantes. El detalle previo está en [AUDITORIA_INICIAL.md](AUDITORIA_INICIAL.md).

| Gravedad | Defecto o riesgo inicial | Resultado |
|---|---|---|
| Alta | H2 permitía UPDATE/DELETE de movimientos. | Rechazo en la base, probado con SQL directo. |
| Alta | Era posible insertar reversos de otro pago, parciales o de otro reverso. | Rechazo de nuevas inserciones en H2 y guardas Oracle preparadas. |
| Alta | Un escritor hijo directo podía omitir el bloqueo del padre. | Guardas para eventos y ledger; pruebas con conexiones/transacciones independientes. |
| Alta | Compensación automática habilitada fuera de demo sin validar captura única. | Deshabilitada por defecto fuera de local; Oracle también exige habilitación explícita. |
| Media | Timeout real de adquisición devolvía 503 y no 409. | Reproducido por nueva prueba, corregido y verificado. |
| Media | Huella dependía del orden del repositorio. | Snapshot canónico; prueba por permutación y vector conservado. |
| Media | Oracle carecía de CHECK y una estrategia completa de migración/recuperación. | Preflight, CHECK para escrituras nuevas, migración reanudable y guía de recuperación. |
| Media | Consulta podía omitir relaciones inválidas con neto aparentemente sano. | Se amplió y se probó ese caso de legado. |
| Media | Al revisar la paridad se encontró que un tipo de movimiento desconocido se sumaba como débito en Java/PLSQL. | El neto ahora suma solo DEBIT y resta REVERSAL; conserva revisión manual para el tipo inválido. |
| Limitación | No había instancia Oracle real. | Permanece explícitamente pendiente; suite Oracle omitida, no presentada como aprobada. |

Se conservaron comportamientos correctos: BigDecimal, SQL parametrizado, transacción única, rollback por fallo de auditoría, tratamiento manual de contradicción, unicidad de auditoría y agregación independiente de eventos/ledger.

## B. Cambios realizados

| Archivos | Propósito |
|---|---|
| `ReconciliationEngine.java` | Ordenar snapshots por ID y exigir permiso de compensación para varios débitos activos. |
| `ReconciliationService.java` | READ COMMITTED explícito; política configurable sin bandera pública en la API. |
| `PaymentRepository.java` | WAIT 5, traducción del timeout de adquisición a conflicto y conversiones exactas de IDs/versiones. |
| `persistence/h2/AccountingGuard.java` y `db/local/schema.sql` | Inmutabilidad, relación de reversos y bloqueo del padre en escrituras directas. CHECK de versión/acción. |
| `application.yml`, `application-local.yml` | Default seguro y excepción explícita para la demo; niveles INFO para loggers que podían volcar detalles SQL/excepciones en DEBUG. |
| `pom.xml` | Acceso a la API Trigger de la dependencia H2 existente; sufijo opcional del artefacto para no reemplazar un JAR en uso. |
| `ReconciliationIntegrationTest.java` | Bases de prueba aisladas, guardas, escritores concurrentes, timeout y fallos reales/inyectados con rollback. |
| `ReconciliationEngineTest.java`, `ProductionPolicyTest.java`, `EvidenceFingerprintTest.java` | Política segura, canonicalización, vector SHA-256 y decisiones conservadoras. |
| `AnomalyQueryTest.java` | Ventana temporal, legado inválido con neto coincidente y falta de evidencia. |
| `contract/reconciliation-cases.json`, `ReconciliationContractCases.java`, `ReconciliationContractTest.java` | Once escenarios comunes, no duplicados manualmente entre los motores. |
| `OracleReconciliationContractTest.java` | Adaptador JDBC Oracle opcional: motivos, netos, versión, huella y repetición de la misma matriz, con rollback de fixtures. |
| `scripts/audit-smoke.ps1` | Arranque aislado, ocho comandos HTTP y prueba de reinicialización H2 sin afectar la aplicación abierta. |
| `front/src/presentation.js` | Explicación del nuevo motivo de revisión manual. No se altera el login ni la autorización existente. |
| README y documentación | Modelo, política, instalación, pruebas, recuperación y límites reales. |

## C. Base de datos

El [modelo](MODELO_DATOS.md) conserva las cuatro tablas y sus relaciones. En H2 se comprobaron inmutabilidad y validación de nuevas inserciones. Los scripts Oracle implementan el mismo objetivo con CHECK, índices únicos y triggers; todavía no están certificados por ejecución.

| Script Oracle | Cambio o finalidad |
|---|---|
| `01_support_migration.sql` | Columna de evidencia si falta, reutilización de índices válidos/equivalentes y secuencias adelantadas sin bajar IDs. Detención ante duplicados incompatibles. |
| `02_detect_anomalies.sql` | Actividad reciente de tres tablas, historial completo y validación adicional de vínculos/importe sin multiplicar netos. |
| `03_reconcile_payment.sql` | Compensación predeterminada en 0, hash UTF-8 explícito, neto solo de tipos conocidos y contrato de OUT/errores. Conserva SAVEPOINT, rollback local y ausencia de COMMIT. |
| `06_verify_procedure.sql` | Política segura y habilitación explícita del caso ficticio BX17; muestra, repetición y vector común. |
| `07_preflight.sql` | PK/FK y referencias únicas obligatorias, rango de IDs/versiones, inventario de objetos y problemas del legado. |
| `08_integrity_constraints.sql` | CHECK ENABLE NOVALIDATE; conserva el legado y exige clave en nuevas auditorías. |
| `09_write_guards.sql` | Bloqueo de PAYMENT en DML de eventos/ledger; trigger compuesto para validar reversos sin leer una tabla mutante. |
| `10_verify_guards.sql` | Pruebas de inmutabilidad, reversos inválidos, unicidad y clave de auditoría. |
| `11_verify_rollback.sql` | Fallo de auditoría, restauración de estado/ledger y conservación del trabajo anterior al SAVEPOINT. |
| `12_lock_session_a.sql`, `13_compete_session_b.sql` | Espera y timeout entre sesiones, con rollback del ensayo. |
| `14_postcheck.sql` | Objetos válidos y guardas/CHECK habilitados. |
| `15_backup_metadata.sql` | Extracción de DDL previo sin exportar filas; respaldo protegido fuera de Git. |
| `deploy_existing.sql`, `demo_install.sql` | Separar migración existente de instalación y pruebas de laboratorio. |

Los scripts no eliminan ni reescriben históricos. ENABLE NOVALIDATE no certifica la validez del legado. Las secuencias consumidas no retroceden tras rollback. DDL Oracle tiene commits implícitos, por lo que recuperación y reanudación requieren mantenimiento y respaldo, no un ROLLBACK ficticio.

Seleccionar 5003 para el reverso de BX17 se deriva de conservar el menor ID activo (5002); es determinismo del escenario ficticio, no prueba de cronología ni de captura única. Con la política segura deshabilitada para compensar, ambos débitos se conservan y se registra revisión manual.

No se añadieron índices temporales/rangos sin mediciones. La consulta adicional puede tener costo; hacen falta estadísticas y planes reales para decidir optimizaciones. No se afirma una mejora de rendimiento medida.

## D. Pruebas y evidencia

Comandos ejecutados con Java portátil y Maven Wrapper, cachés dentro del workspace:

```powershell
# Desde back, con JAVA_HOME/MAVEN_USER_HOME configurados al JDK/caché del proyecto:
.\mvnw.cmd '-Dmaven.repo.local=.m2' -B test
.\mvnw.cmd '-Dmaven.repo.local=.m2' '-Dartifact.suffix=-audit' -B verify

# Desde la raíz:
powershell -NoProfile -ExecutionPolicy Bypass -File .\back\scripts\audit-smoke.ps1
npm.cmd --prefix front test
npm.cmd --prefix front run test:e2e
```

| Ejecución | Resultado observado |
|---|---|
| Línea base | 25 aprobadas; 0 fallos/errores. |
| Primer grupo ampliado | Una prueba falló: timeout devolvía 503 frente a 409. |
| Primer grupo corregido | 32 aprobadas; 0 fallos/errores. |
| Verificación final Java/H2 | 49 registradas: **48 aprobadas, 1 omitida**, 0 fallos/errores; BUILD SUCCESS. |
| OracleReconciliationContractTest | Suite omitida por falta de ORACLE_TEST_URL; contiene once escenarios cuando se habilita. |
| Cliente/proxy frontend | 17 aprobadas. |
| Navegador Chrome | 14 aprobadas. |
| JAR auditado por HTTP | Ocho comandos aprobados, reinicio H2 comprobado y política segura comprobada. |

Desglose final Java: AnomalyQueryTest 4; ProductionPolicyTest 1; ReconciliationEngineTest 5; ReconciliationIntegrationTest 25; EvidenceFingerprintTest 2; ReconciliationContractTest 11; suite Oracle omitida 1. Total: 48 casos ejecutados correctamente y una omisión registrada por el runner.

Se verificó rollback por fallo de auditoría y por una colisión PK real en el segundo reverso, después de insertar el primero. Ningún movimiento parcial permanece y el reintento posterior funciona. También se comprobaron dos conciliaciones simultáneas, un evento directo esperando al padre, un reverso competidor rechazado y HTTP 409 después de cinco segundos de espera.

La evidencia HTTP confirma AX91 sin débito nuevo; BX17 con un reverso y repetición sin efectos; CQ22 manual sin auditoría duplicada; DK44 sano. Tras reiniciar solo la instancia de pruebas, BX17 volvió a neto 178000 y quedó en revisión con la política segura. Archivo: [evidencia/auditoria-http.json](evidencia/auditoria-http.json), con hash del JAR y sin cabeceras de autorización.

Reportes regenerables en `back/target/surefire-reports`; logs locales excluidos de Git: `audit-baseline.log`, `audit-integrity.log`, `audit-integrity-fixed.log`, `audit-contract.log`, `audit-final-verify.log`. El [resumen estructurado](evidencia/auditoria-pruebas.json) conserva los conteos para revisión sin depender de esos logs locales.

## E. Riesgos pendientes

- Compilación y ejecución Oracle de DDL, triggers y PL/SQL, incluyendo códigos reales de timeout y comportamiento del driver.
- Comparación efectiva de SHA-256 entre Java/Oracle, NLS y pruebas simultáneas Java/PLSQL. El vector Java y la matriz están probados; la paridad Oracle está preparada, no demostrada.
- Privilegios del propietario/DBA permiten desactivar protecciones: el runtime no debe tenerlos. Las guardas no convierten en confiable a un escritor con control DDL.
- Operaciones que tomen varios pagos pueden sufrir deadlocks si no ordenan los padres. Una escritura posterior puede incorporar evidencia nueva; debe evaluarse nuevamente.
- La autorización de captura única, moneda y finalización de APPROVED requiere evidencia externa que el modelo no contiene. Se bloquea por defecto la compensación de duplicados fuera de demo.
- Las pruebas cubren inserciones individuales del servicio; no se certificaron cargas masivas con referencias adelantadas en un mismo INSERT ni variantes de transacción de terceros.
- Oracle requiere READ COMMITTED. Un llamador con snapshot SERIALIZABLE antiguo queda fuera del contrato; el procedimiento no cambia silenciosamente la transacción del llamador.
- No hay mediciones de rendimiento Oracle ni prueba con volumen productivo.

## F. Estado final

**Terminado y comprobado localmente:** backend, guardas H2, decisiones seguras, idempotencia, concurrencia, rollback, login, regresión del frontend y arranque/reinicio local aislado.

**Preparado y parcialmente validado:** esquema/migración Oracle, PL/SQL, guardas, instalación, recuperación y ensayos automatizados/manuales. Se revisaron fuentes y documentación; falta ejecutarlos en Oracle real.

La aplicación del usuario en 8080/5173 no fue reiniciada ni sus datos H2 inspeccionados/modificados. Se generó y probó `support-reconciliation-1.0.0-audit.jar` en otro puerto y se detuvo al terminar. La instancia ya abierta conserva su versión previa; activar el código auditado requiere una recompilación/reinicio consciente de que H2 en memoria pierde su estado al detenerse. El artefacto auditado queda disponible para revisión inmediata.

## G. Próximos pasos priorizados

1. Ejecutar `demo_install.sql` en Oracle 19c+ dedicado y guardar versión, errores, mensajes y postcheck.
2. Habilitar OracleReconciliationContractTest con credenciales externas a Git. Verificar los once escenarios, huellas e idempotencia.
3. Ejecutar las pruebas de sesiones, escritores hijos y Java/PLSQL; repetir con configuración NLS alternativa y validar rollback del llamador.
4. Revisar contrato de proveedor/captura/moneda y mantener compensación deshabilitada en entornos reales hasta autorizarla.
5. Obtener backup e inventario del esquema real, evaluar legado, revisar planes con datos representativos y aplicar `deploy_existing.sql` solo en mantenimiento.
6. Para adoptar localmente: conservar la información de la sesión abierta que se necesite, detenerla y ejecutar `front/scripts/start-local.ps1 -Build`. No se hizo automáticamente para evitar perder datos en memoria.

Guía reproducible: [ORACLE_INSTALACION_Y_RECUPERACION.md](ORACLE_INSTALACION_Y_RECUPERACION.md). Las instrucciones y scripts no equivalen a una certificación de Oracle ni a una autorización para compensar fondos reales.
