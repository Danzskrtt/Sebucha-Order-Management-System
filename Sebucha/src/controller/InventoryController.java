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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import model.Product;
import model.SqliteConnection;
import model.InventoryIdGenerator;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.ResourceBundle;

//InventoryController

//Controls the Inventory screen: loads products, formats the table, and
//handles add/update/delete actions, image selection, and page navigation.
 
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
    
    // Action buttons
    @FXML private Button addButton;
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
    @FXML private TableColumn<Product, String> actionsColumn;
    
    // Database connection
    private Connection connection;
    
    // Data
    private ObservableList<Product> productsList = FXCollections.observableArrayList();
    private DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
    private String selectedImagePath = "";
    private Product selectedProduct = null;
    
    /*
     * Connects to DB, prepares combo boxes/table, loads products, and sets window behavior.
     */
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Initialize database connection
        connection = SqliteConnection.Connector();
        
        // Setup combo boxes
        setupComboBoxes();
        
        // Setup image view for automatic centering and sizing
        setupImageView();
        
        // Setup table
        setupTable();
        
        // Load products
        loadProducts();
        
        // Setup window
        Platform.runLater(() -> {
            Stage stage = (Stage) inventorybutton.getScene().getWindow();
            stage.setResizable(false);
            stage.centerOnScreen();
        });
        
        // Add shutdown hook to close database connection
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            SqliteConnection.closeConnection();
        }));
    }
    
    /**
     * Configures the ImageView for automatic centering and proper sizing
     */
    private void setupImageView() {
        // Enable image preservation and centering
        productImageView.setPreserveRatio(true);
        productImageView.setSmooth(true);
        
        // Set preferred dimensions for the image preview
        productImageView.setFitWidth(200);  // Adjust based on your UI layout
        productImageView.setFitHeight(200); // Adjust based on your UI layout
        
        // Set default image with auto-fit
        setImageWithAutoFit(new Image(getClass().getResourceAsStream("/view/images/addimage.png")));
    }
    
    /**
     * Sets an image in the ImageView with automatic centering and fitting
     */
    private void setImageWithAutoFit(Image image) {
        if (image != null) {
            productImageView.setImage(image);
            
            // The ImageView will automatically center and fit the image
            // due to the preserveRatio=true and fitWidth/fitHeight settings
        }
    }
    
    // Navigation methods
    /* Loads the Dashboard page. */
    @FXML
    private void handleDashboardButton(ActionEvent event) {
        try {
            loadScene(event, "/view/fxml/Dashboard.fxml");
        } catch (IOException e) {
            showAlert("Error", "Could not load Dashboard page: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
    
    /* Refreshes the current Inventory list without changing page. */
    @FXML
    private void handleInventoryButton(ActionEvent event) {
        // Already on inventory page
        refreshProducts();
    }
    
    /* Navigates to the Order page. */
    @FXML
    private void handleOrderButton(ActionEvent event) {
        try {
            loadScene(event, "/view/fxml/Order.fxml");
        } catch (IOException e) {
            showAlert("Error", "Could not load Order page: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
   
    /* Navigates to the Recent Orders page. */
    @FXML
    private void handleRecentOrderButton(ActionEvent event) {
        try {
            loadScene(event, "/view/fxml/RecentOrder.fxml");
        } catch (IOException e) {
            showAlert("Error", "Could not load Recent Orders page: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
    
    /* Confirms and logs out to the Login page. */
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
    
    /* Reloads products from DB and shows a short success notice. */
    @FXML
    private void handleRefreshButton(ActionEvent event) {
        refreshProducts();
        showAlert("Refresh", "Product list has been refreshed successfully!", Alert.AlertType.INFORMATION);
    }
    
    // CRUD Operations
    /*
     * Validates inputs and inserts a new product.
     * Generates category-based numeric ID and stores timestamp/image path.
     */
    @FXML
    private void handleAddProduct(ActionEvent event) {
        if (validateForm()) {
            String sql = "INSERT INTO products (id, name, category, price, stock, status, image_path, date_added) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            
            PreparedStatement prepare = null;
            try {
                prepare = connection.prepareStatement(sql);
                
                // Generate a numeric ID for the database (the table display will format it with category code)
                int numericId = InventoryIdGenerator.generateIdForCategory(connection, categoryComboBox.getValue());
                
                prepare.setInt(1, numericId);
                prepare.setString(2, productNameField.getText().trim());
                prepare.setString(3, categoryComboBox.getValue());
                prepare.setDouble(4, Double.parseDouble(priceField.getText().trim()));
                prepare.setInt(5, Integer.parseInt(stockField.getText().trim()));
                prepare.setString(6, statusComboBox.getValue());
                prepare.setString(7, selectedImagePath != null ? selectedImagePath : "");
                
                long currentTimestamp = System.currentTimeMillis() / 1000;
                prepare.setLong(8, currentTimestamp);
                
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
    
    /* Updates the selected product after validation. */
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
    
    /* Confirms and deletes the selected product. */
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
    
    /* Clears all form fields and current selection. */
    @FXML
    private void handleClearForm(ActionEvent event) {
        clearForm();
    }
    
    // Image handling
    /* Lets the user pick an image file and previews it. */
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
            
            try {
                Image image = new Image(selectedFile.toURI().toString());
                setImageWithAutoFit(image);
            } catch (Exception e) {
                showAlert("Error", "Could not load image: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }
    
    /* Removes custom image and restores the default placeholder. */
    @FXML
    private void handleRemoveImage(ActionEvent event) {
        selectedImagePath = "";
        setImageWithAutoFit(new Image(getClass().getResourceAsStream("/view/images/addimage.png")));
    }
    
    // Setup methods
    /*
     * Fills category/status options and auto-generates a readable product ID
     * whenever category changes.
     */
    private void setupComboBoxes() {
        // Category options
        categoryComboBox.getItems().addAll(
            "Premium Series", "Classic Series", "Latte Series", "Frappe Series", "Healthy Fruit Tea", "Hot Drinks", "Food Pair"
            ,"Add-ons"
        );
        
        // Add listener to category selection to auto-generate product ID
        categoryComboBox.setOnAction(event -> {
            String selectedCategory = categoryComboBox.getValue();
            if (selectedCategory != null && !selectedCategory.isEmpty()) {
                // Generate category-based ID and display it in the productIdField
                String generatedId = InventoryIdGenerator.generateCategoryIdString(connection, selectedCategory);
                productIdField.setText(generatedId);
                System.out.println("Generated ID for " + selectedCategory + ": " + generatedId);
            }
        });
        
        // Status options
        statusComboBox.getItems().addAll(
            "Available", "Out of Stock", "Discontinued", "Low Stock"
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
        
        // Apply white background styling to all columns except status
        String whiteColumnStyle = "-fx-alignment: center;";
        
        // Format ID column to show category-based format (e.g., "CLA-001")
        idColumn.setCellFactory(column -> new TableCell<Product, Integer>() {
            @Override
            protected void updateItem(Integer id, boolean empty) {
                super.updateItem(id, empty);
                if (empty || id == null) {
                    setText(null);
                    setStyle("");
                } else {
                    // Get the product from the table row to access category
                    Product product = getTableView().getItems().get(getIndex());
                    if (product != null) {
                        String categoryCode = InventoryIdGenerator.getCategoryCode(product.getCategory());
                        setText(String.format("%s-%03d", categoryCode, id));
                    } else {
                        setText(String.valueOf(id));
                    }
                    setStyle(whiteColumnStyle);
                }
            }
        });
        
        // Format name column
        nameColumn.setCellFactory(column -> new TableCell<Product, String>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(name);
                    setStyle(whiteColumnStyle);
                }
            }
        });
        
        // Format category column with unique color coding for each category
        categoryColumn.setCellFactory(column -> new TableCell<Product, String>() {
            @Override
            protected void updateItem(String category, boolean empty) {
                super.updateItem(category, empty);
                if (empty || category == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(category);
                    
                    // Apply unique color coding for each category
                    String style = "-fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 5; -fx-alignment: center; ";
                    
                    switch (category.toLowerCase()) {
                        case "premium series":
                            style += "-fx-background-color: #D4AF37;"; // Rich Gold
                            break;
                        case "classic series":
                            style += "-fx-background-color: #6F4E37;"; // Deep Brown
                            break;
                        case "latte series":
                            style += "-fx-background-color: #F5DEB3; -fx-text-fill: #333333;"; // Cream Beige with dark text
                            break;
                        case "frappe series":
                            style += "-fx-background-color: #4DB6AC;"; // Cool Teal
                            break;
                        case "healthy fruit tea":
                            style += "-fx-background-color: #FFB74D; -fx-text-fill: #333333;"; // Bright Orange with dark text
                            break;
                        case "hot drinks":
                            style += "-fx-background-color: #E57373;"; // Warm Red
                            break;
                        case "food pair":
                            style += "-fx-background-color: #8BC34A;"; // Olive Green
                            break;
                        case "add-ons":
                            style += "-fx-background-color: #CFD8DC; -fx-text-fill: #333333;"; // Light Gray with dark text
                            break;
                        default:
                            style += "-fx-background-color: #9E9E9E;"; // Default gray for unknown categories
                            break;
                    }
                    
                    setStyle(style);
                    setAlignment(javafx.geometry.Pos.CENTER);
                }
            }
        });
        
        // Format stock column
        stockColumn.setCellFactory(column -> new TableCell<Product, Integer>() {
            @Override
            protected void updateItem(Integer stock, boolean empty) {
                super.updateItem(stock, empty);
                if (empty || stock == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.valueOf(stock));
                    // Apply bold text styling
                    setStyle("-fx-alignment: center; -fx-font-weight: bold;");
                }
            }
        });
        
        // Format date column
        dateColumn.setCellFactory(column -> new TableCell<Product, String>() {
            @Override
            protected void updateItem(String date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(date);
                    setStyle(whiteColumnStyle);
                }
            }
        });
        
        // Format price column
        priceColumn.setCellFactory(column -> new TableCell<Product, Double>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText("₱ " + decimalFormat.format(price));
                    // Apply bold green text styling
                    setStyle("-fx-alignment: center; -fx-font-weight: bold; -fx-text-fill: #22C55E;");
                }
            }
        });
        
        // Keep the existing status column with color coding (unchanged)
        statusColumn.setCellFactory(column -> new TableCell<Product, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    
                    // Apply color coding based on status
                    String style = "-fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 5; ";
                    
                    switch (status.toLowerCase()) {
                        case "available":
                            style += "-fx-background-color: #22C55E;"; // Green 
                            break;
                        case "out of stock":
                            style += "-fx-background-color: #F97316;"; // Orange 
                            break;
                        case "discontinued":
                            style += "-fx-background-color: #EF4444;"; // Red 
                            break;
                        case "low stock":
                            style += "-fx-background-color: #FFC107;"; //Amber
                            break;
                    }
                    
                    setStyle(style);
                    setAlignment(javafx.geometry.Pos.CENTER);
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
                    // Center the image in the cell
                    setAlignment(javafx.geometry.Pos.CENTER);
                }
            }
        });
        
        // Format actions column with Update and Delete buttons
        actionsColumn.setCellFactory(column -> new TableCell<Product, String>() {
            private final Button updateButton = new Button();
            private final Button deleteButton = new Button();
            private final javafx.scene.layout.HBox buttonBox = new javafx.scene.layout.HBox(6);
            
            {
                buttonBox.setAlignment(javafx.geometry.Pos.CENTER);
                buttonBox.setPadding(new javafx.geometry.Insets(4, 4, 4, 4));
                
                updateButton.setStyle(
                    "-fx-background-color: linear-gradient(to bottom, #2196F3 0%, #1E88E5 50%, #1976D2 100%);" +
                    "-fx-background-radius: 8;" +
                    "-fx-text-fill: white;" +
                    "-fx-font-family: 'Calibri';" +
                    "-fx-font-size: 10px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-cursor: hand;" +
                    "-fx-padding: 4 8;" +
                    "-fx-pref-width: 60px;" +
                    "-fx-pref-height: 28px;"
                );
                updateButton.setText("Update");
                updateButton.setTooltip(new Tooltip("Update Product"));
                
                deleteButton.setStyle(
                    "-fx-background-color: linear-gradient(to bottom, #EF4444 0%, #DC2626 50%, #991B1B 100%);" +
                    "-fx-background-radius: 8;" +
                    "-fx-text-fill: white;" +
                    "-fx-font-family: 'Calibri';" +
                    "-fx-font-size: 10px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-cursor: hand;" +
                    "-fx-padding: 4 8;" +
                    "-fx-pref-width: 60px;" +
                    "-fx-pref-height: 28px;"
                );
                deleteButton.setText("Delete");
                deleteButton.setTooltip(new Tooltip("Delete Product"));
                
                // Set button actions
                updateButton.setOnAction(event -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        Product product = getTableRow().getItem();
                        openUpdatePopup(product);
                    }
                });
                
                deleteButton.setOnAction(event -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        Product product = getTableRow().getItem();
                        selectedProduct = product;
                        handleDeleteProduct(new ActionEvent(deleteButton, this));
                    }
                });
                
                buttonBox.getChildren().addAll(updateButton, deleteButton);
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
        
        // Disable table row selection completely
        productsTable.setRowFactory(tv -> {
            TableRow<Product> row = new TableRow<Product>() {
                @Override
                public void updateItem(Product item, boolean empty) {
                    super.updateItem(item, empty);
                    // Remove any selection styling
                    setStyle("-fx-background-color: transparent;");
                }
            };
            
            // Disable row selection on mouse click - only allow scrolling and button clicks
            row.setOnMouseClicked(event -> {
                // Clear any selection that might occur
                productsTable.getSelectionModel().clearSelection();
                event.consume(); // Prevent further event propagation
            });
            
            // Allow scrolling by not consuming scroll events
            row.setOnScroll(event -> {
                // Let scroll events pass through to the table
            });
            
            return row;
        });
        
        // Disable table selection model
        productsTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        productsTable.setFocusTraversable(false); // Disable focus traversal
        
        // Remove the old table selection listener since we're disabling row selection
        // productsTable.getSelectionModel().selectedItemProperty().addListener(...);
        
        // Set the items to the table
        productsTable.setItems(productsList);
        System.out.println("Table setup completed with action buttons and disabled row selection.");
    }
    
    /** Loads products from DB into the table's backing list and refreshes the view. */
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
                // Convert Unix timestamp to LocalDateTime
                long unixTimestamp = result.getLong("date_added");
                LocalDateTime dateAdded = java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochSecond(unixTimestamp), 
                    java.time.ZoneId.systemDefault()
                );
                
                Product product = new Product(
                    result.getInt("id"),
                    result.getString("name"),
                    result.getString("category"),
                    result.getDouble("price"),
                    result.getInt("stock"),
                    result.getString("status"),
                    result.getString("image_path"),
                    dateAdded
                );
                productsList.add(product);
                count++;
                System.out.println("Loaded product: " + product.getName()); 
            }
            
            System.out.println("Total products loaded: " + count); 
            System.out.println("ProductsList size: " + productsList.size()); 
            
            // Refresh the table view
            productsTable.refresh();
            
        } catch (SQLException e) {
            System.err.println("SQL Error in loadProducts: " + e.getMessage());
            e.printStackTrace();
            showAlert("Database Error", "Error loading products: " + e.getMessage(), Alert.AlertType.ERROR);
        } finally {
          
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
    
    /** Copies selected product to the form, formats its ID, and previews its image. */
    private void selectProductForEdit(Product product) {
        selectedProduct = product;
        // Display the category-based ID format in the field
        String categoryCode = InventoryIdGenerator.getCategoryCode(product.getCategory());
        String formattedId = String.format("%s-%03d", categoryCode, product.getId());
        productIdField.setText(formattedId);
        
        productNameField.setText(product.getName());
        categoryComboBox.setValue(product.getCategory());
        priceField.setText(String.valueOf(product.getPrice()));
        stockField.setText(String.valueOf(product.getStock()));
        statusComboBox.setValue(product.getStatus());
        
        if (product.getImagePath() != null && !product.getImagePath().isEmpty()) {
            selectedImagePath = product.getImagePath();
            
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
    
    /* Quick info dialog for a product (formatted ID, price, stock, date). */
    private void showProductDetails(Product product) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Product Details");
        alert.setHeaderText(product.getName());
        
        // Format the ID with category code
        String categoryCode = InventoryIdGenerator.getCategoryCode(product.getCategory());
        String formattedId = String.format("%s-%03d", categoryCode, product.getId());
        
        alert.setContentText(
            "ID: " + formattedId + "\n" +
            "Category: " + product.getCategory() + "\n" +
            "Price: ₱ " + decimalFormat.format(product.getPrice()) + "\n" +
            "Stock: " + product.getStock() + "\n" +
            "Status: " + product.getStatus() + "\n" +
            "Date Added: " + product.getDateAdded()
        );
        alert.showAndWait();
    }
    
    /* Validates required fields and numeric formats; shows targeted errors. */
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
    
    /* Resets the form and clears table selection. */
    private void clearForm() {
        selectedProduct = null;
        productIdField.clear();
        productNameField.clear();
        
        // Clear category combobox selection and restore prompt text
        categoryComboBox.getSelectionModel().clearSelection();
        categoryComboBox.setPromptText("Select category");
        
        priceField.clear();
        stockField.clear();
        
        // Clear status combobox selection and restore prompt text  
        statusComboBox.getSelectionModel().clearSelection();
        statusComboBox.setPromptText("Select status");
        
        handleRemoveImage(null);
        productsTable.getSelectionModel().clearSelection();
    }
    
    /* Convenience wrapper to reload products. */
    private void refreshProducts() {
        loadProducts();
    }
    
    /* Opens the update popup window for the selected product */
    private void openUpdatePopup(Product product) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/ProductUpdatePopup.fxml"));
            Parent root = loader.load();
            
            // Get the controller and set the product data
            ProductUpdatePopupController controller = loader.getController();
            controller.setProduct(product);
            controller.setParentController(this);
            
            // Create a new stage for the popup
            Stage popupStage = new Stage();
            popupStage.setTitle("Update Product");
            popupStage.setScene(new Scene(root));
            popupStage.setResizable(false);
            popupStage.initOwner(inventorybutton.getScene().getWindow());
            popupStage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            
            // Center the popup
            popupStage.centerOnScreen();
            
            // Show the popup
            popupStage.showAndWait();
            
        } catch (IOException e) {
            showAlert("Error", "Could not open update popup: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }
    
    /* Public method to refresh the table - called by popup controller */
    public void refreshTable() {
        loadProducts();
    }
    
    // Utility methods
    /* Loads another FXML view into the current stage. */
    private void loadScene(ActionEvent event, String fxmlPath) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        Scene scene = new Scene(root);
        stage.setTitle("Sebucha Order Management System");
        stage.setScene(scene);
        stage.show();
    }
    
    /* Shows an alert with title/message/type. */
    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
