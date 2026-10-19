-- SOLO para un usuario/esquema vacío de laboratorio. Ejecutar desde db/oracle.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
@@00_schema.sql
@@05_seed_demo.sql
@@01_support_migration.sql
@@03_reconcile_payment.sql
@@04_ledger_immutability.sql
-- SHOW ERRORS no provoca SQLERROR: fallar si existen objetos inválidos.
DECLARE v_errors NUMBER;
BEGIN
 SELECT COUNT(*) INTO v_errors FROM user_errors
 WHERE name IN ('RECONCILE_PAYMENT','TRG_LEDGER_IMMUTABLE');
 IF v_errors>0 THEN RAISE_APPLICATION_ERROR(-20011,'Compilation errors: inspect USER_ERRORS'); END IF;
END;
/
@@06_verify_procedure.sql
