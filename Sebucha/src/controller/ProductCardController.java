package controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import model.Product;

import java.io.File;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ResourceBundle;

public class ProductCardController implements Initializable {
    
    @FXML private Label productNameLabel;
    @FXML private Label productPriceLabel;
    @FXML private Label productCategoryLabel;
    @FXML private Label productStockLabel;
    @FXML private ImageView productImageView;
    @FXML private Spinner<Integer> quantitySpinner;
    @FXML private Button addToCartButton;
    
    private Product product;
    private OrderController orderController;
    private DecimalFormat decimalFormat = new DecimalFormat("#0.00");
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize quantity spinner
        SpinnerValueFactory<Integer> valueFactory = 
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99, 1);
        quantitySpinner.setValueFactory(valueFactory);
        quantitySpinner.setEditable(true);
    }
    
    public void setProduct(Product product) {
        this.product = product;
        updateCardDisplay();
    }
    
    public void setOrderController(OrderController orderController) {
        this.orderController = orderController;
    }
    
    private void updateCardDisplay() {
        if (product != null) {
            productNameLabel.setText(product.getName());
            productPriceLabel.setText("₱" + decimalFormat.format(product.getPrice()));
            productCategoryLabel.setText(product.getCategory());
            productStockLabel.setText("Stock: " + product.getStock());
            
            // Load product image
            loadProductImage();
            
            // Enable/disable add button based on stock
            addToCartButton.setDisable(product.getStock() <= 0);
            
            // Set max quantity based on available stock
            if (product.getStock() > 0) {
                SpinnerValueFactory<Integer> valueFactory = 
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(1, product.getStock(), 1);
                quantitySpinner.setValueFactory(valueFactory);
            }
        }
    }
    
    private void loadProductImage() {
        try {
            String imagePath = product.getImagePath();
            if (imagePath != null && !imagePath.isEmpty()) {
                File imageFile = new File(imagePath);
                if (imageFile.exists()) {
                    Image image = new Image(imageFile.toURI().toString());
                    productImageView.setImage(image);
                } else {
                    // Load default image
                    loadDefaultImage();
                }
            } else {
                loadDefaultImage();
            }
        } catch (Exception e) {
            loadDefaultImage();
        }
    }
    
    private void loadDefaultImage() {
        try {
            Image defaultImage = new Image(getClass().getResourceAsStream("/view/images/addimage.png"));
            productImageView.setImage(defaultImage);
        } catch (Exception e) {
            // If default image not found, leave empty
            System.err.println("Could not load default product image");
        }
    }
    
    @FXML
    private void handleAddToCart() {
        if (product != null && orderController != null) {
            int quantity = quantitySpinner.getValue();
            if (quantity > 0 && quantity <= product.getStock()) {
                orderController.addProductToCart(product, quantity);
                
                // Reset quantity spinner to 1
                quantitySpinner.getValueFactory().setValue(1);
            }
        }
    }
    
    public Product getProduct() {
        return product;
    }
}
