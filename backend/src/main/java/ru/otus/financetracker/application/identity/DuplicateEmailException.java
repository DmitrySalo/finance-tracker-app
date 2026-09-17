package ru.otus.financetracker.application.identity;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(Throwable cause) {
        super(cause);
    }
}
