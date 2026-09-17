CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    base_currency CHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_users_email_normalized CHECK (email = lower(email)),
    CONSTRAINT chk_users_email_not_blank CHECK (char_length(btrim(email)) > 0),
    CONSTRAINT chk_users_display_name_not_blank CHECK (char_length(btrim(display_name)) > 0),
    CONSTRAINT chk_users_base_currency_format CHECK (base_currency ~ '^[A-Z]{3}$')
);

CREATE UNIQUE INDEX uq_users_email ON users (email);

CREATE FUNCTION set_users_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_users_set_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION set_users_updated_at();
