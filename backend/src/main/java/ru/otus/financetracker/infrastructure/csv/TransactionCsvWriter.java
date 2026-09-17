package ru.otus.financetracker.infrastructure.csv;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.springframework.stereotype.Component;
import ru.otus.financetracker.domain.transactions.Transaction;

@Component
public class TransactionCsvWriter {

    private static final List<String> HEADER = List.of(
            "categoryId", "amount", "currency", "exchangeRateToBase", "transactionDate", "description", "transactionType"
    );

    public void writeHeader(Writer writer) throws IOException {
        writeRecord(writer, HEADER);
    }

    public void writeTransactions(Writer writer, List<Transaction> transactions) throws IOException {
        for (Transaction transaction : transactions) {
            writeRecord(writer, List.of(
                    transaction.categoryId().toString(),
                    transaction.amount().toPlainString(),
                    transaction.currency(),
                    transaction.exchangeRateToBase().toPlainString(),
                    transaction.transactionDate().toString(),
                    transaction.description() == null ? "" : transaction.description(),
                    transaction.transactionType().name()
            ));
        }
    }

    private void writeRecord(Writer writer, List<String> values) throws IOException {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                writer.write(',');
            }
            writer.write(escape(values.get(index)));
        }
        writer.write("\r\n");
    }

    private String escape(String value) {
        String protectedValue = startsSpreadsheetFormula(value) ? "'" + value : value;
        if (protectedValue.indexOf(',') < 0 && protectedValue.indexOf('"') < 0
                && protectedValue.indexOf('\r') < 0 && protectedValue.indexOf('\n') < 0) {
            return protectedValue;
        }
        return '"' + protectedValue.replace("\"", "\"\"") + '"';
    }

    private boolean startsSpreadsheetFormula(String value) {
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index < value.length() && switch (value.charAt(index)) {
            case '=', '+', '-', '@' -> true;
            default -> false;
        };
    }
}
