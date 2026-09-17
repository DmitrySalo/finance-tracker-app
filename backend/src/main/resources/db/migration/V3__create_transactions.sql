ALTER TABLE categories
    ADD CONSTRAINT uq_categories_user_id_transaction_type UNIQUE (user_id, id, transaction_type);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    category_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    exchange_rate_to_base NUMERIC(19,8) NOT NULL,
    transaction_date DATE NOT NULL,
    description VARCHAR(1000),
    transaction_type VARCHAR(7) NOT NULL,
    recurring_transaction_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_transactions_category_owner_and_type
        FOREIGN KEY (user_id, category_id, transaction_type)
        REFERENCES categories (user_id, id, transaction_type),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transactions_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_transactions_exchange_rate_to_base_positive CHECK (exchange_rate_to_base > 0),
    CONSTRAINT chk_transactions_transaction_type CHECK (transaction_type IN ('INCOME', 'EXPENSE'))
);

CREATE INDEX idx_transactions_user_transaction_date_id
    ON transactions (user_id, transaction_date DESC, id DESC);

CREATE INDEX idx_transactions_user_category_transaction_date
    ON transactions (user_id, category_id, transaction_date DESC);

CREATE FUNCTION set_transactions_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_transactions_set_updated_at
BEFORE UPDATE ON transactions
FOR EACH ROW
EXECUTE FUNCTION set_transactions_updated_at();
