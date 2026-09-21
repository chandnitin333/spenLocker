package com.spendlocker.model;

public class RecurringExpense {
    private long id;
    private String category;
    private double amount;
    private String merchantOrVendor;
    private PaymentMethod paymentMethod;
    private String notes;
    private RecurrenceFrequency frequency;
    private String nextDueDate;
    private boolean active = true;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getMerchantOrVendor() { return merchantOrVendor; }
    public void setMerchantOrVendor(String merchantOrVendor) { this.merchantOrVendor = merchantOrVendor; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public RecurrenceFrequency getFrequency() { return frequency; }
    public void setFrequency(RecurrenceFrequency frequency) { this.frequency = frequency; }

    public String getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(String nextDueDate) { this.nextDueDate = nextDueDate; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
