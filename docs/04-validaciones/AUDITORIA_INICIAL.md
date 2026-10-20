[Volver al ?ndice de documentaci?n](../README.md)

# Auditoría inicial — PagoClaro

Inspección previa a modificaciones: estructura versionada, Java/Spring/JDBC, controladores, configuración de seguridad/transacciones, pruebas, esquema H2 y todos los scripts Oracle. No se encontraron AGENTS.md aplicables. Línea base: `mvnw -B test`, 25 pruebas, cero fallos/errores. No se dispone de docker, SQLcl, SQL*Plus ni conexión Oracle configurada para certificar ejecución.

| Prioridad | Hallazgo verificable | Corrección prevista |
|---|---|---|
| Alta | H2 acepta UPDATE/DELETE del ledger; la prueba de débito parcial lo modifica directamente. | Inmutabilidad en la base local y pruebas aisladas con esquema nuevo. |
| Alta | FK simple y unicidad no impiden reversos cruzados, de importe distinto o dirigidos a otro reverso. | Validación de nuevas inserciones en H2 y Oracle. |
| Alta | SELECT FOR UPDATE solo funciona si los escritores de tablas hijas cooperan; el DML directo actual puede omitirlo. | Guardas de escritura que bloqueen primero PAYMENT para eventos y ledger. |
| Alta | Motor y procedimiento compensan duplicados también fuera del laboratorio basándose en un supuesto documentado pero no configurado. | Compensación deshabilitada por defecto; habilitación explícita en demo/operación validada. |
| Media | Oracle no tiene los CHECK de H2; migración no es reanudable y puede crear índices redundantes. | Preflight, restricciones para escrituras nuevas sin borrar legado, detección de índices/secuencias y recuperación documentada. |
| Media | La huella Java no ordena por sí misma; depende del ORDER BY del repositorio. | Canonicalización en Snapshot y prueba por permutación; conservar vector ya publicado. |
| Media | Falta prueba de timeout, competencia con nuevos eventos y conflictos reales de restricciones. | Integración con transacciones y conexiones independientes. |
| Media | Consulta no detecta algunos vínculos de reverso inválidos cuando el neto coincide. | Detección adicional de integridad sin multiplicar importes. |
| Limitación | PL/SQL y bloqueos Oracle no se han ejecutado en Oracle real. | Contratos y guiones reproducibles; resultados pendientes, sin equivalencia artificial con H2. |

Aspectos correctos que se conservan: BigDecimal, decisiones conservadoras ante contradicción, reversos vinculados deterministas por ID, transacción de estado/ledger/auditoría, rollback por fallo, parámetros SQL, auditoría SHA-256 incremental, unicidad por evidencia, agregaciones independientes, login sin tocar pagos y seguridad de rol SUPPORT. No hay entidades JPA porque la arquitectura usa JDBC explícitamente.

El ID menor determina qué fila conservar en el escenario ficticio; no demuestra cronología ni un único cobro real. La política segura debe distinguir demostración de autorización contable en un entorno real.
