package controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;
import model.*;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class OrderController implements Initializable {

    // Navigation buttons
    @FXML private Button dashboardbutton;
    @FXML private Button inventorybutton;
    @FXML private Button orderbutton;
    @FXML private Button recentorderbutton;
    @FXML private Button logoutbutton;
    @FXML private Button refreshOrderButton;

    // Product Search and Filter Section
    @FXML private TextField productSearchField;
    @FXML private ComboBox<String> productCategoryFilter;
    @FXML private Button clearFilterButton;

    // Order Summary Section
    @FXML private TextField customerNameField;
    @FXML private ComboBox<String> orderTypeComboBox;
    @FXML private ComboBox<String> paymentMethodComboBox;
    @FXML private TextField orderTotalField;
    @FXML private Button placeOrderButton;
    @FXML private Button clearCartButton;
    @FXML private Button receiptButton;

    // Product Cards Container
    @FXML private ScrollPane productScrollPane;
    @FXML private FlowPane productCardsContainer;

    // Shopping Cart Table
    @FXML private TableView<OrderItem> shoppingCartTable;
    @FXML private TableColumn<OrderItem, String> cartProductColumn;
    @FXML private TableColumn<OrderItem, Double> cartPriceColumn;
    @FXML private TableColumn<OrderItem, Integer> cartQuantityColumn;
    @FXML private TableColumn<OrderItem, Double> cartTotalColumn;
    @FXML private TableColumn<OrderItem, Button> cartActionColumn;

    // Data collections
    private ObservableList<Product> availableProducts = FXCollections.observableArrayList();
    private ObservableList<OrderItem> shoppingCart = FXCollections.observableArrayList();
    private DecimalFormat decimalFormat = new DecimalFormat("#0.00");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Set default customer name
        customerNameField.setText("None");
        
        initializeComboBoxes();
        initializeShoppingCartTable();
        loadAvailableProducts();
        setupEventHandlers();
        clearOrderForm();
    }
    
    private boolean placeOrder() {
        Connection connection = null;
        PreparedStatement orderStmt = null;
        PreparedStatement orderItemsStmt = null;
        
        try {
            connection = SqliteConnection.Connector();
            connection.setAutoCommit(false);
            
            // Get current timestamp for order date and time
            LocalDateTime now = LocalDateTime.now();
            String orderDate = now.toLocalDate().toString();
            String orderTime = now.toLocalTime().toString();
            
            // Generate order ID using OrderIdGenerator
            String orderId = OrderIdGenerator.generateOrderId();
            
            // Insert order into orders table
            String orderSql = "INSERT INTO orders (id, customer_name, order_type, payment_method, order_status, total_amount, order_date, order_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            orderStmt = connection.prepareStatement(orderSql);
            
            orderStmt.setString(1, orderId);
            orderStmt.setString(2, customerNameField.getText().trim());
            orderStmt.setString(3, orderTypeComboBox.getValue());
            orderStmt.setString(4, paymentMethodComboBox.getValue());
            orderStmt.setString(5, "Completed"); // Default status
            orderStmt.setDouble(6, shoppingCart.stream().mapToDouble(OrderItem::getTotalPrice).sum());
            orderStmt.setString(7, orderDate);
            orderStmt.setString(8, orderTime);
            
            orderStmt.executeUpdate();
            
            // Insert order items
            String orderItemsSql = "INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, total_price, customization_details) VALUES (?, ?, ?, ?, ?, ?, ?)";
            orderItemsStmt = connection.prepareStatement(orderItemsSql);
            
            // Update product stock
            String updateStockSql = "UPDATE products SET stock = stock - ? WHERE id = ?";
            try (PreparedStatement updateStockStmt = connection.prepareStatement(updateStockSql)) {
                for (OrderItem item : shoppingCart) {
                    // Insert order item
                    orderItemsStmt.setString(1, orderId);
                    orderItemsStmt.setInt(2, item.getProductId());
                    orderItemsStmt.setString(3, item.getProductName());
                    orderItemsStmt.setInt(4, item.getQuantity());
                    orderItemsStmt.setDouble(5, item.getUnitPrice());
                    orderItemsStmt.setDouble(6, item.getTotalPrice());
                    orderItemsStmt.setString(7, item.getCustomizationDetails());
                    orderItemsStmt.executeUpdate();
                    
                    // Update stock
                    updateStockStmt.setInt(1, item.getQuantity());
                    updateStockStmt.setInt(2, item.getProductId());
                    updateStockStmt.executeUpdate();
                }
            }
            
            connection.commit();
            return true;
            
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to save order: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (orderStmt != null) orderStmt.close();
                if (orderItemsStmt != null) orderItemsStmt.close();
                if (connection != null) {
                    connection.setAutoCommit(true);
                    connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void initializeComboBoxes() {
    	// Initialize product category filter
        productCategoryFilter.getItems().addAll(
            "All Categories", 
            "Premium Series",
            "Classic Series",
            "Latte Series",
            "Frappe Series", 
            "Healthy Fruit Tea", 
            "Hot Drinks", 
            "Food Pair"
        );
        productCategoryFilter.setValue("All Categories");

        // Initialize order type
        orderTypeComboBox.getItems().addAll("Dine-in", "Takeout", "Delivery");
        orderTypeComboBox.setValue("Dine-in");

        // Initialize payment method
        paymentMethodComboBox.getItems().addAll("Cash", "Card", "GCash", "PayMaya");
        paymentMethodComboBox.setValue("Cash");
    }

    private void initializeShoppingCartTable() {
        System.out.println("Initializing shopping cart table...");
        
        // Initialize shopping cart table columns with proper property binding
        cartProductColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getProductName()));
        cartPriceColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleDoubleProperty(cellData.getValue().getUnitPrice()).asObject());
        cartQuantityColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getQuantity()).asObject());
        cartTotalColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleDoubleProperty(cellData.getValue().getTotalPrice()).asObject());

        // Format price columns in cart
        cartPriceColumn.setCellFactory(column -> new TableCell<OrderItem, Double>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText("₱" + decimalFormat.format(price));
                }
            }
        });

        cartTotalColumn.setCellFactory(column -> new TableCell<OrderItem, Double>() {
            @Override
            protected void updateItem(Double total, boolean empty) {
                super.updateItem(total, empty);
                if (empty || total == null) {
                    setText(null);
                } else {
                    setText("₱" + decimalFormat.format(total));
                }
            }
        });

        // Add remove button in action column
        cartActionColumn.setCellFactory(column -> new TableCell<OrderItem, Button>() {
            private final Button removeButton = new Button("Remove");
            
            {
                removeButton.setStyle("-fx-background-color: linear-gradient(to bottom, #EF5350, #D32F2F); " +
                                    "-fx-background-radius: 8; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 12px;");
                removeButton.setPrefWidth(80);
                removeButton.setOnAction(event -> {
                    OrderItem item = getTableView().getItems().get(getIndex());
                    removeFromCart(item);
                });
            }

            @Override
            protected void updateItem(Button button, boolean empty) {
                super.updateItem(button, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(removeButton);
                }
            }
        });

        // Set the data source for the table
        shoppingCartTable.setItems(shoppingCart);
        
        // Enable table selection
        shoppingCartTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        
        System.out.println("Shopping cart table initialized successfully");
    }

    private void setupEventHandlers() {
        // Product search functionality
        productSearchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterProducts();
        });

        // Category filter functionality
        productCategoryFilter.valueProperty().addListener((observable, oldValue, newValue) -> {
            filterProducts();
        });

        // Shopping cart change listener to update total
        shoppingCart.addListener((javafx.collections.ListChangeListener.Change<? extends OrderItem> change) -> {
            updateOrderTotal();
        });
    }

    private void loadAvailableProducts() {
        availableProducts.clear();
        Connection connection = null;
        
        try {
            connection = SqliteConnection.Connector();
            String query = "SELECT * FROM products WHERE status = 'Available' AND stock > 0";
            
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                // Handle date_added as TEXT instead of TIMESTAMP
                String dateAddedString = resultSet.getString("date_added");
                LocalDateTime dateAdded;
                
                try {
                    // Try to parse as LocalDate first, then convert to LocalDateTime
                    if (dateAddedString != null && !dateAddedString.isEmpty()) {
                        if (dateAddedString.contains("T") || dateAddedString.contains(" ")) {
                            // Full datetime string
                            dateAdded = LocalDateTime.parse(dateAddedString.replace(" ", "T"));
                        } else {
                            // Just date string, add time
                            dateAdded = LocalDate.parse(dateAddedString).atStartOfDay();
                        }
                    } else {
                        dateAdded = LocalDateTime.now();
                    }
                } catch (Exception e) {
                    // If parsing fails, use current time
                    dateAdded = LocalDateTime.now();
                }
                
                Product product = new Product(
                    resultSet.getInt("id"),
                    resultSet.getString("name"),
                    resultSet.getString("category"),
                    resultSet.getDouble("price"),
                    resultSet.getInt("stock"),
                    resultSet.getString("status"),
                    resultSet.getString("image_path"),
                    dateAdded
                );
                availableProducts.add(product);
            }
            
            // Load product cards into FlowPane
            loadProductCards();
            
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Error loading products: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadProductCards() {
        productCardsContainer.getChildren().clear();
        
        for (Product product : availableProducts) {
            // Only show series and food pair categories, exclude Add-ons
            if (shouldDisplayProduct(product)) {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/ProductCard.fxml"));
                    Node productCard = loader.load();
                    
                    ProductCardController cardController = loader.getController();
                    cardController.setProduct(product);
                    cardController.setOrderController(this);
                    
                    productCardsContainer.getChildren().add(productCard);
                    
                } catch (IOException e) {
                    System.err.println("Error loading product card: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
    
    private boolean shouldDisplayProduct(Product product) {
        String category = product.getCategory();
        // Only display series and food pair categories, excluding Add-ons
        return category.contains("Series") || category.equals("Food Pair") || category.equals("Hot Drinks");
    }

    private void filterProducts() {
        String searchText = productSearchField.getText().toLowerCase();
        String selectedCategory = productCategoryFilter.getValue();
        
        productCardsContainer.getChildren().clear();
        
        for (Product product : availableProducts) {
            // Only show series and food pair categories, exclude Add-ons
            if (!shouldDisplayProduct(product)) {
                continue;
            }
            
            boolean matchesSearch = searchText.isEmpty() || 
                                  product.getName().toLowerCase().contains(searchText) ||
                                  product.getCategory().toLowerCase().contains(searchText);
            
            boolean matchesCategory = "All Categories".equals(selectedCategory) || 
                                    product.getCategory().equals(selectedCategory);
            
            if (matchesSearch && matchesCategory) {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/ProductCard.fxml"));
                    Node productCard = loader.load();
                    
                    ProductCardController cardController = loader.getController();
                    cardController.setProduct(product);
                    cardController.setOrderController(this);
                    
                    productCardsContainer.getChildren().add(productCard);
                    
                } catch (IOException e) {
                    System.err.println("Error loading product card: " + e.getMessage());
                }
            }
        }
    }

    public void addProductToCart(Product product, int quantity) {
        // Check if product already exists in cart
        for (OrderItem item : shoppingCart) {
            if (item.getProductId() == product.getId()) {
                // Update quantity
                item.setQuantity(item.getQuantity() + quantity);
                item.setTotalPrice(item.getQuantity() * item.getUnitPrice());
                shoppingCartTable.refresh();
                return;
            }
        }
        
        // Add new item to cart
        OrderItem newItem = new OrderItem(
            0, // orderId will be set when order is placed
            product.getId(),
            product.getName(),
            quantity,
            product.getPrice(),
            quantity * product.getPrice()
        );
        
        shoppingCart.add(newItem);
    }

    // Enhanced method to handle Add-ons and Cups combination logic
    public void addEnhancedProductToCart(Product product, int quantity, String selectedSize, 
                                       String selectedAddOn, String customProductName, double totalPrice) {
        
        // Special handling for Add-ons and Cups - they can be combined with other products
        if (product.getCategory().equals("Add-ons") || product.getCategory().equals("Cups")) {
            
            // Look for existing compatible items in cart that can be combined with this add-on/cup
            OrderItem compatibleItem = null;
            for (OrderItem item : shoppingCart) {
                // Find items from other categories that can be combined
                if (item.canCombineWithAddOns() && item.getQuantity() == quantity) {
                    compatibleItem = item;
                    break;
                }
            }
            
            if (compatibleItem != null) {
                // Combine with existing compatible item
                String combinedName = compatibleItem.getProductName();
                
                if (product.getCategory().equals("Add-ons")) {
                    combinedName += " + " + product.getName();
                } else if (product.getCategory().equals("Cups")) {
                    combinedName = product.getName() + " with " + compatibleItem.getProductName();
                }
                
                // Update the compatible item
                compatibleItem.setProductName(combinedName);
                compatibleItem.setUnitPrice(compatibleItem.getUnitPrice() + product.getPrice());
                compatibleItem.setTotalPrice(compatibleItem.getQuantity() * compatibleItem.getUnitPrice());
                
                // Update customization details
                String existingDetails = compatibleItem.getCustomizationDetails();
                String newDetails = existingDetails != null ? existingDetails : "";
                
                if (product.getCategory().equals("Add-ons")) {
                    newDetails += (newDetails.isEmpty() ? "" : ", ") + "Add-on: " + product.getName();
                } else {
                    newDetails += (newDetails.isEmpty() ? "" : ", ") + "Cup: " + product.getName();
                }
                
                compatibleItem.setCustomizationDetails(newDetails);
                shoppingCartTable.refresh();
                return;
            }
        }
        
        // Check if the exact same customized product already exists in cart
        for (OrderItem item : shoppingCart) {
            if (item.getProductId() == product.getId() && 
                areCustomizationsEqual(item, selectedSize, selectedAddOn)) {
                // Update quantity for existing customized item
                item.setQuantity(item.getQuantity() + quantity);
                item.setTotalPrice(item.getQuantity() * item.getUnitPrice());
                shoppingCartTable.refresh();
                return;
            }
        }
        
        // Create customization details string
        String customizationDetails = "";
        if (selectedSize != null && !selectedSize.equals("Medium")) {
            customizationDetails += "Size: " + selectedSize;
        }
        if (selectedAddOn != null && !selectedAddOn.equals("None")) {
            if (!customizationDetails.isEmpty()) customizationDetails += ", ";
            customizationDetails += "Add-on: " + selectedAddOn;
        }
        
        // Add new customized item to cart
        OrderItem newItem = new OrderItem(
            0, // orderId will be set when order is placed
            product.getId(),
            customProductName,
            quantity,
            totalPrice / quantity, // unit price
            totalPrice,
            selectedSize,
            selectedAddOn,
            customizationDetails.isEmpty() ? null : customizationDetails,
            product.getCategory()
        );
        
        shoppingCart.add(newItem);
    }
    
    // Helper method to check if customizations match
    private boolean areCustomizationsEqual(OrderItem item, String size, String addOn) {
        String itemAddOn = item.getAddOn();
        
        // Handle null values for add-ons only
        if (itemAddOn == null) itemAddOn = "None";
        if (addOn == null) addOn = "None";
        
        return itemAddOn.equals(addOn);
    }

    private void removeFromCart(OrderItem item) {
        shoppingCart.remove(item);
    }

    private void updateOrderTotal() {
        double total = shoppingCart.stream()
                                 .mapToDouble(OrderItem::getTotalPrice)
                                 .sum();
        orderTotalField.setText("₱" + decimalFormat.format(total));
    }

    private void clearOrderForm() {
        customerNameField.setText("None"); // Set default value to "None"
        shoppingCart.clear();
        orderTotalField.setText("₱0.00");
        orderTypeComboBox.setValue("Dine-in");
        paymentMethodComboBox.setValue("Cash");
    }

    @FXML
    private void handlePlaceOrder(ActionEvent event) {
        if (!shoppingCart.isEmpty()) {
            // Ensure customer name is set to "None" if empty
            if (customerNameField.getText().trim().isEmpty()) {
                customerNameField.setText("None");
            }
            
            if (placeOrder()) {
                showAlert(Alert.AlertType.INFORMATION, "Success", "Order placed successfully!");
                clearOrderForm();
            }
        } else {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please add items to cart before placing order.");
        }
    }

    @FXML
    private void handleRefreshOrder(ActionEvent event) {
        loadAvailableProducts();
    }

    @FXML
    private void handleClearFilter(ActionEvent event) {
        productSearchField.clear();
        productCategoryFilter.setValue("All Categories");
        filterProducts();
    }

    @FXML
    private void handleClearCart(ActionEvent event) {
        if (!shoppingCart.isEmpty()) {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Clear Cart");
            confirmAlert.setHeaderText("Are you sure you want to clear the cart?");
            confirmAlert.setContentText("All items will be removed from your cart.");

            Optional<ButtonType> result = confirmAlert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                shoppingCart.clear();
                showAlert(Alert.AlertType.INFORMATION, "Cart Cleared", "All items have been removed from your cart.");
            }
        }
    }

    @FXML
    private void handlePrintReceipt(ActionEvent event) {
        if (shoppingCart.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Items", "Please add items to cart before printing receipt.");
            return;
        }

        String customerName = customerNameField.getText().trim();
        if (customerName.isEmpty()) {
            customerName = "None";
            customerNameField.setText(customerName);
        }

        try {
            // Generate unique order ID using OrderIdGenerator
            String orderId = OrderIdGenerator.generateOrderId();
            
            // Get order details
            String orderType = orderTypeComboBox.getValue();
            String paymentMethod = paymentMethodComboBox.getValue();
            double totalAmount = shoppingCart.stream().mapToDouble(OrderItem::getTotalPrice).sum();
            
            // Get current stage
            Stage currentStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            
            // Convert shopping cart to list for receipt generator
            List<OrderItem> orderItems = new ArrayList<>(shoppingCart);
            
            // Generate receipt using ReceiptGenerator
            boolean success = ReceiptGenerator.generateReceipt(
                currentStage, 
                orderId,
                customerName,
                orderType, 
                paymentMethod, 
                totalAmount, 
                orderItems
            );
            
            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Receipt", "Receipt generated and saved successfully!");
            }

        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not generate receipt: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Navigation methods
    @FXML
    private void handleDashboardButton(ActionEvent event) {
        try {
            loadScene(event, "/view/fxml/Dashboard.fxml");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not load Dashboard page: " + e.getMessage());
        }
    }

    @FXML
    private void handleInventoryButton(ActionEvent event) {
        try {
            loadScene(event, "/view/fxml/Inventory.fxml");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not load Inventory page: " + e.getMessage());
        }
    }

    @FXML
    private void handleOrderButton(ActionEvent event) {
        // Already on order page
        loadAvailableProducts();
    }

    @FXML
    private void handleRecentOrderButton(ActionEvent event) {
        try {
            loadScene(event, "/view/fxml/RecentOrder.fxml");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not load Recent Orders page: " + e.getMessage());
        }
    }

    @FXML
    private void handleLogoutButton(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Logout Confirmation");
        alert.setHeaderText("Are you sure you want to logout?");
        alert.setContentText("You will be redirected to the login page.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                loadScene(event, "/view/fxml/LoginPage.fxml");
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Error", "Could not load Login page: " + e.getMessage());
            }
        }
    }

    // Utility methods
    private void loadScene(ActionEvent event, String fxmlPath) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        Scene scene = new Scene(root);
        stage.setTitle("Sebucha Order Management System");
        stage.setScene(scene);
        stage.show();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
