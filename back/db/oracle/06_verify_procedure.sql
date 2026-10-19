-- SQL*Plus/SQLcl, sobre el demo recién creado. Siempre revierte las pruebas.
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
DECLARE
 v_out VARCHAR2(40); v_reason VARCHAR2(100); v_before NUMBER; v_after NUMBER;
 v_count NUMBER; v_key VARCHAR2(64);
 PROCEDURE check_true(p_condition BOOLEAN,p_message VARCHAR2) IS
 BEGIN
   IF p_condition IS NULL OR NOT p_condition THEN RAISE_APPLICATION_ERROR(-20010,p_message); END IF;
 END;
BEGIN
 reconcile_payment('PAY-AX91',v_out,v_reason,v_before,v_after);
 check_true(v_out='RESOLVED' AND v_after=125000,'AX91 must be confirmed');
 reconcile_payment('PAY-BX17',v_out,v_reason,v_before,v_after);
 check_true(v_out='MANUAL_REVIEW' AND v_reason='DUPLICATE_DEBITS_REQUIRE_REVIEW','Default must not infer a single capture');
 SELECT COUNT(*) INTO v_count FROM ledger_entry WHERE payment_id=2;
 check_true(v_count=2,'Default policy must preserve ledger');
 -- Explicitly accept the fictional single-capture premise for this demo case only.
 reconcile_payment('PAY-BX17',v_out,v_reason,v_before,v_after,1);
 check_true(v_out='RESOLVED' AND v_before=178000 AND v_after=89000,'BX17 must be compensated');
 SELECT COUNT(*) INTO v_count FROM ledger_entry WHERE payment_id=2;
 check_true(v_count=3,'BX17 must have two debits and one reversal');
 reconcile_payment('PAY-BX17',v_out,v_reason,v_before,v_after);
 check_true(v_out='ALREADY_CONSISTENT','BX17 retry must be no-op');
 SELECT COUNT(*) INTO v_count FROM support_audit WHERE payment_id=2;
 check_true(v_count=2,'BX17 retry must not duplicate audit (one manual + one resolved)');
 reconcile_payment('PAY-CQ22',v_out,v_reason,v_before,v_after);
 check_true(v_out='MANUAL_REVIEW' AND v_after=0,'CQ22 must remain manual');
 SELECT evidence_key INTO v_key FROM support_audit WHERE payment_id=3;
 check_true(v_key='d370b764524d08df9570e7724b05ca1ffa5b82f98ced2d5cc1e0e928076266af','CQ22 fingerprint must match Java canonical vector');
 DBMS_OUTPUT.PUT_LINE('CQ22 evidence_key='||v_key);
 reconcile_payment('PAY-CQ22',v_out,v_reason,v_before,v_after);
 SELECT COUNT(*) INTO v_count FROM support_audit WHERE payment_id=3;
 check_true(v_count=1,'CQ22 retry must not duplicate audit');
 reconcile_payment('PAY-DK44',v_out,v_reason,v_before,v_after);
 check_true(v_out='ALREADY_CONSISTENT','DK44 must remain healthy');
 reconcile_payment('MISSING',v_out,v_reason,v_before,v_after);
 check_true(v_out='NOT_FOUND','Missing payment must be reported');
 ROLLBACK;
 DBMS_OUTPUT.PUT_LINE('PASS: Oracle sample cases and retries; changes rolled back');
EXCEPTION WHEN OTHERS THEN ROLLBACK; RAISE;
END;
/
