-- SOLO para un usuario/esquema vacío de laboratorio. Ejecutar desde db/oracle.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
@@00_schema.sql
@@05_seed_demo.sql
@@07_preflight.sql
@@01_support_migration.sql
@@08_integrity_constraints.sql
ALTER SESSION SET PLSQL_CCFLAGS='pagoclaro_demo:TRUE';
@@03_reconcile_payment.sql
ALTER SESSION SET PLSQL_CCFLAGS='pagoclaro_demo:FALSE';
@@04_ledger_immutability.sql
@@09_write_guards.sql
-- SHOW ERRORS no provoca SQLERROR: fallar si existen objetos inválidos.
DECLARE v_errors NUMBER;
BEGIN
 SELECT COUNT(*) INTO v_errors FROM user_errors
 WHERE name IN ('RECONCILE_PAYMENT','TRG_LEDGER_IMMUTABLE','TRG_PROVIDER_PARENT_LOCK','TRG_LEDGER_INSERT_GUARD')
   AND attribute='ERROR';
 IF v_errors>0 THEN RAISE_APPLICATION_ERROR(-20011,'Compilation errors: inspect USER_ERRORS'); END IF;
END;
/
@@06_verify_procedure.sql
@@10_verify_guards.sql
@@11_verify_rollback.sql
-- Tests finished: leave the installed procedure in safe mode too.
@@03_reconcile_payment.sql
@@18_verify_safe_policy.sql
@@14_postcheck.sql
