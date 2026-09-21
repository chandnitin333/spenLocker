package com.spendlocker.pdf;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptParserTest {

    private final ReceiptParser parser = new ReceiptParser();

    @Test
    void findsTotalAmountNearTheWordTotal() {
        String text = """
            FreshMart Grocery
            123 Main Street

            Milk            3.50
            Bread           2.75
            Eggs            4.20
            Total:         10.45
            Thank you for shopping!
            """;
        ParsedReceipt result = parser.parse(text);
        assertEquals(10.45, result.amount(), 0.001);
    }

    @Test
    void findsIsoDateInText() {
        String text = "Invoice Date: 2025-04-12\nAmount Due: 250.00";
        ParsedReceipt result = parser.parse(text);
        assertEquals(LocalDate.of(2025, 4, 12), result.date());
    }

    @Test
    void takesFirstNonBlankLineAsVendor() {
        String text = "\n\nAcme Hardware Store\n42 Elm Street\nTotal: 19.99";
        ParsedReceipt result = parser.parse(text);
        assertEquals("Acme Hardware Store", result.vendor());
    }

    @Test
    void blankTextYieldsNoSignal() {
        ParsedReceipt result = parser.parse("");
        assertFalse(result.hasAnySignal());
    }

    @Test
    void fallsBackToLargestAmountWhenNoTotalKeyword() {
        String text = "Item A   5.00\nItem B   99.99\nItem C   2.00";
        ParsedReceipt result = parser.parse(text);
        assertEquals(99.99, result.amount(), 0.001);
    }
}
