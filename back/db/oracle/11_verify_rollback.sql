-- ONLY a dedicated demo schema. Test DDL commits implicitly; never use in an active transaction.
-- The temporary trigger is removed on success and on assertion failure.
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
DECLARE
 v_out VARCHAR2(40); v_reason VARCHAR2(100); v_before NUMBER; v_after NUMBER; v_count NUMBER;
 PROCEDURE remove_test_trigger IS
 BEGIN EXECUTE IMMEDIATE 'DROP TRIGGER trg_test_audit_failure';
 EXCEPTION WHEN OTHERS THEN IF SQLCODE<>-4080 THEN RAISE; END IF;
 END;
BEGIN
 EXECUTE IMMEDIATE 'CREATE OR REPLACE TRIGGER trg_test_audit_failure BEFORE INSERT ON support_audit FOR EACH ROW BEGIN IF :NEW.payment_id=2 THEN RAISE_APPLICATION_ERROR(-20020,''Injected audit failure''); END IF; END;';
 INSERT INTO support_audit(id,payment_id,action,reason,created_at,evidence_key)
 VALUES (9900,4,'MANUAL_REVIEW','CALLER-WORK',LOCALTIMESTAMP,LPAD('a',64,'a'));
 BEGIN
   reconcile_payment('PAY-BX17',v_out,v_reason,v_before,v_after,1);
   RAISE_APPLICATION_ERROR(-20010,'Expected audit failure');
 EXCEPTION WHEN OTHERS THEN IF SQLCODE<>-20020 THEN RAISE; END IF;
 END;
 -- OUT values are intentionally ignored after the failed call.
 SELECT COUNT(*) INTO v_count FROM ledger_entry WHERE payment_id=2;
 IF v_count<>2 THEN RAISE_APPLICATION_ERROR(-20010,'Ledger rollback failed'); END IF;
 SELECT row_version INTO v_count FROM payment WHERE id=2;
 IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20010,'Payment rollback failed'); END IF;
 SELECT COUNT(*) INTO v_count FROM support_audit WHERE payment_id=2;
 IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20010,'Audit rollback failed'); END IF;
 SELECT COUNT(*) INTO v_count FROM support_audit WHERE id=9900 AND reason='CALLER-WORK';
 IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20010,'Procedure rolled back work preceding its savepoint'); END IF;
 ROLLBACK;
 remove_test_trigger;
 DBMS_OUTPUT.PUT_LINE('PASS: failure propagates and restores payment, ledger and audit');
EXCEPTION WHEN OTHERS THEN
 ROLLBACK;
 remove_test_trigger;
 RAISE;
END;
/
