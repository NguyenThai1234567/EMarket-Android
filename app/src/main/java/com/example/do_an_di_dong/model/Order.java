package com.example.do_an_di_dong.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Order {
    @SerializedName(value = "orderId", alternate = {"OrderId", "id", "Id"})
    private int orderId;

    @SerializedName(value = "orderCode", alternate = {"OrderCode", "code", "Code"})
    private String orderCode;

    @SerializedName(value = "customerId", alternate = {"CustomerId"})
    private int customerId;

    @SerializedName(value = "customerName", alternate = {"CustomerName"})
    private String customerName;

    @SerializedName(value = "userId", alternate = {"UserId"})
    private Integer userId;

    @SerializedName(value = "userName", alternate = {"UserName"})
    private String userName;

    @SerializedName(value = "branchId", alternate = {"BranchId"})
    private Integer branchId;

    @SerializedName(value = "branchName", alternate = {"BranchName"})
    private String branchName;

    @SerializedName(value = "orderDate", alternate = {"OrderDate"})
    private String orderDate;

    @SerializedName(value = "createdAt", alternate = {"CreatedAt"})
    private String createdAt;

    @SerializedName(value = "status", alternate = {"Status"})
    private String status;

    @SerializedName(value = "totalAmount", alternate = {"TotalAmount", "finalAmount", "FinalAmount"})
    private double totalAmount;

    @SerializedName(value = "discountAmount", alternate = {"DiscountAmount", "discount", "Discount"})
    private double discountAmount;

    @SerializedName(value = "manualDiscount", alternate = {"ManualDiscount"})
    private double manualDiscount;

    @SerializedName(value = "note", alternate = {"Note", "notes", "Notes", "cancelReason", "CancelReason"})
    private String note;

    @SerializedName(value = "paymentMethod", alternate = {"PaymentMethod"})
    private String paymentMethod;

    @SerializedName(value = "paymentStatus", alternate = {"PaymentStatus"})
    private String paymentStatus;

    @SerializedName(value = "orderDetails", alternate = {"OrderDetails", "details", "Details"})
    private List<OrderDetail> orderDetails;

    public int getOrderId() { return orderId; }
    public String getOrderCode() { return (orderCode != null && !orderCode.isEmpty()) ? orderCode : ("#ORD-" + orderId); }
    public int getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public Integer getUserId() { return userId; }
    public String getUserName() { return userName; }
    public Integer getBranchId() { return branchId; }
    public String getBranchName() { return branchName; }
    public String getOrderDate() { return (orderDate != null && !orderDate.isEmpty()) ? orderDate : createdAt; }
    public String getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public double getTotalAmount() { return totalAmount; }
    public double getDiscountAmount() { return discountAmount + manualDiscount; }
    public String getNote() { return note; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getPaymentStatus() { return paymentStatus; }
    public List<OrderDetail> getOrderDetails() { return orderDetails; }
}
