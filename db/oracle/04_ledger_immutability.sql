-- Instalar después de revisar integraciones que escriben en ledger.
-- El usuario de aplicación no debe ser dueño del esquema ni poder desactivar triggers.
CREATE OR REPLACE TRIGGER trg_ledger_immutable
BEFORE UPDATE OR DELETE ON ledger_entry
BEGIN
 RAISE_APPLICATION_ERROR(-20001,'Ledger entries are immutable; append a linked reversal');
END;
/
SHOW ERRORS TRIGGER trg_ledger_immutable;
