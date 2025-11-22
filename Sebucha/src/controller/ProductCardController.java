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

 // Renders a single product card in the Order page: shows image, name, price,
 //stock, and lets the user select quantity and add-ons to add to cart.
 
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
        loadAddOnsFromDatabase();
        
        SpinnerValueFactory<Integer> valueFactory = 
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99, 1);
        quantitySpinner.setValueFactory(valueFactory);
        
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
                String formattedPrice = String.format("%.2f", price);
                dbAddOns.add(name + " (+₱" + formattedPrice + ")");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error loading add-ons: " + e.getMessage());
        }
        
        addOnsComboBox.setItems(dbAddOns);
        addOnsComboBox.setValue("None");
        
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

    public void setOrderController(OrderController orderController) {
        this.orderController = orderController;
    }

    // Syncs labels and image with the current product and styles the stock badge
    private void updateProductDisplay() {
        if (product != null) {
            productNameLabel.setText(product.getName());
            
            productPriceLabel.setText(String.format("₱%.2f", product.getPrice()));
            
            productCategoryLabel.setText(product.getCategory());
            
            productStockLabel.setText("Stock: " + product.getStock());
            updateStockLabel();
            
            try {
                String imagePath = product.getImagePath();
                Image image = null;
                
                if (imagePath != null && !imagePath.isEmpty()) {
                    File imageFile = new File(imagePath);
                    if (imageFile.exists()) {
                        image = new Image(imageFile.toURI().toString());
                    }
                }
                
                if (image == null) {
                    image = new Image(getClass().getResource("/view/images/addimage.png").toExternalForm());
                }
                
                productImageView.setImage(image);
                
                productImageView.setPreserveRatio(true);
                productImageView.setSmooth(true);
                productImageView.setCache(true);
                
                productImageView.setFitWidth(275.0);
                productImageView.setFitHeight(275.0);
                
                productImageView.setStyle(
                    "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 15, 0, 0, 5);" +
                    "-fx-background-color: transparent;"
                );
                
            } catch (Exception e) {
                System.err.println("Error loading product image: " + e.getMessage());
                try {
                    Image fallbackImage = new Image(getClass().getResource("/view/images/addimage.png").toExternalForm());
                    productImageView.setImage(fallbackImage);
                    
                    productImageView.setPreserveRatio(true);
                    productImageView.setSmooth(true);
                    productImageView.setCache(true);
                    productImageView.setFitWidth(275.0);
                    productImageView.setFitHeight(275.0);
                    
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

    
     //Adds the selected product (with optional add-on) to the cart.
     //Validates stock and quantity, calculates total, and resets inputs on success.
     
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

        String customProductName = product.getName();
        if (!selectedAddOn.equals("None")) {
            customProductName += " + " + selectedAddOn;
        }

        if (orderController != null) {
            orderController.addEnhancedProductToCart(product, quantity, selectedAddOn, customProductName, totalPrice);
        } else {
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

    // Add to Cart button
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

}
