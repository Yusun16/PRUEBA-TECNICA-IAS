-- Oracle 19c+. Sin COMMIT: el llamador confirma la unidad de trabajo.
-- Caller contract: READ COMMITTED; do not invoke from an older SERIALIZABLE/read-only snapshot.
-- API Java y este procedimiento deben compartir el bloqueo de PAYMENT.
CREATE OR REPLACE PROCEDURE reconcile_payment (
 p_external_ref IN payment.external_ref%TYPE,
 p_outcome OUT VARCHAR2,
 p_reason OUT VARCHAR2,
 p_net_before OUT NUMBER,
 p_net_after OUT NUMBER,
 p_allow_duplicate_compensation IN NUMBER DEFAULT 0
) AUTHID DEFINER AS
 v_payment payment%ROWTYPE;
 v_approved PLS_INTEGER;
 v_declined PLS_INTEGER;
 v_invalid PLS_INTEGER;
 v_bad PLS_INTEGER;
 v_entries PLS_INTEGER;
 v_active PLS_INTEGER;
 v_keep NUMBER;
 v_target payment.status%TYPE;
 v_key VARCHAR2(64);
 v_count PLS_INTEGER;

 FUNCTION num(p_value NUMBER) RETURN VARCHAR2 IS
 BEGIN
   RETURN TO_CHAR(p_value,'FM9999999999999999999999999990');
 END;
 FUNCTION money(p_value NUMBER) RETURN VARCHAR2 IS
 BEGIN
   RETURN TO_CHAR(p_value,'FM9999999999999990.00','NLS_NUMERIC_CHARACTERS=''.,''');
 END;
 FUNCTION hash_value(p_value VARCHAR2) RETURN VARCHAR2 IS
   v_hash VARCHAR2(64);
 BEGIN
   SELECT LOWER(RAWTOHEX(STANDARD_HASH(UTL_I18N.STRING_TO_RAW(p_value,'AL32UTF8'),'SHA256')))
   INTO v_hash FROM dual;
   RETURN v_hash;
 END;
 PROCEDURE mark_manual(p_code VARCHAR2) IS
 BEGIN
   p_outcome := 'MANUAL_REVIEW'; p_reason := p_code;
 END;
BEGIN
 SAVEPOINT reconciliation_start;
 p_outcome := NULL; p_reason := NULL; p_net_before := NULL; p_net_after := NULL;
 IF p_allow_duplicate_compensation IS NULL OR p_allow_duplicate_compensation NOT IN (0,1) THEN
   RAISE_APPLICATION_ERROR(-20004,'Compensation policy must be 0 or 1');
 END IF;
 BEGIN
   SELECT * INTO v_payment FROM payment WHERE external_ref=p_external_ref FOR UPDATE WAIT 5;
 EXCEPTION WHEN NO_DATA_FOUND THEN
   p_outcome := 'NOT_FOUND'; p_reason := 'PAYMENT_NOT_FOUND';
   p_net_before := NULL; p_net_after := NULL; RETURN;
 END;
 p_outcome := NULL; p_reason := NULL; v_target := v_payment.status;
 SELECT COUNT(*), NVL(SUM(CASE WHEN entry_type='DEBIT' THEN amount
                             WHEN entry_type='REVERSAL' THEN -amount ELSE 0 END),0)
 INTO v_entries, p_net_before FROM ledger_entry WHERE payment_id=v_payment.id;
 p_net_after := p_net_before;

 IF v_payment.amount<=0 OR v_payment.status NOT IN ('PENDING','CONFIRMED','REJECTED') THEN
   mark_manual('INVALID_PAYMENT');
 END IF;
 SELECT COUNT(*) INTO v_bad FROM ledger_entry
 WHERE payment_id=v_payment.id AND (amount<=0 OR entry_type NOT IN ('DEBIT','REVERSAL'));
 IF p_outcome IS NULL AND v_bad>0 THEN mark_manual('INVALID_LEDGER'); END IF;
 SELECT COUNT(*) INTO v_bad FROM ledger_entry
 WHERE payment_id=v_payment.id AND entry_type='DEBIT'
 AND (related_entry_id IS NOT NULL OR amount<>v_payment.amount);
 IF p_outcome IS NULL AND v_bad>0 THEN mark_manual('AMBIGUOUS_DEBIT'); END IF;
 SELECT COUNT(*) INTO v_bad FROM ledger_entry r
 WHERE r.payment_id=v_payment.id AND r.entry_type='REVERSAL'
 AND NOT EXISTS (SELECT 1 FROM ledger_entry d WHERE d.id=r.related_entry_id
     AND d.payment_id=v_payment.id AND d.entry_type='DEBIT' AND d.amount=r.amount);
 IF p_outcome IS NULL AND v_bad>0 THEN mark_manual('INVALID_REVERSAL'); END IF;
 SELECT COUNT(*) INTO v_bad FROM (
   SELECT related_entry_id FROM ledger_entry WHERE payment_id=v_payment.id AND entry_type='REVERSAL'
   GROUP BY related_entry_id HAVING COUNT(*)>1
 );
 IF p_outcome IS NULL AND v_bad>0 THEN mark_manual('INVALID_REVERSAL'); END IF;

 SELECT NVL(SUM(CASE WHEN provider_status='APPROVED' THEN 1 ELSE 0 END),0),
        NVL(SUM(CASE WHEN provider_status='DECLINED' THEN 1 ELSE 0 END),0),
        NVL(SUM(CASE WHEN provider_status NOT IN ('APPROVED','DECLINED') THEN 1 ELSE 0 END),0)
 INTO v_approved,v_declined,v_invalid FROM provider_event WHERE payment_id=v_payment.id;
 IF p_outcome IS NULL THEN
   IF v_approved+v_declined+v_invalid=0 THEN mark_manual('MISSING_PROVIDER_EVIDENCE');
   ELSIF v_invalid>0 OR (v_approved>0 AND v_declined>0) THEN mark_manual('CONTRADICTORY_PROVIDER_EVIDENCE');
   ELSIF v_declined>0 THEN
     IF v_entries>0 THEN mark_manual('DECLINED_WITH_LEDGER_HISTORY');
     ELSE v_target:='REJECTED'; p_reason:='DECLINED_WITHOUT_LEDGER'; END IF;
   ELSE
     SELECT COUNT(*), MIN(d.id) INTO v_active,v_keep FROM ledger_entry d
     WHERE d.payment_id=v_payment.id AND d.entry_type='DEBIT'
       AND NOT EXISTS (SELECT 1 FROM ledger_entry r WHERE r.payment_id=v_payment.id
                       AND r.entry_type='REVERSAL' AND r.related_entry_id=d.id);
     IF v_active=0 AND v_entries>0 THEN mark_manual('APPROVED_WITH_REVERSED_HISTORY');
     ELSIF v_active>1 AND p_allow_duplicate_compensation=0 THEN mark_manual('DUPLICATE_DEBITS_REQUIRE_REVIEW');
     ELSE v_target:='CONFIRMED'; p_reason:='APPROVED_RECONCILED'; END IF;
   END IF;
 END IF;

 IF p_outcome IS NULL THEN
   IF v_target=v_payment.status AND (v_target='REJECTED' OR v_active=1) THEN
     p_outcome:='ALREADY_CONSISTENT'; p_reason:='NO_CHANGES_REQUIRED'; RETURN;
   END IF;
   p_outcome:='RESOLVED';
 END IF;

 -- Misma huella incremental que Java; no concatena todo el historial en VARCHAR2.
 v_key:=hash_value('policy-v1|'||p_reason);
 v_key:=hash_value(v_key||'|P:'||num(v_payment.id)||':'||v_payment.status||':'
                    ||money(v_payment.amount)||':'||num(v_payment.row_version));
 FOR e IN (SELECT id,provider_status FROM provider_event WHERE payment_id=v_payment.id ORDER BY id) LOOP
   v_key:=hash_value(v_key||'|E:'||num(e.id)||':'||e.provider_status);
 END LOOP;
 FOR e IN (SELECT id,entry_type,amount,related_entry_id FROM ledger_entry WHERE payment_id=v_payment.id ORDER BY id) LOOP
   v_key:=hash_value(v_key||'|L:'||num(e.id)||':'||e.entry_type||':'||money(e.amount)||':'
                       ||CASE WHEN e.related_entry_id IS NULL THEN 'null' ELSE num(e.related_entry_id) END);
 END LOOP;

 IF p_outcome='RESOLVED' THEN
   IF v_target='CONFIRMED' THEN
     IF v_active=0 THEN
       INSERT INTO ledger_entry(id,payment_id,entry_type,amount,related_entry_id,created_at)
       VALUES (ledger_entry_seq.NEXTVAL,v_payment.id,'DEBIT',v_payment.amount,NULL,LOCALTIMESTAMP);
     ELSE
       FOR d IN (SELECT d.id,d.amount FROM ledger_entry d WHERE d.payment_id=v_payment.id AND d.entry_type='DEBIT'
                 AND d.id<>v_keep AND NOT EXISTS (SELECT 1 FROM ledger_entry r
                   WHERE r.payment_id=v_payment.id AND r.entry_type='REVERSAL' AND r.related_entry_id=d.id)
                 ORDER BY d.id) LOOP
         INSERT INTO ledger_entry(id,payment_id,entry_type,amount,related_entry_id,created_at)
         VALUES (ledger_entry_seq.NEXTVAL,v_payment.id,'REVERSAL',d.amount,d.id,LOCALTIMESTAMP);
       END LOOP;
     END IF;
     p_net_after:=v_payment.amount;
   ELSE p_net_after:=0;
   END IF;
   UPDATE payment SET status=v_target,updated_at=LOCALTIMESTAMP,row_version=row_version+1 WHERE id=v_payment.id;
 END IF;
 SELECT COUNT(*) INTO v_count FROM support_audit WHERE payment_id=v_payment.id AND evidence_key=v_key;
 IF v_count=0 THEN
   INSERT INTO support_audit(id,payment_id,action,reason,created_at,evidence_key)
   VALUES (support_audit_seq.NEXTVAL,v_payment.id,p_outcome,p_reason,LOCALTIMESTAMP,v_key);
 END IF;
EXCEPTION WHEN OTHERS THEN
 -- OUT values are not a result when the call raises: callers must discard them.
 p_outcome := NULL; p_reason := NULL; p_net_before := NULL; p_net_after := NULL;
 ROLLBACK TO reconciliation_start;
 RAISE;
END;
/
SHOW ERRORS PROCEDURE reconcile_payment;
