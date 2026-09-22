package com.spendlocker.model;

public class FixedDeposit {
    private long id;
    private String depositor;
    private String bank;
    private String fdNumber;
    private double principal;
    private double ratePercent;
    private int tenureValue;
    private TenureUnit tenureUnit = TenureUnit.MONTHS;
    private Compounding compounding = Compounding.QUARTERLY;
    private InterestPayout payout = InterestPayout.CUMULATIVE;
    private String startDate;
    private String maturityDate;
    private String nominee;
    private String notes;
    private String deletedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getDepositor() { return depositor; }
    public void setDepositor(String depositor) { this.depositor = depositor; }

    public String getBank() { return bank; }
    public void setBank(String bank) { this.bank = bank; }

    public String getFdNumber() { return fdNumber; }
    public void setFdNumber(String fdNumber) { this.fdNumber = fdNumber; }

    public double getPrincipal() { return principal; }
    public void setPrincipal(double principal) { this.principal = principal; }

    public double getRatePercent() { return ratePercent; }
    public void setRatePercent(double ratePercent) { this.ratePercent = ratePercent; }

    public int getTenureValue() { return tenureValue; }
    public void setTenureValue(int tenureValue) { this.tenureValue = tenureValue; }

    public TenureUnit getTenureUnit() { return tenureUnit; }
    public void setTenureUnit(TenureUnit tenureUnit) { this.tenureUnit = tenureUnit; }

    public Compounding getCompounding() { return compounding; }
    public void setCompounding(Compounding compounding) { this.compounding = compounding; }

    public InterestPayout getPayout() { return payout; }
    public void setPayout(InterestPayout payout) { this.payout = payout; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getMaturityDate() { return maturityDate; }
    public void setMaturityDate(String maturityDate) { this.maturityDate = maturityDate; }

    public String getNominee() { return nominee; }
    public void setNominee(String nominee) { this.nominee = nominee; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }
}
