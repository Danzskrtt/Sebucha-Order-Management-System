package controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import model.Product;
import model.SqliteConnection;
import model.InventoryIdGenerator;

import java.io.File;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ResourceBundle;

public class ProductUpdatePopupController implements Initializable {

    @FXML private TextField productIdField;
    @FXML private TextField productNameField;
    @FXML private ComboBox<String> categoryComboBox;
    @FXML private TextField priceField;
    @FXML private TextField stockField;
    @FXML private ComboBox<String> statusComboBox;
    @FXML private ImageView productImageView;
    @FXML private Button selectImageButton;
    @FXML private Button removeImageButton;
    @FXML private Button updateButton;
    @FXML private Button cancelButton;

    private Product productToUpdate;
    private String selectedImagePath = "";
    private InventoryController parentController;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupComboBoxes();
        // Set default image
        productImageView.setImage(new Image(getClass().getResourceAsStream("/view/images/addimage.png")));
    }

    public void setProduct(Product product) {
        this.productToUpdate = product;
        populateFields();
    }

    public void setParentController(InventoryController controller) {
        this.parentController = controller;
    }

    private void setupComboBoxes() {
        // Category options
        categoryComboBox.getItems().addAll(
            "Premium Series", "Classic Series", "Latte Series", "Frappe Series", 
            "Healthy Fruit Tea", "Hot Drinks", "Food Pair", "Add-ons"
        );
        
        // Status options
        statusComboBox.getItems().addAll(
            "Available", "Out of Stock", "Discontinued", "Low Stock"
        );
    }

    private void populateFields() {
        if (productToUpdate != null) {
            // Display the category-based ID format
            String categoryCode = InventoryIdGenerator.getCategoryCode(productToUpdate.getCategory());
            String formattedId = String.format("%s-%03d", categoryCode, productToUpdate.getId());
            productIdField.setText(formattedId);
            
            productNameField.setText(productToUpdate.getName());
            categoryComboBox.setValue(productToUpdate.getCategory());
            priceField.setText(String.valueOf(productToUpdate.getPrice()));
            stockField.setText(String.valueOf(productToUpdate.getStock()));
            statusComboBox.setValue(productToUpdate.getStatus());
            
            // Load existing image
            if (productToUpdate.getImagePath() != null && !productToUpdate.getImagePath().isEmpty()) {
                selectedImagePath = productToUpdate.getImagePath();
                try {
                    File imageFile = new File(selectedImagePath);
                    if (imageFile.exists()) {
                        productImageView.setImage(new Image(imageFile.toURI().toString()));
                    } else {
                        productImageView.setImage(new Image(getClass().getResourceAsStream("/view/images/addimage.png")));
                    }
                } catch (Exception e) {
                    productImageView.setImage(new Image(getClass().getResourceAsStream("/view/images/addimage.png")));
                }
            }
        }
    }

    @FXML
    private void handleSelectImage() {
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
                productImageView.setImage(image);
            } catch (Exception e) {
                showAlert("Error", "Could not load image: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    @FXML
    private void handleRemoveImage() {
        selectedImagePath = "";
        productImageView.setImage(new Image(getClass().getResourceAsStream("/view/images/addimage.png")));
    }

    @FXML
    private void handleUpdate() {
        if (validateForm()) {
            Connection connection = SqliteConnection.Connector();
            String sql = "UPDATE products SET name=?, category=?, price=?, stock=?, status=?, image_path=? WHERE id=?";
            
            PreparedStatement prepare = null;
            try {
                prepare = connection.prepareStatement(sql);
                prepare.setString(1, productNameField.getText().trim());
                prepare.setString(2, categoryComboBox.getValue());
                prepare.setDouble(3, Double.parseDouble(priceField.getText().trim()));
                prepare.setInt(4, Integer.parseInt(stockField.getText().trim()));
                prepare.setString(5, statusComboBox.getValue());
                prepare.setString(6, selectedImagePath);
                prepare.setInt(7, productToUpdate.getId());
                
                int result = prepare.executeUpdate();
                if (result > 0) {
                    showAlert("Success", "Product updated successfully!", Alert.AlertType.INFORMATION);
                    // Refresh parent controller's table
                    if (parentController != null) {
                        parentController.refreshTable();
                    }
                    closeWindow();
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
    private void handleCancel() {
        closeWindow();
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
            Double.parseDouble(priceField.getText().trim());
        } catch (NumberFormatException e) {
            showAlert("Validation Error", "Please enter a valid price!", Alert.AlertType.ERROR);
            return false;
        }
        
        try {
            Integer.parseInt(stockField.getText().trim());
        } catch (NumberFormatException e) {
            showAlert("Validation Error", "Please enter a valid stock quantity!", Alert.AlertType.ERROR);
            return false;
        }
        
        return true;
    }

    private void closeWindow() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}