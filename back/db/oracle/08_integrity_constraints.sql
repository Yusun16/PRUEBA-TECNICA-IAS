-- Re-runnable. Protect new writes while retaining legacy violations for investigation.
-- Existing NULL evidence_key is retained; new rows/changes require a key.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
DECLARE
 PROCEDURE ensure_check(p_table VARCHAR2,p_name VARCHAR2,p_expression VARCHAR2) IS
   v_count NUMBER;
 BEGIN
   SELECT COUNT(*) INTO v_count FROM user_constraints
   WHERE table_name=UPPER(p_table) AND constraint_name=UPPER(p_name);
   IF v_count=0 THEN
     EXECUTE IMMEDIATE 'ALTER TABLE '||p_table||' ADD CONSTRAINT '||p_name||
       ' CHECK ('||p_expression||') ENABLE NOVALIDATE';
   END IF;
 END;
BEGIN
 ensure_check('payment','ck_payment_status','status IN (''PENDING'',''CONFIRMED'',''REJECTED'')');
 ensure_check('payment','ck_payment_amount','amount > 0');
 ensure_check('payment','ck_payment_version','row_version >= 0 AND row_version=TRUNC(row_version)');
 ensure_check('provider_event','ck_provider_status','provider_status IN (''APPROVED'',''DECLINED'')');
 ensure_check('ledger_entry','ck_ledger_type','entry_type IN (''DEBIT'',''REVERSAL'')');
 ensure_check('ledger_entry','ck_ledger_amount','amount > 0');
 ensure_check('ledger_entry','ck_ledger_relation',
   '(entry_type=''DEBIT'' AND related_entry_id IS NULL) OR (entry_type=''REVERSAL'' AND related_entry_id IS NOT NULL)');
 ensure_check('support_audit','ck_audit_action','action IN (''RESOLVED'',''MANUAL_REVIEW'')');
 ensure_check('support_audit','ck_audit_new_evidence','evidence_key IS NOT NULL');
END;
/
