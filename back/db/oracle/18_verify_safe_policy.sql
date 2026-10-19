-- Dedicated fictional lab only; run after compiling pagoclaro_demo:FALSE.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET SERVEROUTPUT ON
DECLARE
 v_out VARCHAR2(40); v_reason VARCHAR2(100); v_before NUMBER; v_after NUMBER; v_count NUMBER;
BEGIN
 BEGIN
   reconcile_payment('PAY-BX17',v_out,v_reason,v_before,v_after,1);
   RAISE_APPLICATION_ERROR(-20010,'Production compilation accepted simulation flag');
 EXCEPTION WHEN OTHERS THEN
   IF SQLCODE<>-20005 THEN RAISE; END IF;
 END;
 reconcile_payment('PAY-BX17',v_out,v_reason,v_before,v_after);
 IF v_out<>'MANUAL_REVIEW' OR v_reason<>'DUPLICATE_DEBITS_REQUIRE_REVIEW' THEN
   RAISE_APPLICATION_ERROR(-20010,'Safe default must preserve duplicate ledger');
 END IF;
 SELECT COUNT(*) INTO v_count FROM ledger_entry WHERE payment_id=2;
 IF v_count<>2 THEN RAISE_APPLICATION_ERROR(-20010,'Safe mode changed original ledger'); END IF;
 ROLLBACK;
 DBMS_OUTPUT.PUT_LINE('PASS: compiled safe mode rejects runtime simulation authorization');
EXCEPTION WHEN OTHERS THEN ROLLBACK; RAISE;
END;
/
