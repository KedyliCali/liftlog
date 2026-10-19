package com.behlul.liftlog.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Writes CSV that Excel in a Turkish locale opens correctly with a double click:
 * <ul>
 *   <li>UTF-8 with a byte order mark, otherwise Excel guesses Windows-1254 and garbles ç, ğ, ş...</li>
 *   <li>semicolon separators and decimal commas, because Turkish Excel already uses the comma for decimals</li>
 *   <li>RFC 4180 quoting, so names containing ; " or line breaks stay in one cell</li>
 * </ul>
 * Rows go straight to the stream through a small buffer, so an export never builds one big string.
 * {@link #flush()} never closes the stream: a ZIP export writes several files into the same one.
 */
public final class CsvWriter {

    static final char SEPARATOR = ';';
    private static final String LINE_END = "\r\n";
    private static final char BYTE_ORDER_MARK = '﻿';

    private final Writer out;

    private CsvWriter(OutputStream stream) {
        this.out = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
    }

    /** Starts a CSV file on the stream: writes the byte order mark and the header row. */
    public static CsvWriter start(OutputStream stream, String... headers) {
        CsvWriter writer = new CsvWriter(stream);
        writer.write(String.valueOf(BYTE_ORDER_MARK));
        writer.row((Object[]) headers);
        return writer;
    }

    /** Null becomes an empty cell; BigDecimal gets a decimal comma; everything else uses toString. */
    public void row(Object... cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                line.append(SEPARATOR);
            }
            line.append(cell(cells[i]));
        }
        write(line.append(LINE_END).toString());
    }

    public void flush() {
        try {
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal number) {
            return decimal(number);
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        return text(value.toString());
    }

    /** 82.50 -> "82,5", 100.00 -> "100": trailing zeros are noise in a spreadsheet. */
    static String decimal(BigDecimal number) {
        return number.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    static String text(String value) {
        String safe = neutraliseFormula(value);
        boolean needsQuotes = safe.indexOf(SEPARATOR) >= 0 || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0
                || (!safe.isEmpty() && (Character.isWhitespace(safe.charAt(0))
                || Character.isWhitespace(safe.charAt(safe.length() - 1))));
        return needsQuotes ? '"' + safe.replace("\"", "\"\"") + '"' : safe;
    }

    /**
     * Text the user typed (a workout note, a food name) starting with = + - or @ would run as a formula
     * when the file is opened in Excel ("CSV injection"); a leading apostrophe keeps it plain text.
     */
    private static String neutraliseFormula(String value) {
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    private void write(String text) {
        try {
            out.write(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
