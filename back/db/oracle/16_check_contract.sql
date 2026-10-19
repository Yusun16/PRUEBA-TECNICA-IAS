-- Internal include: integrity_apply=1 installs missing CHECKs, 0 verifies read-only.
-- Conservative textual equivalence, not a general SQL equivalence prover.
SET SERVEROUTPUT ON
DECLARE
 FUNCTION canonical(p_expression VARCHAR2) RETURN VARCHAR2 IS
   v_result VARCHAR2(4000):=''; v_literal BOOLEAN:=FALSE; v_char VARCHAR2(1);
 BEGIN
   IF p_expression IS NULL THEN RETURN NULL; END IF;
   FOR i IN 1..LENGTH(p_expression) LOOP
     v_char:=SUBSTR(p_expression,i,1);
     IF v_char='''' THEN v_literal:=NOT v_literal; END IF;
     IF v_literal OR NOT REGEXP_LIKE(v_char,'[[:space:]]') THEN
       v_result:=v_result||CASE WHEN v_literal THEN v_char ELSE UPPER(v_char) END;
     END IF;
   END LOOP;
   RETURN v_result;
 END;
 PROCEDURE ensure_check(p_table VARCHAR2,p_name VARCHAR2,p_expression VARCHAR2) IS
   v_named NUMBER:=0; v_equivalent NUMBER:=0;
 BEGIN
   FOR c IN (SELECT constraint_name,constraint_type,status,validated,deferrable,search_condition_vc
             FROM user_constraints WHERE table_name=UPPER(p_table)) LOOP
     IF c.constraint_name=UPPER(p_name) THEN
       v_named:=1;
       IF c.constraint_type<>'C' OR canonical(c.search_condition_vc)<>canonical(p_expression)
          OR c.search_condition_vc IS NULL THEN
         RAISE_APPLICATION_ERROR(-20019,'Different CHECK definition: '||p_name||'; manual review required');
       END IF;
     END IF;
     IF c.constraint_type='C' AND canonical(c.search_condition_vc)=canonical(p_expression) THEN
       IF c.status='ENABLED' AND c.validated IN ('VALIDATED','NOT VALIDATED')
          AND c.deferrable='NOT DEFERRABLE' THEN
         v_equivalent:=v_equivalent+1;
         DBMS_OUTPUT.PUT_LINE('VERIFIED CHECK '||c.constraint_name||' '||c.validated);
       ELSIF c.constraint_name=UPPER(p_name) THEN
         RAISE_APPLICATION_ERROR(-20019,'Disabled or incompatible CHECK: '||p_name||'; manual review required');
       END IF;
     END IF;
   END LOOP;
   IF v_equivalent=0 THEN
     IF &integrity_apply=0 OR v_named=1 THEN
       RAISE_APPLICATION_ERROR(-20019,'Missing enabled CHECK contract: '||p_name);
     END IF;
     EXECUTE IMMEDIATE 'ALTER TABLE '||p_table||' ADD CONSTRAINT '||p_name||
       ' CHECK ('||p_expression||') ENABLE NOVALIDATE';
     DBMS_OUTPUT.PUT_LINE('CREATED CHECK '||p_name||' ENABLE NOVALIDATE');
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
