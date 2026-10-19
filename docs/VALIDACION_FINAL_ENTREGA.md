# PagoClaro — validación integral y preparación de entrega

**Actualización posterior:** la formalización Git autorizada y la reproducción independiente se documentan en [PREPARACION_GIT_ENTREGA.md](PREPARACION_GIT_ENTREGA.md). Las referencias de este informe al HEAD c8fbb1a, ausencia de commits y clon pendiente describen el estado histórico de esta validación, anterior a esa autorización.

Fecha: 19 de octubre de 2026, America/Bogota. Alcance: versión completa del workspace, incluidos cambios locales previos. No se crearon commits ni se hizo push. No se reinició la aplicación del usuario ni se ejecutaron migraciones Oracle.

## A. Estado general

**Veredicto: LISTO CON LIMITACIONES DOCUMENTADAS para evaluación local de esta versión completa.** Pasaron 53 pruebas Java/H2, 17 de cliente/proxy y 14 de navegador con API controlada. Una suite Oracle está omitida, no aprobada. También se ejecutaron ocho comandos HTTP sobre Java real, reinicialización H2 aislada y ocho conciliaciones desde Chrome a través del proxy Node contra Java real.

La entrega por Git requiere una acción posterior del propietario: revisar e incorporar los cambios y archivos nuevos. **Un clon del HEAD actual c8fbb1a no contiene la versión validada.** Este informe no certifica que ese clon antiguo reproduzca estos resultados. No se hicieron commits porque la instrucción lo prohíbe sin autorización expresa. Oracle 19c+ permanece pendiente; el enunciado permite documentar infraestructura no disponible.

Se preservan Java 21, Spring JDBC, HTTP Basic, la interfaz existente y las cuatro tablas. No se agregaron servicios externos, Docker, JWT, JPA ni funcionalidades financieras nuevas.

## B. Diagnóstico y riesgos

| Área | Estado | Evidencia | Gravedad/riesgo |
|---|---|---|---|
| Arquitectura | Conforme por inspección y pruebas | Controller delgado; service transaccional; motor puro; repository JDBC | Bajo |
| Seguridad HTTP | Se reprodujo 401 sin cuerpo ni Content-Type; corregido | Inspección HTTP del JAR previo y pruebas 401/403 actualizadas | Medio, cerrado localmente |
| Contraseña demo | Oracle exigía una variable, pero aceptaba el literal demo; corregido | SupportCredentialPolicy y dos pruebas | Alto, cerrado para contraseña vacía/demo fuera de local |
| Política financiera | Conservadora; cambios anteriores preservados | 11 escenarios compartidos y 26 pruebas de integración | Bajo local; captura externa no implementada |
| Transacciones y concurrencia | Pruebas H2 aprobadas | WAIT 5, READ COMMITTED, rollback, escritores directos y reintentos | Alto residual en Oracle hasta ejecución real |
| Integración web | Verificada sin mocks adicionales | entrega-integracion-real.json | Bajo en Chrome instalado; no certifica todos los navegadores |
| Compilación limpia | Aislada sin tocar target activo | clean verify en back/.tools/delivery-build | Medio resuelto para esta ejecución |
| Documentación N3 | Instrucciones antiguas inseguras corregidas | OPERACION_N3.md y DECISIONES.md | Alto: antes incluían APPROVED sin ledger como caso seguro |
| Oracle | Revisión estática y contratos incluidos; ejecución pendiente | SQL 00–18 y suite condicional omitida | Alto; no declarar compatibilidad certificada |
| Git | Historial visible, modificaciones y archivos nuevos pendientes | entrega-git.json | Medio: el HEAD aún no es la entrega completa |
| Archivo desconocido | back/db/oracle.zip no rastreado, preservado | Estado Git | Bajo: no incluir automáticamente como entrega vigente |
| Límite de dos horas | No certificable con estas ampliaciones | Historial y alcance extendido por instrucciones posteriores | Medio: no presentar las iteraciones como trabajo acreditado de dos horas |

No se encontraron fallos locales en las ejecuciones finales. Esto no equivale a una auditoría de seguridad completa, prueba de carga ni certificación productiva.

## C. Cambios realizados en esta validación

| Archivo | Cambio justificado |
|---|---|
| back/pom.xml | Propiedad build.output-directory, por defecto target, para ejecutar clean real sobre una salida aislada. |
| back/src/main/java/com/pagoclaro/support/api/SecurityConfiguration.java | Respuestas ProblemDetail para 401/403, no-store y conservación de Basic, rol y STATELESS. |
| back/src/main/java/com/pagoclaro/support/api/SupportCredentialPolicy.java | Rechazo de contraseña vacía o demo fuera de local; no cambia el sistema de autenticación. |
| back/src/test/java/com/pagoclaro/support/SupportCredentialPolicyTest.java | Dos pruebas para perfiles y credenciales de laboratorio. |
| back/src/test/java/com/pagoclaro/support/ReconciliationIntegrationTest.java | Aserciones de contrato HTTP para 401/403 en pruebas existentes. |
| back/scripts/audit-smoke.ps1 | Argumento opcional JarPath para comprobar el artefacto limpio aislado; conserva comportamiento habitual. |
| front/scripts/verify-integration.mjs | Prueba reproducible Chrome → Node → Java con procesos propios, sin interceptar ni simular la API. |
| README.md, back/README.md, front/README.md | Ruta rápida, versiones y pruebas actuales, separación de JAR activo/validado y comandos reproducibles. |
| docs/OPERACION_N3.md, docs/DECISIONES.md | Retiran instrucciones obsoletas de creación de débito y autorización productiva mediante flag. |
| docs/FRONTEND.md, docs/USO_IA.md | Distinguen evidencia histórica, automatización con mocks e integración real actual. |
| docs/VALIDACION_FINAL_ENTREGA.md y docs/evidencia/entrega-* | Informe, evidencias, manifiesto de fuentes y propuesta de entrega. |

Los cambios financieros y Oracle que ya estaban sin commit al iniciar se conservaron y se validaron como parte del conjunto; están explicados en CORRECCION_INTEGRIDAD_ORACLE.md. No se reconstruyó ni refactorizó globalmente la aplicación.

## D. Pruebas ejecutadas y evidencia

### Entorno y compilación

JDK ejecutado: Temurin 21.0.12.1+1. Maven Wrapper: 3.9.11. Node: 26.5.0. Chrome instalado detectado por Playwright. Dependencias comprobadas dentro del JAR: Spring Boot 3.5.16, Spring Security Core 6.5.11, H2 2.3.232 y ojdbc11 23.7.0.25.01. La presencia del driver Oracle no acredita una conexión ni ejecución Oracle.

Desde la raíz se configuraron JAVA_HOME al JDK portátil existente y MAVEN_USER_HOME a back/.tools/maven-home. Desde back/ se ejecutó:

```powershell
$env:DEBUG = 'false'
$deliveryBuild = Join-Path (Get-Location) '.tools\delivery-build'
.\mvnw.cmd '-Dmaven.repo.local=.m2' '-Dartifact.suffix=-audit' "-Dbuild.output-directory=$deliveryBuild" -B clean verify
```

Antes de clean se comprobó que la ruta absoluta de salida estaba dentro del workspace. No se limpió back/target, que contiene el JAR de la aplicación abierta. La propiedad adicional es la única diferencia necesaria frente al comando solicitado.

Resultado final: BUILD SUCCESS, 31.094 segundos, finalizado a las 15:33:09 -05:00. La primera invocación se interrumpió por ErrorActionPreference=Stop de PowerShell ante una advertencia stderr de Mockito sobre autoanexado del agente. No fue un fallo de aserción de PagoClaro: se repitió con manejo normal de stderr y terminó correctamente. La advertencia sigue siendo una limitación de herramienta para JDK futuros; se usó Java 21.

| Suite Java | Ejecutadas/aprobadas | Fallos/errores | Omitidas |
|---|---:|---:|---:|
| AnomalyQueryTest | 4 | 0 | 0 |
| ProductionPolicyTest | 1 | 0 | 0 |
| ReconciliationEngineTest | 5 | 0 | 0 |
| ReconciliationIntegrationTest | 26 | 0 | 0 |
| EvidenceFingerprintTest | 2 | 0 | 0 |
| ReconciliationContractTest | 11 | 0 | 0 |
| SimulationPolicyTest | 2 | 0 | 0 |
| SupportCredentialPolicyTest | 2 | 0 | 0 |
| OracleReconciliationContractTest | 0 | 0 | 1 suite |
| **Total registrado: 54** | **53** | **0** | **1** |

Surefire representa la fábrica de pruebas Oracle deshabilitada como una omisión. No se afirma que se hayan ejecutado sus once escenarios.

### Frontend y procesos reales

Desde front/ se ejecutaron los comandos existentes:

```powershell
npm test
npm run test:e2e
node scripts/verify-integration.mjs
```

`npm test`: 17 aprobadas, cero fallos. `npm run test:e2e`: 14 aprobadas, cero fallos, 12.7 segundos. Esta suite abre su servidor en 5174 y controla las respuestas de la API.

La prueba nueva utiliza Java real en 18084 y Node real en 5175. Comprueba acceso directo a dashboard sin login, credenciales incorrectas, login correcto, no-store, ausencia de cookie de sesión, cuatro referencias y sus reintentos, mensajes renderizados, ausencia de credenciales en localStorage/sessionStorage, logout y recarga. También verifica ProblemDetail real para 400/401/404, ausencia de errores JavaScript y que JSON, query y cabecera no habiliten compensación al llamar directamente al backend seguro.

Desde la raíz se ejecutó además:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\back\scripts\audit-smoke.ps1 -JarPath 'C:\PRUEBA TECNICA IAS\back\.tools\delivery-build\support-reconciliation-1.0.0-audit.jar'
```

El smoke realizó ocho comandos HTTP en 18082: AX91, BX17 demo y repetición, CQ22 y repetición, DK44; después detuvo únicamente su Java, inició otro H2 limpio con compensación deshabilitada y comprobó BX17 manual y reintento. El neto inicial volvió a 178000, demostrando reinicialización aislada.

Las pruebas HTTP no se suman artificialmente a la cantidad de tests JUnit. Son ejecuciones adicionales con evidencia independiente.

### Archivos de evidencia

- [entrega-pruebas.json](evidencia/entrega-pruebas.json): conteos derivados de Surefire, versiones empaquetadas, SHA-256, manifiesto de fuentes y procesos conservados.
- [entrega-maven-resumen.txt](evidencia/entrega-maven-resumen.txt): resultados por suite y BUILD SUCCESS extraídos de la ejecución final.
- [entrega-http.json](evidencia/entrega-http.json): ocho respuestas reales y reinicialización H2.
- [entrega-integracion-real.json](evidencia/entrega-integracion-real.json): login, errores y ocho conciliaciones Chrome/Node/Java sin mocks.
- [entrega-git.json](evidencia/entrega-git.json): estado Git, historial, exclusiones y archivo desconocido preservado.

Logs completos y Surefire XML están en back/.tools/delivery-maven.log y back/.tools/delivery-build/surefire-reports; frontend en front/.tools/delivery-*.log. Son archivos locales ignorados, no publicaciones de logs ni secretos. Las evidencias compartibles no contienen Authorization ni contraseñas operativas.

## E. Integración, reglas y seguridad

El recorrido inspeccionado es HTTP → Security → Controller → Service → Repository → Database → Response. El controller recibe la referencia; el motor concentra decisiones; el servicio mantiene una transacción READ COMMITTED con timeout 15 segundos; el repositorio usa SQL parametrizado y bloqueo WAIT 5 antes de leer evidencia mutable.

| Caso | Resultado comprobado | Persistencia comprobada |
|---|---|---|
| AX91 | RESOLVED → CONFIRMED; repetición ALREADY_CONSISTENT | Débito existente conservado, cero movimientos nuevos, una versión y auditoría. |
| BX17 seguro | MANUAL_REVIEW / DUPLICATE_DEBITS_REQUIRE_REVIEW | Neto 178000, cero movimientos; segunda auditoría equivalente no creada. |
| BX17 demo | RESOLVED; repetición ALREADY_CONSISTENT | Débitos 5002/5003 conservados, reverso de 5003 por 89000, neto 89000. |
| CQ22 | MANUAL_REVIEW por contradicción | Estado/ledger intactos y auditoría idempotente. |
| DK44 | ALREADY_CONSISTENT | Sin movimientos, cambio de versión ni nueva auditoría. |
| APPROVED sin ledger | MANUAL_REVIEW / APPROVED_WITHOUT_CAPTURE_EVIDENCE | Ningún débito creado, versión intacta y auditoría idempotente. |

HTTP 200/400/401/403/404/409/503/500 tienen cobertura Spring/MockMvc; 400/401/404 se comprobaron además sobre socket HTTP real. 409 se reproduce con bloqueo real H2; 503 con fallo inyectado de persistencia y 500 con un identificador numérico inválido, sin filtrar detalles. No se afirma haber provocado una caída productiva real para esas pruebas.

Basic, SUPPORT y STATELESS se conservan. El proxy restringe archivos, rutas, métodos, origen y cuerpos; no expone el proyecto completo. El frontend presenta el resultado de Java, no calcula correcciones. Bloquea doble envío y no reintenta automáticamente. No existe control público para activar compensaciones.

Los importes usan BigDecimal en Java; el cliente conserva tokens decimales. El resultado expone cantidades de movimientos/auditoría, no un listado completo de filas contables ni un historial global. La actividad de pantalla es solo de sesión; SUPPORT_AUDIT es persistencia independiente.

## F. Base de datos, artefacto y Oracle

H2: esquema limpio, importes positivos, tipos/estados válidos, relaciones y unicidad; AccountingGuard protege inmutabilidad, importe completo y coordinación de escritores. Las pruebas verifican rechazo de reversos parciales, cruzados, dobles y de otro reverso, así como atomicidad de inserciones múltiples y rollback ante fallo de auditoría o segunda compensación.

El artefacto probado es back/.tools/delivery-build/support-reconciliation-1.0.0-audit.jar. Se compararon **26 clases/recursos compilados con sus entradas BOOT-INF/classes y todos coinciden**. El SHA-256 es común a las evidencias de build, smoke y navegador; el manifiesto identifica las fuentes evaluadas. No se entrega como vigente el JAR antiguo del proceso del usuario. Para entrega Git se compila desde fuentes; los binarios y .tools permanecen ignorados.

El procedimiento PL/SQL es una interfaz alternativa a Java/JDBC. Conserva seis argumentos, SAVEPOINT, ROLLBACK TO y COMMIT del llamador. Por revisión estática comparte reglas y serialización incremental SHA-256 con Java. La matriz de once escenarios cubre muestra, seguro/demo, falta de evidencia, falta de débito, débito parcial, rechazo e historial revertido. El caso de débito parcial es un snapshot legado en el motor Java; Oracle prueba que una inserción nueva inválida sea rechazada. Esa diferencia de preparación de fixtures está explícita y no constituye un permiso para desactivar guardas.

Oracle no fue ejecutado. Para certificar: esquema de laboratorio vacío, demo_install.sql, pruebas SQL 06/10/11/18, postcheck 14 y dos sesiones 12/13; para el contrato JDBC de demo recompilar únicamente ese laboratorio con pagoclaro_demo:TRUE, ejecutar la suite y volver a compilar FALSE. Repetir bajo NLS alternativo y comprobar driver, bloqueos y rollback. En esquema existente usar deploy_existing.sql con respaldo, mantenimiento y preflight, nunca los scripts de recreación/seed.

Las limitaciones de compilación, índices especiales, restricciones equivalentes, secuencias y legado ENABLE NOVALIDATE siguen descritas en [la guía Oracle](ORACLE_INSTALACION_Y_RECUPERACION.md) y [la corrección de integridad](CORRECCION_INTEGRIDAD_ORACLE.md). No se afirma integridad histórica ni reversibilidad de DDL mediante ROLLBACK.

## G. Git, documentación y reproducción

Historial inspeccionado: c8fbb1a documentación de auditoría; 4a88344 integridad; dc617ca login; ffda1a1 retirada de ejemplos públicos; 8993e92 frontend; 66cf4b8 organización. Rama actual master. Se conservaron modificaciones previas y back/db/oracle.zip. No hubo reset, eliminación de archivos desconocidos, commits ni push.

No se detectaron binarios, node_modules, logs ni cachés rastreados en la revisión de nombres de archivos. .gitignore excluye target, .tools, .m2, node_modules, test-results, playwright-report, .env y logs. La búsqueda acotada de claves privadas e identificadores de acceso conocidos no sustituye un escáner completo de secretos. Los datos de ejemplo y la contraseña demo documentada son exclusivamente ficticios; no se publican en assets web.

**Propuesta de commits, no ejecutada:**

1. `fix: enforce conservative reconciliation and Oracle integrity contracts`: correcciones financieras/Oracle previas, fixtures y pruebas relacionadas, incluyendo los nuevos SQL 16–18.
2. `fix: standardize security errors and isolate delivery checks`: seguridad, política de contraseña, pruebas, salida Maven, smoke y prueba de integración real.
3. `docs: publish final delivery validation and evidence`: README, guías vigentes, trazabilidad e informes/evidencias.

Revisar explícitamente cada ruta antes de agregarla. No incluir back/db/oracle.zip automáticamente. Una vez que el propietario incorpore estos cambios, comprobar el commit final y reproducir desde un clon limpio. Esa clonación futura no fue ejecutada ni se afirma validada aquí.

Ruta rápida para otro evaluador: obtener la versión completa; configurar JDK 21; desde back ejecutar mvnw.cmd clean verify; desde la raíz iniciar front/scripts/start-local.ps1 con puertos libres; abrir /login, usar credenciales locales documentadas y conciliar cuatro referencias. Node no necesita dependencias externas para servir la consola. Para pruebas frontend ejecutar npm ci, npm test y npm run test:e2e; se necesita Chrome/Edge o Chromium configurado. El repositorio incluye package-lock.json. Se comprobó la instalación de dependencias existente, no una descarga desde cero en otro equipo.

Reiniciar H2 descarta el estado anterior y restaura la muestra. No usar la ruta rápida para interrumpir una sesión ajena: los scripts rechazan puertos ocupados. Los procesos originales 8080/PID 12256 y 5173/PID 14732 se mantuvieron; los puertos de QA quedaron cerrados al terminar.

## H. Matriz de requisitos del enunciado

Fuente: PDF original presente en docs/Prueba_Tecnica_Desarrollador_Soporte_Java_Candidato.pdf y su extracción disponible Prueba_Tecnica_texto.txt. Se revisaron sus cinco páginas de contenido extraído; no se inventaron requisitos cuando el material reconoce vacíos de evidencia.

| Requisito | Estado | Evidencia concreta |
|---|---|---|
| Java y Spring Boot | Cumple localmente | pom.xml, JDK y librerías del JAR, clean verify. |
| Operación por referencia usando tres fuentes | Cumple | ReconciliationController, Service, Repository; HTTP y navegador reales. |
| Solo correcciones necesarias y ledger inmutable | Cumple H2; Oracle pendiente | Engine, AccountingGuard, SQL 04/09 y pruebas de integridad. |
| Evidencia contradictoria o insuficiente → revisión | Cumple | CQ22, sin eventos, APPROVED vacío/revertido, contrato y pruebas. |
| Reintentos sin efectos ni auditorías equivalentes | Cumple H2 | Repeticiones con conteos, SHA-256, UNIQUE, HTTP real. |
| Solicitudes cercanas conservan consistencia | Cumple H2; Oracle pendiente | Pruebas concurrentes, bloqueo, escritor directo y timeout. |
| Auditoría/logs útiles sin secretos | Cumple alcance implementado | SUPPORT_AUDIT, logging afterCommit, errores sanitizados; no atribución persistida a operador. |
| Consulta SQL últimas 24 horas | Entregada y probada H2 | 02_detect_anomalies.sql y cuatro pruebas de consulta; actividad por tres tablas, neto con historial completo. |
| Procedimiento PL/SQL atómico invocable | Entregado; ejecución pendiente | 03, aserciones 06/11/18, guía y suite condicional. |
| Índices/restricciones separados y explicados | Cumple entrega documental | SQL 01/07/08/09/14/16/17 y guías. |
| Pruebas principales y al menos dos bordes | Cumple | 53 aprobadas; errores, reversos inválidos, rollback y concurrencia. |
| Estrategia reproducible si Oracle no disponible | Cumple documentación | Suite ORACLE_TEST_URL y secuencia de laboratorio; no se cuenta como ejecutada. |
| Diagnóstico: hechos, hipótesis y validaciones | Cumple | DECISIONES.md separa timeout/reintento observado de hipótesis de duplicación. |
| Decisiones, alternativas, trade-offs y riesgos | Cumple | ADRs de DECISIONES.md y correcciones posteriores explícitas. |
| Nota operativa breve | Cumple actualizada | OPERACION_N3.md, criterios de detención y verificaciones posteriores. |
| Git con historial visible | Historial presente; entrega actual pendiente de incorporar | Log y entrega-git.json; no commits automáticos. |
| Ejecución/documentación revisable | Cumple workspace; clon final no comprobado | README rápido, scripts, pom y lockfile. |
| Uso de IA trazable | Cumple | USO_IA.md: contribuciones, validación, propuestas corregidas y límites. |
| Máximo dos horas | No acreditado para esta versión ampliada | Varias rondas y frontend posterior; no se reescribió historia para simular cumplimiento. |
| Frontend | Extra solicitado por el usuario, no requisito del PDF | Login/dashboard existentes, pruebas controladas y reales; no se rediseñó. |

La ruta rápida permite evaluar primero el núcleo exigido sin estudiar toda la extensión. No se presenta la cantidad de scripts o pantallas como sustituto de los requisitos funcionales.

## I. Limitaciones y fuera de alcance

- Sin instancia Oracle: compilación PL/SQL, semántica de locks/driver, NLS, migración real, recuperación y concurrencia Oracle no certificadas.
- Sin integración de captura externa: APPROVED no autoriza crear débito y dos aprobaciones no prueban una captura única. Compensación productiva bloqueada.
- El modelo no contiene moneda ni todas las relaciones externas necesarias para un sistema financiero productivo. La simulación 5003 no valida negocio real.
- Sin auditoría inmutable del usuario operador, IAM, JWT, recuperación de contraseña, TLS desplegado ni permisos DBA efectivamente verificados. Son aspectos productivos fuera de esta prueba.
- Sin prueba de carga, auditoría formal WCAG, todos los navegadores ni entorno Linux/macOS ejecutado. La comprobación fue Windows/Chrome.
- La nota de dos horas no se acredita para las ampliaciones. La comparación binaria demuestra correspondencia con este build local, no reproducibilidad byte a byte de builds futuros.
- El workspace validado todavía no está incorporado al HEAD. La formalización Git y el clon limpio requieren la revisión y acción posterior del propietario.

## J. Veredicto final

**LISTO CON LIMITACIONES DOCUMENTADAS.** El código completo del workspace compila y supera las verificaciones locales ejecutadas, mantiene integridad/idempotencia y funciona de navegador a base de datos. No queda un fallo local reproducido sin corregir dentro del alcance revisado.

La preparación técnica local está completada. Antes de entregar una URL Git como versión final, deben incorporarse y revisarse los archivos nuevos y modificaciones; el HEAD actual no es la entrega validada. La certificación Oracle requiere el laboratorio descrito y no se declara completada. Se preservaron los datos de la aplicación abierta y no se hicieron acciones remotas ni commits sin autorización.
