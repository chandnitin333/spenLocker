package com.spendlocker.search;

import com.spendlocker.model.Document;
import com.spendlocker.model.Expense;
import com.spendlocker.model.Investment;

import java.util.List;

public record SearchResults(List<Expense> expenses, List<Document> documents, List<Investment> investments) {

    public boolean isEmpty() {
        return expenses.isEmpty() && documents.isEmpty() && investments.isEmpty();
    }
}
