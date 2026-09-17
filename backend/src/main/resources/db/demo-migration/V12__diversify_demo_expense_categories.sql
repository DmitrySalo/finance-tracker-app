ALTER TABLE transactions DISABLE TRIGGER trg_transactions_set_updated_at;

WITH demo_expenses AS (
    SELECT transaction.id,
           transaction.user_id,
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
                          CASE demo_expenses.monthly_sequence_number % 3
                              WHEN 1 THEN '00000000-0000-0000-0000-000000000014'::uuid
                              WHEN 2 THEN '00000000-0000-0000-0000-000000000015'::uuid
                              ELSE '00000000-0000-0000-0000-000000000016'::uuid
                          END
                      ELSE CASE demo_expenses.monthly_sequence_number % 3
                          WHEN 1 THEN '00000000-0000-0000-0000-000000000024'::uuid
                          WHEN 2 THEN '00000000-0000-0000-0000-000000000025'::uuid
                          ELSE '00000000-0000-0000-0000-000000000026'::uuid
                      END
                  END,
    amount = CASE demo_expenses.monthly_sequence_number % 3
                 WHEN 1 THEN 85.0000
                 WHEN 2 THEN 45.0000
                 ELSE 20.0000
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
