package com.example.do_an_di_dong.model;

import com.google.gson.annotations.SerializedName;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class QuotationSaveRequest {
    @SerializedName(value = "QuotationId", alternate = {"quotationId"})
    private int quotationId;

    @SerializedName(value = "QuotationCode", alternate = {"quotationCode"})
    private String quotationCode;

    @SerializedName(value = "BranchId", alternate = {"branchId"})
    private int branchId;

    @SerializedName(value = "BranchName", alternate = {"branchName"})
    private String branchName;

    @SerializedName(value = "UserId", alternate = {"userId"})
    private int userId = 1;

    @SerializedName(value = "CreatorName", alternate = {"creatorName"})
    private String creatorName;

    @SerializedName(value = "CustomerId", alternate = {"customerId"})
    private Integer customerId;

    @SerializedName(value = "CustomerName", alternate = {"customerName"})
    private String customerName;

    @SerializedName(value = "CustomerPhone", alternate = {"customerPhone"})
    private String customerPhone;

    @SerializedName(value = "CustomerAddress", alternate = {"customerAddress"})
    private String customerAddress;

    @SerializedName(value = "IssueDate", alternate = {"issueDate"})
    private String issueDate;

    @SerializedName(value = "ExpiryDate", alternate = {"expiryDate"})
    private String expiryDate;

    @SerializedName(value = "Note", alternate = {"note"})
    private String note;

    @SerializedName(value = "TotalAmount", alternate = {"totalAmount"})
    private double totalAmount;

    @SerializedName(value = "DiscountAmount", alternate = {"discountAmount"})
    private double discountAmount;

    @SerializedName(value = "ManualDiscount", alternate = {"manualDiscount"})
    private double manualDiscount;

    @SerializedName(value = "DiscountReason", alternate = {"discountReason"})
    private String discountReason = "";

    @SerializedName(value = "FinalAmount", alternate = {"finalAmount"})
    private double finalAmount;

    @SerializedName(value = "Status", alternate = {"status"})
    private String status;

    @SerializedName(value = "Details", alternate = {"details"})
    private List<QuotationDetailRequest> details;

    private static String defaultIsoNow() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());
    }

    private static String defaultIsoExpiry() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 30);
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(cal.getTime());
    }

    public QuotationSaveRequest(int quotationId, String customerName, int branchId, double totalAmount, List<QuotationDetailRequest> details) {
        this.quotationId = quotationId;
        this.quotationCode = quotationId > 0 ? ("BG-" + quotationId) : ("BG-" + (System.currentTimeMillis() % 1000000));
        this.customerName = customerName;
        this.branchId = branchId > 0 ? branchId : 1;
        this.userId = 1;
        this.issueDate = defaultIsoNow();
        this.expiryDate = defaultIsoExpiry();
        this.totalAmount = totalAmount;
        this.discountAmount = 0;
        this.manualDiscount = 0;
        this.finalAmount = totalAmount;
        this.status = "Draft";
        this.note = "";
        this.details = details;
    }

    public QuotationSaveRequest(int quotationId, int branchId, Integer customerId, String customerName,
                                String customerPhone, String customerAddress, String note,
                                double totalAmount, double discountAmount, double manualDiscount,
                                String status, List<QuotationDetailRequest> details) {
        this.quotationId = quotationId;
        this.quotationCode = quotationId > 0 ? ("BG-" + quotationId) : ("BG-" + (System.currentTimeMillis() % 1000000));
        this.branchId = branchId > 0 ? branchId : 1;
        this.userId = 1;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.customerAddress = customerAddress;
        this.issueDate = defaultIsoNow();
        this.expiryDate = defaultIsoExpiry();
        this.note = note;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount;
        this.manualDiscount = manualDiscount;
        this.finalAmount = Math.max(0, totalAmount - discountAmount - manualDiscount);
        this.status = (status != null && !status.isEmpty()) ? status : "Draft";
        this.details = details;
    }

    public int getQuotationId() { return quotationId; }
    public String getQuotationCode() { return quotationCode; }
    public void setQuotationCode(String quotationCode) { this.quotationCode = quotationCode; }
    public int getBranchId() { return branchId; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId > 0 ? userId : 1; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public void setIssueDate(String issueDate) { this.issueDate = issueDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }
    public void setDiscountReason(String discountReason) { this.discountReason = discountReason; }
    public Integer getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public String getCustomerAddress() { return customerAddress; }
    public String getNote() { return note; }
    public double getTotalAmount() { return totalAmount; }
    public double getDiscountAmount() { return discountAmount; }
    public double getManualDiscount() { return manualDiscount; }
    public double getFinalAmount() { return finalAmount; }
    public void setFinalAmount(double finalAmount) { this.finalAmount = finalAmount; }
    public String getStatus() { return status; }
    public List<QuotationDetailRequest> getDetails() { return details; }
}
