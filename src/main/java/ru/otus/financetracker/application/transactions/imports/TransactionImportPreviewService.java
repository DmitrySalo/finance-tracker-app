package ru.otus.financetracker.application.transactions.imports;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.otus.financetracker.application.categories.CategoryRepository;
import ru.otus.financetracker.configuration.ApplicationProperties;
import ru.otus.financetracker.domain.categories.TransactionType;
import ru.otus.financetracker.infrastructure.csv.CsvFormatException;
import ru.otus.financetracker.infrastructure.csv.CsvRecord;
import ru.otus.financetracker.infrastructure.csv.CsvRecordReader;

@Service
public class TransactionImportPreviewService {

    private static final Set<String> REQUIRED_COLUMNS = Set.of(
            "categoryId", "amount", "currency", "exchangeRateToBase", "transactionDate", "transactionType");
    private static final Set<String> ALLOWED_COLUMNS = Set.of(
            "categoryId", "amount", "currency", "exchangeRateToBase", "transactionDate", "description", "transactionType");

    private final CsvRecordReader csvRecordReader;
    private final CategoryRepository categoryRepository;
    private final ApplicationProperties applicationProperties;

    public TransactionImportPreviewService(CsvRecordReader csvRecordReader, CategoryRepository categoryRepository,
                                           ApplicationProperties applicationProperties) {
        this.csvRecordReader = csvRecordReader;
        this.categoryRepository = categoryRepository;
        this.applicationProperties = applicationProperties;
    }

    public TransactionImportPreview preview(UUID userId, MultipartFile file, Map<String, String> columns) {
        validateFile(file);
        try (var input = new PushbackReader(new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)), 1)) {
            CsvRecord header = csvRecordReader.readRecord(input, 1);
            if (header == null) {
                throw invalid("file", "REQUIRED", "CSV file must contain a header.");
            }
            Map<String, Integer> mapping = validateMapping(columns, header.values());
            return previewRows(userId, input, header.endLineNumber() + 1, mapping);
        } catch (IOException | CsvFormatException exception) {
            throw invalid("file", "INVALID_FORMAT", "CSV file is invalid.");
        }
    }

    private TransactionImportPreview previewRows(UUID userId, PushbackReader reader, int nextLine, Map<String, Integer> mapping)
            throws IOException {
        List<TransactionImportPreview.Row> rows = new ArrayList<>();
        List<TransactionImportPreview.LineError> errors = new ArrayList<>();
        int rowCount = 0;
        CsvRecord record;
        while ((record = csvRecordReader.readRecord(reader, nextLine)) != null) {
            if (++rowCount > applicationProperties.limits().maxCsvRows()) {
                throw invalid("file", "MAX_ROWS", "CSV file exceeds the allowed row limit.");
            }
            nextLine = record.endLineNumber() + 1;
            Map<String, String> values = values(record, mapping);
            rows.add(new TransactionImportPreview.Row(record.lineNumber(), values.get("categoryId"), values.get("amount"),
                    values.get("currency"), values.get("exchangeRateToBase"), values.get("transactionDate"),
                    values.get("description"), values.get("transactionType")));
            validate(userId, record.lineNumber(), values, errors);
        }
        return new TransactionImportPreview(List.copyOf(rows), List.copyOf(errors));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("file", "REQUIRED", "CSV file must not be empty.");
        }
        if (file.getSize() > applicationProperties.limits().maxCsvFileSize().toBytes()) {
            throw new CsvImportPayloadTooLargeException();
        }
    }

    private Map<String, Integer> validateMapping(Map<String, String> columns, List<String> header) {
        if (columns == null || columns.isEmpty() || !ALLOWED_COLUMNS.containsAll(columns.keySet())
                || !columns.keySet().containsAll(REQUIRED_COLUMNS)) {
            throw invalid("mapping", "INVALID", "Column mapping is invalid.");
        }
        Map<String, Integer> sourceColumns = new HashMap<>();
        for (int index = 0; index < header.size(); index++) {
            if (header.get(index).isBlank() || sourceColumns.putIfAbsent(header.get(index), index) != null) {
                throw invalid("file", "INVALID_HEADER", "CSV header contains blank or duplicate columns.");
            }
        }
        Map<String, Integer> mapping = new HashMap<>();
        Set<String> usedHeaders = new HashSet<>();
        for (var entry : columns.entrySet()) {
            Integer index = sourceColumns.get(entry.getValue());
            if (entry.getValue() == null || entry.getValue().isBlank() || index == null || !usedHeaders.add(entry.getValue())) {
                throw invalid("mapping", "INVALID", "Column mapping is invalid.");
            }
            mapping.put(entry.getKey(), index);
        }
        return mapping;
    }

    private Map<String, String> values(CsvRecord record, Map<String, Integer> mapping) {
        Map<String, String> values = new HashMap<>();
        for (var entry : mapping.entrySet()) {
            values.put(entry.getKey(), entry.getValue() < record.values().size() ? record.values().get(entry.getValue()) : "");
        }
        return values;
    }

    private void validate(UUID userId, int lineNumber, Map<String, String> values, List<TransactionImportPreview.LineError> errors) {
        validateCategory(userId, lineNumber, values.get("categoryId"), values.get("transactionType"), errors);
        validateDecimal(lineNumber, "amount", values.get("amount"), 15, 4, errors);
        validateCurrency(lineNumber, values.get("currency"), errors);
        validateDecimal(lineNumber, "exchangeRateToBase", values.get("exchangeRateToBase"), 11, 8, errors);
        validateDate(lineNumber, values.get("transactionDate"), errors);
        validateType(lineNumber, values.get("transactionType"), errors);
        if (values.containsKey("description") && values.get("description").length() > 1000) {
            addError(errors, lineNumber, "description", "SIZE", "Description must not exceed 1000 characters.");
        }
    }

    private void validateCategory(UUID userId, int lineNumber, String categoryId, String transactionType,
                                  List<TransactionImportPreview.LineError> errors) {
        try {
            UUID id = UUID.fromString(categoryId);
            categoryRepository.findByIdAndUserId(id, userId).ifPresentOrElse(category -> {
                if (!category.transactionType().name().equals(transactionType)) {
                    addError(errors, lineNumber, "transactionType", "MISMATCH", "Transaction type must match category type.");
                }
            }, () -> addError(errors, lineNumber, "categoryId", "NOT_FOUND", "Category is unavailable."));
        } catch (IllegalArgumentException | NullPointerException exception) {
            addError(errors, lineNumber, "categoryId", "UUID", "Category ID must be a UUID.");
        }
    }

    private void validateDecimal(int lineNumber, String field, String value, int integerDigits, int fractionDigits,
                                 List<TransactionImportPreview.LineError> errors) {
        try {
            BigDecimal decimal = new BigDecimal(value);
            if (decimal.signum() <= 0 || decimal.precision() - decimal.scale() > integerDigits || decimal.scale() > fractionDigits) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException | NullPointerException exception) {
            addError(errors, lineNumber, field, "INVALID", "Must be a positive decimal with allowed precision.");
        }
    }

    private void validateCurrency(int lineNumber, String value, List<TransactionImportPreview.LineError> errors) {
        if (value == null || !value.matches("[A-Z]{3}")) {
            addError(errors, lineNumber, "currency", "PATTERN", "Currency must be a three-letter uppercase code.");
        }
    }

    private void validateDate(int lineNumber, String value, List<TransactionImportPreview.LineError> errors) {
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            addError(errors, lineNumber, "transactionDate", "DATE", "Transaction date must use YYYY-MM-DD.");
        }
    }

    private void validateType(int lineNumber, String value, List<TransactionImportPreview.LineError> errors) {
        try {
            TransactionType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            addError(errors, lineNumber, "transactionType", "ENUM", "Transaction type is invalid.");
        }
    }

    private void addError(List<TransactionImportPreview.LineError> errors, int lineNumber, String field,
                          String code, String message) {
        errors.add(new TransactionImportPreview.LineError(lineNumber, field, code, message));
    }

    private CsvImportValidationException invalid(String field, String code, String message) {
        return new CsvImportValidationException(List.of(new ImportValidationViolation(field, code, message)));
    }
}
