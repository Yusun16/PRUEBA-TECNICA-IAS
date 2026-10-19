-- Read-only inventory. Run before any DDL with all writers stopped.
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
DECLARE
 v_count NUMBER;
 PROCEDURE require_unique(p_table VARCHAR2,p_column VARCHAR2) IS
   v_found NUMBER;
 BEGIN
   SELECT COUNT(*) INTO v_found FROM (
     SELECT i.index_name FROM user_indexes i JOIN user_ind_columns c ON c.index_name=i.index_name
     WHERE i.table_name=UPPER(p_table) AND i.uniqueness='UNIQUE' AND i.status='VALID'
     GROUP BY i.index_name HAVING COUNT(*)=1 AND MAX(c.column_name)=UPPER(p_column));
   IF v_found=0 THEN RAISE_APPLICATION_ERROR(-20016,'Missing immediate uniqueness: '||p_table||'.'||p_column); END IF;
 END;
 PROCEDURE require_fk(p_child VARCHAR2,p_column VARCHAR2,p_parent VARCHAR2) IS
   v_found NUMBER;
 BEGIN
   SELECT COUNT(*) INTO v_found FROM user_constraints fk
   JOIN user_constraints pk ON pk.constraint_name=fk.r_constraint_name AND fk.r_owner=USER
   JOIN user_cons_columns fc ON fc.constraint_name=fk.constraint_name
   JOIN user_cons_columns pc ON pc.constraint_name=pk.constraint_name AND pc.position=fc.position
   WHERE fk.constraint_type='R' AND fk.status='ENABLED'
     AND fk.table_name=UPPER(p_child) AND fc.column_name=UPPER(p_column)
     AND pk.table_name=UPPER(p_parent) AND pc.column_name='ID';
   IF v_found=0 THEN RAISE_APPLICATION_ERROR(-20016,'Missing required FK: '||p_child||'.'||p_column); END IF;
 END;
BEGIN
 SELECT COUNT(*) INTO v_count FROM user_constraints WHERE constraint_type='P' AND status='ENABLED'
   AND table_name IN ('PAYMENT','PROVIDER_EVENT','LEDGER_ENTRY','SUPPORT_AUDIT');
 IF v_count<>4 THEN RAISE_APPLICATION_ERROR(-20016,'Expected four enabled primary keys'); END IF;
 SELECT COUNT(*) INTO v_count FROM (
   SELECT id FROM payment UNION ALL SELECT id FROM provider_event
   UNION ALL SELECT id FROM ledger_entry UNION ALL SELECT id FROM support_audit
 ) WHERE id< -9223372036854775808 OR id<>TRUNC(id) OR id>9223372036854775806;
 IF v_count>0 THEN RAISE_APPLICATION_ERROR(-20012,'IDs must be Java-long integers; investigate without rewriting history'); END IF;
 SELECT COUNT(*) INTO v_count FROM payment
 WHERE row_version<0 OR row_version<>TRUNC(row_version) OR row_version>9223372036854775806;
 IF v_count>0 THEN RAISE_APPLICATION_ERROR(-20012,'Unsupported row_version; investigate before migration'); END IF;
 require_fk('provider_event','payment_id','payment');
 require_fk('ledger_entry','payment_id','payment');
 require_fk('ledger_entry','related_entry_id','ledger_entry');
 require_fk('support_audit','payment_id','payment');
 require_unique('payment','external_ref');
 require_unique('provider_event','provider_event_id');
END;
/
SELECT table_name,constraint_name,constraint_type,status,validated
FROM user_constraints WHERE table_name IN ('PAYMENT','PROVIDER_EVENT','LEDGER_ENTRY','SUPPORT_AUDIT')
ORDER BY table_name,constraint_name;
SELECT table_name,column_name,data_type,data_length,char_length,data_precision,data_scale,nullable
FROM user_tab_columns WHERE table_name IN ('PAYMENT','PROVIDER_EVENT','LEDGER_ENTRY','SUPPORT_AUDIT')
ORDER BY table_name,column_id;
SELECT i.table_name,i.index_name,i.uniqueness,c.column_position,c.column_name
FROM user_indexes i JOIN user_ind_columns c ON c.index_name=i.index_name
WHERE i.table_name IN ('PAYMENT','PROVIDER_EVENT','LEDGER_ENTRY','SUPPORT_AUDIT')
ORDER BY i.table_name,i.index_name,c.column_position;
SELECT sequence_name,increment_by,last_number,cycle_flag FROM user_sequences
WHERE sequence_name IN ('LEDGER_ENTRY_SEQ','SUPPORT_AUDIT_SEQ');
SELECT id,external_ref,status,amount,row_version FROM payment
WHERE status NOT IN ('PENDING','CONFIRMED','REJECTED') OR amount<=0
   OR row_version<0 OR row_version<>TRUNC(row_version);
SELECT id,payment_id,provider_status FROM provider_event WHERE provider_status NOT IN ('APPROVED','DECLINED');
SELECT r.id,r.payment_id,r.entry_type,r.amount,r.related_entry_id FROM ledger_entry r
WHERE r.entry_type NOT IN ('DEBIT','REVERSAL') OR r.amount<=0
 OR (r.entry_type='DEBIT' AND r.related_entry_id IS NOT NULL)
 OR (r.entry_type='REVERSAL' AND NOT EXISTS (
     SELECT 1 FROM ledger_entry d WHERE d.id=r.related_entry_id AND d.payment_id=r.payment_id
      AND d.entry_type='DEBIT' AND d.amount=r.amount AND d.related_entry_id IS NULL));
SELECT related_entry_id,COUNT(*) AS references_count FROM ledger_entry WHERE related_entry_id IS NOT NULL
GROUP BY related_entry_id HAVING COUNT(*)>1;
