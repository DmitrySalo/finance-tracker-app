package ru.otus.financetracker.application.transactions.imports;

import java.util.List;

public class CsvImportValidationException extends RuntimeException {

    private final List<ImportValidationViolation> violations;

    public CsvImportValidationException(List<ImportValidationViolation> violations) {
        this.violations = List.copyOf(violations);
    }

    public List<ImportValidationViolation> violations() {
        return violations;
    }
}
