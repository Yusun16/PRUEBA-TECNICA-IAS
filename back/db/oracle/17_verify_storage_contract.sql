-- Read-only verification; no sequence consumption, DDL or historical certification.
DECLARE
 v_count NUMBER; v_max NUMBER;
 PROCEDURE require_index(p_table VARCHAR2,p_first VARCHAR2,p_second VARCHAR2 DEFAULT NULL,p_unique NUMBER DEFAULT 0) IS
   v_found NUMBER;
 BEGIN
   SELECT COUNT(*) INTO v_found FROM (
     SELECT i.index_name,i.uniqueness,COUNT(*) AS n,
       SUM(CASE WHEN c.column_position=1 AND c.column_name=UPPER(p_first) THEN 1 ELSE 0 END) AS first_match,
       SUM(CASE WHEN c.column_position=2 AND c.column_name=UPPER(p_second) THEN 1 ELSE 0 END) AS second_match,
       SUM(CASE WHEN c.column_name=UPPER(p_first) OR c.column_name=UPPER(p_second) THEN 1 ELSE 0 END) AS matches
     FROM user_indexes i JOIN user_ind_columns c ON c.index_name=i.index_name
     WHERE i.table_name=UPPER(p_table) AND i.status='VALID' AND i.index_type='NORMAL' AND i.partitioned='NO'
       AND (p_unique=1 OR i.visibility='VISIBLE')
       AND NOT EXISTS (SELECT 1 FROM user_ind_expressions e WHERE e.index_name=i.index_name)
       AND NOT EXISTS (SELECT 1 FROM user_ind_columns d WHERE d.index_name=i.index_name AND d.descend<>'ASC')
     GROUP BY i.index_name,i.uniqueness
   ) WHERE (p_unique=0 AND first_match=1 AND (p_second IS NULL OR second_match=1))
      OR (p_unique=1 AND uniqueness='UNIQUE' AND n=CASE WHEN p_second IS NULL THEN 1 ELSE 2 END AND matches=n);
   IF v_found=0 THEN RAISE_APPLICATION_ERROR(-20011,'Missing effective index: '||p_table||'.'||p_first); END IF;
 END;
BEGIN
 require_index('ledger_entry','related_entry_id',NULL,1);
 require_index('support_audit','payment_id','evidence_key',1);
 require_index('provider_event','payment_id');
 require_index('ledger_entry','payment_id');
 require_index('payment','external_ref',NULL,1);
 require_index('provider_event','provider_event_id',NULL,1);
 FOR s IN (SELECT 'LEDGER_ENTRY_SEQ' AS seq_name,'LEDGER_ENTRY' AS table_name FROM dual
           UNION ALL SELECT 'SUPPORT_AUDIT_SEQ','SUPPORT_AUDIT' FROM dual) LOOP
   EXECUTE IMMEDIATE 'SELECT GREATEST(NVL(MAX(id),0),0) FROM '||s.table_name INTO v_max;
   SELECT COUNT(*) INTO v_count FROM user_sequences WHERE sequence_name=s.seq_name
     AND increment_by>0 AND cycle_flag='N' AND max_value>v_max
     AND last_number>v_max AND last_number<=9223372036854775806;
   IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20011,'Missing or incompatible sequence: '||s.seq_name); END IF;
 END LOOP;
 SELECT COUNT(*) INTO v_count FROM user_arguments WHERE object_name='RECONCILE_PAYMENT' AND package_name IS NULL;
 IF v_count<>6 THEN RAISE_APPLICATION_ERROR(-20011,'Unexpected procedure argument count'); END IF;
 SELECT COUNT(*) INTO v_count FROM user_arguments WHERE object_name='RECONCILE_PAYMENT' AND package_name IS NULL
   AND ((position=1 AND argument_name='P_EXTERNAL_REF' AND in_out='IN' AND data_type='VARCHAR2')
     OR (position=2 AND argument_name='P_OUTCOME' AND in_out='OUT' AND data_type='VARCHAR2')
     OR (position=3 AND argument_name='P_REASON' AND in_out='OUT' AND data_type='VARCHAR2')
     OR (position=4 AND argument_name='P_NET_BEFORE' AND in_out='OUT' AND data_type='NUMBER')
     OR (position=5 AND argument_name='P_NET_AFTER' AND in_out='OUT' AND data_type='NUMBER')
     OR (position=6 AND argument_name='P_ALLOW_DUPLICATE_COMPENSATION' AND in_out='IN' AND data_type='NUMBER' AND defaulted='Y'));
 IF v_count<>6 THEN RAISE_APPLICATION_ERROR(-20011,'Unexpected procedure signature'); END IF;
 SELECT COUNT(*) INTO v_count FROM user_plsql_object_settings WHERE name='RECONCILE_PAYMENT' AND type='PROCEDURE'
   AND REGEXP_LIKE(plsql_ccflags,'(^|,)[[:space:]]*pagoclaro_demo[[:space:]]*:[[:space:]]*FALSE([[:space:]]*,|[[:space:]]*$)','i');
 IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20011,'Safe deployment must explicitly compile pagoclaro_demo:FALSE'); END IF;
 SELECT COUNT(*) INTO v_count FROM user_errors WHERE attribute='ERROR'
   AND name IN ('RECONCILE_PAYMENT','TRG_LEDGER_IMMUTABLE','TRG_PROVIDER_PARENT_LOCK','TRG_LEDGER_INSERT_GUARD');
 IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20011,'Compilation errors remain'); END IF;
END;
/
