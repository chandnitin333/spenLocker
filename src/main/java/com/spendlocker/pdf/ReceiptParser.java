package com.spendlocker.pdf;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort heuristic reader for receipt/invoice text extracted by PDFBox.
 * Looks for a "Total"-style amount, a nearby date, and takes the first
 * non-blank line as the vendor/business name.
 */
public class ReceiptParser {

    private static final Pattern AMOUNT_NEAR_TOTAL = Pattern.compile(
            "(?im)\\b(total|amount due|grand total|balance due)\\b[^0-9]{0,20}([$₹€£]?\\s?[0-9][0-9,]*\\.[0-9]{2})");
    private static final Pattern ANY_AMOUNT = Pattern.compile("[$₹€£]?\\s?[0-9][0-9,]*\\.[0-9]{2}");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy"),
            DateTimeFormatter.ofPattern("MMM d, yyyy"),
            DateTimeFormatter.ofPattern("MMMM d, yyyy"));
    private static final Pattern DATE_TOKEN = Pattern.compile(
            "\\b(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4}|[A-Za-z]{3,9}\\s+\\d{1,2},?\\s+\\d{4})\\b");

    public ParsedReceipt parse(String text) {
        if (text == null || text.isBlank()) {
            return new ParsedReceipt(null, null, null);
        }
        return new ParsedReceipt(findDate(text), findAmount(text), findVendor(text));
    }

    private Double findAmount(String text) {
        Matcher nearTotal = AMOUNT_NEAR_TOTAL.matcher(text);
        if (nearTotal.find()) {
            return parseAmount(nearTotal.group(2));
        }
        Matcher any = ANY_AMOUNT.matcher(text);
        Double largest = null;
        while (any.find()) {
            Double value = parseAmount(any.group());
            if (value != null && (largest == null || value > largest)) {
                largest = value;
            }
        }
        return largest;
    }

    private Double parseAmount(String raw) {
        try {
            return Double.parseDouble(raw.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate findDate(String text) {
        Matcher matcher = DATE_TOKEN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group().replace(",", "");
            for (DateTimeFormatter format : DATE_FORMATS) {
                try {
                    return LocalDate.parse(token, format);
                } catch (DateTimeParseException ignored) {
                    // try the next candidate format
                }
            }
        }
        return null;
    }

    private String findVendor(String text) {
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.length() >= 3 && trimmed.length() <= 60) {
                return trimmed;
            }
        }
        return null;
    }
}
