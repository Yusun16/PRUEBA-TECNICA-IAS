-- SQLcl/SQL*Plus session A, dedicated demo schema. This script intentionally leaves the lock open.
SET AUTOCOMMIT OFF
SELECT id,external_ref,row_version FROM payment WHERE external_ref='PAY-BX17' FOR UPDATE;
PROMPT PAYMENT is locked. Run 13_compete_session_b.sql in session B.
PROMPT Hold this lock for at least 5 seconds to test timeout, then ROLLBACK here.
PROMPT To test successful waiting, COMMIT or ROLLBACK here before B reaches 5 seconds.
