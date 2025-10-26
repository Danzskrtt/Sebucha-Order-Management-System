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

    // Dashboard data labels
    @FXML private Label today_income;
    @FXML private Label products_sold;
    @FXML private Label total_income;

    // Charts for data visualization
    @FXML private BarChart<String, Number> bestsellers;
    @FXML private LineChart<String, Number> incomechart;

    // Database tools
    private Connection connection;
    private PreparedStatement prepare;
    private ResultSet result;

    // Used for formatting currency and numbers
    private final DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        connection = SqliteConnection.Connector();

        // Load all dashboard data once the controller initializes
        loadDashboardData();

        // Prepare charts
        setupCharts();

        // Adjust window properties after UI loads
        Platform.runLater(() -> {
            Stage stage = (Stage) dashboardbutton.getScene().getWindow();
            stage.setResizable(true);
            stage.centerOnScreen();
        });
    }

    // ========== Navigation Handlers ==========

    @FXML
    private void handleDashboardButton(ActionEvent event) {
        // Already on the dashboard — just refresh data
        refreshDashboard();
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
        alert.setContentText("You’ll be redirected to the login page.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                loadScene(event, "/view/fxml/LoginPage.fxml");
            } catch (IOException e) {
                showAlert("Error", "Could not load Login page: " + e.getMessage(), AlertType.ERROR);
            }
        }
    }

    // ========== Reset Functionality ==========

    @FXML
    private void handleResetButton(ActionEvent event) {
        // Step 1: Ask user for initial confirmation
        Alert firstConfirmation = new Alert(AlertType.WARNING);
        firstConfirmation.setTitle("Reset Confirmation - Step 1");
        firstConfirmation.setHeaderText("Reset all order history and dashboard data?");
        firstConfirmation.setContentText(
            "This will permanently delete:\n" +
            "• All order records\n" +
            "• All order items\n" +
            "• Dashboard revenue and statistics\n" +
            "• Recent order history\n\n" +
            "Do you wish to continue?"
        );

        ButtonType continueButton = new ButtonType("Continue");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonType.CANCEL.getButtonData());
        firstConfirmation.getButtonTypes().setAll(continueButton, cancelButton);

        Optional<ButtonType> firstResult = firstConfirmation.showAndWait();

        if (firstResult.isPresent() && firstResult.get() == continueButton) {
            // Step 2: Final warning before permanent deletion
            Alert secondConfirmation = new Alert(AlertType.ERROR);
            secondConfirmation.setTitle("Reset Confirmation - Final Step");
            secondConfirmation.setHeaderText("⚠️ FINAL WARNING: This action cannot be undone!");
            secondConfirmation.setContentText(
                "This will:\n\n" +
                "🗑️ Delete all orders and order items\n" +
                "📊 Reset dashboard to ₱0.00\n" +
                "📈 Clear charts and statistics\n" +
                "📋 Empty recent orders table\n\n" +
                "Are you absolutely sure?"
            );

            ButtonType resetButton = new ButtonType("Yes, Delete Everything");
            ButtonType keepDataButton = new ButtonType("No, Keep Data", ButtonType.CANCEL.getButtonData());
            secondConfirmation.getButtonTypes().setAll(resetButton, keepDataButton);

            Optional<ButtonType> secondResult = secondConfirmation.showAndWait();

            if (secondResult.isPresent() && secondResult.get() == resetButton) {
                performCompleteReset();
                showAlert(
                    "Reset Complete",
                    "All order data has been permanently deleted.\n" +
                    "Dashboard has been cleared and is ready for new orders.",
                    AlertType.INFORMATION
                );
                refreshDashboard();
            } else {
                showAlert("Reset Cancelled", "No data was deleted.", AlertType.INFORMATION);
            }
        } else {
            showAlert("Reset Cancelled", "No changes were made.", AlertType.INFORMATION);
        }
    }

    /**
     * Permanently removes all order and order item data,
     * then resets related database sequences.
     */
    private void performCompleteReset() {
        Connection connection = null;

        try {
            connection = SqliteConnection.Connector();
            connection.setAutoCommit(false); // Begin transaction for safety

            // Delete all order items first (foreign key constraint)
            String deleteOrderItemsQuery = "DELETE FROM order_items";
            PreparedStatement deleteItemsStatement = connection.prepareStatement(deleteOrderItemsQuery);
            int itemsDeleted = deleteItemsStatement.executeUpdate();

            // Delete all orders
            String deleteOrdersQuery = "DELETE FROM orders";
            PreparedStatement deleteOrdersStatement = connection.prepareStatement(deleteOrdersQuery);
            int ordersDeleted = deleteOrdersStatement.executeUpdate();

            // Reset auto-increment counters
            try {
                PreparedStatement resetSequence = connection.prepareStatement(
                    "DELETE FROM sqlite_sequence WHERE name IN ('orders', 'order_items')"
                );
                resetSequence.executeUpdate();
                resetSequence.close();
            } catch (SQLException e) {
                System.out.println("Note: sqlite_sequence table not found or not needed.");
            }

            connection.commit(); // Apply all deletions

            System.out.println("=== COMPLETE RESET PERFORMED ===");
            System.out.println("Orders deleted: " + ordersDeleted);
            System.out.println("Order items deleted: " + itemsDeleted);
            System.out.println("Dashboard reset successfully.");
            System.out.println("===============================");

        } catch (SQLException e) {
            // If anything fails, revert all database changes
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
            showAlert("Reset Failed", "An error occurred during reset: " + e.getMessage(), AlertType.ERROR);
            e.printStackTrace();
        } finally {
            // Restore autocommit and close connection
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

    // ========== Data Loading ==========

    /**
     * Loads all dashboard data and updates labels and charts.
     */
    private void loadDashboardData() {
        loadTodayIncomeFromRecentOrders();
        loadProductsSoldFromRecentOrders();
        loadTotalIncomeFromRecentOrders();
        loadBestSellersChart();
        loadIncomeChart();
    }

    private void loadTodayIncomeFromRecentOrders() {
        Connection connection = null;
        try {
            connection = SqliteConnection.Connector();
            String query = "SELECT SUM(total_amount) as today_total FROM orders " +
                           "WHERE datetime(order_date || ' ' || order_time) >= datetime('now', '-24 hours')";

            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();

            double todayTotal = resultSet.next() ? resultSet.getDouble("today_total") : 0.0;
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

            int totalSold = resultSet.next() ? resultSet.getInt("total_sold") : 0;
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

            double totalIncome = resultSet.next() ? resultSet.getDouble("total_revenue") : 0.0;
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

    // ========== Charts ==========

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

            bestsellers.getData().clear();
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Best Sellers");

            while (result.next()) {
                series.getData().add(new XYChart.Data<>(result.getString("name"), result.getInt("total_quantity")));
            }

            bestsellers.getData().add(series);

        } catch (SQLException e) {
            System.err.println("Error loading best sellers chart: " + e.getMessage());
            bestsellers.getData().clear();
        }
    }

    private void loadIncomeChart() {
        String sql = "SELECT DATE(order_date) as date, SUM(total_amount) as daily_income " +
                     "FROM orders WHERE order_date >= date('now', '-7 days') " +
                     "GROUP BY DATE(order_date) ORDER BY date";

        try {
            prepare = connection.prepareStatement(sql);
            result = prepare.executeQuery();

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Daily Income");

            while (result.next()) {
                series.getData().add(new XYChart.Data<>(result.getString("date"), result.getDouble("daily_income")));
            }

            incomechart.getData().clear();
            incomechart.getData().add(series);

        } catch (SQLException e) {
            System.err.println("Error loading income chart: " + e.getMessage());
        }
    }

    /**
     * Sets up chart properties
     */
    private void setupCharts() {
        bestsellers.setLegendVisible(false);
        bestsellers.setAnimated(true);

        if (bestsellers.getXAxis() instanceof javafx.scene.chart.CategoryAxis xAxis) {
            xAxis.setTickLabelRotation(0); // Keep labels horizontal
        }

        incomechart.setLegendVisible(false);
        incomechart.setAnimated(true);
        incomechart.setCreateSymbols(false);
    }

    // ========== Utility ==========

    private void refreshDashboard() {
        loadDashboardData();
    }

    private void loadScene(ActionEvent event, String fxmlPath) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setTitle("Sebucha Order Management System");
        stage.setScene(new Scene(root));
        stage.show();
    }

    private void showAlert(String title, String message, AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * A helper for testing — prints dashboard metrics to the console.
     * Used for verifying data consistency between controllers.
     */
    public void demonstrateDashboardMetrics() {
        try {
            RecentOrderController recentOrderController = new RecentOrderController();
            double todaysIncome = recentOrderController.getTodaysIncome();
            int productsSold = recentOrderController.getTodaysProductsSold();
            double totalRevenue = recentOrderController.getTotalRevenue();

            System.out.println("=== DASHBOARD METRICS ===");
            System.out.println("Today's Income (24h): ₱" + decimalFormat.format(todaysIncome));
            System.out.println("Products Sold (24h): " + productsSold);
            System.out.println("Total Revenue: ₱" + decimalFormat.format(totalRevenue));
            System.out.println("=========================");

        } catch (Exception e) {
            System.err.println("Error retrieving dashboard metrics: " + e.getMessage());
        }
    }
}
