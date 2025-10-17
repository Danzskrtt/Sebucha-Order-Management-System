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
    @FXML private Button receiptButton; // Add receipt button field

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
        initializeComboBoxes();
        initializeShoppingCartTable();
        loadAvailableProducts();
        setupEventHandlers();
        clearOrderForm();
    }

    private void initializeComboBoxes() {
        // Initialize product category filter
        productCategoryFilter.getItems().addAll("All Categories", "Coffee", "Tea", "Pastries", "Sandwiches", "Beverages");
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

    private void filterProducts() {
        String searchText = productSearchField.getText().toLowerCase();
        String selectedCategory = productCategoryFilter.getValue();
        
        productCardsContainer.getChildren().clear();
        
        for (Product product : availableProducts) {
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

    private void removeFromCart(OrderItem item) {
        shoppingCart.remove(item);
    }

    private void updateOrderTotal() {
        double total = shoppingCart.stream()
                                 .mapToDouble(OrderItem::getTotalPrice)
                                 .sum();
        orderTotalField.setText("₱" + decimalFormat.format(total));
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
    private void handlePlaceOrder(ActionEvent event) {
        if (validateOrderForm()) {
            if (placeOrder()) {
                showAlert(Alert.AlertType.INFORMATION, "Success", "Order placed successfully!");
                clearOrderForm();
            }
        }
    }

    @FXML
    private void handleClearCart(ActionEvent event) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Clear Order");
        confirmAlert.setHeaderText("Clear Order Confirmation");
        confirmAlert.setContentText("Are you sure you want to clear all items from the Order?");
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            shoppingCart.clear();
            clearOrderForm();
        }
    }

    @FXML
    private void handlePrintReceipt(ActionEvent event) {
        // Check if there's a recent order to print receipt for
        if (lastPlacedOrderId == null || lastPlacedOrderId.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Recent Order", 
                     "Please place an order first before printing a receipt.");
            return;
        }
        
        // Check if we have the last order data
        if (lastOrderData == null || lastOrderData.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Order Data", 
                     "No order data available for receipt printing.");
            return;
        }
        
        // Get current stage for file chooser
        Stage currentStage = (Stage) receiptButton.getScene().getWindow();
        
        // Generate receipt using the ReceiptGenerator
        boolean success = ReceiptGenerator.generateReceipt(
            currentStage,
            lastPlacedOrderId,
            lastCustomerName != null ? lastCustomerName : "Walk-in Customer",
            lastOrderType != null ? lastOrderType : "Dine-in",
            lastPaymentMethod != null ? lastPaymentMethod : "Cash",
            lastTotalAmount,
            lastOrderData
        );
        
        if (success) {
            System.out.println("Receipt generated successfully for order: " + lastPlacedOrderId);
        }
    }

    // Add fields to store last order information for receipt generation
    private String lastPlacedOrderId;
    private String lastCustomerName;
    private String lastOrderType;
    private String lastPaymentMethod;
    private double lastTotalAmount;
    private List<OrderItem> lastOrderData;

    private boolean validateOrderForm() {
        if (customerNameField.getText().trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please enter customer name.");
            return false;
        }
        
        if (shoppingCart.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please add items to cart before placing order.");
            return false;
        }
        
        if (orderTypeComboBox.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please select order type.");
            return false;
        }
        
        if (paymentMethodComboBox.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please select payment method.");
            return false;
        }
        
        return true;
    }

    private boolean placeOrder() {
        Connection connection = null;
        
        try {
            connection = SqliteConnection.Connector();
            connection.setAutoCommit(false);
            
            // Generate custom order ID using OrderIdGenerator
            String customOrderId = OrderIdGenerator.generateOrderId();
            
            // Prepare date and time as TEXT per DB schema
            LocalDateTime now = LocalDateTime.now();
            String orderDateStr = now.toLocalDate().toString(); // YYYY-MM-DD
            String orderTimeStr = now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            
            // Modified query to include custom order_id
            String orderQuery = "INSERT INTO orders (id, customer_name, order_type, payment_method, total_amount, order_date, order_time, order_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement orderStatement = connection.prepareStatement(orderQuery);
            
            double totalAmount = shoppingCart.stream().mapToDouble(OrderItem::getTotalPrice).sum();
            
            orderStatement.setString(1, customOrderId); // Use custom generated ID
            orderStatement.setString(2, customerNameField.getText().trim());
            orderStatement.setString(3, orderTypeComboBox.getValue());
            orderStatement.setString(4, paymentMethodComboBox.getValue());
            orderStatement.setDouble(5, totalAmount);
            orderStatement.setString(6, orderDateStr);
            orderStatement.setString(7, orderTimeStr);
            orderStatement.setString(8, "Completed");
            
            orderStatement.executeUpdate();
            
            // Insert order items using the custom order ID
            String itemQuery = "INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, total_price) VALUES (?, ?, ?, ?, ?, ?)";
            PreparedStatement itemStatement = connection.prepareStatement(itemQuery);
            
            for (OrderItem item : shoppingCart) {
                itemStatement.setString(1, customOrderId); // Use custom order ID
                itemStatement.setInt(2, item.getProductId());
                itemStatement.setString(3, item.getProductName());
                itemStatement.setInt(4, item.getQuantity());
                itemStatement.setDouble(5, item.getUnitPrice());
                itemStatement.setDouble(6, item.getTotalPrice());
                itemStatement.addBatch();
                
                String updateStockQuery = "UPDATE products SET stock = stock - ? WHERE id = ?";
                try (PreparedStatement stockStatement = connection.prepareStatement(updateStockQuery)) {
                    stockStatement.setInt(1, item.getQuantity());
                    stockStatement.setInt(2, item.getProductId());
                    stockStatement.executeUpdate();
                }
            }
            
            itemStatement.executeBatch();
            itemStatement.close();
            orderStatement.close();
            connection.commit();
            
            // Show success message with the generated order ID
            showAlert(Alert.AlertType.INFORMATION, "Success", 
                     "Order placed successfully!\nOrder ID: " + customOrderId);
            
            // Save last order information for receipt generation
            lastPlacedOrderId = customOrderId;
            lastCustomerName = customerNameField.getText().trim();
            lastOrderType = orderTypeComboBox.getValue();
            lastPaymentMethod = paymentMethodComboBox.getValue();
            lastTotalAmount = totalAmount;
            lastOrderData = new ArrayList<>(shoppingCart);
            
            return true;
            
        } catch (SQLException e) {
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
            showAlert(Alert.AlertType.ERROR, "Database Error", "Error placing order: " + e.getMessage());
            return false;
        } finally {
            try {
                if (connection != null) {
                    connection.setAutoCommit(true);
                    connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void clearOrderForm() {
        customerNameField.setText("None");
        orderTypeComboBox.setValue("Dine-in");
        paymentMethodComboBox.setValue("Cash");
        orderTotalField.setText("₱0.00");
        shoppingCart.clear();
    }

    // Navigation methods
    @FXML
    private void handleDashboardButton(ActionEvent event) {
        navigateToScene(event, "/view/fxml/Dashboard.fxml");
    }

    @FXML
    private void handleInventoryButton(ActionEvent event) {
        navigateToScene(event, "/view/fxml/Inventory.fxml");
    }

    @FXML
    private void handleOrderButton(ActionEvent event) {
        // Already on order page
    }

    @FXML
    private void handleRecentOrderButton(ActionEvent event) {
        navigateToScene(event, "/view/fxml/RecentOrder.fxml");
    }

    @FXML
    private void handleLogoutButton(ActionEvent event) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Logout");
        confirmAlert.setHeaderText("Confirm Logout");
        confirmAlert.setContentText("Are you sure you want to logout?");
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            navigateToScene(event, "/view/fxml/LoginPage.fxml");
        }
    }

    private void navigateToScene(ActionEvent event, String fxmlPath) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            Scene scene = new Scene(root);
            stage.setTitle("Sebucha Order Management System");
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Navigation Error", "Could not load page: " + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(alertType);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
