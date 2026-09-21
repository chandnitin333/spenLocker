package com.spendlocker.model;

public class Investment {
    private long id;
    private String assetName;
    private String assetTicker;
    private String investmentType;
    private String purchaseDate;
    private double principalAmount;
    private double currentUnitPrice;
    private double totalUnits;
    private String notes;
    private String deletedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }

    public String getAssetTicker() { return assetTicker; }
    public void setAssetTicker(String assetTicker) { this.assetTicker = assetTicker; }

    public String getInvestmentType() { return investmentType; }
    public void setInvestmentType(String investmentType) { this.investmentType = investmentType; }

    public String getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(String purchaseDate) { this.purchaseDate = purchaseDate; }

    public double getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(double principalAmount) { this.principalAmount = principalAmount; }

    public double getCurrentUnitPrice() { return currentUnitPrice; }
    public void setCurrentUnitPrice(double currentUnitPrice) { this.currentUnitPrice = currentUnitPrice; }

    public double getTotalUnits() { return totalUnits; }
    public void setTotalUnits(double totalUnits) { this.totalUnits = totalUnits; }

    public double getCurrentTotalValue() { return currentUnitPrice * totalUnits; }

    public double getRoiPercent() {
        return principalAmount == 0 ? 0.0 : ((getCurrentTotalValue() - principalAmount) / principalAmount) * 100.0;
    }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }
}
