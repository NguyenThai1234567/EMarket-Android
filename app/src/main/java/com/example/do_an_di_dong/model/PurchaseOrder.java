package com.example.do_an_di_dong.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PurchaseOrder {
    @SerializedName(value = "purchaseOrderId", alternate = {"PurchaseOrderId", "poId", "PoId", "id", "Id"})
    private int purchaseOrderId;

    @SerializedName(value = "supplierId", alternate = {"SupplierId"})
    private int supplierId;

    @SerializedName(value = "supplierName", alternate = {"SupplierName"})
    private String supplierName;

    @SerializedName(value = "warehouseId", alternate = {"WarehouseId"})
    private int warehouseId;

    @SerializedName(value = "warehouseName", alternate = {"WarehouseName"})
    private String warehouseName;

    @SerializedName(value = "userId", alternate = {"UserId"})
    private int userId;

    @SerializedName(value = "userName", alternate = {"UserName"})
    private String userName;

    @SerializedName(value = "branchId", alternate = {"BranchId"})
    private int branchId;

    @SerializedName(value = "branchName", alternate = {"BranchName"})
    private String branchName;

    @SerializedName(value = "orderDate", alternate = {"OrderDate"})
    private String orderDate;

    @SerializedName(value = "createdAt", alternate = {"CreatedAt"})
    private String createdAt;

    @SerializedName(value = "status", alternate = {"Status"})
    private String status;

    @SerializedName(value = "paymentStatus", alternate = {"PaymentStatus"})
    private String paymentStatus = "UNPAID";

    @SerializedName(value = "totalAmount", alternate = {"TotalAmount"})
    private double totalAmount;

    @SerializedName(value = "paidAmount", alternate = {"PaidAmount"})
    private double paidAmount;

    @SerializedName(value = "dueAmount", alternate = {"DueAmount"})
    private double dueAmount;

    @SerializedName(value = "notes", alternate = {"Notes", "note", "Note"})
    private String notes;

    @SerializedName(value = "Details", alternate = {"details"})
    private List<Map<String, Object>> details = new ArrayList<>();

    public int getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(int purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }

    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public int getWarehouseId() { return warehouseId; }
    public void setWarehouseId(int warehouseId) { this.warehouseId = warehouseId; }

    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public int getBranchId() { return branchId; }
    public void setBranchId(int branchId) { this.branchId = branchId; }

    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }

    public String getOrderDate() { return (orderDate != null && !orderDate.isEmpty()) ? orderDate : createdAt; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public double getPaidAmount() { return paidAmount; }
    public void setPaidAmount(double paidAmount) { this.paidAmount = paidAmount; }

    public double getDueAmount() { return dueAmount; }
    public void setDueAmount(double dueAmount) { this.dueAmount = dueAmount; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<Map<String, Object>> getDetails() { return details; }
    public void setDetails(List<Map<String, Object>> details) { this.details = details; }
}
