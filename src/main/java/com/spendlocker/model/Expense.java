package com.spendlocker.model;

public class Expense {
    private long id;
    private String transactionDate;
    private double amount;
    private String category;
    private String merchantOrVendor;
    private PaymentMethod paymentMethod;
    private String notes;
    private Long documentId;
    private String createdAt;
    private String deletedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTransactionDate() { return transactionDate; }
    public void setTransactionDate(String transactionDate) { this.transactionDate = transactionDate; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getMerchantOrVendor() { return merchantOrVendor; }
    public void setMerchantOrVendor(String merchantOrVendor) { this.merchantOrVendor = merchantOrVendor; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }
}
