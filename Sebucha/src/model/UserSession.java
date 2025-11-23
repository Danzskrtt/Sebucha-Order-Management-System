package model;

/* session manager to store current user information
 * across different controllers in the application
 */

public class UserSession {
    private static UserSession instance;
    private String username;
    private String userRole;
    
    private UserSession() {}
    
    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }
    
    public void setUser(String username, String userRole) {
        this.username = username;
        this.userRole = userRole;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getUserRole() {
        return userRole;
    }
    
    public boolean isAdmin() {
        return "admin".equals(userRole);
    }
    
    public boolean isStaff() {
        return "staff".equals(userRole);
    }
    
    public void clearSession() {
        username = null;
        userRole = null;
    }
    
    public boolean isLoggedIn() {
        return username != null && userRole != null;
    }
}