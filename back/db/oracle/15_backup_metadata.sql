-- Read-only DDL extraction. Store the spool outside the repository and protect access to it.
-- Does not back up data: arrange an Oracle backup/export separately before migration.
SET LONG 2000000
SET LONGCHUNKSIZE 2000000
SET PAGESIZE 0
SET LINESIZE 32767
SET TRIMSPOOL ON
SET FEEDBACK OFF
BEGIN
 DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM,'SQLTERMINATOR',TRUE);
END;
/
-- Required argument: absolute protected directory OUTSIDE this repository.
-- Required second argument: absolute repository root, used only to reject an internal destination.
-- Example: @15_backup_metadata.sql D:/ProtectedBackups/PagoClaro/metadata-before.sql "C:/PRUEBA TECNICA IAS"
-- SQL*Plus writes on the client; DBA must check path/access before invocation.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
DECLARE
 v_destination VARCHAR2(4000):=LOWER(REPLACE('&1',CHR(92),'/'));
 v_repository VARCHAR2(4000):=RTRIM(LOWER(REPLACE('&2',CHR(92),'/')),'/');
BEGIN
 IF NOT REGEXP_LIKE(v_destination,'^([a-z]:/|/)')
    OR NOT REGEXP_LIKE(v_repository,'^([a-z]:/|/)')
    OR INSTR(v_destination,'..')>0 OR INSTR(v_repository,'..')>0
    OR v_repository IS NULL OR v_destination IS NULL
    OR SUBSTR(v_destination,1,LENGTH(v_repository)+1)=v_repository||'/' THEN
   RAISE_APPLICATION_ERROR(-20021,'Use absolute backup path outside the repository; no parent traversal');
 END IF;
END;
/
SPOOL "&1"
SELECT DBMS_METADATA.GET_DDL('TABLE',table_name) FROM user_tables
WHERE table_name IN ('PAYMENT','PROVIDER_EVENT','LEDGER_ENTRY','SUPPORT_AUDIT');
SELECT DBMS_METADATA.GET_DDL('INDEX',index_name) FROM user_indexes
WHERE table_name IN ('PAYMENT','PROVIDER_EVENT','LEDGER_ENTRY','SUPPORT_AUDIT') AND generated='N';
SELECT DBMS_METADATA.GET_DDL('SEQUENCE',sequence_name) FROM user_sequences
WHERE sequence_name IN ('LEDGER_ENTRY_SEQ','SUPPORT_AUDIT_SEQ');
SELECT DBMS_METADATA.GET_DDL('TRIGGER',trigger_name) FROM user_triggers
WHERE table_name IN ('PAYMENT','PROVIDER_EVENT','LEDGER_ENTRY','SUPPORT_AUDIT');
SELECT DBMS_METADATA.GET_DDL('PROCEDURE',object_name) FROM user_objects
WHERE object_type='PROCEDURE' AND object_name='RECONCILE_PAYMENT';
SPOOL OFF
UNDEFINE 1
UNDEFINE 2
SET FEEDBACK ON
SET PAGESIZE 50
