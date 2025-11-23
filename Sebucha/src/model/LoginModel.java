package model;

import java.sql.SQLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LoginModel {
	
    Connection connection;
    private String currentUserRole;
    private String currentUsername;

    public LoginModel() {
        connection = SqliteConnection.Connector();
        if (connection == null) {
            System.out.println("Database Connection Failed");
            System.exit(1);
        }
    }
    
    public boolean isDbConnected() {
        try {
            return !connection.isClosed();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean isLogin(String user, String pass) throws SQLException {
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String query = "SELECT * FROM users WHERE username = ? AND password = ?";
        try {
            preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, user);
            preparedStatement.setString(2, pass);
            
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                currentUsername = user;
                currentUserRole = resultSet.getString("role");
                if (currentUserRole == null) {
                    currentUserRole = "staff"; // Default role
                }
                
                // Set user session for access across controllers
                UserSession.getInstance().setUser(currentUsername, currentUserRole);
                
                return true;
            } else {
                currentUsername = null;
                currentUserRole = null;
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (resultSet != null) resultSet.close();
            if (preparedStatement != null) preparedStatement.close();
        }
    }
    
    public String getCurrentUserRole() {
        return currentUserRole;
    }
    
    public String getCurrentUsername() {
        return currentUsername;
    }
    
    public boolean isAdmin() {
        return "admin".equals(currentUserRole);
    }
    
    public boolean isStaff() {
        return "staff".equals(currentUserRole);
    }
}
