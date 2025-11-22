package controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import model.Order;
import model.OrderItem;
import model.ReceiptGenerator;
import model.SqliteConnection;
import java.io.File;
import java.io.FileWriter;
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

//RecentOrderController

//Displays recent orders filtering (search, status, payment, date range),
//shows summary stats, supports viewing details, reprinting receipts, updating status
//exporting to Excel, and navigation.
 
public class RecentOrderController implements Initializable {

    // Navigation buttons
    @FXML private Button dashboardbutton;
    @FXML private Button inventorybutton;
    @FXML private Button orderbutton;
    @FXML private Button recentorderbutton;
    @FXML private Button logoutbutton;

    // Filter controls
    @FXML private TextField customerSearchField;
    @FXML private ComboBox<String> statusFilterComboBox;
    @FXML private ComboBox<String> paymentFilterComboBox;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private Button refreshButton;
    @FXML private Button clearFiltersButton;
    @FXML private Button exportButton;

    // Orders table
    @FXML private TableView<Order> ordersTable;
    @FXML private TableColumn<Order, String> orderIdColumn; 
    @FXML private TableColumn<Order, String> customerNameColumn;
    @FXML private TableColumn<Order, String> orderDateColumn;
    @FXML private TableColumn<Order, String> orderTypeColumn;
    @FXML private TableColumn<Order, String> paymentMethodColumn;
    @FXML private TableColumn<Order, String> totalAmountColumn;
    @FXML private TableColumn<Order, String> statusColumn;
    @FXML private TableColumn<Order, String> itemsColumn;
    @FXML private TableColumn<Order, String> actionsColumn;

    // Summary labels
    @FXML private Label totalOrdersLabel;
    @FXML private Label totalRevenueLabel;
    @FXML private Label avgOrderValueLabel;
    @FXML private Label dateRangeLabel;

    // Data collections
    private ObservableList<Order> allOrders = FXCollections.observableArrayList();
    private ObservableList<Order> filteredOrders = FXCollections.observableArrayList();
    private DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
    private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    // Initialize table, filters, listeners, load orders, and update summaries.
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            System.out.println("Initializing RecentOrderController...");
            setupTableColumns();
            initializeFilters();
            setupEventHandlers();
            loadOrderHistory();
            updateSummaryStatistics();
            System.out.println("RecentOrderController initialized successfully.");
        } catch (Exception e) {
            System.err.println("Error initializing RecentOrderController: " + e.getMessage());
            e.printStackTrace();
         
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Initialization Error");
                alert.setHeaderText("Failed to initialize Recent Orders");
                alert.setContentText("Error: " + e.getMessage());
                alert.showAndWait();
            });
        }
    }

    // Build column bindings and cell renderers (status colors, amount format, action buttons)
    
    private void setupTableColumns() {
        // Configure table columns with centered text
        orderIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        orderIdColumn.setCellFactory(col -> new TableCell<Order, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-alignment: center; -fx-padding: 8px;");
                }
            }
        });
        
        customerNameColumn.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        customerNameColumn.setCellFactory(col -> new TableCell<Order, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-alignment: center; -fx-padding: 8px;");
                }
            }
        });
        
        orderTypeColumn.setCellValueFactory(new PropertyValueFactory<>("orderType"));
        orderTypeColumn.setCellFactory(col -> new TableCell<Order, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-alignment: center; -fx-padding: 8px;");
                }
            }
        });
        
        paymentMethodColumn.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        paymentMethodColumn.setCellFactory(col -> new TableCell<Order, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-alignment: center; -fx-padding: 8px;");
                }
            }
        });
        
        // Status column with color coding based on order status
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusColumn.setCellFactory(col -> new TableCell<Order, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    
                    // Apply color based on status
                    String cellStyle = "-fx-alignment: center; -fx-font-weight: bold; " +
                                     "-fx-padding: 6px 12px; ";
                    
                    switch (status.toLowerCase()) {
                        case "pending":
                            cellStyle += "-fx-background-color: #FFD966; -fx-text-fill: #8B4513;";
                            break;
                        case "completed":
                            cellStyle += "-fx-background-color: #4CAF50; -fx-text-fill: white;";
                            break;
                        case "cancelled":
                            cellStyle += "-fx-background-color: #F44336; -fx-text-fill: white;";
                            break;
                        default:
                            cellStyle += "-fx-background-color: #E0E0E0; -fx-text-fill: #333333;";
                            break;
                    }
                    
                    setStyle(cellStyle);
                }
            }
        });
        
        // Format date column with center alignment
        orderDateColumn.setCellValueFactory(cellData -> {
            LocalDateTime orderDate = cellData.getValue().getOrderDate();
            return new SimpleStringProperty(orderDate != null ? orderDate.format(dateFormatter) : "N/A");
        });
        orderDateColumn.setCellFactory(col -> new TableCell<Order, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-alignment: center; -fx-padding: 8px;");
                }
            }
        });
        
        // Format total amount column with center alignment
        totalAmountColumn.setCellValueFactory(cellData -> {
            double amount = cellData.getValue().getTotalAmount();
            return new SimpleStringProperty("₱" + decimalFormat.format(amount));
        });
        
        totalAmountColumn.setCellFactory(col -> new TableCell<Order, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                   
                    setStyle("-fx-font-weight: bold; " +
                           "-fx-text-fill: #28a745; " +  
                           "-fx-alignment: center; " +
                           "-fx-padding: 8px;");
                }
            }
        });
        
        // Items column with center alignment
        itemsColumn.setCellValueFactory(cellData -> {
            Order order = cellData.getValue();
            String itemsSummary = getOrderItemsSummary(order.getId());
            return new SimpleStringProperty(itemsSummary);
        });
        
        itemsColumn.setCellFactory(col -> new TableCell<Order, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-font-weight: bold; -fx-alignment: center; -fx-padding: 8px;");
                }
            }
        });
        
        actionsColumn.setPrefWidth(150);
        actionsColumn.setMaxWidth(150);
        actionsColumn.setMinWidth(150);
        
   
        actionsColumn.setCellFactory(col -> new TableCell<Order, String>() {
            private final Button viewButton = new Button();
            private final Button reprintButton = new Button();
            private final Button statusButton = new Button();
            private final javafx.scene.layout.HBox buttonBox = new javafx.scene.layout.HBox(6); 
            
            {
                
                buttonBox.setAlignment(javafx.geometry.Pos.CENTER);
                buttonBox.setPadding(new javafx.geometry.Insets(4, 4, 4, 4));
                
                
                try {
                    // View Details button 
                    ImageView viewIcon = new ImageView(new Image(getClass().getResourceAsStream("/view/images/view_details.png")));
                    viewIcon.setFitWidth(30);
                    viewIcon.setFitHeight(30);
                    viewIcon.setPreserveRatio(true);
                    viewButton.setGraphic(viewIcon);
                    viewButton.setTooltip(new Tooltip("View Details"));
                    
                    // Reprint button 
                    ImageView reprintIcon = new ImageView(new Image(getClass().getResourceAsStream("/view/images/reprint.png")));
                    reprintIcon.setFitWidth(30);
                    reprintIcon.setFitHeight(30);
                    reprintIcon.setPreserveRatio(true);
                    reprintButton.setGraphic(reprintIcon);
                    reprintButton.setTooltip(new Tooltip("Reprint Invoice"));
                    
                    // Update Status button
                    ImageView statusIcon = new ImageView(new Image(getClass().getResourceAsStream("/view/images/update_status.png")));
                    statusIcon.setFitWidth(30);
                    statusIcon.setFitHeight(30);
                    statusIcon.setPreserveRatio(true);
                    statusButton.setGraphic(statusIcon);
                    statusButton.setTooltip(new Tooltip("Update Status"));
                    
                } catch (Exception e) {
                    System.err.println("Error loading button icons: " + e.getMessage());
                    viewButton.setText("View");
                    reprintButton.setText("Print");
                    statusButton.setText("Status");
                }
                
                // Style for View Details button
                viewButton.setStyle(
                    "-fx-background-color: linear-gradient(to bottom, #C4B5FD 0%, #8B5CF6 50%, #5B21B6 100%);" +
                    "-fx-background-radius: 8;" +
                    "-fx-text-fill: white;" +
                    "-fx-font-family: 'Calibri';" +
                    "-fx-font-size: 10px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-cursor: hand;" +
                    "-fx-padding: 2 2;" + 
                    "-fx-pref-width: 30px;" + 
                    "-fx-pref-height: 30px;" 
                );
                
                // Style for Reprint button 
                reprintButton.setStyle(
                    "-fx-background-color: linear-gradient(to bottom, #4ADE80 0%, #22C55E 50%, #16A34A 100%);" +
                    "-fx-background-radius: 8;" +
                    "-fx-text-fill: white;" +
                    "-fx-font-family: 'Calibri';" +
                    "-fx-font-size: 10px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-cursor: hand;" +
                    "-fx-padding: 2 2;" + 
                    "-fx-pref-width: 30px;" + 
                    "-fx-pref-height: 30px;" 
                );
                
                // Style for Update Status button 
                statusButton.setStyle(
                    "-fx-background-color: linear-gradient(to bottom, #2196F3 0%, #1E88E5 50%, #1976D2 100%);" +
                    "-fx-background-radius: 8;" +
                    "-fx-text-fill: white;" +
                    "-fx-font-family: 'Calibri';" +
                    "-fx-font-size: 10px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-cursor: hand;" +
                    "-fx-padding: 2 2;" + 
                    "-fx-pref-width: 30px;" + 
                    "-fx-pref-height: 30px;" 
                );
                
                // Set button actions
                viewButton.setOnAction(event -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        viewOrderDetails(getTableRow().getItem());
                    }
                });
                
                reprintButton.setOnAction(event -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        reprintInvoice(getTableRow().getItem());
                    }
                });
                
                statusButton.setOnAction(event -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        updateOrderStatus(getTableRow().getItem());
                    }
                });
                
                buttonBox.getChildren().addAll(viewButton, reprintButton, statusButton);
            }
            
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(buttonBox);
                }
            }
        });
        
        // Set table data
        ordersTable.setItems(filteredOrders);
    }

    // filter combo boxes and default date range
    private void initializeFilters() {
        // Initialize status filter
        statusFilterComboBox.getItems().addAll("All Status", "Completed", "Pending", "Cancelled");
        statusFilterComboBox.setValue("All Status");
        
        // Initialize payment method filter
        paymentFilterComboBox.getItems().addAll("All Payments", "Cash", "Card", "GCash", "Gothyme");
        paymentFilterComboBox.setValue("All Payments");
        
        // Set default date range (last 30 days)
        toDatePicker.setValue(LocalDate.now());
        fromDatePicker.setValue(LocalDate.now().minusDays(30));
    }

    // Wire filter/search/date listeners to re-apply filters on change
    private void setupEventHandlers() {
        // Search field listener
        customerSearchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        
        // Filter combo boxes listeners
        statusFilterComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        paymentFilterComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        
        // Date picker listeners
        fromDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        toDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
    }

    // Load orders from db with null-safe fields, then apply filters and update UI
    private void loadOrderHistory() {
        allOrders.clear();
        Connection connection = null;
        
        try {
            connection = SqliteConnection.Connector();
            if (connection == null) {
                System.err.println("Failed to establish database connection");
                showAlert("Database Error", "Could not connect to database");
                return;
            }
            
            //check if the orders table exists and what columns it has
            String checkTableQuery = "SELECT name FROM sqlite_master WHERE type='table' AND name='orders'";
            PreparedStatement checkStmt = connection.prepareStatement(checkTableQuery);
            ResultSet tableCheck = checkStmt.executeQuery();
            
            if (!tableCheck.next()) {
                System.err.println("Orders table does not exist");
                showAlert("Database Error", "Orders table not found in database");
                return;
            }
            
            // Use a safer query that handles missing columns
            String query = "SELECT id, " +
                          "COALESCE(customer_name, 'N/A') as customer_name, " +
                          "COALESCE(order_type, 'N/A') as order_type, " +
                          "COALESCE(payment_method, 'N/A') as payment_method, " +
                          "COALESCE(total_amount, 0) as total_amount, " +
                          "COALESCE(order_date, date('now')) as order_date, " +
                          "COALESCE(order_time, time('now')) as order_time, " +
                          "COALESCE(order_status, 'Pending') as order_status " +
                          "FROM orders ORDER BY order_date DESC, order_time DESC";
            
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();
            
            int loadedOrders = 0;
            while (resultSet.next()) {
                try {
                    // Safely get values with null checks
                    String orderId = resultSet.getString("id");
                    String customerName = resultSet.getString("customer_name");
                    String orderType = resultSet.getString("order_type");
                    String paymentMethod = resultSet.getString("payment_method");
                    double totalAmount = resultSet.getDouble("total_amount");
                    String orderStatus = resultSet.getString("order_status");
                    
                    // Combine order_date and order_time into a single timestamp
                    String datePart = resultSet.getString("order_date");
                    String timePart = resultSet.getString("order_time");
                    LocalDateTime orderDate = parseOrderDateTime(datePart, timePart);
                    
                    // Create order object with null safety
                    Order order = new Order(
                        orderId != null ? orderId : "N/A",
                        customerName != null ? customerName : "N/A",
                        orderType != null ? orderType : "N/A",
                        paymentMethod != null ? paymentMethod : "N/A",
                        totalAmount,
                        orderDate,
                        orderStatus != null ? orderStatus : "Pending"
                    );
                    
                    allOrders.add(order);
                    loadedOrders++;
                } catch (Exception e) {
                    System.err.println("Error parsing order row: " + e.getMessage());
                    // Continue with next row instead of failing completely
                }
            }
            
            System.out.println("Successfully loaded " + loadedOrders + " orders");
            applyFilters();
            
        } catch (SQLException e) {
            System.err.println("SQLException in loadOrderHistory: " + e.getMessage());
            e.printStackTrace();
            showAlert("Database Error", 
                     "Error loading order history: " + e.getMessage() + 
                     "\n\nPlease check if the database is accessible and contains the required tables.");
        } catch (Exception e) {
            System.err.println("Unexpected error in loadOrderHistory: " + e.getMessage());
            e.printStackTrace();
            showAlert("Unexpected Error", 
                     "An unexpected error occurred: " + e.getMessage());
        } finally {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }

    // Safely parse separate date/time strings into a LocalDateTime
    private LocalDateTime parseOrderDateTime(String datePart, String timePart) {
        try {
            if (datePart == null || datePart.isEmpty()) {
                return LocalDateTime.now();
            }
            if (timePart == null || timePart.isEmpty()) {
                return parseOrderDate(datePart); 
            }
            return LocalDateTime.parse(datePart + "T" + timePart);
        } catch (Exception ex) {
            try {
                return LocalDateTime.parse((datePart + " " + timePart).replace(" ", "T"));
            } catch (Exception ignored) {
                return parseOrderDate(datePart);
            }
        }
    }

    // Apply all active filters to the in-memory orders list and refresh summaries
    private void applyFilters() {
        filteredOrders.clear();
        
        String searchText = customerSearchField.getText() != null ? customerSearchField.getText().toLowerCase() : "";
        String statusFilter = statusFilterComboBox.getValue();
        String paymentFilter = paymentFilterComboBox.getValue();
        LocalDate fromDate = fromDatePicker.getValue();
        LocalDate toDate = toDatePicker.getValue();
        
        for (Order order : allOrders) {
            boolean matches = true;
            
            // Customer name filter
            if (!searchText.isEmpty() && order.getCustomerName() != null) {
                matches = matches && order.getCustomerName().toLowerCase().contains(searchText);
            }
            
            // Status filter
            if (!"All Status".equals(statusFilter) && order.getStatus() != null) {
                matches = matches && statusFilter.equals(order.getStatus());
            }
            
            // Payment method filter
            if (!"All Payments".equals(paymentFilter) && order.getPaymentMethod() != null) {
                matches = matches && paymentFilter.equals(order.getPaymentMethod());
            }
            
            // Date range filter
            if (fromDate != null && toDate != null && order.getOrderDate() != null) {
                LocalDate orderDate = order.getOrderDate().toLocalDate();
                matches = matches && !orderDate.isBefore(fromDate) && !orderDate.isAfter(toDate);
            }
            
            if (matches) {
                filteredOrders.add(order);
            }
        }
        
        updateSummaryStatistics();
        updateDateRangeLabel();
    }

    // Compute and render total orders, revenue, and average order value
    private void updateSummaryStatistics() {
        int totalOrders = filteredOrders.size();
        double totalRevenue = filteredOrders.stream()
                                          .mapToDouble(Order::getTotalAmount)
                                          .sum();
        double avgOrderValue = totalOrders > 0 ? totalRevenue / totalOrders : 0;
        
        totalOrdersLabel.setText("Total Orders: " + totalOrders);
        totalRevenueLabel.setText("Total Revenue: ₱" + decimalFormat.format(totalRevenue));
        avgOrderValueLabel.setText("Avg Order: ₱" + decimalFormat.format(avgOrderValue));
    }

    // Update the date range label based on pickers
    private void updateDateRangeLabel() {
        LocalDate fromDate = fromDatePicker.getValue();
        LocalDate toDate = toDatePicker.getValue();
        
        if (fromDate != null && toDate != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
            dateRangeLabel.setText("Showing: " + fromDate.format(formatter) + " - " + toDate.format(formatter));
        } else {
            dateRangeLabel.setText("Showing: All Orders");
        }
    }

    // Show a simple dialog with full details of the selected order
    private void viewOrderDetails(Order order) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Order Details");
        alert.setHeaderText("Order #" + order.getId());
        
        StringBuilder details = new StringBuilder();
        details.append("Customer: ").append(order.getCustomerName()).append("\n");
        details.append("Date: ").append(order.getOrderDate().format(dateFormatter)).append("\n");
        details.append("Type: ").append(order.getOrderType()).append("\n");
        details.append("Payment: ").append(order.getPaymentMethod()).append("\n");
        details.append("Status: ").append(order.getStatus()).append("\n");
        details.append("Total: ₱").append(decimalFormat.format(order.getTotalAmount())).append("\n\n");
        details.append("Items:\n").append(getDetailedOrderItems(order.getId()));
        
        alert.setContentText(details.toString());
        alert.showAndWait();
    }

    // Re-generate a receipt PDF for an existing order
    private void reprintInvoice(Order order) {
        try {
            // Get order items for the receipt
            List<OrderItem> orderItems = getOrderItemsForReceipt(order.getId());
            
            // Get current stage
            Stage currentStage = (Stage) ordersTable.getScene().getWindow();
            
            // Generate receipt using ReceiptGenerator
            boolean success = ReceiptGenerator.generateReceipt(
                currentStage,
                order.getId(),
                order.getCustomerName(),
                order.getOrderType(),
                order.getPaymentMethod(),
                order.getTotalAmount(),
                orderItems
            );
            
            if (success) {
                showAlert("Reprint Invoice", "Invoice reprinted successfully!");
            } else {
                showAlert("Reprint Invoice", "Failed to reprint invoice.");
            }
        } catch (Exception e) {
            showAlert("Reprint Invoice", "Error reprinting invoice: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // when cancelling order restores stock automatically
    private void updateOrderStatus(Order order) {
        // Prevent updating status of cancelled orders
        if ("Cancelled".equals(order.getStatus())) {
            showAlert("Status Update Not Allowed", 
                     "Cannot update the status of a cancelled order. Cancelled orders are final and cannot be modified.");
            return;
        }
        
        // Create choice dialog for status selection
        ChoiceDialog<String> dialog = new ChoiceDialog<>("Pending", "Pending", "Completed", "Cancelled");
        dialog.setTitle("Update Order Status");
        dialog.setHeaderText("Update status for Order #" + order.getId());
        dialog.setContentText("Select new status:");
        
        dialog.setSelectedItem(order.getStatus());
        
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String newStatus = result.get();
            String previousStatus = order.getStatus();
            
            // Additional safety check - prevent any changes to cancelled orders
            if ("Cancelled".equals(previousStatus)) {
                showAlert("Status Update Not Allowed", 
                         "This order has already been cancelled and cannot be modified.");
                return;
            }
            
            // Check if order is being cancelled and handle stock restoration
            if ("Cancelled".equals(newStatus)) {
                Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
                confirmationAlert.setTitle("Confirm Order Cancellation");
                confirmationAlert.setHeaderText("Cancel Order #" + order.getId());
                confirmationAlert.setContentText(
                    "⚠️WARNING: This action cannot be undone!\n\n" +
                    "This will:\n" +
                    "• Cancel the order permanently\n" +
                    "• Restore all items back to inventory stock\n" +
                    "• Prevent any future status changes\n\n" +
                    "Are you sure you want to cancel this order?"
                );
                
                try {
                    confirmationAlert.getDialogPane().setStyle(
                        "-fx-font-size: 14px; " +
                        "-fx-padding: 20px; " +
                        "-fx-background-color: #f8f9fa;"
                    );
                } catch (Exception styleEx) {
                    System.out.println("Could not apply custom styling to dialog: " + styleEx.getMessage());
                }
                
                Optional<ButtonType> confirmResult = confirmationAlert.showAndWait();
                if (confirmResult.isPresent() && confirmResult.get() == ButtonType.OK) {
                    if (isOrderAlreadyCancelled(order.getId())) {
                        showAlert("Order Already Cancelled", 
                                 "This order has already been cancelled by another process. Refreshing order list");
                        loadOrderHistory(); 
                        return;
                    }
                    
                    // Restore stock first
                    if (restoreOrderItemsToInventory(order.getId())) {
                        // Update status in database
                        if (updateOrderStatusInDatabase(order.getId(), newStatus)) {
                            // Update the order object
                            order.setStatus(newStatus);
                            
                            ordersTable.refresh();
                            
                            showAlert("Order Cancelled", 
                                     "Order #" + order.getId() + " has been permanently cancelled and inventory stock has been restored.\n\n" +
                                     "This order can no longer be modified.");
                        } else {
                            showAlert("Update Failed", "Failed to update order status in database.");
                        }
                    } else {
                        showAlert("Cancellation Failed", "Failed to restore inventory stock. Order status not changed.");
                    }
                }
            } else {
                if (updateOrderStatusInDatabase(order.getId(), newStatus)) {
                    order.setStatus(newStatus);
                    
                    // Refresh the table
                    ordersTable.refresh();
                    
                    showAlert("Status Updated", "Order status updated to: " + newStatus);
                } else {
                    showAlert("Update Failed", "Failed to update order status in database.");
                }
            }
        }
    }
    
    //status change to the orders table
    private boolean updateOrderStatusInDatabase(String orderId, String newStatus) {
        Connection connection = null;
        try {
            connection = SqliteConnection.Connector();
            String query = "UPDATE orders SET order_status = ? WHERE id = ?";
            
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, newStatus);
            statement.setString(2, orderId);
            
            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Error updating order status: " + e.getMessage());
            return false;
        } finally {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    
    // Fetch order items from DB for receipt generation
    private List<OrderItem> getOrderItemsForReceipt(String orderId) {
        List<OrderItem> orderItems = new ArrayList<>();
        Connection connection = null;
        
        try {
            connection = SqliteConnection.Connector();
            String query = "SELECT oi.product_id, oi.product_name, oi.quantity, oi.unit_price, oi.total_price, " +
                          "oi.customization_details FROM order_items oi WHERE oi.order_id = ?";
            
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, orderId);
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                OrderItem item = new OrderItem();
                item.setProductId(resultSet.getInt("product_id"));
                item.setProductName(resultSet.getString("product_name"));
                item.setQuantity(resultSet.getInt("quantity"));
                item.setUnitPrice(resultSet.getDouble("unit_price"));
                item.setTotalPrice(resultSet.getDouble("total_price"));
                item.setCustomizationDetails(resultSet.getString("customization_details"));
                
                orderItems.add(item);
            }
            
        } catch (SQLException e) {
            System.err.println("Error loading order items for receipt: " + e.getMessage());
        } finally {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        return orderItems;
    }

    // items view for a given order - now includes add-ons from customization details
    private String getDetailedOrderItems(String orderId) {
        Connection connection = null;
        StringBuilder items = new StringBuilder();
        
        try {
            connection = SqliteConnection.Connector();
            // Updated query to get customization_details and product_name from order_items
            String query = "SELECT oi.quantity, oi.unit_price, oi.total_price, " +
                          "COALESCE(oi.product_name, p.name) AS display_name, " +
                          "oi.customization_details " +
                          "FROM order_items oi LEFT JOIN products p ON oi.product_id = p.id " +
                          "WHERE oi.order_id = ?";
            
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, orderId);
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                String displayName = resultSet.getString("display_name");
                String customizationDetails = resultSet.getString("customization_details");
                
                // Build the display name with add-ons
                StringBuilder itemName = new StringBuilder();
                itemName.append(displayName);
                
                // Add customization details (add-ons) if they exist
                if (customizationDetails != null && !customizationDetails.trim().isEmpty()) {
                    // Parse customization details to extract add-on information
                    String[] details = customizationDetails.split(", ");
                    for (String detail : details) {
                        if (detail.startsWith("Add-on: ")) {
                            String addOnName = detail.substring("Add-on: ".length());
                            // Remove price information if present (e.g., "Extra Shot (+₱15)")
                            if (addOnName.contains(" (+₱")) {
                                addOnName = addOnName.substring(0, addOnName.indexOf(" (+₱"));
                            }
                            itemName.append(" + ").append(addOnName);
                        }
                    }
                }
                
                items.append("• ")
                     .append(resultSet.getInt("quantity"))
                     .append("x ")
                     .append(itemName.toString())
                     .append(" @ ₱")
                     .append(decimalFormat.format(resultSet.getDouble("unit_price")))
                     .append(" = ₱")
                     .append(decimalFormat.format(resultSet.getDouble("total_price")))
                     .append("\n");
            }
            
        } catch (SQLException e) {
            items.append("Error loading order items: ").append(e.getMessage());
        } finally {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        return items.toString();
    }

    //items summary
    private String getOrderItemsSummary(String orderId) {
        Connection connection = null;
        try {
            connection = SqliteConnection.Connector();
            String query = "SELECT oi.quantity, COALESCE(p.name, oi.product_name) AS name " +
                           "FROM order_items oi LEFT JOIN products p ON oi.product_id = p.id " +
                           "WHERE oi.order_id = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, orderId);
            ResultSet rs = statement.executeQuery();
            java.util.List<String> items = new java.util.ArrayList<>();
            while (rs.next()) {
                items.add(rs.getInt("quantity") + "x " + rs.getString("name"));
            }
            if (items.isEmpty()) return "No items";
            if (items.size() <= 2) return String.join(", ", items);
            return items.get(0) + ", " + items.get(1) + " +" + (items.size() - 2) + " more";
        } catch (SQLException e) {
            return "Error loading items";
        } finally {
            try { if (connection != null) connection.close(); } catch (SQLException ignored) {}
        }
    }

    // Parse various date shapes into a LocalDateTime fallback
    private LocalDateTime parseOrderDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDate.parse(dateString).atStartOfDay();
        } catch (Exception ex) {
            try {
                return LocalDateTime.parse(dateString.contains(" ") ? dateString.replace(" ", "T") : dateString);
            } catch (Exception ignored) {
                return LocalDateTime.now();
            }
        }
    }

    // Event handlers
    
    // Reload orders
    @FXML
    private void handleRefresh() {
        loadOrderHistory();
        showAlert("Refresh Complete", "Order history has been refreshed.");
    }

    // Clear all filters
    @FXML
    private void handleClearFilters() {
        customerSearchField.clear();
        statusFilterComboBox.setValue("All Status");
        paymentFilterComboBox.setValue("All Payments");
        fromDatePicker.setValue(LocalDate.now().minusDays(30));
        toDatePicker.setValue(LocalDate.now());
        applyFilters(); 
    }

    //Excel path and export
    @FXML
    private void handleExport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Order History");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        fileChooser.setInitialFileName("order_history_" + 
                                     LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv");
        
        Stage stage = (Stage) exportButton.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);
        
        if (file != null) {
            exportToCSV(file);
        }
    }

    // Write filtered orders to Excel with a header row
    private void exportToCSV(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            writer.append("Order ID,Customer,Date & Time,Type,Payment,Amount,Status,Items\n");
            
            for (Order order : filteredOrders) {
                writer.append(String.valueOf(order.getId())).append(",");
                writer.append(order.getCustomerName()).append(",");
                writer.append(order.getOrderDate().format(dateFormatter)).append(",");
                writer.append(order.getOrderType()).append(",");
                writer.append(order.getPaymentMethod()).append(",");
                writer.append(decimalFormat.format(order.getTotalAmount())).append(",");
                writer.append(order.getStatus()).append(",");
                writer.append("\"").append(getOrderItemsSummary(order.getId())).append("\"");
                writer.append("\n");
            }
            
            showAlert("Export Successful", "Order history exported to: " + file.getAbsolutePath());
                     
        } catch (IOException e) {
            showAlert("Export Failed", "Error exporting order history: " + e.getMessage());
        }
    }

    // Navigation methods
    
    //Dashboard page
    @FXML
    private void handleDashboardButton() {
        navigateToPage("/view/fxml/Dashboard.fxml", "Dashboard");
    }

    // Go to Inventory page
    @FXML
    private void handleInventoryButton() {
        navigateToPage("/view/fxml/Inventory.fxml", "Inventory Management");
    }

    // Go to Order page
    @FXML
    private void handleOrderButton() {
        navigateToPage("/view/fxml/Order.fxml", "Order Management");
    }

    // Recent Orders page
    @FXML
    private void handleRecentOrderButton() {
        // Already on Recent Orders page
    }

    // Confirm and logout to Login page
    @FXML
    private void handleLogoutButton() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Logout");
        alert.setHeaderText("Are you sure you want to logout?");
        
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            navigateToPage("/view/fxml/LoginPage.fxml", "Login");
        }
    }

    //swap scenes
    private void navigateToPage(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            
            Stage stage = (Stage) dashboardbutton.getScene().getWindow();
            Scene scene = new Scene(root);
            stage.setTitle("Sebucha Order Management System");
            stage.setScene(scene);   
            stage.show();
            
        } catch (IOException e) {
            showAlert("Navigation Error", "Error loading page: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    private boolean isOrderAlreadyCancelled(String orderId) {
        Connection connection = null;
        try {
            connection = SqliteConnection.Connector();
            String query = "SELECT order_status FROM orders WHERE id = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, orderId);
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                String currentStatus = resultSet.getString("order_status");
                return "Cancelled".equals(currentStatus);
            }
            
            return false; 
            
        } catch (SQLException e) {
            System.err.println("Error checking order status: " + e.getMessage());
            return false;
        } finally {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    
    //  Restore inventory stock for all items in a cancelled order
     
    private boolean restoreOrderItemsToInventory(String orderId) {
        Connection connection = null;
        try {
            connection = SqliteConnection.Connector();
            connection.setAutoCommit(false);
            
            // Get all order items for this order
            String getItemsQuery = "SELECT product_id, quantity FROM order_items WHERE order_id = ?";
            PreparedStatement getItemsStmt = connection.prepareStatement(getItemsQuery);
            getItemsStmt.setString(1, orderId);
            ResultSet itemsResult = getItemsStmt.executeQuery();
            
            // Update stock for each product
            String updateStockQuery = "UPDATE products SET stock = stock + ? WHERE id = ?";
            PreparedStatement updateStockStmt = connection.prepareStatement(updateStockQuery);
            
            int itemsProcessed = 0;
            while (itemsResult.next()) {
                int productId = itemsResult.getInt("product_id");
                int quantity = itemsResult.getInt("quantity");
                
                updateStockStmt.setInt(1, quantity);
                updateStockStmt.setInt(2, productId);
                updateStockStmt.addBatch();
                itemsProcessed++;
            }
            
            if (itemsProcessed > 0) {
                int[] results = updateStockStmt.executeBatch();
                
                for (int result : results) {
                    if (result <= 0) {
                        connection.rollback();
                        System.err.println("Failed to update stock for one or more products");
                        return false;
                    }
                }
                
                connection.commit();
                System.out.println("Successfully restored stock for " + itemsProcessed + " products from order " + orderId);
                return true;
            } else {
                System.out.println("No items found for order " + orderId);
                return false;
            }
            
        } catch (SQLException e) {
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException rollbackEx) {
                System.err.println("Error during rollback: " + rollbackEx.getMessage());
            }
            System.err.println("Error restoring inventory stock: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (connection != null) {
                    connection.setAutoCommit(true);
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }
}
