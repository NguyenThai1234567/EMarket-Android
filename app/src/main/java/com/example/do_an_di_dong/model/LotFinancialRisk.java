package com.example.do_an_di_dong.model;

import com.google.gson.annotations.SerializedName;

public class LotFinancialRisk {
    @SerializedName(value = "productId", alternate = {"ProductId", "product_id"})
    private int productId;

    @SerializedName(value = "lotCode", alternate = {"LotCode", "batchNumber", "BatchNumber", "lotId", "LotId", "lot_id"})
    private String lotCode;

    @SerializedName(value = "productName", alternate = {"ProductName", "product_name", "name", "Name"})
    private String productName;

    @SerializedName(value = "expiryDate", alternate = {"ExpiryDate", "expiredAt", "ExpiredAt"})
    private String expiryDate;

    @SerializedName(value = "daysToExpiry", alternate = {"DaysToExpiry", "days_to_expiry"})
    private Integer daysToExpiry;

    @SerializedName(value = "provisionValue", alternate = {"ProvisionValue", "riskValue", "RiskValue", "provision_value"})
    private double riskValue;

    @SerializedName(value = "lotStatus", alternate = {"LotStatus", "status", "Status", "lot_status"})
    private String status; // DANGER, WARNING, SAFE, EXPIRED, Critical, High

    @SerializedName(value = "costPrice", alternate = {"CostPrice", "cost_price"})
    private double costPrice;

    @SerializedName(value = "quantity", alternate = {"Quantity"})
    private int quantity;

    @SerializedName(value = "riskQty", alternate = {"RiskQty", "risk_qty"})
    private int riskQty;

    @SerializedName(value = "recommendation", alternate = {"Recommendation"})
    private String recommendation;

    public int getProductId() { return productId; }
    public String getLotCode() { return (lotCode != null) ? lotCode : "Lô"; }
    public String getProductName() { return (productName != null) ? productName : "Sản phẩm"; }
    public String getExpiryDate() {
        if (expiryDate != null && !expiryDate.isEmpty()) {
            return expiryDate.contains("T") ? expiryDate.split("T")[0] : expiryDate;
        }
        return "—";
    }
    public Integer getDaysToExpiry() { return daysToExpiry; }
    public int getDaysRemaining() { return daysToExpiry != null ? daysToExpiry : 0; }
    public double getRiskValue() { return riskValue; }
    public String getStatus() { return status; }
    public double getCostPrice() { return costPrice; }
    public int getQuantity() { return quantity; }
    public int getRiskQty() { return riskQty; }
    public String getRecommendation() { return recommendation; }
}
