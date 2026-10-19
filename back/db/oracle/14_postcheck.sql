-- Read-only post-deployment inventory; fails if a required object is missing, invalid or disabled.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
DECLARE v_count NUMBER;
BEGIN
 SELECT COUNT(*) INTO v_count FROM user_objects
 WHERE object_name IN ('RECONCILE_PAYMENT','TRG_LEDGER_IMMUTABLE','TRG_PROVIDER_PARENT_LOCK','TRG_LEDGER_INSERT_GUARD')
   AND status='VALID' AND object_type IN ('PROCEDURE','TRIGGER');
 IF v_count<>4 THEN RAISE_APPLICATION_ERROR(-20011,'Missing or invalid reconciliation objects'); END IF;
 SELECT COUNT(*) INTO v_count FROM user_triggers
 WHERE trigger_name IN ('TRG_LEDGER_IMMUTABLE','TRG_PROVIDER_PARENT_LOCK','TRG_LEDGER_INSERT_GUARD') AND status='ENABLED';
 IF v_count<>3 THEN RAISE_APPLICATION_ERROR(-20011,'Required write guards are disabled or missing'); END IF;
 SELECT COUNT(*) INTO v_count FROM user_constraints
 WHERE constraint_name IN ('CK_PAYMENT_STATUS','CK_PAYMENT_AMOUNT','CK_PAYMENT_VERSION','CK_PROVIDER_STATUS',
   'CK_LEDGER_TYPE','CK_LEDGER_AMOUNT','CK_LEDGER_RELATION','CK_AUDIT_ACTION','CK_AUDIT_NEW_EVIDENCE') AND status='ENABLED';
 IF v_count<>9 THEN RAISE_APPLICATION_ERROR(-20011,'Required CHECK constraints are disabled or missing'); END IF;
END;
/
SELECT name,type,line,position,text FROM user_errors
WHERE name IN ('RECONCILE_PAYMENT','TRG_LEDGER_IMMUTABLE','TRG_PROVIDER_PARENT_LOCK','TRG_LEDGER_INSERT_GUARD')
ORDER BY name,sequence;
SELECT table_name,constraint_name,status,validated FROM user_constraints
WHERE table_name IN ('PAYMENT','PROVIDER_EVENT','LEDGER_ENTRY','SUPPORT_AUDIT') ORDER BY table_name,constraint_name;
SELECT sequence_name,increment_by,last_number,cycle_flag FROM user_sequences
WHERE sequence_name IN ('LEDGER_ENTRY_SEQ','SUPPORT_AUDIT_SEQ');
