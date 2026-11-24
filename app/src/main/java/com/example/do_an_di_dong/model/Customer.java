package com.example.do_an_di_dong.model;

import com.google.gson.annotations.SerializedName;

public class Customer {
    @SerializedName(value = "customerId", alternate = {"CustomerId", "id", "Id"})
    private int customerId;

    @SerializedName(value = "fullName", alternate = {"FullName", "customerName", "CustomerName", "name", "Name"})
    private String customerName;

    @SerializedName(value = "email", alternate = {"Email"})
    private String email;

    @SerializedName(value = "phone", alternate = {"Phone"})
    private String phone;

    @SerializedName(value = "customerType", alternate = {"CustomerType"})
    private String customerType; // VIP, Normal, New

    @SerializedName(value = "pointBalance", alternate = {"PointBalance"})
    private int pointBalance;

    @SerializedName(value = "pointEarnedTotal", alternate = {"PointEarnedTotal", "points_earned_total", "PointsEarnedTotal", "totalPointsEarned", "TotalPointsEarned"})
    private int pointEarnedTotal;

    @SerializedName(value = "userImg", alternate = {"UserImg", "image", "Image", "avatar", "Avatar"})
    private String userImg;

    @SerializedName(value = "createdAt", alternate = {"CreatedAt"})
    private String createdAt;

    public Customer() {}

    public Customer(String customerName, String phone, String email) {
        this.customerName = customerName;
        this.phone = phone;
        this.email = email;
        this.customerType = "Standard";
    }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public String getCustomerName() { return (customerName != null) ? customerName : "Khách hàng #" + customerId; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getCustomerType() { return customerType; }
    public void setCustomerType(String customerType) { this.customerType = customerType; }

    public int getPointBalance() { return pointBalance; }
    public void setPointBalance(int pointBalance) { this.pointBalance = pointBalance; }

    public int getPointEarnedTotal() { return pointEarnedTotal > 0 ? pointEarnedTotal : pointBalance; }
    public void setPointEarnedTotal(int pointEarnedTotal) { this.pointEarnedTotal = pointEarnedTotal; }

    public String getUserImg() { return userImg; }
    public void setUserImg(String userImg) { this.userImg = userImg; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getFullName() { return getCustomerName(); }
    public int getLoyaltyPoints() { return pointBalance; }

    public String getTierName() {
        if (customerType != null && !customerType.trim().isEmpty()
                && !"Standard".equalsIgnoreCase(customerType.trim())
                && !"Normal".equalsIgnoreCase(customerType.trim())) {
            if ("VIP".equalsIgnoreCase(customerType.trim())) return "Hạng VIP";
            if ("Wholesale".equalsIgnoreCase(customerType.trim())) return "Khách sỉ";
            if ("Partner".equalsIgnoreCase(customerType.trim())) return "Đối tác";
            return customerType.trim();
        }
        int basis = Math.max(pointBalance, pointEarnedTotal);
        if (basis >= 1000) return "Kim Cương";
        if (basis >= 500) return "Vàng";
        if (basis >= 200) return "Bạc";
        return "Đồng";
    }

    public double getDiscountRate() {
        String type = customerType != null ? customerType.trim().toLowerCase() : "";
        int basis = Math.max(pointBalance, pointEarnedTotal);
        if ("wholesale".equals(type) || "partner".equals(type)) return 0.10;
        if ("vip".equals(type) || basis >= 1000) return 0.05;
        if (basis >= 500) return 0.03;
        if (basis >= 200) return 0.01;
        return 0.0;
    }
}
