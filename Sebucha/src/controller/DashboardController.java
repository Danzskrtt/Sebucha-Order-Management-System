package controller;

import javafx.application.Platform;


import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import model.SqliteConnection;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.Optional;
import java.util.ResourceBundle;

public class DashboardController implements Initializable {
    
    // Navigation buttons
    @FXML private Button dashboardbutton;
    @FXML private Button inventorybutton;
    @FXML private Button orderbutton;
    @FXML private Button recentorderbutton;
    @FXML private Button logoutbutton;
    @FXML private Button ResetButton;
    
    // Data display labels
    @FXML private Label today_income;
    @FXML private Label products_sold;
    @FXML private Label total_income;
    
    // Charts
    @FXML private BarChart<String, Number> bestsellers;
    @FXML private LineChart<String, Number> incomechart;
    
    // Database connection
    private Connection connection;
    private PreparedStatement prepare;
    private ResultSet result;
    
    // Formatter for currency display
    private DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        connection = SqliteConnection.Connector();
        
      
        loadDashboardData();
        
      
        setupCharts();
        
       
        setActiveButton(dashboardbutton);
        
        
        Platform.runLater(() -> {
            Stage stage = (Stage) dashboardbutton.getScene().getWindow();
            
            stage.setResizable(true);
            stage.centerOnScreen();
        });
    }
    
    // Navigation methods
    @FXML
    private void handleDashboardButton(ActionEvent event) {
        // Already on dashboard, just refresh
        refreshDashboard();
        setActiveButton(dashboardbutton);
    }
    
    @FXML
    private void handleInventoryButton(ActionEvent event) {
        try {
            loadScene(event, "/view/fxml/Inventory.fxml");
        } catch (IOException e) {
            showAlert("Error", "Could not load Inventory page: " + e.getMessage(), AlertType.ERROR);
        }
    }
    
    @FXML
    private void handleOrderButton(ActionEvent event) {
        try {
            loadScene(event, "/view/fxml/Order.fxml");
        } catch (IOException e) {
            showAlert("Error", "Could not load Order page: " + e.getMessage(), AlertType.ERROR);
        }
    }
    
    @FXML
    private void handleRecentOrderButton(ActionEvent event) {
        try {
            loadScene(event, "/view/fxml/RecentOrder.fxml");
        } catch (IOException e) {
            showAlert("Error", "Could not load Recent Orders page: " + e.getMessage(), AlertType.ERROR);
        }
    }
    
    @FXML
    private void handleLogoutButton(ActionEvent event) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("Logout Confirmation");
        alert.setHeaderText("Are you sure you want to logout?");
        alert.setContentText("You will be redirected to the login page.");
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                loadScene(event, "/view/fxml/LoginPage.fxml");
            } catch (IOException e) {
                showAlert("Error", "Could not load Login page: " + e.getMessage(), AlertType.ERROR);
            }
        }
    }
    
    @FXML
    private void handleResetButton(ActionEvent event) {
        // First confirmation dialog - Step 1
        Alert firstConfirmation = new Alert(AlertType.WARNING);
        firstConfirmation.setTitle("Reset Confirmation - Step 1");
        firstConfirmation.setHeaderText("Are you sure you want to reset ALL order history and dashboard data?");
        firstConfirmation.setContentText("This action will permanently delete:\n" +
                                      "• All order records from the database\n" +
                                      "• All order items and transaction history\n" +
                                      "• Dashboard revenue and statistics\n" +
                                      "• Recent order history display\n\n" +
                                      "Do you want to continue?");
        
        
        ButtonType continueButton = new ButtonType("Continue");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonType.CANCEL.getButtonData());
        firstConfirmation.getButtonTypes().setAll(continueButton, cancelButton);
        
        Optional<ButtonType> firstResult = firstConfirmation.showAndWait();
        
        if (firstResult.isPresent() && firstResult.get() == continueButton) {
            // Second confirmation dialog - Step 2 (Final Warning)
            Alert secondConfirmation = new Alert(AlertType.ERROR);
            secondConfirmation.setTitle("Reset Confirmation - Step 2");
            secondConfirmation.setHeaderText("FINAL WARNING: This will permanently delete ALL order data!");
            secondConfirmation.setContentText("This action cannot be undone and will:\n\n" +
                                          "🗑️ DELETE ALL ORDERS from database\n" +
                                          "🗑️ DELETE ALL ORDER ITEMS\n" +
                                          "📊 RESET Dashboard to ₱0.00\n" +
                                          "📈 CLEAR All charts and statistics\n" +
                                          "📋 EMPTY Recent orders table\n\n" +
                                          "⚠️ THIS CANNOT BE REVERSED! ⚠️\n\n" +
                                          "Are you absolutely sure you want to proceed?");
            
           
            ButtonType resetButton = new ButtonType("Yes, Delete Everything");
            ButtonType keepDataButton = new ButtonType("No, Keep Data", ButtonType.CANCEL.getButtonData());
            secondConfirmation.getButtonTypes().setAll(resetButton, keepDataButton);
            
            Optional<ButtonType> secondResult = secondConfirmation.showAndWait();
            
            if (secondResult.isPresent() && secondResult.get() == resetButton) {
                performCompleteReset();
                showAlert("Reset Complete", 
                         "All order history has been permanently deleted!\n" +
                         "Dashboard and charts have been reset.\n" +
                         "System is now ready for new orders.", AlertType.INFORMATION);
                
                // Refresh the dashboard after reset
                refreshDashboard();
            } else {
                showAlert("Reset Cancelled", "All order data has been preserved.", AlertType.INFORMATION);
            }
        } else {
            showAlert("Reset Cancelled", "No changes were made to order data.", AlertType.INFORMATION);
        }
    }
    
    private void performCompleteReset() {
        Connection connection = null;
        
        try {
            connection = SqliteConnection.Connector();
            connection.setAutoCommit(false); // Start transaction
            
            // Delete all order items first (due to foreign key constraints)
            String deleteOrderItemsQuery = "DELETE FROM order_items";
            PreparedStatement deleteItemsStatement = connection.prepareStatement(deleteOrderItemsQuery);
            int itemsDeleted = deleteItemsStatement.executeUpdate();
            
            // Delete all orders
            String deleteOrdersQuery = "DELETE FROM orders";
            PreparedStatement deleteOrdersStatement = connection.prepareStatement(deleteOrdersQuery);
            int ordersDeleted = deleteOrdersStatement.executeUpdate();
            
            // Reset auto-increment counters (if applicable)
            try {
                PreparedStatement resetSequence = connection.prepareStatement("DELETE FROM sqlite_sequence WHERE name IN ('orders', 'order_items')");
                resetSequence.executeUpdate();
                resetSequence.close();
            } catch (SQLException e) {
                // sqlite_sequence might not exist, this is okay
                System.out.println("Note: sqlite_sequence table not found or reset not needed");
            }
            
            connection.commit(); // Commit transaction
            
            // Log the reset operation
            System.out.println("=== COMPLETE RESET PERFORMED ===");
            System.out.println("Orders deleted: " + ordersDeleted);
            System.out.println("Order items deleted: " + itemsDeleted);
            System.out.println("Dashboard reset: Complete");
            System.out.println("===============================");
            
        } catch (SQLException e) {
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
            showAlert("Reset Failed", "Error during reset operation: " + e.getMessage(), AlertType.ERROR);
            e.printStackTrace();
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
    
    // Data loading methods
    private void loadDashboardData() {
        loadTodayIncomeFromRecentOrders();
        loadProductsSoldFromRecentOrders();
        loadTotalIncomeFromRecentOrders();
        loadBestSellersChart();
        loadIncomeChart();
    }
    
    // Updated methods to use direct database queries instead of RecentOrderController instances
    private void loadTodayIncomeFromRecentOrders() {
        Connection connection = null;
        try {
            connection = SqliteConnection.Connector();
            String query = "SELECT SUM(total_amount) as today_total FROM orders " +
                          "WHERE datetime(order_date || ' ' || order_time) >= datetime('now', '-24 hours')";
            
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();
            
            double todayTotal = 0.0;
            if (resultSet.next()) {
                todayTotal = resultSet.getDouble("today_total");
            }
            
            today_income.setText("₱ " + decimalFormat.format(todayTotal));
            
        } catch (SQLException e) {
            System.err.println("Error loading today's income: " + e.getMessage());
            today_income.setText("₱ 0.00");
        } finally {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void loadProductsSoldFromRecentOrders() {
        Connection connection = null;
        try {
            connection = SqliteConnection.Connector();
            String query = "SELECT SUM(oi.quantity) as total_sold FROM order_items oi " +
                          "JOIN orders o ON oi.order_id = o.id " +
                          "WHERE datetime(o.order_date || ' ' || o.order_time) >= datetime('now', '-24 hours')";
            
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();
            
            int totalSold = 0;
            if (resultSet.next()) {
                totalSold = resultSet.getInt("total_sold");
            }
            
            products_sold.setText(String.valueOf(totalSold));
            
        } catch (SQLException e) {
            System.err.println("Error loading products sold: " + e.getMessage());
            products_sold.setText("0");
        } finally {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void loadTotalIncomeFromRecentOrders() {
        Connection connection = null;
        try {
            connection = SqliteConnection.Connector();
            String query = "SELECT SUM(total_amount) as total_revenue FROM orders";
            
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();
            
            double totalIncome = 0.0;
            if (resultSet.next()) {
                totalIncome = resultSet.getDouble("total_revenue");
            }
            
            total_income.setText("₱ " + decimalFormat.format(totalIncome));
            
        } catch (SQLException e) {
            System.err.println("Error loading total income: " + e.getMessage());
            total_income.setText("₱ 0.00");
        } finally {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // Keep original methods as fallback
    private void loadTodayIncome() {
        String sql = "SELECT SUM(total_amount) as today_total FROM orders WHERE datetime(order_date || ' ' || order_time) >= datetime('now', '-24 hours')";
        
        try {
            prepare = connection.prepareStatement(sql);
            result = prepare.executeQuery();
            
            double todayTotal = 0.0;
            if (result.next()) {
                todayTotal = result.getDouble("today_total");
            }
            
            today_income.setText("₱ " + decimalFormat.format(todayTotal));
            
        } catch (SQLException e) {
            System.err.println("Error loading today's income: " + e.getMessage());
            today_income.setText("₱ 0.00");
        }
    }
    
    private void loadProductsSold() {
        String sql = "SELECT SUM(oi.quantity) as total_sold FROM order_items oi " +
                    "JOIN orders o ON oi.order_id = o.id WHERE datetime(o.order_date || ' ' || o.order_time) >= datetime('now', '-24 hours')";
        
        try {
            prepare = connection.prepareStatement(sql);
            result = prepare.executeQuery();
            
            int totalSold = 0;
            if (result.next()) {
                totalSold = result.getInt("total_sold");
            }
            
            products_sold.setText(String.valueOf(totalSold));
            
        } catch (SQLException e) {
            System.err.println("Error loading products sold: " + e.getMessage());
            products_sold.setText("0");
        }
    }
    
    private void loadTotalIncome() {
        String sql = "SELECT SUM(total_amount) as total_income FROM orders";
        
        try {
            prepare = connection.prepareStatement(sql);
            result = prepare.executeQuery();
            
            double totalIncome = 0.0;
            if (result.next()) {
                totalIncome = result.getDouble("total_income");
            }
            
            total_income.setText("₱ " + decimalFormat.format(totalIncome));
            
        } catch (SQLException e) {
            System.err.println("Error loading total income: " + e.getMessage());
            total_income.setText("₱ 0.00");
        }
    }
    
    private void loadBestSellersChart() {
        String sql = "SELECT p.name, SUM(oi.quantity) as total_quantity " +
                    "FROM order_items oi " +
                    "JOIN products p ON oi.product_id = p.id " +
                    "GROUP BY p.id, p.name " +
                    "ORDER BY total_quantity DESC " +
                    "LIMIT 5";
        
        try {
            prepare = connection.prepareStatement(sql);
            result = prepare.executeQuery();
            
            // Clear existing data
            bestsellers.getData().clear();
            
            // Create a single series for all products
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Best Sellers");
            
            while (result.next()) {
                String productName = result.getString("name");
                int quantity = result.getInt("total_quantity");
                series.getData().add(new XYChart.Data<>(productName, quantity));
            }
            
            bestsellers.getData().add(series);
            
        } catch (SQLException e) {
            System.err.println("Error loading best sellers chart: " + e.getMessage());
            // Just clear the chart if there's an error
            clearBestSellersChart();
        }
    }
    
    private void clearBestSellersChart() {
        bestsellers.getData().clear();
    }
    
    private void loadIncomeChart() {
        String sql = "SELECT DATE(order_date) as date, SUM(total_amount) as daily_income " +
                    "FROM orders " +
                    "WHERE order_date >= date('now', '-7 days') " +
                    "GROUP BY DATE(order_date) " +
                    "ORDER BY date";
        
        try {
            prepare = connection.prepareStatement(sql);
            result = prepare.executeQuery();
            
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Daily Income");
            
            while (result.next()) {
                String date = result.getString("date");
                double income = result.getDouble("daily_income");
                series.getData().add(new XYChart.Data<>(date, income));
            }
            
            incomechart.getData().clear();
            incomechart.getData().add(series);
            
        } catch (SQLException e) {
            System.err.println("Error loading income chart: " + e.getMessage());
        }
    }
    
    // Chart setup - removed all styling
    private void setupCharts() {
        // Basic chart configuration without styling
        bestsellers.setLegendVisible(false);
        bestsellers.setAnimated(true);
        
        // Configure X-axis for horizontal labels (basic setup only)
        if (bestsellers.getXAxis() instanceof javafx.scene.chart.CategoryAxis) {
            javafx.scene.chart.CategoryAxis xAxis = (javafx.scene.chart.CategoryAxis) bestsellers.getXAxis();
            xAxis.setTickLabelRotation(0); // Horizontal labels
        }
        
        incomechart.setLegendVisible(false);
        incomechart.setAnimated(true);
        incomechart.setCreateSymbols(false);
    }
    
    // Utility methods
    private void refreshDashboard() {
        loadDashboardData();
    }
    
    private void loadScene(ActionEvent event, String fxmlPath) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        Scene scene = new Scene(root);
        stage.setTitle("Sebucha Order Management System");
        stage.setScene(scene);
        stage.show();
    }
    
    private void setActiveButton(Button activeButton) {
        // Define base styles for navigation buttons - all buttons maintain bold font weight
        String baseStyle = "-fx-background-color: linear-gradient(to bottom, #EEEEEE, #E0E0E0); -fx-background-radius: 15; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2); -fx-cursor: hand; -fx-font-weight: bold;";
        String activeStyle = "-fx-background-color: linear-gradient(to bottom, #EEEEEE, #E0E0E0); -fx-background-radius: 15; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0, 0, 3); -fx-cursor: hand; -fx-font-weight: bold; -fx-border-color: rgba(255,255,255,0.5); -fx-border-width: 1; -fx-border-radius: 15;";
        
        // Reset all buttons to base style
        dashboardbutton.setStyle(baseStyle);
        inventorybutton.setStyle(baseStyle);
        orderbutton.setStyle(baseStyle);
        recentorderbutton.setStyle(baseStyle);
        
        // Set active button style
        activeButton.setStyle(activeStyle);
    }
    
    private void showAlert(String title, String message, AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Test method to demonstrate dashboard metrics retrieval
     * This method shows how to get reference data from RecentOrderController
     */
    public void demonstrateDashboardMetrics() {
        try {
            RecentOrderController recentOrderController = new RecentOrderController();
            
            // Get the three key metrics
            double todaysIncome24hrs = recentOrderController.getTodaysIncome();
            int productsSoldToday = recentOrderController.getTodaysProductsSold();
            double totalRevenue = recentOrderController.getTotalRevenue();
            
            // Display metrics in console for reference
            System.out.println("=== DASHBOARD METRICS REFERENCE ===");
            System.out.println("Today's Income (24 hours): ₱" + decimalFormat.format(todaysIncome24hrs));
            System.out.println("Products Sold Today: " + productsSoldToday + " units");
            System.out.println("Total Revenue (All Time): ₱" + decimalFormat.format(totalRevenue));
            System.out.println("==================================");
            
            // You can also use these values for any other purpose
            return;
            
        } catch (Exception e) {
            System.err.println("Error getting dashboard metrics: " + e.getMessage());
        }
    }
}

