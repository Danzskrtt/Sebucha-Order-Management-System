package controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import model.Product;
import model.OrderItem;
import model.SqliteConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.ArrayList;
import java.util.List;

public class ProductCardController implements Initializable {

    @FXML
    private ImageView productImageView;
    
    @FXML
    private Label productNameLabel;
    
    @FXML
    private Label productPriceLabel;
    
    @FXML
    private Label productCategoryLabel;
    
    @FXML
    private Label productStockLabel;
    
    @FXML
    private ComboBox<String> addOnsComboBox;
    
    @FXML
    private Spinner<Integer> quantitySpinner;
    
    @FXML
    private Button addToCartButton;

    private Product product;
    private OrderController orderController; // Reference to parent controller
    private static List<OrderItem> cartItems = new ArrayList<>();
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize the add-ons ComboBox with database values
        loadAddOnsFromDatabase();
        
        // Initialize the quantity spinner
        SpinnerValueFactory<Integer> valueFactory = 
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99, 1);
        quantitySpinner.setValueFactory(valueFactory);
        
        // Make spinner editable
        quantitySpinner.setEditable(true);
    }

    private void loadAddOnsFromDatabase() {
        ObservableList<String> dbAddOns = FXCollections.observableArrayList();
        dbAddOns.add("None"); // Always include "None" as first option
        
        String query = "SELECT name, price FROM products WHERE category = 'Add-ons' AND status = 'Available' ORDER BY name";
        
        try (Connection connect = SqliteConnection.Connector();
             PreparedStatement prepare = connect.prepareStatement(query);
             ResultSet result = prepare.executeQuery()) {
            
            while (result.next()) {
                String name = result.getString("name");
                double price = result.getDouble("price");
                // Format the price with two decimal places
                String formattedPrice = String.format("%.2f", price);
                // Add the add-on with its price
                dbAddOns.add(name + " (+₱" + formattedPrice + ")");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error loading add-ons: " + e.getMessage());
        }
        
        // Set items to ComboBox even if database fetch fails
        addOnsComboBox.setItems(dbAddOns);
        addOnsComboBox.setValue("None");
        
        // Add a listener to handle null selection
        addOnsComboBox.setOnAction(event -> {
            if (addOnsComboBox.getValue() == null) {
                addOnsComboBox.setValue("None");
            }
        });
    }

    public void setProduct(Product product) {
        this.product = product;
        updateProductDisplay();
    }

    // New method to set the OrderController reference
    public void setOrderController(OrderController orderController) {
        this.orderController = orderController;
    }

    private void updateProductDisplay() {
        if (product != null) {
            // Set product name
            productNameLabel.setText(product.getName());
            
            // Set product price with currency formatting
            productPriceLabel.setText(String.format("₱%.2f", product.getPrice()));
            
            // Set category
            productCategoryLabel.setText(product.getCategory());
            
            // Set stock with color coding
            productStockLabel.setText("Stock: " + product.getStock());
            updateStockLabel();
            
            // Load and set the product image
            try {
                String imagePath = product.getImagePath();
                Image image = null;
                
                if (imagePath != null && !imagePath.isEmpty()) {
                    File imageFile = new File(imagePath);
                    if (imageFile.exists()) {
                        image = new Image(imageFile.toURI().toString());
                    }
                }
                
                // If product image couldn't be loaded, use addimage.png as default
                if (image == null) {
                    image = new Image(getClass().getResource("/view/images/addimage.png").toExternalForm());
                }
                
                productImageView.setImage(image);
                
            } catch (Exception e) {
                System.err.println("Error loading product image: " + e.getMessage());
                // Use addimage.png as fallback
                try {
                    productImageView.setImage(new Image(getClass().getResource("/view/images/addimage.png").toExternalForm()));
                } catch (Exception ex) {
                    System.err.println("Failed to load fallback image: " + ex.getMessage());
                }
            }
            
            // Enable/disable add to cart based on stock
            addToCartButton.setDisable(product.getStock() <= 0);
        }
    }

    private void updateStockLabel() {
        if (product != null) {
            String stockStyle;
            if (product.getStock() <= 0) {
                stockStyle = "-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #fecaca, #fca5a5); " +
                           "-fx-text-fill: #991b1b; " +
                           "-fx-background-radius: 25px; " +
                           "-fx-font-family: 'Segoe UI Semibold'; " +
                           "-fx-font-weight: 700; " +
                           "-fx-font-size: 12px; " +
                           "-fx-padding: 6px 12px; " +
                           "-fx-effect: dropshadow(gaussian, rgba(153,27,27,0.2), 8, 0, 0, 2);";
            } else if (product.getStock() <= 5) {
                stockStyle = "-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #fed7aa, #fdba74); " +
                           "-fx-text-fill: #c2410c; " +
                           "-fx-background-radius: 25px; " +
                           "-fx-font-family: 'Segoe UI Semibold'; " +
                           "-fx-font-weight: 700; " +
                           "-fx-font-size: 12px; " +
                           "-fx-padding: 6px 12px; " +
                           "-fx-effect: dropshadow(gaussian, rgba(194,65,12,0.2), 8, 0, 0, 2);";
            } else {
                stockStyle = "-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #dcfce7, #bbf7d0); " +
                           "-fx-text-fill: #166534; " +
                           "-fx-background-radius: 25px; " +
                           "-fx-font-family: 'Segoe UI Semibold'; " +
                           "-fx-font-weight: 700; " +
                           "-fx-font-size: 12px; " +
                           "-fx-padding: 6px 12px; " +
                           "-fx-effect: dropshadow(gaussian, rgba(22,101,52,0.2), 8, 0, 0, 2);";
            }
            productStockLabel.setStyle(stockStyle);
        }
    }

    @FXML
    private void handleAddToCart() {
        if (product == null) {
            showAlert("Error", "No product selected.", Alert.AlertType.ERROR);
            return;
        }

        if (product.getStock() <= 0) {
            showAlert("Out of Stock", "This product is currently out of stock.", Alert.AlertType.WARNING);
            return;
        }

        int quantity = quantitySpinner.getValue();
        if (quantity > product.getStock()) {
            showAlert("Insufficient Stock", 
                     "Only " + product.getStock() + " items available in stock.", 
                     Alert.AlertType.WARNING);
            return;
        }

        // Get selected add-on
        String selectedAddOn = addOnsComboBox.getValue();
        double addOnPrice = getAddOnPrice(selectedAddOn);
        
        // Calculate total price
        double unitPrice = product.getPrice() + addOnPrice;
        double totalPrice = unitPrice * quantity;

        // Create custom product name if add-ons are selected
        String customProductName = product.getName();
        if (!selectedAddOn.equals("None")) {
            customProductName += " + " + selectedAddOn;
        }

        // Use OrderController's enhanced method if available, otherwise use static cart
        if (orderController != null) {
            orderController.addEnhancedProductToCart(product, quantity, "Medium", selectedAddOn, customProductName, totalPrice);
        } else {
            // Fallback to static cart
            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.getId());
            orderItem.setProductName(customProductName);
            orderItem.setQuantity(quantity);
            orderItem.setUnitPrice(unitPrice);
            orderItem.setTotalPrice(totalPrice);
            orderItem.setAddOn(selectedAddOn);
            orderItem.setProductCategory(product.getCategory());
            cartItems.add(orderItem);
        }

        // Show success message
        showAlert("Added to Cart", 
                 quantity + "x " + customProductName + " added to cart!", 
                 Alert.AlertType.INFORMATION);

        // Reset quantity to 1
        quantitySpinner.getValueFactory().setValue(1);
        addOnsComboBox.setValue("None");
    }

    private double getAddOnPrice(String addOnWithPrice) {
        if (addOnWithPrice == null || addOnWithPrice.equals("None")) {
            return 0.0;
        }
        
        try {
            // Extract price from the format "Name (+₱XX.XX)"
            int startIndex = addOnWithPrice.lastIndexOf("₱") + 1;
            int endIndex = addOnWithPrice.lastIndexOf(")");
            String priceStr = addOnWithPrice.substring(startIndex, endIndex);
            return Double.parseDouble(priceStr);
        } catch (Exception e) {
            e.printStackTrace();
            return 0.0;
        }
    }

    @FXML
    private void handleButtonHover(MouseEvent event) {
        if (addToCartButton != null && !addToCartButton.isDisabled()) {
            addToCartButton.setStyle(
                "-fx-background-color: #818cf8; " +
                "-fx-text-fill: white; " +
                "-fx-background-radius: 12; " +
                "-fx-font-family: 'Segoe UI'; " +
                "-fx-font-weight: 600; " +
                "-fx-font-size: 16;"
            );
        }
    }

    @FXML
    private void handleButtonExit(MouseEvent event) {
        if (addToCartButton != null && !addToCartButton.isDisabled()) {
            addToCartButton.setStyle(
                "-fx-background-color: #4f46e5; " +
                "-fx-text-fill: white; " +
                "-fx-background-radius: 12; " +
                "-fx-font-family: 'Segoe UI'; " +
                "-fx-font-weight: 600; " +
                "-fx-font-size: 16;"
            );
        }
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Static method to get cart items (for other controllers to access)
    public static List<OrderItem> getCartItems() {
        return new ArrayList<>(cartItems);
    }

    // Static method to clear cart
    public static void clearCart() {
        cartItems.clear();
    }

    // Static method to get cart total
    public static double getCartTotal() {
        return cartItems.stream().mapToDouble(OrderItem::getTotalPrice).sum();
    }

    // Static method to get cart item count
    public static int getCartItemCount() {
        return cartItems.stream().mapToInt(OrderItem::getQuantity).sum();
    }
}
