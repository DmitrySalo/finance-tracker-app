package ru.otus.financetracker.infrastructure.csv;

import java.io.PushbackReader;
import java.io.StringReader;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvRecordReaderTests {

    private final CsvRecordReader reader = new CsvRecordReader();

    @Test
    void shouldParseQuotedCommasEscapedQuotesAndEmbeddedNewlines() throws Exception {
        var input = new PushbackReader(new StringReader("name,note\r\nDinner,\"first line\r\nsecond \"\"quoted\"\" line\"\r\n"), 1);

        var header = reader.readRecord(input, 1);
        var record = reader.readRecord(input, header.endLineNumber() + 1);

        assertThat(header.values()).containsExactly("name", "note");
        assertThat(record.lineNumber()).isEqualTo(2);
        assertThat(record.endLineNumber()).isEqualTo(3);
        assertThat(record.values()).containsExactly("Dinner", "first line\r\nsecond \"quoted\" line");
    }
}
