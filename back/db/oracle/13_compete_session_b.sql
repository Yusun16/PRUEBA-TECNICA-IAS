-- SQLcl/SQL*Plus session B, against the same dedicated schema as session A.
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
DECLARE
 v_out VARCHAR2(40); v_reason VARCHAR2(100); v_before NUMBER; v_after NUMBER;
BEGIN
 reconcile_payment('PAY-BX17',v_out,v_reason,v_before,v_after);
 DBMS_OUTPUT.PUT_LINE(v_out||'/'||v_reason||'/'||v_before||' -> '||v_after);
 -- Roll back the experiment, keeping the original demo intact.
 ROLLBACK;
EXCEPTION WHEN OTHERS THEN ROLLBACK; RAISE;
END;
/
