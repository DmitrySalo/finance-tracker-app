package ru.otus.financetracker.application.transactions;

public class TransactionCategoryTypeMismatchException extends RuntimeException {

    public TransactionCategoryTypeMismatchException() {
        super("Transaction type must match category type.");
    }
}
