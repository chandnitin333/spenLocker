package com.spendlocker.pdf;

import java.time.LocalDate;

public record ParsedReceipt(LocalDate date, Double amount, String vendor) {

    public boolean hasAnySignal() {
        return date != null || amount != null || (vendor != null && !vendor.isBlank());
    }
}
