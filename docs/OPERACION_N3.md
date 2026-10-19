# Guía operativa para soporte N3

1. Identificar el pago con la consulta `back/db/oracle/02_detect_anomalies.sql`. Correlacionar eventos con proveedor y comprobar monto/moneda, estado final y ausencia de devoluciones fuera del modelo. La consulta detecta candidatos; no declara que sea seguro corregirlos.
2. Confirmar que todos los escritores toman primero el bloqueo de PAYMENT o detener los que no lo hacen. Trabajar inicialmente sobre un solo pago y conservar conteos/IDs de movimientos previos.
3. Ejecutar el POST autenticado o el procedimiento PL/SQL. El procedimiento necesita COMMIT explícito; no dejar sesiones con transacciones abiertas. La API confirma automáticamente si termina sin error.
4. Examinar outcome y reason. RESOLVED significa que se persistió una corrección; ALREADY_CONSISTENT significa que no hubo escrituras; MANUAL_REVIEW exige detener compensaciones y escalar con la evidencia.
5. Verificar estado, neto, reversos vinculados y auditoría después de ejecutar. Repetir el mismo comando debe producir cero movimientos y ninguna auditoría equivalente adicional.

Casos seguros bajo los supuestos acordados: solo APPROVED y cero débitos sin historia previa, o uno/varios débitos completos válidos; solo DECLINED sin ledger. Detenerse ante APPROVED + DECLINED, falta de eventos, devoluciones, importes parciales, moneda no comprobada, referencias cruzadas o historial que no pueda explicarse.

Después de la auditoría, “varios débitos completos” no autoriza compensar por sí solo: fuera de la demo se devuelve DUPLICATE_DEBITS_REQUIRE_REVIEW. Validar primero captura única, importe, moneda e historial externo. La habilitación de compensación corresponde a configuración operativa revisada o al parámetro explícito del procedimiento, nunca a una suposición por contar APPROVED. Oracle necesita instalar también las guardas y CHECK de la guía de despliegue.

Validación posterior en Oracle:

```sql
SELECT p.external_ref,p.status,p.amount,p.row_version,
       NVL(SUM(CASE WHEN l.entry_type='DEBIT' THEN l.amount
                    WHEN l.entry_type='REVERSAL' THEN -l.amount ELSE 0 END),0) net_debit
FROM payment p LEFT JOIN ledger_entry l ON l.payment_id=p.id
WHERE p.external_ref=:payment_ref
GROUP BY p.external_ref,p.status,p.amount,p.row_version;

SELECT l.id,l.entry_type,l.amount,l.related_entry_id,l.created_at
FROM ledger_entry l JOIN payment p ON p.id=l.payment_id
WHERE p.external_ref=:payment_ref ORDER BY l.id;

SELECT a.id,a.action,a.reason,a.evidence_key,a.created_at
FROM support_audit a JOIN payment p ON p.id=a.payment_id
WHERE p.external_ref=:payment_ref ORDER BY a.id;
```

En una corrección aprobada el neto final debe igualar PAYMENT.amount; los débitos originales siguen presentes y cada sobrante tiene un reverso completo. Una revisión manual conserva estado, versión y movimientos; solo puede agregar una auditoría por evidencia nueva.

Ante timeout de HTTP o 503, no asumir éxito ni fracaso: verificar y repetir de forma controlada. Ante 409, esperar a que finalice la operación activa y reintentar; si persiste, revisar bloqueadores con DBA. No borrar filas para desbloquear una conciliación ni desactivar restricciones. No ejecutar otro cobro al proveedor.

No revertir la solución eliminando compensaciones. Si una decisión resultó incorrecta, conservar evidencia, detener automatismos y diseñar con negocio una nueva operación contable explícita; esa operación queda fuera de este ejercicio.
