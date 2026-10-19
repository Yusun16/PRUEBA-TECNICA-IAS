-- New writes only; legacy is not certified. Shared verification also runs in 14.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
DEFINE integrity_apply = 1
@@16_check_contract.sql
UNDEFINE integrity_apply
