CREATE TABLE recurring_transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    category_id UUID NOT NULL,
    category_transaction_type VARCHAR(7) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    exchange_rate_to_base NUMERIC(19,8) NOT NULL,
    description VARCHAR(1000),
    transaction_type VARCHAR(7) NOT NULL,
    day_of_month INTEGER NOT NULL,
    start_date DATE NOT NULL,
    next_occurrence_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_recurring_transactions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_recurring_transactions_category_owner_and_type
        FOREIGN KEY (user_id, category_id, category_transaction_type)
        REFERENCES categories (user_id, id, transaction_type),
    CONSTRAINT chk_recurring_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_recurring_transactions_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_recurring_transactions_exchange_rate_positive CHECK (exchange_rate_to_base > 0),
    CONSTRAINT chk_recurring_transactions_type_matches_category CHECK (transaction_type = category_transaction_type),
    CONSTRAINT chk_recurring_transactions_day_of_month CHECK (day_of_month BETWEEN 1 AND 31)
);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_recurring_transaction
        FOREIGN KEY (recurring_transaction_id) REFERENCES recurring_transactions (id) ON DELETE SET NULL;

CREATE INDEX idx_recurring_transactions_active_next_occurrence
    ON recurring_transactions (active, next_occurrence_date);

CREATE TABLE recurring_transaction_occurrences (
    id UUID PRIMARY KEY,
    recurring_transaction_id UUID NOT NULL,
    occurrence_date DATE NOT NULL,
    transaction_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recurring_occurrences_rule FOREIGN KEY (recurring_transaction_id)
        REFERENCES recurring_transactions (id) ON DELETE CASCADE,
    CONSTRAINT fk_recurring_occurrences_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE,
    CONSTRAINT uq_recurring_occurrences_rule_date UNIQUE (recurring_transaction_id, occurrence_date),
    CONSTRAINT uq_recurring_occurrences_transaction UNIQUE (transaction_id)
);

CREATE FUNCTION set_recurring_transactions_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_recurring_transactions_set_updated_at
BEFORE UPDATE ON recurring_transactions
FOR EACH ROW
EXECUTE FUNCTION set_recurring_transactions_updated_at();
