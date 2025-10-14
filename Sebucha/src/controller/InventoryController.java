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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import model.Product;
import model.SqliteConnection;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.ResourceBundle;

public class InventoryController implements Initializable {
    
    // Navigation buttons
    @FXML private Button dashboardbutton;
    @FXML private Button inventorybutton;
    @FXML private Button orderbutton;
    @FXML private Button recentorderbutton;
    @FXML private Button logoutbutton;
    @FXML private Button refreshButton;
    
    // Form fields
    @FXML private TextField productIdField;
    @FXML private TextField productNameField;
    @FXML private ComboBox<String> categoryComboBox;
    @FXML private TextField priceField;
    @FXML private TextField stockField;
    @FXML private ComboBox<String> statusComboBox;
    @FXML private TextField imagePathField;
    
    // Action buttons
    @FXML private Button addButton;
    @FXML private Button updateButton;
    @FXML private Button deleteButton;
    @FXML private Button clearButton;
    @FXML private Button selectImageButton;
    @FXML private Button removeImageButton;
    
    // Image view
    @FXML private ImageView productImageView;
    
    // Table and columns
    @FXML private TableView<Product> productsTable;
    @FXML private TableColumn<Product, Integer> idColumn;
    @FXML private TableColumn<Product, String> imageColumn;
    @FXML private TableColumn<Product, String> nameColumn;
    @FXML private TableColumn<Product, String> categoryColumn;
    @FXML private TableColumn<Product, Double> priceColumn;
    @FXML private TableColumn<Product, Integer> stockColumn;
    @FXML private TableColumn<Product, String> statusColumn;
    @FXML private TableColumn<Product, String> dateColumn;
    
    // Database connection
    private Connection connection;
    private PreparedStatement prepare;
    private ResultSet result;
    
    // Data
    private ObservableList<Product> productsList = FXCollections.observableArrayList();
    private DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
    private String selectedImagePath = "";
    private Product selectedProduct = null;
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Initialize database connection
        connection = SqliteConnection.Connector();
        
        // Setup combo boxes
        setupComboBoxes();
        
        // Setup table
        setupTable();
        
        // Load products
        loadProducts();
        
        // Set active button
        setActiveButton(inventorybutton);
        
        // Setup window
        Platform.runLater(() -> {
            Stage stage = (Stage) inventorybutton.getScene().getWindow();
            stage.setResizable(true);
            stage.centerOnScreen();
        });
        
        // Add shutdown hook to close database connection
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            SqliteConnection.closeConnection();
        }));
    }
    
    // Navigation methods
    @FXML
    private void handleDashboardButton(ActionEvent event) {
        try {
            loadScene(event, "/view/fxml/Dashboard.fxml");
        } catch (IOException e) {
            showAlert("Error", "Could not load Dashboard page: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
    
    @FXML
    private void handleInventoryButton(ActionEvent event) {
        // Already on inventory page
        refreshProducts();
        setActiveButton(inventorybutton);
    }
    
    @FXML
    private void handleOrderButton(ActionEvent event) {
        try {
            loadScene(event, "/view/fxml/Order.fxml");
        } catch (IOException e) {
            showAlert("Error", "Could not load Order page: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
    
    @FXML
    private void handleRecentOrderButton(ActionEvent event) {
        try {
            loadScene(event, "/view/fxml/RecentOrder.fxml");
        } catch (IOException e) {
            showAlert("Error", "Could not load Recent Orders page: " + e.getMessage(), Alert.AlertType.ERROR);
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
                showAlert("Error", "Could not load Login page: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }
    
    @FXML
    private void handleRefreshButton(ActionEvent event) {
        refreshProducts();
        showAlert("Refresh", "Product list has been refreshed successfully!", Alert.AlertType.INFORMATION);
    }
    
    // CRUD Operations
    @FXML
    private void handleAddProduct(ActionEvent event) {
        if (validateForm()) {
            String sql = "INSERT INTO products (name, category, price, stock, status, image_path, date_added) VALUES (?, ?, ?, ?, ?, ?, ?)";
            
            PreparedStatement prepare = null;
            try {
                prepare = connection.prepareStatement(sql);
                prepare.setString(1, productNameField.getText().trim());
                prepare.setString(2, categoryComboBox.getValue());
                prepare.setDouble(3, Double.parseDouble(priceField.getText().trim()));
                prepare.setInt(4, Integer.parseInt(stockField.getText().trim()));
                prepare.setString(5, statusComboBox.getValue());
                prepare.setString(6, selectedImagePath != null ? selectedImagePath : "");
                
                // Store as current timestamp in seconds (Unix timestamp)
                long currentTimestamp = System.currentTimeMillis() / 1000;
                prepare.setLong(7, currentTimestamp);
                
                int result = prepare.executeUpdate();
                if (result > 0) {
                    showAlert("Success", "Product added successfully!", Alert.AlertType.INFORMATION);
                    clearForm();
                    
                    // Force refresh the table data
                    Platform.runLater(() -> {
                        loadProducts();
                        productsTable.refresh();
                        System.out.println("Table refreshed after adding product");
                    });
                } else {
                    showAlert("Error", "Failed to add product!", Alert.AlertType.ERROR);
                }
                
            } catch (SQLException e) {
                System.err.println("SQL Error in handleAddProduct: " + e.getMessage());
                e.printStackTrace();
                showAlert("Database Error", "Error adding product: " + e.getMessage(), Alert.AlertType.ERROR);
            } catch (NumberFormatException e) {
                showAlert("Input Error", "Please enter valid numbers for price and stock!", Alert.AlertType.ERROR);
            } finally {
                // Always close PreparedStatement
                if (prepare != null) {
                    try {
                        prepare.close();
                    } catch (SQLException e) {
                        System.err.println("Error closing PreparedStatement: " + e.getMessage());
                    }
                }
            }
        }
    }
    
    @FXML
    private void handleUpdateProduct(ActionEvent event) {
        if (selectedProduct == null) {
            showAlert("No Selection", "Please select a product to update!", Alert.AlertType.WARNING);
            return;
        }
        
        if (validateForm()) {
            String sql = "UPDATE products SET name=?, category=?, price=?, stock=?, status=?, image_path=? WHERE id=?";
            
            PreparedStatement prepare = null;
            try {
                prepare = connection.prepareStatement(sql);
                prepare.setString(1, productNameField.getText());
                prepare.setString(2, categoryComboBox.getValue());
                prepare.setDouble(3, Double.parseDouble(priceField.getText()));
                prepare.setInt(4, Integer.parseInt(stockField.getText()));
                prepare.setString(5, statusComboBox.getValue());
                prepare.setString(6, selectedImagePath);
                prepare.setInt(7, selectedProduct.getId());
                
                int result = prepare.executeUpdate();
                if (result > 0) {
                    showAlert("Success", "Product updated successfully!", Alert.AlertType.INFORMATION);
                    clearForm();
                    loadProducts();
                } else {
                    showAlert("Error", "Failed to update product!", Alert.AlertType.ERROR);
                }
                
            } catch (SQLException e) {
                showAlert("Database Error", "Error updating product: " + e.getMessage(), Alert.AlertType.ERROR);
            } catch (NumberFormatException e) {
                showAlert("Input Error", "Please enter valid numbers for price and stock!", Alert.AlertType.ERROR);
            } finally {
           
                if (prepare != null) {
                    try {
                        prepare.close();
                    } catch (SQLException e) {
                        System.err.println("Error closing PreparedStatement: " + e.getMessage());
                    }
                }
            }
        }
    }
    
    @FXML
    private void handleDeleteProduct(ActionEvent event) {
        if (selectedProduct == null) {
            showAlert("No Selection", "Please select a product to delete!", Alert.AlertType.WARNING);
            return;
        }
        
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Delete Confirmation");
        confirmAlert.setHeaderText("Are you sure you want to delete this product?");
        confirmAlert.setContentText("Product: " + selectedProduct.getName() + "\nThis action cannot be undone!");
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String sql = "DELETE FROM products WHERE id=?";
            
            PreparedStatement prepare = null;
            try {
                prepare = connection.prepareStatement(sql);
                prepare.setInt(1, selectedProduct.getId());
                
                int deleteResult = prepare.executeUpdate();
                if (deleteResult > 0) {
                    showAlert("Success", "Product deleted successfully!", Alert.AlertType.INFORMATION);
                    clearForm();
                    loadProducts();
                } else {
                    showAlert("Error", "Failed to delete product!", Alert.AlertType.ERROR);
                }
                
            } catch (SQLException e) {
                showAlert("Database Error", "Error deleting product: " + e.getMessage(), Alert.AlertType.ERROR);
            } finally {
                // Always close PreparedStatement
                if (prepare != null) {
                    try {
                        prepare.close();
                    } catch (SQLException e) {
                        System.err.println("Error closing PreparedStatement: " + e.getMessage());
                    }
                }
            }
        }
    }
    
    @FXML
    private void handleClearForm(ActionEvent event) {
        clearForm();
    }
    
    // Image handling
    @FXML
    private void handleSelectImage(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Product Image");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
        );
        
        Stage stage = (Stage) selectImageButton.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);
        
        if (selectedFile != null) {
            selectedImagePath = selectedFile.getAbsolutePath();
            imagePathField.setText(selectedImagePath);
            
            try {
                Image image = new Image(selectedFile.toURI().toString());
                productImageView.setImage(image);
            } catch (Exception e) {
                showAlert("Error", "Could not load image: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }
    
    @FXML
    private void handleRemoveImage(ActionEvent event) {
        selectedImagePath = "";
        imagePathField.clear();
        productImageView.setImage(new Image(getClass().getResourceAsStream("/view/images/addimage.png")));
    }
    
    // Setup methods
    private void setupComboBoxes() {
        // Category options
        categoryComboBox.getItems().addAll(
            "Tea", "Coffee", "Smoothie", "Pastry", "Snack", "Other"
        );
        
        // Status options
        statusComboBox.getItems().addAll(
            "Available", "Out of Stock", "Discontinued"
        );
    }
    
    private void setupTable() {
        System.out.println("Setting up table columns...");
        
        // Setup table columns with explicit cell value factories
        idColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getId()).asObject());
        nameColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getName()));
        categoryColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getCategory()));
        priceColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleDoubleProperty(cellData.getValue().getPrice()).asObject());
        stockColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getStock()).asObject());
        statusColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getStatus()));
        dateColumn.setCellValueFactory(cellData -> {
            LocalDateTime dateAdded = cellData.getValue().getDateAdded();
            String dateString = dateAdded != null ? dateAdded.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")) : "";
            return new javafx.beans.property.SimpleStringProperty(dateString);
        });
        
        // Format price column
        priceColumn.setCellFactory(column -> new TableCell<Product, Double>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText("₱ " + decimalFormat.format(price));
                }
            }
        });
        
        // Setup image column with explicit cell value factory
        imageColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getImagePath()));
        imageColumn.setCellFactory(column -> new TableCell<Product, String>() {
            private final ImageView imageView = new ImageView();
            
            {
                imageView.setFitHeight(40);
                imageView.setFitWidth(40);
                imageView.setPreserveRatio(true);
            }
            
            @Override
            protected void updateItem(String imagePath, boolean empty) {
                super.updateItem(imagePath, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    try {
                        if (imagePath != null && !imagePath.isEmpty()) {
                            File imageFile = new File(imagePath);
                            if (imageFile.exists()) {
                                imageView.setImage(new Image(imageFile.toURI().toString()));
                            } else {
                                imageView.setImage(new Image(getClass().getResourceAsStream("/view/images/addimage.png")));
                            }
                        } else {
                            imageView.setImage(new Image(getClass().getResourceAsStream("/view/images/addimage.png")));
                        }
                    } catch (Exception e) {
                        imageView.setImage(new Image(getClass().getResourceAsStream("/view/images/addimage.png")));
                    }
                    setGraphic(imageView);
                }
            }
        });
        
        // Set the items to the table
        productsTable.setItems(productsList);
        System.out.println("Table items set. ProductsList size: " + productsList.size()); // Debug
        
        // Table selection listener
        productsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                selectedProduct = newSelection;
                selectProductForEdit(newSelection);
                System.out.println("Selected product: " + newSelection.getName()); // Debug
            }
        });
        
        // Enable table selection
        productsTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        productsTable.setFocusTraversable(true);
        
        System.out.println("Table setup completed."); // Debug
    }
    
    private void loadProducts() {
        productsList.clear();
        String sql = "SELECT * FROM products ORDER BY id DESC";
        
        PreparedStatement prepare = null;
        ResultSet result = null;
        
        try {
            prepare = connection.prepareStatement(sql);
            result = prepare.executeQuery();
            
            int count = 0;
            while (result.next()) {
                Product product = new Product(
                    result.getInt("id"),
                    result.getString("name"),
                    result.getString("category"),
                    result.getDouble("price"),
                    result.getInt("stock"),
                    result.getString("status"),
                    result.getString("image_path"),
                    result.getLong("date_added") // Read as Unix timestamp (long)
                );
                productsList.add(product);
                count++;
                System.out.println("Loaded product: " + product.getName()); // Debug output
            }
            
            System.out.println("Total products loaded: " + count); // Debug output
            System.out.println("ProductsList size: " + productsList.size()); // Debug output
            
            // Refresh the table view
            productsTable.refresh();
            
        } catch (SQLException e) {
            System.err.println("SQL Error in loadProducts: " + e.getMessage());
            e.printStackTrace();
            showAlert("Database Error", "Error loading products: " + e.getMessage(), Alert.AlertType.ERROR);
        } finally {
            // Always close ResultSet and PreparedStatement
            if (result != null) {
                try {
                    result.close();
                } catch (SQLException e) {
                    System.err.println("Error closing ResultSet: " + e.getMessage());
                }
            }
            if (prepare != null) {
                try {
                    prepare.close();
                } catch (SQLException e) {
                    System.err.println("Error closing PreparedStatement: " + e.getMessage());
                }
            }
        }
    }
    
    private void selectProductForEdit(Product product) {
        selectedProduct = product;
        productIdField.setText(String.valueOf(product.getId()));
        productNameField.setText(product.getName());
        categoryComboBox.setValue(product.getCategory());
        priceField.setText(String.valueOf(product.getPrice()));
        stockField.setText(String.valueOf(product.getStock()));
        statusComboBox.setValue(product.getStatus());
        
        if (product.getImagePath() != null && !product.getImagePath().isEmpty()) {
            selectedImagePath = product.getImagePath();
            imagePathField.setText(selectedImagePath);
            
            try {
                File imageFile = new File(selectedImagePath);
                if (imageFile.exists()) {
                    productImageView.setImage(new Image(imageFile.toURI().toString()));
                } else {
                    productImageView.setImage(new Image(getClass().getResourceAsStream("/view/images/sebucha1.png")));
                }
            } catch (Exception e) {
                productImageView.setImage(new Image(getClass().getResourceAsStream("/view/images/sebucha1.png")));
            }
        } else {
            handleRemoveImage(null);
        }
    }
    
    private void showProductDetails(Product product) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Product Details");
        alert.setHeaderText(product.getName());
        alert.setContentText(
            "ID: " + product.getId() + "\n" +
            "Category: " + product.getCategory() + "\n" +
            "Price: ₱ " + decimalFormat.format(product.getPrice()) + "\n" +
            "Stock: " + product.getStock() + "\n" +
            "Status: " + product.getStatus() + "\n" +
            "Date Added: " + product.getDateAdded()
        );
        alert.showAndWait();
    }
    
    private boolean validateForm() {
        if (productNameField.getText().trim().isEmpty()) {
            showAlert("Validation Error", "Product name is required!", Alert.AlertType.ERROR);
            return false;
        }
        
        if (categoryComboBox.getValue() == null) {
            showAlert("Validation Error", "Please select a category!", Alert.AlertType.ERROR);
            return false;
        }
        
        if (priceField.getText().trim().isEmpty()) {
            showAlert("Validation Error", "Price is required!", Alert.AlertType.ERROR);
            return false;
        }
        
        if (stockField.getText().trim().isEmpty()) {
            showAlert("Validation Error", "Stock quantity is required!", Alert.AlertType.ERROR);
            return false;
        }
        
        if (statusComboBox.getValue() == null) {
            showAlert("Validation Error", "Please select a status!", Alert.AlertType.ERROR);
            return false;
        }
        
        try {
            Double.parseDouble(priceField.getText());
        } catch (NumberFormatException e) {
            showAlert("Validation Error", "Please enter a valid price!", Alert.AlertType.ERROR);
            return false;
        }
        
        try {
            Integer.parseInt(stockField.getText());
        } catch (NumberFormatException e) {
            showAlert("Validation Error", "Please enter a valid stock quantity!", Alert.AlertType.ERROR);
            return false;
        }
        
        return true;
    }
    
    private void clearForm() {
        selectedProduct = null;
        productIdField.clear();
        productNameField.clear();
        categoryComboBox.setValue(null);
        priceField.clear();
        stockField.clear();
        statusComboBox.setValue(null);
        handleRemoveImage(null);
        productsTable.getSelectionModel().clearSelection();
    }
    
    private void refreshProducts() {
        loadProducts();
    }
    
    // Utility methods
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
    
    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
