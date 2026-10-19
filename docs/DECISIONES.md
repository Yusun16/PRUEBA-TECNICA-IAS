# Diagnóstico y decisiones técnicas

## Hechos, hipótesis y comprobaciones

El extracto muestra timeout del proveedor, un reintento programado, recepción de EVT-B2 APPROVED, creación del débito 5003 y estado ya CONFIRMED durante el reintento. La verificación posterior informa netDebit=178000 y expected=89000. La muestra de BX17 contiene dos APPROVED y dos DEBIT.

Hipótesis: el flujo de confirmación y reintento no serializaba ni deduplicaba correctamente la contabilización. El timeout puede significar respuesta perdida después de una aprobación remota, no rechazo. El log no permite demostrar quién creó cada débito ni afirmar si dos eventos representan una notificación repetida o intentos distintos.

Antes de producción: revisar trazas por paymentRef, request/attempt y provider_event_id; contrato de estados finales del proveedor; importe y moneda; orden real de commits; listeners, jobs y operadores que escriben; restricciones/índices; reversos y devoluciones ya ejecutados. Confirmar que la evidencia consultada está completa y que todos los escritores pueden seguir el bloqueo común.

Corrección: detener reintentos inseguros, clasificar evidencia y contabilidad, insertar reversos vinculados únicamente para duplicados completos verificables, actualizar estado y dejar auditoría en una misma transacción. No se llama al proveedor ni se reejecuta un cobro.

## ADR-001 — JDBC y motor de decisión puro

Contexto: tiempo acotado y operaciones SQL específicas de soporte. Alternativas: JPA con locking, JDBC o delegar toda la lógica a PL/SQL. Se eligió JDBC para hacer visibles bloqueo y escrituras, con motor Java puro para concentrar reglas. Spring administra la transacción y los parámetros SQL.

Trade-off: más SQL explícito y mapeo manual; menos configuración y ausencia de comportamiento implícito de flushing. PL/SQL se entrega como alternativa operativa, lo que introduce duplicación de políticas. Se mantiene una matriz común y una huella idéntica; la paridad real en Oracle debe validarse antes de producción.

Verificación: integración con base real H2, pruebas del motor y SQL de detección ejecutado desde el archivo de entrega.

## ADR-002 — Serializar por pago

Alternativas: bloqueo optimista con reintentos, lock distribuido, SERIALIZABLE o bloqueo pesimista del padre. Se eligió SELECT FOR UPDATE sobre PAYMENT antes de leer evidencia y ledger. No requiere infraestructura adicional, serializa correcciones del mismo pago y permite paralelismo entre pagos distintos.

Riesgo: los insertos en tablas hijas no quedan protegidos si otro proceso no bloquea primero el padre. El contrato de escritura es obligatorio. Orden de bloqueo: PAYMENT, luego sus datos. No ejecutar lotes que tomen pagos en órdenes distintos sin definir una estrategia.

Trade-off: una solicitud puede esperar o agotar timeout. La API informa conflicto o indisponibilidad sin filtrar detalles; el cliente puede reintentar porque la operación es idempotente. En producción revisar semántica real de cancelación de consultas del driver Oracle.

Verificación: dos hilos llaman al servicio Spring real, por lo tanto dos transacciones/conexiones; se comprueba exactamente un RESOLVED, un ALREADY_CONSISTENT, un reverso, una auditoría y una actualización de versión.

## ADR-003 — Compensación conservadora y determinista

Regla vigente tras las revisiones: APPROVED sin DECLINED y con un débito completo activo permite corregir estado. APPROVED sin ledger no demuestra captura y exige revisión manual. Varios débitos exigen revisión manual por defecto. Solo en simulación ficticia local o compilación Oracle de laboratorio se conserva el débito activo de menor ID y se reversan sobrantes; el ID aporta determinismo, no prueba de precedencia ni captura. Los reversos existentes se validan antes de decidir.

Alternativas rechazadas: borrar duplicados, sobrescribir importes, crear un ajuste por la diferencia sin relación con un débito concreto o usar el último evento recibido como verdad. Esas opciones eliminan evidencia o inventan precedencia/autorización no suministrada.

DECLINED con ledger, APPROVED completamente revertido o importes parciales requieren revisión; podrían representar devolución, captura parcial o información faltante. No se recontabiliza un pago completamente revertido.

Riesgo: aun dos débitos completos podrían representar operaciones distintas fuera del modelo. Validar el supuesto de un pago/una captura/una moneda. El modelo no incluye moneda ni importe de eventos del proveedor.

Verificación: casos de muestra, débitos ausentes, varios activos con uno ya revertido, reversos parciales/duplicados/cruzados e importes inválidos.

## ADR-004 — Idempotencia basada en evidencia persistida

Alternativas: clave HTTP obligatoria, deduplicar solo por referencia, o persistir una huella de evidencia. Se eligió estado contable efectivo para efectos financieros y SHA-256 incremental para auditoría. Una clave HTTP diferente no puede autorizar un segundo débito. Una referencia sola impediría registrar evidencia nueva.

Formato: h0=SHA256(`policy-v1|motivo`); después h=SHA256(`h|P:id:status:amount:version`), seguido de cada `h|E:id:status` y `h|L:id:type:amount:relatedId` ordenados por ID. Importe fijo con dos decimales, sin agrupadores; relación ausente se expresa `null`. Solo caracteres ASCII canónicos, por lo que coincide entre UTF-8 Java y charsets Oracle compatibles con ASCII. Longitud de cada paso acotada; evita límites de VARCHAR2 al concatenar historiales grandes.

No se incluyen fechas ni provider_event_id porque el ID interno único identifica la fila y los eventos se asumen inmutables. Una corrección del ID externo manteniendo la misma fila/estado no es evidencia nueva para esta política. Si cambian estas reglas, versionar `policy-v1`.

Migración: evidence_key nullable para auditorías históricas y restricción única por pago y clave; los dos nuevos caminos siempre la completan. Esto no deduplica retroactivamente auditorías previas al despliegue.

Verificación: repetir correcciones y revisiones mantiene conteos; un evento nuevo permite otra auditoría. Probar interoperabilidad Java/PLSQL según VALIDACION_ORACLE.md.

## ADR-005 — Demo H2 y scripts Oracle explícitos

H2 local permite ejecutar sin infraestructura; Oracle es el destino de scripts y perfil alternativo. No se agrega Docker, colas ni una segunda base obligatoria. La aplicación no aplica DDL automáticamente en Oracle.

Riesgo: H2 Oracle mode no implementa PL/SQL y difiere en bloqueos, NLS, timestamps y errores. Se entrega un instalador de laboratorio y aserciones Oracle que fallan ante errores. Esa validación permanece pendiente sin un servidor Oracle disponible.

## ADR-006 — Acceso mínimo de demostración

La API permite solo comandos autenticados con rol SUPPORT y escucha en loopback por defecto. Basic y credenciales locales simplifican la evaluación. No pretende reemplazar IAM empresarial. Antes de exponerla: TLS, credenciales rotadas, red interna y permisos de base mínimos. El trigger de inmutabilidad protege el ledger en Oracle; cuenta runtime sin privilegios DDL.

## ADR-007 — Cierre de auditoría: guardas y autorización de compensación

La revisión posterior encontró que H2 solo protegía la inmutabilidad mediante código y que las escrituras hijas directas podían omitir el bloqueo. Se añadieron guardas locales y scripts Oracle equivalentes. H2 requiere la interfaz Trigger al compilar; se reutilizó la dependencia H2 existente con alcance de compilación opcional, sin incorporar un servidor nuevo. El servicio mantiene READ COMMITTED y WAIT 5; el timeout de adquisición se traduce a conflicto sin absorber otros errores de persistencia.

Se conserva la decisión determinista de menor ID solo para simulación. Java rechaza activar compensación fuera de local y la compilación Oracle segura rechaza el parámetro 1. El mecanismo externo de autorización productiva no está implementado. No se expone una bandera de autorización en la API pública.

Se mantiene policy-v1 y su vector publicado, ordenando Snapshot por ID y probando permutaciones/escala decimal. Java y Oracle usan una matriz de contrato común; la suite Oracle solo se ejecuta con una conexión de laboratorio explícita. El trabajo Oracle sigue parcialmente validado hasta compilar y ejecutar allí.

La migración preserva legado con ENABLE NOVALIDATE, reutiliza índices y alinea secuencias existentes hacia adelante. No incluye limpieza de movimientos ni rollback destructivo de DDL. El costo adicional de guardas y consulta de integridad debe medirse en Oracle; no se atribuyen mejoras de rendimiento a pruebas sobre cuatro pagos.

## Referencias técnicas consultadas

- [Requisitos de Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html).
- [Transacciones declarativas de Spring](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html).
- [STANDARD_HASH en Oracle 19c](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/STANDARD_HASH.html).
- [Control transaccional y bloqueos Oracle](https://docs.oracle.com/en/database/oracle/oracle-database/19/lnpls/transaction-processing-and-control.html).
