package model;

public class OrderItem {
    private int orderId;
    private int productId;
    private String productName;
    private int quantity;
    private double unitPrice;
    private double totalPrice;
    private String size;
    private String addOn;
    private String customizationDetails;
    private String productCategory;

    // Default constructor
    public OrderItem() {}

    // Enhanced constructor with customization
    public OrderItem(int orderId, int productId, String productName, int quantity, 
                     double unitPrice, double totalPrice, String size, String addOn, 
                     String customizationDetails, String productCategory) {
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.size = size;
        this.addOn = addOn;
        this.customizationDetails = customizationDetails;
        this.productCategory = productCategory;
    }

    // Getters
    public int getOrderId() {
        return orderId;
    }

    public int getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public String getSize() {
        return size;
    }

    public String getAddOn() {
        return addOn;
    }

    public String getCustomizationDetails() {
        return customizationDetails;
    }

    public String getProductCategory() {
        return productCategory;
    }

    // Setters
    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        // Automatically recalculate total price when quantity changes
        this.totalPrice = this.unitPrice * quantity;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
        // Automatically recalculate total price when unit price changes
        this.totalPrice = unitPrice * this.quantity;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public void setAddOn(String addOn) {
        this.addOn = addOn;
    }

    public void setCustomizationDetails(String customizationDetails) {
        this.customizationDetails = customizationDetails;
    }

    public void setProductCategory(String productCategory) {
        this.productCategory = productCategory;
    }

    // Utility method to calculate total price
    public void calculateTotalPrice() {
        this.totalPrice = this.unitPrice * this.quantity;
    }

    // Method to get formatted price string
    public String getFormattedUnitPrice() {
        return String.format("₱%.2f", unitPrice);
    }

    public String getFormattedTotalPrice() {
        return String.format("₱%.2f", totalPrice);
    }

    // Method to get display string for add-ons
    public String getDisplayAddOn() {
        return (addOn == null || addOn.equals("None")) ? "" : addOn;
    }

    // Method to check if item has customizations
    public boolean hasCustomizations() {
        return (addOn != null && !addOn.equals("None")) || 
               (size != null && !size.isEmpty()) || 
               (customizationDetails != null && !customizationDetails.isEmpty());
    }

    // New method to check if this order item can be combined with add-ons
    public boolean canCombineWithAddOns() {
        // Items from series categories can typically be combined with add-ons
        String category = this.productCategory;
        if (category == null) return false;
        
        return category.contains("Series") || 
               category.equals("Hot Drinks") || 
               category.equals("Food Pair");
    }

    @Override
    public String toString() {
        return "OrderItem{" +
                "orderId=" + orderId +
                ", productId=" + productId +
                ", productName='" + productName + '\'' +
                ", quantity=" + quantity +
                ", unitPrice=" + unitPrice +
                ", totalPrice=" + totalPrice +
                ", size='" + size + '\'' +
                ", addOn='" + addOn + '\'' +
                ", customizationDetails='" + customizationDetails + '\'' +
                ", productCategory='" + productCategory + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        OrderItem orderItem = (OrderItem) obj;
        return orderId == orderItem.orderId &&
               productId == orderItem.productId &&
               quantity == orderItem.quantity &&
               Double.compare(orderItem.unitPrice, unitPrice) == 0;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(orderId, productId, quantity, unitPrice);
    }
}
