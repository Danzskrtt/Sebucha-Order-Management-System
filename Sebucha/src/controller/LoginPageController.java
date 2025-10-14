package controller;

import javafx.fxml.FXML;

import javafx.scene.control.Label;
import java.net.URL;
import java.sql.SQLException;
import java.util.ResourceBundle;
import model.LoginModel; 
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;
import javafx.event.ActionEvent;
import javafx.scene.paint.Color;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Node;
import java.io.IOException;

public class LoginPageController implements Initializable {
    public LoginModel loginmodel = new LoginModel();
    
    @FXML
    private Label isConnected;
    
    @FXML
    private TextField UsernameField;
    
    @FXML
    private TextField PasswordField;
    
    @Override
    
    public void initialize(URL location, ResourceBundle resources) {
    	
    	if(loginmodel.isDbConnected()) {
    		isConnected.setText("Database is Connected");
    	}else {
    		isConnected.setText("Database is not Connected");
    	}
    }
    
    private boolean validateInput(String username, String password) {
       
        UsernameField.setStyle("");
        PasswordField.setStyle("");
        
        boolean isValid = true;
        StringBuilder errorMessage = new StringBuilder();
        
        // Check if username is empty
        if (username == null || username.trim().isEmpty()) {
            UsernameField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            errorMessage.append("Username cannot be empty. ");
            isValid = false;
        } else if (username.trim().length() < 3) {
            UsernameField.setStyle("-fx-border-color: orange; -fx-border-width: 2px;");
            errorMessage.append("Username must be at least 3 characters. ");
            isValid = false;
        }
        
        // Check if password is empty
        if (password == null || password.trim().isEmpty()) {
            PasswordField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            errorMessage.append("Password cannot be empty. ");
            isValid = false;
        } else if (password.length() < 4) {
            PasswordField.setStyle("-fx-border-color: orange; -fx-border-width: 2px;");
            errorMessage.append("Password must be at least 4 characters. ");
            isValid = false;
        }
        
        // Display validation error message if any
        if (!isValid) {
            isConnected.setText(errorMessage.toString().trim());
            isConnected.setTextFill(Color.RED);
        }
        
        return isValid;
    }
   
    public void Login (ActionEvent event) {
        String username = UsernameField.getText();
        String password = PasswordField.getText();
        
        // Validate input first
        if (!validateInput(username, password)) {
            return;
        }
        
        UsernameField.setStyle("");
        PasswordField.setStyle("");
        
    	try {
            if(loginmodel.isLogin(username.trim(), password)) {
                // Navigate to Dashboard
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fxml/Dashboard.fxml")); // adjusted path (file must exist)
                    Parent root = loader.load();
                    
                    Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
                    Scene scene = new Scene(root); 
                    stage.setScene(scene);
                    stage.setTitle("Sebucha - Dashboard");
                    stage.setResizable(true); 
                    stage.show();
                    
                } catch (IOException e) {
                    isConnected.setText("Error loading dashboard. Please try again.");
                    isConnected.setTextFill(Color.RED);
                    e.printStackTrace();
                }
                
            } else {
                isConnected.setText("Username and Password is not correct");
                isConnected.setTextFill(Color.RED);
                
                
                UsernameField.setStyle("-fx-border-color: red; -fx-border-width: 1px;");
                PasswordField.setStyle("-fx-border-color: red; -fx-border-width: 1px;");
            }
        } catch (SQLException e) {
            isConnected.setText("Database connection error. Please try again later.");
            isConnected.setTextFill(Color.RED);
            e.printStackTrace();
        }
    }
    
}