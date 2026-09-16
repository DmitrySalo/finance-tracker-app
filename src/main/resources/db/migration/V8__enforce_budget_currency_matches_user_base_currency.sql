DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM budgets
        JOIN users ON users.id = budgets.user_id
        WHERE budgets.currency <> users.base_currency
    ) THEN
        RAISE EXCEPTION 'Cannot enforce budget currency invariant: existing budget currency differs from owner base currency.';
    END IF;
END;
$$;

ALTER TABLE users
    ADD CONSTRAINT uq_users_id_base_currency UNIQUE (id, base_currency);

ALTER TABLE budgets
    ADD CONSTRAINT fk_budgets_user_base_currency
        FOREIGN KEY (user_id, currency) REFERENCES users (id, base_currency);
