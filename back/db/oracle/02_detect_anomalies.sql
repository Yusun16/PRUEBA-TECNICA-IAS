-- Ventana = actividad en PAYMENT, PROVIDER_EVENT o LEDGER_ENTRY en últimas 24h.
-- Se lee TODO el historial de cada pago seleccionado para calcular el neto real.
-- Estado sin evidencia o con contradicción se reporta como revisión, no se adivina monto.
WITH recent_ids AS (
 SELECT id AS payment_id FROM payment WHERE updated_at >= LOCALTIMESTAMP - INTERVAL '1' DAY
 UNION
 SELECT payment_id FROM provider_event WHERE received_at >= LOCALTIMESTAMP - INTERVAL '1' DAY
 UNION
 SELECT payment_id FROM ledger_entry WHERE created_at >= LOCALTIMESTAMP - INTERVAL '1' DAY
), events AS (
 SELECT pe.payment_id,
        SUM(CASE WHEN pe.provider_status='APPROVED' THEN 1 ELSE 0 END) AS approved,
        SUM(CASE WHEN pe.provider_status='DECLINED' THEN 1 ELSE 0 END) AS declined,
        SUM(CASE WHEN pe.provider_status NOT IN ('APPROVED','DECLINED') THEN 1 ELSE 0 END) AS invalid_events
 FROM provider_event pe JOIN recent_ids r ON r.payment_id=pe.payment_id GROUP BY pe.payment_id
), ledger AS (
 SELECT le.payment_id,
        SUM(CASE WHEN le.entry_type='DEBIT' THEN le.amount
                 WHEN le.entry_type='REVERSAL' THEN -le.amount ELSE 0 END) AS net_debit,
        SUM(CASE WHEN le.entry_type NOT IN ('DEBIT','REVERSAL') OR le.amount<=0 THEN 1 ELSE 0 END) AS invalid_entries
 FROM ledger_entry le JOIN recent_ids r ON r.payment_id=le.payment_id GROUP BY le.payment_id
), facts AS (
 SELECT p.id, p.external_ref, p.status, p.amount,
        NVL(e.approved,0) AS approved, NVL(e.declined,0) AS declined,
        NVL(e.invalid_events,0) AS invalid_events, NVL(l.invalid_entries,0) AS invalid_entries,
        NVL(l.net_debit,0) AS net_debit,
        CASE WHEN NVL(e.approved,0)>0 AND NVL(e.declined,0)=0 AND NVL(e.invalid_events,0)=0 THEN p.amount
             WHEN NVL(e.declined,0)>0 AND NVL(e.approved,0)=0 AND NVL(e.invalid_events,0)=0 THEN 0
             WHEN p.status='CONFIRMED' THEN p.amount
             WHEN p.status='REJECTED' THEN 0 END AS expected_net
 FROM payment p JOIN recent_ids r ON r.payment_id=p.id
 LEFT JOIN events e ON e.payment_id=p.id LEFT JOIN ledger l ON l.payment_id=p.id
)
SELECT id, external_ref, status, amount, approved, declined, net_debit, expected_net,
       CASE WHEN approved>0 AND declined>0 THEN 'CONTRADICTORY_PROVIDER_EVIDENCE'
            WHEN approved+declined=0 THEN 'MISSING_PROVIDER_EVIDENCE'
            WHEN invalid_events>0 OR invalid_entries>0 THEN 'INVALID_EVIDENCE'
            WHEN net_debit<>expected_net THEN 'NET_DEBIT_MISMATCH'
            ELSE 'STATUS_MISMATCH' END AS diagnostic
FROM facts
WHERE net_debit<>expected_net
   OR (approved>0 AND declined=0 AND status<>'CONFIRMED')
   OR (declined>0 AND approved=0 AND status<>'REJECTED')
   OR (approved>0 AND declined>0) OR approved+declined=0
   OR invalid_events>0 OR invalid_entries>0
ORDER BY external_ref;
