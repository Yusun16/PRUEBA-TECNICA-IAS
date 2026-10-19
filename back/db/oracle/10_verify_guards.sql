-- Fresh laboratory demo only. No historical updates/deletes are successfully applied.
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
DECLARE
 PROCEDURE must_fail(p_sql VARCHAR2,p_code NUMBER) IS
 BEGIN
   EXECUTE IMMEDIATE p_sql;
   RAISE_APPLICATION_ERROR(-20010,'Expected rejection did not occur');
 EXCEPTION WHEN OTHERS THEN
   IF SQLCODE<>p_code THEN RAISE; END IF;
 END;
BEGIN
 SAVEPOINT guard_tests;
 must_fail('UPDATE ledger_entry SET amount=1 WHERE id=5003',-20001);
 must_fail('DELETE FROM ledger_entry WHERE id=5003',-20001);
 must_fail('INSERT INTO ledger_entry VALUES (9000,2,''REVERSAL'',125000,5001,LOCALTIMESTAMP)',-20002);
 must_fail('INSERT INTO ledger_entry VALUES (9000,2,''REVERSAL'',1,5003,LOCALTIMESTAMP)',-20002);
 INSERT INTO ledger_entry VALUES (9000,2,'REVERSAL',89000,5003,LOCALTIMESTAMP);
 must_fail('INSERT INTO ledger_entry VALUES (9001,2,''REVERSAL'',89000,5003,LOCALTIMESTAMP)',-1);
 must_fail('INSERT INTO ledger_entry VALUES (9001,2,''REVERSAL'',89000,9000,LOCALTIMESTAMP)',-20002);
 must_fail('INSERT INTO provider_event VALUES (9000,2,''TEST-INVALID'',''UNKNOWN'',LOCALTIMESTAMP)',-2290);
 must_fail('INSERT INTO support_audit(id,payment_id,action,reason,created_at) VALUES (9000,2,''MANUAL_REVIEW'',''test'',LOCALTIMESTAMP)',-2290);
 ROLLBACK TO guard_tests;
 DBMS_OUTPUT.PUT_LINE('PASS: append-only ledger, relation guards, uniqueness and new audit key');
EXCEPTION WHEN OTHERS THEN ROLLBACK; RAISE;
END;
/
