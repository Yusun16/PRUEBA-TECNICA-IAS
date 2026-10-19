-- Re-runnable; stop every writer during DDL and sequence alignment.
-- DDL commits implicitly. No historical data is rewritten or deleted.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET SERVEROUTPUT ON
DECLARE
 v_count NUMBER;
 v_bad NUMBER;
 PROCEDURE ensure_index(p_name VARCHAR2,p_table VARCHAR2,p_first VARCHAR2,
                        p_second VARCHAR2 DEFAULT NULL,p_unique NUMBER DEFAULT 0) IS
   v_found NUMBER;
 BEGIN
   SELECT COUNT(*) INTO v_found FROM (
     SELECT i.index_name,i.uniqueness,COUNT(*) AS columns_count,
       SUM(CASE WHEN c.column_position=1 AND c.column_name=UPPER(p_first) THEN 1 ELSE 0 END) AS first_matches,
       SUM(CASE WHEN c.column_position=2 AND c.column_name=UPPER(p_second) THEN 1 ELSE 0 END) AS second_matches,
       SUM(CASE WHEN c.column_name=UPPER(p_first) OR c.column_name=UPPER(p_second) THEN 1 ELSE 0 END) AS matching_columns
     FROM user_indexes i JOIN user_ind_columns c ON c.index_name=i.index_name
     WHERE i.table_name=UPPER(p_table) AND i.status='VALID'
       AND i.index_type='NORMAL' AND i.partitioned='NO'
       AND (p_unique=1 OR i.visibility='VISIBLE')
       AND NOT EXISTS (SELECT 1 FROM user_ind_expressions e WHERE e.index_name=i.index_name)
       AND NOT EXISTS (SELECT 1 FROM user_ind_columns d WHERE d.index_name=i.index_name AND d.descend<>'ASC')
     GROUP BY i.index_name,i.uniqueness
   ) WHERE (p_unique=0 AND columns_count>=CASE WHEN p_second IS NULL THEN 1 ELSE 2 END
             AND first_matches=1 AND (p_second IS NULL OR second_matches=1))
      OR (p_unique=1 AND uniqueness='UNIQUE'
             AND columns_count=CASE WHEN p_second IS NULL THEN 1 ELSE 2 END
             AND matching_columns=columns_count);
   IF v_found=0 THEN
     SELECT COUNT(*) INTO v_found FROM user_objects WHERE object_name=UPPER(p_name);
     IF v_found>0 THEN
       RAISE_APPLICATION_ERROR(-20018,'Index name occupied by incompatible object: '||p_name||'; inspect manually');
     END IF;
     EXECUTE IMMEDIATE 'CREATE '||CASE WHEN p_unique=1 THEN 'UNIQUE ' ELSE '' END||
       'INDEX '||p_name||' ON '||p_table||'('||p_first||
       CASE WHEN p_second IS NOT NULL THEN ','||p_second ELSE '' END||')';
     DBMS_OUTPUT.PUT_LINE('CREATED INDEX '||p_name);
   ELSE
     DBMS_OUTPUT.PUT_LINE('REUSED equivalent index for '||p_name);
   END IF;
 END;
 PROCEDURE align_sequence(p_name VARCHAR2,p_table VARCHAR2) IS
   v_max NUMBER; v_exists NUMBER; v_current NUMBER; v_step NUMBER; v_delta NUMBER; v_cycle VARCHAR2(1);
 BEGIN
   EXECUTE IMMEDIATE 'SELECT GREATEST(NVL(MAX(id),0),0) FROM '||p_table INTO v_max;
   IF v_max<>TRUNC(v_max) OR v_max>=9223372036854775806 THEN
     RAISE_APPLICATION_ERROR(-20012,'IDs outside the supported integer range; investigate before migration');
   END IF;
   SELECT COUNT(*) INTO v_exists FROM user_sequences WHERE sequence_name=UPPER(p_name);
   IF v_exists=0 THEN
     EXECUTE IMMEDIATE 'CREATE SEQUENCE '||p_name||' START WITH '||TO_CHAR(v_max+1,'FM9999999999999999999999999990');
     DBMS_OUTPUT.PUT_LINE('CREATED SEQUENCE '||p_name);
   ELSE
     SELECT increment_by,cycle_flag INTO v_step,v_cycle FROM user_sequences WHERE sequence_name=UPPER(p_name);
     IF v_step<=0 OR v_cycle='Y' THEN RAISE_APPLICATION_ERROR(-20013,'Sequence must be increasing and NOCYCLE'); END IF;
     -- Consume NEXTVAL: LAST_NUMBER is a cache boundary, not necessarily the next value.
     EXECUTE IMMEDIATE 'SELECT '||p_name||'.NEXTVAL FROM dual' INTO v_current;
     IF v_current<=v_max THEN
       v_delta:=v_max+1-v_current;
       BEGIN
         EXECUTE IMMEDIATE 'ALTER SEQUENCE '||p_name||' INCREMENT BY '||TO_CHAR(v_delta,'FM9999999999999999999999999990');
         EXECUTE IMMEDIATE 'SELECT '||p_name||'.NEXTVAL FROM dual' INTO v_current;
         EXECUTE IMMEDIATE 'ALTER SEQUENCE '||p_name||' INCREMENT BY '||TO_CHAR(v_step,'FM9999999999999999999999999990');
       EXCEPTION WHEN OTHERS THEN
         BEGIN EXECUTE IMMEDIATE 'ALTER SEQUENCE '||p_name||' INCREMENT BY '||TO_CHAR(v_step,'FM9999999999999999999999999990');
         EXCEPTION WHEN OTHERS THEN
           DBMS_OUTPUT.PUT_LINE('MANUAL RECOVERY: could not restore increment of '||p_name||': '||SQLERRM);
         END;
         RAISE;
       END;
     END IF;
     IF v_current+v_step>9223372036854775806 THEN
       RAISE_APPLICATION_ERROR(-20012,'Sequence exceeds the supported Java-long identifier range');
     END IF;
   END IF;
   DBMS_OUTPUT.PUT_LINE('VERIFIED sequence above current table IDs: '||p_name);
 END;
BEGIN
 -- Fail before DDL for histories incompatible with the full-reversal uniqueness rule.
 SELECT COUNT(*) INTO v_bad FROM (
   SELECT related_entry_id FROM ledger_entry WHERE related_entry_id IS NOT NULL
   GROUP BY related_entry_id HAVING COUNT(*)>1);
 IF v_bad>0 THEN RAISE_APPLICATION_ERROR(-20014,'Duplicate reversal links: preserve history and investigate'); END IF;
 SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name='SUPPORT_AUDIT' AND column_name='EVIDENCE_KEY';
 IF v_count>0 THEN
   SELECT COUNT(*) INTO v_bad FROM user_tab_columns
   WHERE table_name='SUPPORT_AUDIT' AND column_name='EVIDENCE_KEY' AND data_type='VARCHAR2' AND char_length>=64;
   IF v_bad<>1 THEN RAISE_APPLICATION_ERROR(-20017,'Existing evidence_key must support 64 VARCHAR2 characters'); END IF;
   -- Composite Oracle uniqueness also covers repeated (payment_id, NULL).
   EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM (SELECT payment_id,evidence_key FROM support_audit GROUP BY payment_id,evidence_key HAVING COUNT(*)>1)' INTO v_bad;
   IF v_bad>0 THEN RAISE_APPLICATION_ERROR(-20015,'Duplicate audit evidence: investigate before migration'); END IF;
 ELSE
   SELECT COUNT(*) INTO v_bad FROM (SELECT payment_id FROM support_audit GROUP BY payment_id HAVING COUNT(*)>1);
   IF v_bad>0 THEN
     RAISE_APPLICATION_ERROR(-20015,'Multiple legacy audits would share NULL evidence: preserve history and investigate');
   END IF;
   EXECUTE IMMEDIATE 'ALTER TABLE support_audit ADD (evidence_key VARCHAR2(64))';
 END IF;
 ensure_index('uq_audit_evidence','support_audit','payment_id','evidence_key',1);
 ensure_index('ix_provider_payment','provider_event','payment_id');
 ensure_index('ix_ledger_payment','ledger_entry','payment_id');
 ensure_index('uq_ledger_reversal','ledger_entry','related_entry_id',NULL,1);
 align_sequence('ledger_entry_seq','ledger_entry');
 align_sequence('support_audit_seq','support_audit');
END;
/
