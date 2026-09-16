CREATE TABLE budgets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    category_id UUID NOT NULL,
    category_transaction_type VARCHAR(7) NOT NULL DEFAULT 'EXPENSE',
    budget_month DATE NOT NULL,
    limit_amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_budgets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_budgets_expense_category FOREIGN KEY (user_id, category_id, category_transaction_type)
        REFERENCES categories (user_id, id, transaction_type),
    CONSTRAINT chk_budgets_expense_category CHECK (category_transaction_type = 'EXPENSE'),
    CONSTRAINT chk_budgets_month_first_day CHECK (budget_month = date_trunc('month', budget_month)::date),
    CONSTRAINT chk_budgets_limit_amount_positive CHECK (limit_amount > 0),
    CONSTRAINT chk_budgets_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT uq_budgets_user_category_month UNIQUE (user_id, category_id, budget_month)
);

CREATE INDEX idx_budgets_user_month_category
    ON budgets (user_id, budget_month, category_id);

CREATE FUNCTION set_budgets_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_budgets_set_updated_at
BEFORE UPDATE ON budgets
FOR EACH ROW
EXECUTE FUNCTION set_budgets_updated_at();
