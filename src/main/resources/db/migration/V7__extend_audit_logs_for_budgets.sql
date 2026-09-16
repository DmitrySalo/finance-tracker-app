ALTER TABLE audit_logs DROP CONSTRAINT chk_audit_logs_entity_type;

ALTER TABLE audit_logs
    ADD CONSTRAINT chk_audit_logs_entity_type CHECK (entity_type IN ('TRANSACTION', 'BUDGET'));
