package com.example.do_an_di_dong.model;

import com.google.gson.annotations.SerializedName;

public class Product {
    @SerializedName(value = "productId", alternate = {"ProductId", "product_id", "Id", "id"})
    private int productId;

    @SerializedName(value = "name", alternate = {"Name", "productName", "ProductName"})
    private String name;

    @SerializedName(value = "barcode", alternate = {"Barcode", "sku", "Sku", "SKU"})
    private String sku;

    @SerializedName(value = "price", alternate = {"Price", "salePrice", "SalePrice"})
    private double price;

    @SerializedName(value = "originalPrice", alternate = {"OriginalPrice", "costPrice", "CostPrice", "importPrice", "ImportPrice"})
    private double originalPrice;

    @SerializedName(value = "finalPrice", alternate = {"FinalPrice"})
    private double finalPrice;

    @SerializedName(value = "discountAmount", alternate = {"DiscountAmount"})
    private double discountAmount;

    @SerializedName(value = "promotionName", alternate = {"PromotionName"})
    private String promotionName;

    @SerializedName(value = "quantity", alternate = {"Quantity", "stockQuantity", "StockQuantity", "stock", "Stock", "currentStock", "CurrentStock"})
    private int stockQuantity;

    @SerializedName(value = "minStock", alternate = {"MinStock", "min_stock"})
    private int minStock;

    @SerializedName(value = "maxStock", alternate = {"MaxStock", "max_stock"})
    private int maxStock;

    @SerializedName(value = "image", alternate = {"Image", "imageUrl", "ImageUrl", "productImage", "ProductImage"})
    private String imageUrl;

    @SerializedName(value = "unit", alternate = {"Unit"})
    private String unit;

    @SerializedName(value = "description", alternate = {"Description"})
    private String description;

    @SerializedName(value = "categoryId", alternate = {"CategoryId", "category_id"})
    private int categoryId;

    @SerializedName(value = "categoryName", alternate = {"CategoryName"})
    private String categoryName;

    @SerializedName(value = "supplierId", alternate = {"SupplierId", "supplier_id"})
    private int supplierId;

    @SerializedName(value = "supplierName", alternate = {"SupplierName"})
    private String supplierName;

    @SerializedName(value = "expiredAt", alternate = {"ExpiredAt", "expiryDate", "ExpiryDate"})
    private String expiredAt;

    public Product() {}

    public Product(int productId, String name, String sku, double price, int stockQuantity, String imageUrl) {
        this.productId = productId;
        this.name = name;
        this.sku = sku;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.imageUrl = imageUrl;
    }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getName() { return name != null ? name : ""; }
    public void setName(String name) { this.name = name; }

    public String getSku() { return (sku != null && !sku.isEmpty()) ? sku : ("SP-" + productId); }
    public void setSku(String sku) { this.sku = sku; }

    public double getPrice() {
        if (price > 0) return price;
        if (finalPrice > 0) return finalPrice;
        return originalPrice;
    }
    public void setPrice(double price) { this.price = price; }

    public double getOriginalPrice() { return originalPrice > 0 ? originalPrice : getPrice(); }
    public void setOriginalPrice(double originalPrice) { this.originalPrice = originalPrice; }

    public double getFinalPrice() { return finalPrice > 0 ? finalPrice : getPrice(); }
    public void setFinalPrice(double finalPrice) { this.finalPrice = finalPrice; }

    public double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(double discountAmount) { this.discountAmount = discountAmount; }

    public String getPromotionName() { return promotionName; }
    public void setPromotionName(String promotionName) { this.promotionName = promotionName; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }

    public int getMinStock() { return minStock; }
    public void setMinStock(int minStock) { this.minStock = minStock; }

    public int getMaxStock() { return maxStock; }
    public void setMaxStock(int maxStock) { this.maxStock = maxStock; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getUnit() { return unit != null && !unit.isEmpty() ? unit : "Cái"; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getExpiredAt() { return expiredAt; }
    public void setExpiredAt(String expiredAt) { this.expiredAt = expiredAt; }

    public int getActualStock() { return stockQuantity; }
    public String getImage() { return imageUrl; }
    public String getBarcode() { return sku != null ? sku : ""; }
    public String getFullImageUrl() {
        return com.example.do_an_di_dong.data.api.ApiClient.getFullImageUrl(imageUrl);
    }
}
