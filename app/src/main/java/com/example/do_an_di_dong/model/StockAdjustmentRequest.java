package com.example.do_an_di_dong.model;

import com.google.gson.annotations.SerializedName;

public class StockAdjustmentRequest {
    @SerializedName(value = "ProductId", alternate = {"productId"})
    private int productId;

    @SerializedName(value = "WarehouseId", alternate = {"warehouseId"})
    private int warehouseId;

    @SerializedName(value = "QuantityChange", alternate = {"quantityChange", "adjustedQuantity", "AdjustedQuantity", "quantity", "Quantity"})
    private int adjustedQuantity;

    @SerializedName(value = "MovementType", alternate = {"movementType"})
    private String movementType;

    @SerializedName(value = "Reason", alternate = {"reason"})
    private String reason;

    @SerializedName(value = "UserId", alternate = {"userId"})
    private int userId;

    public StockAdjustmentRequest(int productId, int warehouseId, int adjustedQuantity, String reason) {
        this(productId, warehouseId, adjustedQuantity, "ADJUSTMENT", reason, 1);
    }

    public StockAdjustmentRequest(int productId, int warehouseId, int adjustedQuantity, String movementType, String reason, int userId) {
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.adjustedQuantity = adjustedQuantity;
        this.movementType = movementType != null ? movementType : "ADJUSTMENT";
        this.reason = reason;
        this.userId = userId > 0 ? userId : 1;
    }

    public int getProductId() { return productId; }
    public int getWarehouseId() { return warehouseId; }
    public int getAdjustedQuantity() { return adjustedQuantity; }
    public String getMovementType() { return movementType; }
    public String getReason() { return reason; }
    public int getUserId() { return userId; }
}
