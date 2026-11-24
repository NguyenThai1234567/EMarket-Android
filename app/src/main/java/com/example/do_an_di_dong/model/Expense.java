package com.example.do_an_di_dong.model;

import com.google.gson.annotations.SerializedName;

public class Expense {
    @SerializedName(value = "expenseId", alternate = {"ExpenseId", "id", "Id"})
    private int expenseId;

    @SerializedName(value = "branchId", alternate = {"BranchId"})
    private int branchId;

    @SerializedName(value = "branchName", alternate = {"BranchName"})
    private String branchName;

    @SerializedName(value = "categoryId", alternate = {"CategoryId"})
    private int categoryId;

    @SerializedName(value = "categoryName", alternate = {"CategoryName"})
    private String categoryName;

    @SerializedName(value = "userId", alternate = {"UserId"})
    private int userId;

    @SerializedName(value = "userName", alternate = {"UserName"})
    private String userName;

    @SerializedName(value = "amount", alternate = {"Amount"})
    private double amount;

    @SerializedName(value = "expenseDate", alternate = {"ExpenseDate"})
    private String expenseDate;

    @SerializedName(value = "createdAt", alternate = {"CreatedAt"})
    private String createdAt;

    @SerializedName(value = "note", alternate = {"Note", "description", "Description"})
    private String note;

    @SerializedName(value = "refImage", alternate = {"RefImage", "image", "Image"})
    private String refImage;

    @SerializedName(value = "paymentMethod", alternate = {"PaymentMethod"})
    private String paymentMethod;

    @SerializedName(value = "status", alternate = {"Status"})
    private String status;

    @SerializedName(value = "approvedBy", alternate = {"ApprovedBy"})
    private Integer approvedBy;

    @SerializedName(value = "approverName", alternate = {"ApproverName"})
    private String approverName;

    @SerializedName(value = "approvedAt", alternate = {"ApprovedAt"})
    private String approvedAt;

    @SerializedName(value = "rejectedBy", alternate = {"RejectedBy"})
    private Integer rejectedBy;

    @SerializedName(value = "rejectorName", alternate = {"RejectorName"})
    private String rejectorName;

    @SerializedName(value = "rejectedAt", alternate = {"RejectedAt"})
    private String rejectedAt;

    @SerializedName(value = "rejectionReason", alternate = {"RejectionReason", "rejectReason", "RejectReason", "reason", "Reason"})
    private String rejectionReason;

    public int getExpenseId() { return expenseId; }
    public void setExpenseId(int expenseId) { this.expenseId = expenseId; }
    public int getBranchId() { return branchId; }
    public void setBranchId(int branchId) { this.branchId = branchId; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getExpenseDate() { return (expenseDate != null && !expenseDate.isEmpty()) ? expenseDate : createdAt; }
    public void setExpenseDate(String expenseDate) { this.expenseDate = expenseDate; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getRefImage() { return refImage; }
    public void setRefImage(String refImage) { this.refImage = refImage; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getApprovedBy() { return approvedBy; }
    public String getApproverName() { return approverName; }
    public String getApprovedAt() { return approvedAt; }
    public Integer getRejectedBy() { return rejectedBy; }
    public String getRejectorName() { return rejectorName; }
    public String getRejectedAt() { return rejectedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public String getRejectReason() { return rejectionReason; }
}
