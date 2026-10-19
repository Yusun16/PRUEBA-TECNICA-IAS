-- Writers must lock PAYMENT first. These guards also cover direct DML by non-owner users.
-- One payment per operation is recommended; multi-payment writers must use ascending IDs.
CREATE OR REPLACE TRIGGER trg_provider_parent_lock
BEFORE INSERT OR UPDATE OR DELETE ON provider_event
FOR EACH ROW
DECLARE
 v_first NUMBER; v_second NUMBER; v_id NUMBER;
BEGIN
 IF INSERTING THEN v_first:=:NEW.payment_id;
 ELSIF DELETING THEN v_first:=:OLD.payment_id;
 ELSE v_first:=LEAST(:OLD.payment_id,:NEW.payment_id); v_second:=GREATEST(:OLD.payment_id,:NEW.payment_id);
 END IF;
 SELECT id INTO v_id FROM payment WHERE id=v_first FOR UPDATE WAIT 5;
 IF v_second IS NOT NULL AND v_second<>v_first THEN
   SELECT id INTO v_id FROM payment WHERE id=v_second FOR UPDATE WAIT 5;
 END IF;
END;
/
SHOW ERRORS TRIGGER trg_provider_parent_lock;

-- Validate relations AFTER STATEMENT to avoid the mutating-table error ORA-04091.
CREATE OR REPLACE TRIGGER trg_ledger_insert_guard
FOR INSERT ON ledger_entry
COMPOUND TRIGGER
 TYPE reversal_record IS RECORD (payment_id NUMBER,amount NUMBER,related_id NUMBER);
 TYPE reversal_rows IS TABLE OF reversal_record INDEX BY PLS_INTEGER;
 v_reversals reversal_rows;
 v_size PLS_INTEGER:=0;
 BEFORE EACH ROW IS
   v_payment NUMBER;
 BEGIN
   SELECT id INTO v_payment FROM payment WHERE id=:NEW.payment_id FOR UPDATE WAIT 5;
   IF :NEW.entry_type='REVERSAL' THEN
     v_size:=v_size+1;
     v_reversals(v_size).payment_id:=:NEW.payment_id;
     v_reversals(v_size).amount:=:NEW.amount;
     v_reversals(v_size).related_id:=:NEW.related_entry_id;
   END IF;
 END BEFORE EACH ROW;
 AFTER STATEMENT IS
   v_valid NUMBER;
   v_payment NUMBER; v_amount NUMBER; v_related NUMBER;
 BEGIN
   FOR i IN 1..v_size LOOP
     v_payment:=v_reversals(i).payment_id;
     v_amount:=v_reversals(i).amount;
     v_related:=v_reversals(i).related_id;
     SELECT COUNT(*) INTO v_valid FROM ledger_entry
     WHERE id=v_related AND payment_id=v_payment
       AND amount=v_amount AND entry_type='DEBIT' AND related_entry_id IS NULL;
     IF v_valid<>1 THEN RAISE_APPLICATION_ERROR(-20002,'Invalid full reversal relation'); END IF;
   END LOOP;
 END AFTER STATEMENT;
END;
/
SHOW ERRORS TRIGGER trg_ledger_insert_guard;
