ALTER TABLE transactions DISABLE TRIGGER trg_transactions_set_updated_at;

INSERT INTO categories (id, user_id, name, transaction_type, icon, color, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000017', '00000000-0000-0000-0000-000000000001', 'Utilities', 'EXPENSE', 'bolt', '#0F766E', TIMESTAMP WITH TIME ZONE '2026-03-01 00:00:00+00', TIMESTAMP WITH TIME ZONE '2026-03-01 00:00:00+00'),
    ('00000000-0000-0000-0000-000000000018', '00000000-0000-0000-0000-000000000001', 'Dining', 'EXPENSE', 'utensils', '#C2410C', TIMESTAMP WITH TIME ZONE '2026-03-01 00:00:00+00', TIMESTAMP WITH TIME ZONE '2026-03-01 00:00:00+00'),
    ('00000000-0000-0000-0000-000000000019', '00000000-0000-0000-0000-000000000001', 'Health', 'EXPENSE', 'heart-pulse', '#BE123C', TIMESTAMP WITH TIME ZONE '2026-03-01 00:00:00+00', TIMESTAMP WITH TIME ZONE '2026-03-01 00:00:00+00'),
    ('00000000-0000-0000-0000-000000000027', '00000000-0000-0000-0000-000000000002', 'Utilities', 'EXPENSE', 'bolt', '#0F766E', TIMESTAMP WITH TIME ZONE '2026-03-01 00:00:00+00', TIMESTAMP WITH TIME ZONE '2026-03-01 00:00:00+00'),
    ('00000000-0000-0000-0000-000000000028', '00000000-0000-0000-0000-000000000002', 'Dining', 'EXPENSE', 'utensils', '#C2410C', TIMESTAMP WITH TIME ZONE '2026-03-01 00:00:00+00', TIMESTAMP WITH TIME ZONE '2026-03-01 00:00:00+00'),
    ('00000000-0000-0000-0000-000000000029', '00000000-0000-0000-0000-000000000002', 'Health', 'EXPENSE', 'heart-pulse', '#BE123C', TIMESTAMP WITH TIME ZONE '2026-03-01 00:00:00+00', TIMESTAMP WITH TIME ZONE '2026-03-01 00:00:00+00');

WITH demo_expenses AS (
    SELECT transaction.id,
           transaction.user_id,
           EXTRACT(MONTH FROM transaction.transaction_date)::integer AS month_number,
           row_number() OVER (
               PARTITION BY transaction.user_id, date_trunc('month', transaction.transaction_date)
               ORDER BY seed_transaction.sequence_number
           ) AS monthly_sequence_number
    FROM generate_series(1, 216) AS seed_transaction(sequence_number)
    JOIN transactions AS transaction
      ON transaction.id = md5('synthetic-seed-transaction-' || seed_transaction.sequence_number)::uuid
    WHERE transaction.transaction_type = 'EXPENSE'
)
UPDATE transactions AS transaction
SET category_id = CASE
                      WHEN demo_expenses.user_id = '00000000-0000-0000-0000-000000000001'::uuid THEN
                          CASE demo_expenses.month_number
                              WHEN 3 THEN CASE WHEN demo_expenses.monthly_sequence_number % 4 = 0 THEN '00000000-0000-0000-0000-000000000015'::uuid ELSE '00000000-0000-0000-0000-000000000014'::uuid END
                              WHEN 4 THEN CASE WHEN demo_expenses.monthly_sequence_number % 3 = 1 THEN '00000000-0000-0000-0000-000000000015'::uuid ELSE '00000000-0000-0000-0000-000000000016'::uuid END
                              WHEN 5 THEN CASE WHEN demo_expenses.monthly_sequence_number % 3 = 1 THEN '00000000-0000-0000-0000-000000000014'::uuid ELSE '00000000-0000-0000-0000-000000000016'::uuid END
                              WHEN 6 THEN CASE WHEN demo_expenses.monthly_sequence_number % 2 = 0 THEN '00000000-0000-0000-0000-000000000018'::uuid ELSE '00000000-0000-0000-0000-000000000017'::uuid END
                              WHEN 7 THEN CASE WHEN demo_expenses.monthly_sequence_number % 3 = 0 THEN '00000000-0000-0000-0000-000000000014'::uuid ELSE '00000000-0000-0000-0000-000000000019'::uuid END
                              ELSE CASE WHEN demo_expenses.monthly_sequence_number % 3 = 1 THEN '00000000-0000-0000-0000-000000000017'::uuid ELSE '00000000-0000-0000-0000-000000000016'::uuid END
                          END
                      ELSE CASE demo_expenses.month_number
                          WHEN 3 THEN CASE WHEN demo_expenses.monthly_sequence_number % 4 = 0 THEN '00000000-0000-0000-0000-000000000025'::uuid ELSE '00000000-0000-0000-0000-000000000024'::uuid END
                          WHEN 4 THEN CASE WHEN demo_expenses.monthly_sequence_number % 3 = 1 THEN '00000000-0000-0000-0000-000000000025'::uuid ELSE '00000000-0000-0000-0000-000000000026'::uuid END
                          WHEN 5 THEN CASE WHEN demo_expenses.monthly_sequence_number % 3 = 1 THEN '00000000-0000-0000-0000-000000000024'::uuid ELSE '00000000-0000-0000-0000-000000000026'::uuid END
                          WHEN 6 THEN CASE WHEN demo_expenses.monthly_sequence_number % 2 = 0 THEN '00000000-0000-0000-0000-000000000028'::uuid ELSE '00000000-0000-0000-0000-000000000027'::uuid END
                          WHEN 7 THEN CASE WHEN demo_expenses.monthly_sequence_number % 3 = 0 THEN '00000000-0000-0000-0000-000000000024'::uuid ELSE '00000000-0000-0000-0000-000000000029'::uuid END
                          ELSE CASE WHEN demo_expenses.monthly_sequence_number % 3 = 1 THEN '00000000-0000-0000-0000-000000000027'::uuid ELSE '00000000-0000-0000-0000-000000000026'::uuid END
                      END
                  END,
    amount = CASE demo_expenses.month_number
                 WHEN 3 THEN CASE WHEN demo_expenses.monthly_sequence_number % 4 = 0 THEN 30.0000 ELSE 90.0000 END
                 WHEN 4 THEN CASE WHEN demo_expenses.monthly_sequence_number % 3 = 1 THEN 75.0000 ELSE 45.0000 END
                 WHEN 5 THEN CASE WHEN demo_expenses.monthly_sequence_number % 3 = 1 THEN 40.0000 ELSE 100.0000 END
                 WHEN 6 THEN CASE WHEN demo_expenses.monthly_sequence_number % 2 = 0 THEN 25.0000 ELSE 60.0000 END
                 WHEN 7 THEN CASE WHEN demo_expenses.monthly_sequence_number % 3 = 0 THEN 40.0000 ELSE 80.0000 END
                 ELSE CASE WHEN demo_expenses.monthly_sequence_number % 3 = 1 THEN 40.0000 ELSE 100.0000 END
             END
FROM demo_expenses
WHERE transaction.id = demo_expenses.id;

UPDATE transactions
SET updated_at = TIMESTAMP WITH TIME ZONE '2026-03-01 00:00:00+00'
WHERE id IN (
    SELECT md5('synthetic-seed-transaction-' || sequence_number)::uuid
    FROM generate_series(1, 216) AS seed_transaction(sequence_number)
);

ALTER TABLE transactions ENABLE TRIGGER trg_transactions_set_updated_at;
