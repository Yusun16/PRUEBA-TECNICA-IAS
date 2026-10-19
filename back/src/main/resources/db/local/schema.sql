CREATE TABLE payment (
 id NUMBER PRIMARY KEY, external_ref VARCHAR2(40) UNIQUE NOT NULL,
 status VARCHAR2(20) NOT NULL CHECK (status IN ('PENDING','CONFIRMED','REJECTED')),
 amount NUMBER(18,2) NOT NULL CHECK (amount > 0), updated_at TIMESTAMP NOT NULL,
 row_version NUMBER DEFAULT 0 NOT NULL
);
CREATE TABLE provider_event (
 id NUMBER PRIMARY KEY, payment_id NUMBER NOT NULL REFERENCES payment(id),
 provider_event_id VARCHAR2(60) UNIQUE NOT NULL,
 provider_status VARCHAR2(20) NOT NULL CHECK (provider_status IN ('APPROVED','DECLINED')),
 received_at TIMESTAMP NOT NULL
);
CREATE TABLE ledger_entry (
 id NUMBER PRIMARY KEY, payment_id NUMBER NOT NULL REFERENCES payment(id),
 entry_type VARCHAR2(20) NOT NULL CHECK (entry_type IN ('DEBIT','REVERSAL')),
 amount NUMBER(18,2) NOT NULL CHECK (amount > 0),
 related_entry_id NUMBER REFERENCES ledger_entry(id), created_at TIMESTAMP NOT NULL,
 CONSTRAINT ck_ledger_relation CHECK ((entry_type = 'DEBIT' AND related_entry_id IS NULL)
 OR (entry_type = 'REVERSAL' AND related_entry_id IS NOT NULL)),
 CONSTRAINT uq_ledger_reversal UNIQUE (related_entry_id)
);
CREATE TABLE support_audit (
 id NUMBER PRIMARY KEY, payment_id NUMBER NOT NULL REFERENCES payment(id),
 action VARCHAR2(40) NOT NULL, reason VARCHAR2(400) NOT NULL,
 evidence_key VARCHAR2(64) NOT NULL, created_at TIMESTAMP NOT NULL,
 CONSTRAINT uq_audit_evidence UNIQUE (payment_id, evidence_key)
);
CREATE SEQUENCE ledger_entry_seq START WITH 10000;
CREATE SEQUENCE support_audit_seq START WITH 10000;
CREATE INDEX ix_provider_payment ON provider_event(payment_id);
CREATE INDEX ix_ledger_payment ON ledger_entry(payment_id);
