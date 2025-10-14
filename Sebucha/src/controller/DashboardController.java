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
    @FXML private Button resetbutton;
    
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
        // First confirmation dialog
        Alert firstConfirmation = new Alert(AlertType.WARNING);
        firstConfirmation.setTitle("Reset Confirmation - Step 1");
        firstConfirmation.setHeaderText("Are you sure you want to reset all dashboard data?");
        firstConfirmation.setContentText("This action will clear all displayed income data, product sales, and charts.\n\nDo you want to continue?");
        
        // Add custom buttons for first confirmation
        ButtonType continueButton = new ButtonType("Continue");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonType.CANCEL.getButtonData());
        firstConfirmation.getButtonTypes().setAll(continueButton, cancelButton);
        
        Optional<ButtonType> firstResult = firstConfirmation.showAndWait();
        
        if (firstResult.isPresent() && firstResult.get() == continueButton) {
            // Second confirmation dialog - more specific warning
            Alert secondConfirmation = new Alert(AlertType.ERROR);
            secondConfirmation.setTitle("Reset Confirmation - Step 2");
            secondConfirmation.setHeaderText("FINAL WARNING: This will permanently clear all dashboard data!");
            secondConfirmation.setContentText("• Today's Income: ₱ 0.00\n" +
                                            "• Products Sold: 0\n" +
                                            "• Total Income: ₱ 0.00\n" +
                                            "• All Charts: Cleared\n\n" +
                                            "Are you absolutely sure you want to proceed?");
            
            // Add custom buttons for second confirmation
            ButtonType resetButton = new ButtonType("Yes, Reset Data");
            ButtonType keepDataButton = new ButtonType("No, Keep Data", ButtonType.CANCEL.getButtonData());
            secondConfirmation.getButtonTypes().setAll(resetButton, keepDataButton);
            
            Optional<ButtonType> secondResult = secondConfirmation.showAndWait();
            
            if (secondResult.isPresent() && secondResult.get() == resetButton) {
                // Only reset if both confirmations are accepted
                resetDashboard();
                showAlert("Reset Complete", "Dashboard data has been reset successfully!", AlertType.INFORMATION);
            } else {
                // User cancelled at second step
                showAlert("Reset Cancelled", "Dashboard data has been preserved.", AlertType.INFORMATION);
            }
        } else {
            // User cancelled at first step
            showAlert("Reset Cancelled", "Dashboard data remains unchanged.", AlertType.INFORMATION);
        }
    }
    
    // Data loading methods
    private void loadDashboardData() {
        loadTodayIncome();
        loadProductsSold();
        loadTotalIncome();
        loadBestSellersChart();
        loadIncomeChart();
    }
    
    // Reset all dashboard data to initial/empty state
    private void resetDashboard() {
        // Reset data labels to default values
        today_income.setText("₱ 0.00");
        products_sold.setText("0");
        total_income.setText("₱ 0.00");
        
        // Clear all chart data
        bestsellers.getData().clear();
        incomechart.getData().clear();
        
        // Optionally add empty series to maintain chart structure
        XYChart.Series<String, Number> emptyBarSeries = new XYChart.Series<>();
        emptyBarSeries.setName("Best Sellers");
        bestsellers.getData().add(emptyBarSeries);
        
        XYChart.Series<String, Number> emptyLineSeries = new XYChart.Series<>();
        emptyLineSeries.setName("Daily Income");
        incomechart.getData().add(emptyLineSeries);
    }
    
    private void loadTodayIncome() {
        String sql = "SELECT SUM(total_amount) as today_total FROM orders WHERE DATE(order_date) = ?";
        
        try {
            prepare = connection.prepareStatement(sql);
            prepare.setString(1, LocalDate.now().toString());
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
        String sql = "SELECT SUM(quantity) as total_sold FROM order_items oi " +
                    "JOIN orders o ON oi.order_id = o.id WHERE DATE(o.order_date) = ?";
        
        try {
            prepare = connection.prepareStatement(sql);
            prepare.setString(1, LocalDate.now().toString());
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
                    "LIMIT 5"; // Limited to 5 bars maximum
        
        try {
            prepare = connection.prepareStatement(sql);
            result = prepare.executeQuery();
            
            // Clear existing data
            bestsellers.getData().clear();
            
            // Create a single series for all products (no individual styling)
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
            // Add fallback sample data without styling
            addSampleBestSellersData();
        }
    }
    
    private void addSampleBestSellersData() {
        // Sample data without colors or styling
        String[] sampleProducts = {"Iced Tea", "Coffee Latte", "Bubble Tea", "Green Smoothie", "Chocolate Cake"};
        int[] sampleQuantities = {45, 38, 32, 28, 22};
        
        bestsellers.getData().clear();
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Best Sellers");
        
        for (int i = 0; i < sampleProducts.length; i++) {
            series.getData().add(new XYChart.Data<>(sampleProducts[i], sampleQuantities[i]));
        }
        
        bestsellers.getData().add(series);
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
}
