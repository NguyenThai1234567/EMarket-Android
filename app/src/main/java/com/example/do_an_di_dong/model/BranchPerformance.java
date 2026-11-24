package com.example.do_an_di_dong.model;

import com.google.gson.annotations.SerializedName;

public class BranchPerformance {
    @SerializedName(value = "branchId", alternate = {"BranchId", "id", "Id"})
    private int branchId;

    @SerializedName(value = "branchName", alternate = {"BranchName", "name", "Name"})
    private String branchName;

    @SerializedName(value = "salesAmount", alternate = {"SalesAmount", "revenue", "Revenue", "totalSales", "TotalSales"})
    private double revenue;

    @SerializedName(value = "profit", alternate = {"Profit", "grossProfit", "GrossProfit"})
    private double profit;

    @SerializedName(value = "orderCount", alternate = {"OrderCount"})
    private int orderCount;

    @SerializedName(value = "lowStockCount", alternate = {"LowStockCount"})
    private int lowStockCount;

    @SerializedName(value = "purchaseAmount", alternate = {"PurchaseAmount", "totalPurchase", "TotalPurchase"})
    private double purchaseAmount;

    public int getBranchId() { return branchId; }
    public String getBranchName() { return branchName; }
    public double getRevenue() { return revenue; }
    public double getProfit() { return profit; }
    public int getOrderCount() { return orderCount; }
    public int getLowStockCount() { return lowStockCount; }
    public double getPurchaseAmount() { return purchaseAmount; }
}
