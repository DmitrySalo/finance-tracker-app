package ru.otus.financetracker.infrastructure.csv;

import java.util.List;

public record CsvRecord(int lineNumber, int endLineNumber, List<String> values) {
}
