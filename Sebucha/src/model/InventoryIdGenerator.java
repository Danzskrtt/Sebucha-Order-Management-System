package model;

import java.util.HashMap;
import java.util.Map;

public class InventoryIdGenerator {
    
    // Map of category names to their 3-letter codes
    private static final Map<String, String> CATEGORY_CODES = new HashMap<>();
    
    static {
        // Initialize category code mappings
        CATEGORY_CODES.put("Premium Series", "PRE");
        CATEGORY_CODES.put("Classic Series", "CLA");
        CATEGORY_CODES.put("Latte Series", "LAT");
        CATEGORY_CODES.put("Frappe Series", "FRA");
        CATEGORY_CODES.put("Healthy Fruit Tea", "HFT");
        CATEGORY_CODES.put("Hot Drinks", "HOT");
        CATEGORY_CODES.put("Food Pair", "FOO");
        CATEGORY_CODES.put("Add-ons", "ADD");
    }
    
    /*
     * Generates a unique category-based ID in the format "CODE-XXX"
     * @param categoryName The category name to generate ID for
     * @return A unique ID like "PRE-001", "CLA-123", etc.
     */
    public static String generateCategoryId(String categoryName) {
        // Get the 3-letter code for the category
        String categoryCode = getCategoryCode(categoryName);
        
        // Generate a 3-digit random number based on timestamp (similar to OrderIdGenerator)
        long timestamp = System.currentTimeMillis() % 1000; // Get last 3 digits
        
        // Format as 3-digit number with leading zeros
        return String.format("%s-%03d", categoryCode, timestamp);
    }
    
    /*
     * Gets the 3-letter code for a given category name
     * @param categoryName The category name
     * @return The 3-letter code, or "UNK" for unknown categories
     */
    public static String getCategoryCode(String categoryName) {
        if (categoryName == null) {
            return "UNK";
        }
        
        return CATEGORY_CODES.getOrDefault(categoryName, generateCodeFromName(categoryName));
    }
    
    /*
     * Generates a 3-letter code from category name if not in predefined list
     * @param categoryName The category name
     * @return A 3-letter code derived from the name
     */
    private static String generateCodeFromName(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return "UNK";
        }
        
        String cleanName = categoryName.trim().toUpperCase();
        
        // If single word, take first 3 characters
        if (!cleanName.contains(" ")) {
            return cleanName.length() >= 3 ? cleanName.substring(0, 3) : cleanName;
        }
        
        // If multiple words, take first letter of each word up to 3 letters
        String[] words = cleanName.split("\\s+");
        StringBuilder code = new StringBuilder();
        
        for (String word : words) {
            if (code.length() < 3 && !word.isEmpty()) {
                code.append(word.charAt(0));
            }
        }
        
        // If still less than 3 characters, pad with first word's characters
        if (code.length() < 3 && words.length > 0) {
            String firstWord = words[0];
            for (int i = 1; i < firstWord.length() && code.length() < 3; i++) {
                code.append(firstWord.charAt(i));
            }
        }
        
        // Ensure we have exactly 3 characters
        String result = code.toString();
        if (result.length() < 3) {
            result = String.format("%-3s", result).replace(' ', 'X');
        }
        
        return result.substring(0, 3);
    }
    
    /*
     * Gets all available category codes
     * @return Map of category names to their codes
     */
    public static Map<String, String> getAllCategoryCodes() {
        return new HashMap<>(CATEGORY_CODES);
    }
    
    /*
     * Adds a new category code mapping
     * @param categoryName The category name
     * @param code The 3-letter code
     */
    public static void addCategoryCode(String categoryName, String code) {
        if (categoryName != null && code != null && code.length() == 3) {
            CATEGORY_CODES.put(categoryName, code.toUpperCase());
        }
    }
    
    /*
     * Generates a unique category-based ID that doesn't conflict with existing database records
     * @param connection Database connection to check existing IDs
     * @param categoryName The category name to generate ID for
     * @return A unique ID that doesn't exist in the database
     */
    public static int generateIdForCategory(java.sql.Connection connection, String categoryName) {
        String categoryCode = getCategoryCode(categoryName);
        int attempts = 0;
        int maxAttempts = 100; // Prevent infinite loops
        
        while (attempts < maxAttempts) {
            // Generate a random 3-digit number
            long timestamp = System.currentTimeMillis() % 1000;
            int candidateId = Integer.parseInt(String.format("%03d", timestamp));
            
            // Check if this ID already exists in the database
            if (!idExistsInDatabase(connection, candidateId)) {
                return candidateId;
            }
            
            attempts++;
            try {
                Thread.sleep(1); // Small delay to get different timestamp
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Fallback: generate a random number between 1-999
        return (int) (Math.random() * 999) + 1;
    }
    
    /*
     * Checks if an ID already exists in the products table
     * @param connection Database connection
     * @param id The ID to check
     * @return true if ID exists, false otherwise
     */
    private static boolean idExistsInDatabase(java.sql.Connection connection, int id) {
        if (connection == null) {
            return false;
        }
        
        String checkSql = "SELECT COUNT(*) FROM products WHERE id = ?";
        try (java.sql.PreparedStatement stmt = connection.prepareStatement(checkSql)) {
            stmt.setInt(1, id);
            java.sql.ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Error checking ID existence: " + e.getMessage());
        }
        
        return false;
    }
    
    /*
     * Generates a unique category-based ID string that doesn't conflict with existing database records
     * @param connection Database connection to check existing IDs
     * @param categoryName The category name to generate ID for
     * @return A unique ID string like "CLA-001", "PRE-002", etc.
     */
    public static String generateCategoryIdString(java.sql.Connection connection, String categoryName) {
        String categoryCode = getCategoryCode(categoryName);
        int attempts = 0;
        int maxAttempts = 100; // Prevent infinite loops
        
        while (attempts < maxAttempts) {
            // Generate a random 3-digit number
            long timestamp = System.currentTimeMillis() % 1000;
            int number = Integer.parseInt(String.format("%03d", timestamp));
            String candidateId = String.format("%s-%03d", categoryCode, number);
            
            // Check if this ID already exists in the database
            if (!stringIdExistsInDatabase(connection, candidateId)) {
                return candidateId;
            }
            
            attempts++;
            try {
                Thread.sleep(1); // Small delay to get different timestamp
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        //generate a random number between 1-999
        int fallbackNumber = (int) (Math.random() * 999) + 1;
        return String.format("%s-%03d", categoryCode, fallbackNumber);
    }
    
    /*
     * Checks if a string ID already exists in the products table
     * @param connection Database connection
     * @param id The ID string to check
     * @return true if ID exists, false otherwise
     */
    private static boolean stringIdExistsInDatabase(java.sql.Connection connection, String id) {
        if (connection == null) {
            return false;
        }
        
        // First check if we can query the table structure
        String checkSql = "SELECT COUNT(*) FROM products WHERE CAST(id AS TEXT) = ?";
        try (java.sql.PreparedStatement stmt = connection.prepareStatement(checkSql)) {
            stmt.setString(1, id);
            java.sql.ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (java.sql.SQLException e) {
            
            try {
                String checkSql2 = "SELECT COUNT(*) FROM products WHERE id = ?";
                java.sql.PreparedStatement stmt2 = connection.prepareStatement(checkSql2);
                stmt2.setString(1, id);
                java.sql.ResultSet rs2 = stmt2.executeQuery();
                if (rs2.next()) {
                    return rs2.getInt(1) > 0;
                }
            } catch (java.sql.SQLException e2) {
                System.err.println("Error checking string ID existence: " + e2.getMessage());
            }
        }
        
        return false; 
    }
}