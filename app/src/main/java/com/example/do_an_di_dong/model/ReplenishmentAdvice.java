package com.example.do_an_di_dong.model;

import com.google.gson.annotations.SerializedName;

public class ReplenishmentAdvice {
    @SerializedName(value = "productId", alternate = {"ProductId", "product_id", "id", "Id"})
    private int productId;

    @SerializedName(value = "productName", alternate = {"ProductName", "name", "Name"})
    private String productName;

    @SerializedName(value = "categoryName", alternate = {"CategoryName"})
    private String categoryName;

    @SerializedName(value = "unit", alternate = {"Unit"})
    private String unit;

    @SerializedName(value = "productImage", alternate = {"ProductImage", "image", "Image"})
    private String productImage;

    @SerializedName(value = "currentStock", alternate = {"CurrentStock", "current_stock"})
    private int currentStock;

    @SerializedName(value = "safetyStock", alternate = {"SafetyStock", "safety_stock"})
    private int safetyStock;

    @SerializedName(value = "suggestedQty", alternate = {"SuggestedQty", "suggestedQuantity", "SuggestedQuantity", "suggested_qty"})
    private int suggestedQuantity;

    @SerializedName(value = "expectedDemand", alternate = {"ExpectedDemand", "expectedDemand30d", "ExpectedDemand30d", "expected_demand_30d"})
    private int expectedDemand;

    @SerializedName(value = "reason", alternate = {"Reason"})
    private String reason;

    @SerializedName(value = "priorityLevel", alternate = {"PriorityLevel", "priority_level"})
    private String priorityLevel;

    @SerializedName(value = "confidenceLevel", alternate = {"ConfidenceLevel", "confidenceScore", "ConfidenceScore", "confidence_score"})
    private String confidenceLevel;

    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getCategoryName() { return categoryName; }
    public String getUnit() { return (unit != null && !unit.isEmpty()) ? unit : "cái"; }
    public String getProductImage() { return productImage; }
    public int getCurrentStock() { return currentStock; }
    public int getSafetyStock() { return safetyStock; }
    public int getSuggestedQuantity() { return suggestedQuantity; }
    public int getExpectedDemand() { return expectedDemand; }
    public String getReason() {
        if (reason != null && !reason.isEmpty()) return reason;
        return priorityLevel;
    }
    public String getConfidenceLevelText() { return confidenceLevel != null ? confidenceLevel : "HIGH"; }
    public double getConfidenceLevel() {
        if (confidenceLevel == null) return 0.9;
        try {
            return Double.parseDouble(confidenceLevel);
        } catch (Exception e) {
            if ("HIGH".equalsIgnoreCase(confidenceLevel)) return 0.95;
            if ("MEDIUM".equalsIgnoreCase(confidenceLevel)) return 0.75;
            return 0.6;
        }
    }
}
