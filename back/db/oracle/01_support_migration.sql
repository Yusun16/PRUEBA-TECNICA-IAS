-- Ejecutar una vez, con escritores detenidos y luego de revisar datos existentes.
-- DDL Oracle hace commit implícito. No forma parte de una conciliación.
ALTER TABLE support_audit ADD (evidence_key VARCHAR2(64));
ALTER TABLE support_audit ADD CONSTRAINT uq_audit_evidence UNIQUE (payment_id, evidence_key);
-- Filas históricas pueden conservar NULL; las nuevas siempre llevan huella.
CREATE INDEX ix_provider_payment ON provider_event(payment_id);
CREATE INDEX ix_ledger_payment ON ledger_entry(payment_id);
-- Un reverso completo por débito: no se soportan reversos parciales.
-- Revisar duplicados existentes antes de crear; no limpiar evidencia automáticamente.
CREATE UNIQUE INDEX uq_ledger_reversal ON ledger_entry(related_entry_id);

-- Secuencias arrancan por encima de los IDs existentes; detener todos los escritores
-- durante la migración. Si ya existen secuencias compartidas, reutilizarlas y omitir estos bloques.
DECLARE
 v_next NUMBER;
BEGIN
 SELECT NVL(MAX(id),0)+1 INTO v_next FROM ledger_entry;
 EXECUTE IMMEDIATE 'CREATE SEQUENCE ledger_entry_seq START WITH ' || TO_CHAR(v_next,'FM9999999999999999999999999990');
 SELECT NVL(MAX(id),0)+1 INTO v_next FROM support_audit;
 EXECUTE IMMEDIATE 'CREATE SEQUENCE support_audit_seq START WITH ' || TO_CHAR(v_next,'FM9999999999999999999999999990');
END;
/
