CREATE TABLE categories (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    transaction_type VARCHAR(7) NOT NULL,
    icon VARCHAR(100) NOT NULL,
    color CHAR(7) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_categories_user_transaction_type_name UNIQUE (user_id, transaction_type, name),
    CONSTRAINT chk_categories_name_not_blank CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT chk_categories_transaction_type CHECK (transaction_type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT chk_categories_icon_not_blank CHECK (char_length(btrim(icon)) > 0),
    CONSTRAINT chk_categories_color_format CHECK (color ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE INDEX idx_categories_user_id ON categories (user_id);

CREATE FUNCTION set_categories_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_categories_set_updated_at
BEFORE UPDATE ON categories
FOR EACH ROW
EXECUTE FUNCTION set_categories_updated_at();
