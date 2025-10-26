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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import model.Order;
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
import java.util.Optional;
import java.util.ResourceBundle;

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
    @FXML private Button resetButton;

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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        initializeFilters();
        setupEventHandlers();
        loadOrderHistory();
        updateSummaryStatistics();
    }

    private void setupTableColumns() {
        // Configure table columns
        orderIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        customerNameColumn.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        orderTypeColumn.setCellValueFactory(new PropertyValueFactory<>("orderType"));
        paymentMethodColumn.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        
        // Format date column
        orderDateColumn.setCellValueFactory(cellData -> {
            LocalDateTime orderDate = cellData.getValue().getOrderDate();
            return new SimpleStringProperty(orderDate != null ? orderDate.format(dateFormatter) : "N/A");
        });
        
        // Format total amount column
        totalAmountColumn.setCellValueFactory(cellData -> {
            double amount = cellData.getValue().getTotalAmount();
            return new SimpleStringProperty("₱" + decimalFormat.format(amount));
        });
        
        // Items column
        itemsColumn.setCellValueFactory(cellData -> {
            Order order = cellData.getValue();
            String itemsSummary = getOrderItemsSummary(order.getId());
            return new SimpleStringProperty(itemsSummary);
        });
        
        // Actions column with "View Details" button
        actionsColumn.setCellFactory(col -> new TableCell<Order, String>() {
            private final Button viewButton = new Button("View Details");
            
            {
                viewButton.setStyle("-fx-background-color: linear-gradient(to bottom, #C4B5FD 0%, #8B5CF6 50%, #5B21B6 100%); " +
                                 "-fx-background-radius: 12; " +
                                 "-fx-text-fill: white; " +
                                 "-fx-font-family: 'Calibri'; " +
                                 "-fx-font-size: 13px; " +
                                 "-fx-font-weight: bold; " +
                                 "-fx-cursor: hand; " +
                                 "-fx-padding: 5 15;");
                
                viewButton.setOnAction(event -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        viewOrderDetails(getTableRow().getItem());
                    }
                });
            }
            
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(viewButton);
                }
            }
        });
        
        // Set table data
        ordersTable.setItems(filteredOrders);
    }

    private void initializeFilters() {
        // Initialize status filter
        statusFilterComboBox.getItems().addAll("All Status", "Completed", "Pending", "Cancelled");
        statusFilterComboBox.setValue("All Status");
        
        // Initialize payment method filter
        paymentFilterComboBox.getItems().addAll("All Payments", "Cash", "Card", "GCash", "PayMaya");
        paymentFilterComboBox.setValue("All Payments");
        
        // Set default date range (last 30 days)
        toDatePicker.setValue(LocalDate.now());
        fromDatePicker.setValue(LocalDate.now().minusDays(30));
    }

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

    private void loadOrderHistory() {
        allOrders.clear();
        Connection connection = null;
        
        try {
            connection = SqliteConnection.Connector();
            String query = "SELECT id, customer_name, order_type, payment_method, total_amount, order_date, order_time, order_status FROM orders ORDER BY order_date DESC, order_time DESC";
            
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                try {
                    // Combine order_date and order_time into a single timestamp
                    String datePart = resultSet.getString("order_date");
                    String timePart = resultSet.getString("order_time");
                    LocalDateTime orderDate = parseOrderDateTime(datePart, timePart);
                    
                    // Create order object using String ID to support generated order IDs
                    Order order = new Order(
                        resultSet.getString("id"), 
                        resultSet.getString("customer_name"),
                        resultSet.getString("order_type"),
                        resultSet.getString("payment_method"),
                        resultSet.getDouble("total_amount"),
                        orderDate,
                        resultSet.getString("order_status")
                    );
                    
                    allOrders.add(order);
                } catch (Exception e) {
                    System.err.println("Error parsing order: " + e.getMessage());
                }
            }
            
            applyFilters();
            
        } catch (SQLException e) {
            showAlert("Database Error", 
                     "Error loading order history: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

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
            // Fallbacks for slightly different formats
            try {
                return LocalDateTime.parse((datePart + " " + timePart).replace(" ", "T"));
            } catch (Exception ignored) {
                return parseOrderDate(datePart);
            }
        }
    }

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

    private String getDetailedOrderItems(String orderId) {
        Connection connection = null;
        StringBuilder items = new StringBuilder();
        
        try {
            connection = SqliteConnection.Connector();
            String query = "SELECT oi.quantity, oi.unit_price, oi.total_price, p.name " +
                          "FROM order_items oi JOIN products p ON oi.product_id = p.id " +
                          "WHERE oi.order_id = ?";
            
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, orderId);
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                items.append("• ")
                     .append(resultSet.getInt("quantity"))
                     .append("x ")
                     .append(resultSet.getString("name"))
                     .append(" @ ₱")
                     .append(decimalFormat.format(resultSet.getDouble("unit_price")))
                     .append(" = ₱")
                     .append(decimalFormat.format(resultSet.getDouble("total_price")))
                     .append("\n");
            }
            
        } catch (SQLException e) {
            items.append("Error loading order items");
        } finally {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        return items.toString();
    }

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
    @FXML
    private void handleRefresh() {
        loadOrderHistory();
        showAlert("Refresh Complete", "Order history has been refreshed.");
    }

    @FXML
    private void handleClearFilters() {
        customerSearchField.clear();
        statusFilterComboBox.setValue("All Status");
        paymentFilterComboBox.setValue("All Payments");
        fromDatePicker.setValue(LocalDate.now().minusDays(30));
        toDatePicker.setValue(LocalDate.now());
        applyFilters(); 
    }

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
    @FXML
    private void handleDashboardButton() {
        navigateToPage("/view/fxml/Dashboard.fxml", "Dashboard");
    }

    @FXML
    private void handleInventoryButton() {
        navigateToPage("/view/fxml/Inventory.fxml", "Inventory Management");
    }

    @FXML
    private void handleOrderButton() {
        navigateToPage("/view/fxml/Order.fxml", "Order Management");
    }

    @FXML
    private void handleRecentOrderButton() {
        // Already on Recent Orders page
    }

    @FXML
    private void handleLogoutButton() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Logout");
        alert.setHeaderText("Are you sure you want to logout?");
        
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            navigateToPage("/view/fxml/LoginPage.fxml", "Login");
        }
    }

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

    // Add methods for getting dashboard metrics from recent orders
    
    //Get today's income within the last 24 hours
    public double getTodaysIncome() {
        Connection connection = null;
        double todaysIncome = 0.0;
        
        try {
            connection = SqliteConnection.Connector();
            String query = "SELECT SUM(total_amount) as today_total FROM orders " +
                          "WHERE datetime(order_date || ' ' || order_time) >= datetime('now', '-24 hours')";
            
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                todaysIncome = resultSet.getDouble("today_total");
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting today's income: " + e.getMessage());
        } finally {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        return todaysIncome;
    }
    
    //Get products sold within the last 24 hours
    public int getTodaysProductsSold() {
        Connection connection = null;
        int productsSold = 0;
        
        try {
            connection = SqliteConnection.Connector();
            String query = "SELECT SUM(oi.quantity) as total_sold FROM order_items oi " +
                          "JOIN orders o ON oi.order_id = o.id " +
                          "WHERE datetime(o.order_date || ' ' || o.order_time) >= datetime('now', '-24 hours')";
            
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                productsSold = resultSet.getInt("total_sold");
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting today's products sold: " + e.getMessage());
        } finally {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        return productsSold;
    }
    
    // total revenue from all orders    
    public double getTotalRevenue() {
        Connection connection = null;
        double totalRevenue = 0.0;
        
        try {
            connection = SqliteConnection.Connector();
            String query = "SELECT SUM(total_amount) as total_revenue FROM orders";
            
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                totalRevenue = resultSet.getDouble("total_revenue");
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting total revenue: " + e.getMessage());
        } finally {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        return totalRevenue;
    }
}
