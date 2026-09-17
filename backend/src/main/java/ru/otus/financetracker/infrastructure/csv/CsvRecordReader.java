package ru.otus.financetracker.infrastructure.csv;

import java.io.IOException;
import java.io.PushbackReader;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/** Parses comma-separated records with RFC 4180 quoting and embedded newlines. */
@Component
public class CsvRecordReader {

    public CsvRecord readRecord(PushbackReader reader, int lineNumber) throws IOException {
        List<String> values = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean quoteClosed = false;
        boolean hasCharacters = false;
        int currentLine = lineNumber;

        for (int current; (current = reader.read()) != -1;) {
            char character = (char) current;
            hasCharacters = true;
            if (quoted) {
                if (character == '"') {
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        quoted = false;
                        quoteClosed = true;
                        if (next != -1) {
                            reader.unread(next);
                        }
                    }
                } else {
                    field.append(character);
                    if (character == '\n') {
                        currentLine++;
                    }
                }
                continue;
            }
            if (quoteClosed && character != ',' && character != '\r' && character != '\n') {
                throw new CsvFormatException();
            }
            quoteClosed = false;
            if (character == ',') {
                values.add(field.toString());
                field.setLength(0);
            } else if (character == '"') {
                if (!field.isEmpty()) {
                    throw new CsvFormatException();
                }
                quoted = true;
            } else if (character == '\r' || character == '\n') {
                values.add(field.toString());
                if (character == '\r') {
                    int next = reader.read();
                    if (next != '\n' && next != -1) {
                        reader.unread(next);
                    }
                }
                return new CsvRecord(lineNumber, currentLine, List.copyOf(values));
            } else {
                field.append(character);
            }
        }
        if (quoted) {
            throw new CsvFormatException();
        }
        if (!hasCharacters) {
            return null;
        }
        values.add(field.toString());
        return new CsvRecord(lineNumber, currentLine, List.copyOf(values));
    }
}
