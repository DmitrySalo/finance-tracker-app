CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    before_state JSONB,
    after_state JSONB,
    CONSTRAINT fk_audit_logs_actor_user FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT chk_audit_logs_entity_type CHECK (entity_type IN ('TRANSACTION')),
    CONSTRAINT chk_audit_logs_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT chk_audit_logs_state_for_action CHECK (
        (action = 'CREATE' AND before_state IS NULL AND after_state IS NOT NULL)
        OR (action = 'UPDATE' AND before_state IS NOT NULL AND after_state IS NOT NULL)
        OR (action = 'DELETE' AND before_state IS NOT NULL AND after_state IS NULL)
    )
);

CREATE INDEX idx_audit_logs_actor_user_occurred_at
    ON audit_logs (actor_user_id, occurred_at DESC);

CREATE INDEX idx_audit_logs_entity_type_entity_id_occurred_at
    ON audit_logs (entity_type, entity_id, occurred_at DESC);
