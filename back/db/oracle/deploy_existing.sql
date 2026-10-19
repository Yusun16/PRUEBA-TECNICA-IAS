-- Existing database: maintenance window, backup and 07_preflight review are mandatory prerequisites.
-- Never loads/replaces demo data. Re-run after investigating any partial DDL failure.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
@@07_preflight.sql
@@01_support_migration.sql
@@08_integrity_constraints.sql
@@04_ledger_immutability.sql
@@09_write_guards.sql
ALTER SESSION SET PLSQL_CCFLAGS='pagoclaro_demo:FALSE';
@@03_reconcile_payment.sql
@@14_postcheck.sql
