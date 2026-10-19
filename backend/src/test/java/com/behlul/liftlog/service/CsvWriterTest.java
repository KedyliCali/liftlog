package com.behlul.liftlog.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    void startsWithUtf8ByteOrderMarkThenHeaderRow() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CsvWriter.start(bytes, "Tarih", "Egzersiz").flush();

        byte[] written = bytes.toByteArray();
        assertThat(written).startsWith(0xEF, 0xBB, 0xBF);
        assertThat(new String(written, StandardCharsets.UTF_8)).isEqualTo("﻿Tarih;Egzersiz\r\n");
    }

    @Test
    void writesTurkishCharactersAsUtf8() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CsvWriter csv = CsvWriter.start(bytes, "Yemek");
        csv.row("Şehriyeli tavuk çorbası, ığö");
        csv.flush();

        assertThat(bytes.toString(StandardCharsets.UTF_8)).endsWith("Şehriyeli tavuk çorbası, ığö\r\n");
    }

    @Test
    void separatesWithSemicolonsAndLeavesNullCellsEmpty() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CsvWriter csv = CsvWriter.start(bytes, "a", "b", "c", "d");
        csv.row("2026-10-19", null, 8, new BigDecimal("7.5"));
        csv.flush();

        assertThat(bytes.toString(StandardCharsets.UTF_8)).endsWith("\r\n2026-10-19;;8;7,5\r\n");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "82.50, '82,5'",
            "100.00, '100'",
            "0.0, '0'",
            "116.67, '116,67'",
            "1E+2, '100'",     // never scientific notation
            "-2.25, '-2,25'",  // numbers are not mistaken for formulas
    })
    void writesDecimalsWithACommaAndNoTrailingZeros(String number, String expected) {
        assertThat(CsvWriter.cell(new BigDecimal(number))).isEqualTo(expected);
    }

    @Test
    void quotesCellsContainingSeparatorsQuotesOrLineBreaks() {
        assertThat(CsvWriter.cell("Bench; ağır")).isEqualTo("\"Bench; ağır\"");
        assertThat(CsvWriter.cell("The \"big\" one")).isEqualTo("\"The \"\"big\"\" one\"");
        assertThat(CsvWriter.cell("line one\nline two")).isEqualTo("\"line one\nline two\"");
        assertThat(CsvWriter.cell("windows\r\nbreak")).isEqualTo("\"windows\r\nbreak\"");
        assertThat(CsvWriter.cell(" padded ")).isEqualTo("\" padded \"");
        assertThat(CsvWriter.cell("plain, with comma")).isEqualTo("plain, with comma");
    }

    @Test
    void neutralisesTextThatExcelWouldRunAsAFormula() {
        assertThat(CsvWriter.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(CsvWriter.cell("+90 kg")).isEqualTo("'+90 kg");
        assertThat(CsvWriter.cell("-")).isEqualTo("'-");
        assertThat(CsvWriter.cell("@home")).isEqualTo("'@home");
        assertThat(CsvWriter.cell("Squat")).isEqualTo("Squat");
    }

    @Test
    void aMultiLineCellStaysOneRecord() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CsvWriter csv = CsvWriter.start(bytes, "note", "n");
        csv.row("a;b\n\"c\"", 1);
        csv.flush();

        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .isEqualTo("﻿note;n\r\n\"a;b\n\"\"c\"\"\";1\r\n");
    }

    @Test
    void flushDoesNotCloseTheStream() {
        ClosingSpy stream = new ClosingSpy();
        CsvWriter.start(stream, "a").flush();

        assertThat(stream.closed).isFalse();
    }

    private static final class ClosingSpy extends OutputStream {
        private boolean closed;

        @Override
        public void write(int b) {
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }
    }
}
