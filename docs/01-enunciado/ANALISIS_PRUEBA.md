[Volver al ?ndice de documentaci?n](../README.md)

# Análisis y plan de la prueba técnica

Fuente: `Prueba_Tecnica_Desarrollador_Soporte_Java_Candidato.pdf` (5 páginas).
Estado: análisis inicial; implementación y validaciones pendientes.

## Objetivo y alcance obligatorio

Construir una solución mínima Java/Spring Boot para conciliar pagos por referencia externa usando PAYMENT, PROVIDER_EVENT y LEDGER_ENTRY. Tiempo indicado: máximo 2 horas desde el inicio de la prueba. Entrega mediante repositorio Git con historial visible.

- Operación de soporte para diagnosticar y corregir únicamente casos seguros.
- Ledger inmutable: insertar reversos vinculados; nunca actualizar ni borrar movimientos.
- Reintentos sin efectos contables ni auditorías equivalentes duplicados.
- Consistencia ante solicitudes concurrentes sobre el mismo pago.
- Revisión manual ante evidencia contradictoria, insuficiente o contabilidad no interpretable con seguridad.
- Consulta Oracle de anomalías de las últimas 24 horas.
- Procedimiento o bloque PL/SQL invocable y atómico para conciliar o registrar revisión.
- Scripts separados para cualquier cambio de esquema, con justificación.
- Pruebas automatizadas del flujo principal, al menos dos bordes/errores y evidencia de idempotencia.
- README con ejecución, diagnóstico, decisiones, riesgos, nota operativa y trazabilidad del uso de IA.

## Diagnóstico inicial

Hechos: el log muestra un timeout, programación de reintento, recepción de APPROVED, creación del débito 5003 y estado ya CONFIRMED durante el reintento. Después se reporta débito neto 178000 frente a 89000. La muestra informa dos débitos y dos APPROVED para PAY-BX17.

Hipótesis: el procesamiento de confirmaciones/reintentos pudo crear efectos contables sin una protección idempotente y transaccional adecuada. Dos APPROVED no prueban por sí mismos dos cobros distintos ni dos entregas del mismo evento. El log parcial no demuestra la secuencia completa ni la causa exacta.

Validaciones en producción: correlacionar eventos y solicitudes; verificar significado contractual de APPROVED/DECLINED, montos, moneda y reversos; confirmar si el proveedor permite cambios de resultado; revisar límites transaccionales, restricciones y todos los procesos que escriben en las tres tablas.

## Casos suministrados

| Pago | Resultado esperado inicial |
|---|---|
| PAY-AX91 | Con un APPROVED y un débito válido de 125000, pasar de PENDING a CONFIRMED sin crear otro débito. |
| PAY-BX17 | Si ambos débitos completos de 89000 son duplicados verificables, conservar uno y agregar un reverso de 89000 relacionado con el sobrante. Mantener CONFIRMED. |
| PAY-CQ22 | APPROVED + DECLINED: revisión manual sin modificar estado ni ledger. |
| PAY-DK44 | Ya consistente: no crear movimientos ni auditorías de corrección. |

## Política conservadora propuesta

- Calcular débito neto como suma de DEBIT menos suma de REVERSAL usando BigDecimal.
- Validar importes positivos, tipos, relaciones de reverso y pertenencia al mismo pago antes de corregir.
- Solo APPROVED: confirmar y asegurar un débito neto igual al monto. Crear un débito faltante solo si no existe historial contable ambiguo. Revertir duplicados únicamente si son débitos completos idénticos y sin reversos previos ambiguos; elegir de forma determinista cuál conservar.
- Sin eventos o con estados contradictorios: revisión manual.
- Solo DECLINED: proponer REJECTED sin movimientos cuando no hay ledger. Si existen débitos, enviar inicialmente a revisión manual: el documento no define si DECLINED autoriza una devolución o compensación automática.
- Importes parciales, exceso de reversos, vínculos inválidos y estados no previstos: revisión manual conservando evidencia.
- Estas políticas son decisiones propuestas, no reglas adicionales expresas del PDF; documentar riesgos y supuestos al implementar.

## Diseño propuesto

API: `POST /api/support/payments/{externalRef}/reconcile`, con resultado RESOLVED, ALREADY_CONSISTENT o MANUAL_REVIEW, motivo y resumen contable. Pago inexistente: 404. Validar referencia y evitar exponer excepciones SQL o secretos.

Servicio transaccional con bloqueo de PAYMENT mediante SELECT FOR UPDATE; leer evidencia y ledger después del bloqueo, decidir y persistir estado, movimientos y auditoría en la misma transacción. Los demás escritores deben respetar el mismo bloqueo; si no lo hacen, la garantía no cubre las escrituras concurrentes de esos procesos.

La idempotencia contable se basa en releer el estado efectivo y los reversos existentes. Para auditorías de revisión manual, definir una identidad estable por pago, acción y evidencia relevante; una revisión repetida de la misma evidencia no debe insertar otra auditoría. Si esto requiere ampliar SUPPORT_AUDIT, entregar migración separada. Una evidencia nueva debe poder generar una nueva revisión.

Alternativa de ejecución local: base embebida con datos de muestra para probar Java sin Oracle; los scripts Oracle y PL/SQL se validan separadamente en Oracle. La compatibilidad de la base embebida no demuestra compatibilidad PL/SQL. Mantener equivalentes las decisiones del servicio y del procedimiento mediante una matriz común de escenarios.

Consulta de anomalías: agregar eventos y ledger por separado antes de unirlos para evitar multiplicar importes. Definir explícitamente qué significa últimas 24 horas; propuesta: pagos actualizados o con eventos/movimientos recientes. Incluir contradicción de eventos y distinguir monto esperado de confirmados frente a rechazados; evidencia insuficiente se reporta aparte como revisión.

## Pruebas prioritarias

1. Los cuatro casos de muestra y comprobación de filas e importes finales.
2. Repetir cada conciliación: sin nuevos reversos, débitos ni auditorías equivalentes.
3. Dos conciliaciones simultáneas del mismo pago: un único resultado contable válido.
4. Pago inexistente y pago sin evidencia.
5. Ledger ambiguo o reverso inválido: revisión sin compensaciones.
6. Fallo durante persistencia: rollback de estado, ledger y auditoría.

## Secuencia de trabajo (120 minutos)

1. 0–20: diagnóstico, supuestos, política y preparación de proyecto.
2. 20–85: modelo, persistencia, transacción, API, datos de muestra y auditoría idempotente.
3. 85–110: consulta Oracle, procedimiento PL/SQL y pruebas prioritarias.
4. 110–120: instrucciones, nota operativa, uso de IA, revisión y commits reales del trabajo.

No se requiere frontend, integración en vivo con el proveedor, autenticación completa ni infraestructura adicional. Si se incorpora algo de esto, no debe desplazar los entregables obligatorios.

## Entorno observado

El directorio inicialmente contiene DOCS; no se encontró proyecto Java ni repositorio Git visible en la raíz. Node está disponible. Java y Maven no se encontraron en PATH; confirmar disponibilidad o preparar herramientas antes de ejecutar la aplicación. Python no pudo ejecutarse. Se extrajo el texto del PDF con Node y zlib a `Prueba_Tecnica_texto.txt` para facilitar revisión.

## Trazabilidad de IA

Se utilizó Codex para extraer y analizar el documento y proponer este plan. Se separaron hechos del PDF de hipótesis y políticas propuestas. Todavía no se ha implementado ni probado la solución. Durante el desarrollo se deben registrar resultados ejecutados, ajustes y limitaciones reales.
