package com.example.do_an_di_dong.model;

import com.google.gson.annotations.SerializedName;

public class LoyaltyProgram {
    @SerializedName(value = "loyaltyId", alternate = {"LoyaltyId", "id", "Id"})
    private int loyaltyId;

    @SerializedName(value = "customerId", alternate = {"CustomerId"})
    private int customerId;

    @SerializedName(value = "pointsEarned", alternate = {"PointsEarned", "points", "Points"})
    private int pointsEarned;

    @SerializedName(value = "pointsRedeemed", alternate = {"PointsRedeemed", "pointsUsed", "PointsUsed"})
    private int pointsRedeemed;

    @SerializedName(value = "orderId", alternate = {"OrderId"})
    private Integer orderId;

    @SerializedName(value = "description", alternate = {"Description", "note", "Note"})
    private String description;

    @SerializedName(value = "createdAt", alternate = {"CreatedAt", "transactionDate", "TransactionDate", "updatedAt", "UpdatedAt"})
    private String createdAt;

    public LoyaltyProgram() {}

    public LoyaltyProgram(int customerId, int pointsEarned, int pointsRedeemed, Integer orderId) {
        this.customerId = customerId;
        this.pointsEarned = pointsEarned;
        this.pointsRedeemed = pointsRedeemed;
        this.orderId = orderId;
    }

    public int getLoyaltyId() { return loyaltyId; }
    public void setLoyaltyId(int loyaltyId) { this.loyaltyId = loyaltyId; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public int getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }

    public int getPointsRedeemed() { return pointsRedeemed; }
    public void setPointsRedeemed(int pointsRedeemed) { this.pointsRedeemed = pointsRedeemed; }

    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    /**
     * Số điểm thay đổi ròng của giao dịch này (+ là tích điểm, - là tiêu điểm).
     */
    public int getNetPoints() {
        if (pointsRedeemed > 0) {
            return pointsEarned - pointsRedeemed;
        }
        return pointsEarned;
    }

    /**
     * Số điểm dương tích lũy trong giao dịch này.
     */
    public int getPositiveEarned() {
        return Math.max(0, pointsEarned);
    }

    /**
     * Số điểm đã đổi/tiêu trong giao dịch này.
     */
    public int getPositiveRedeemed() {
        if (pointsRedeemed > 0) return pointsRedeemed;
        if (pointsEarned < 0) return Math.abs(pointsEarned);
        return 0;
    }
}
