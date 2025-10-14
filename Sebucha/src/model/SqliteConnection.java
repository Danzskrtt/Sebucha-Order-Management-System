package model;

import java.sql.Connection;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SqliteConnection {
    private static final String DATABASE_URL = "jdbc:sqlite:sebucha.db";
    private static Connection connection;
    
    public static Connection Connector() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(DATABASE_URL);
            
            // Create tables if they don't exist
            createTables();
            
            return connection;
        } catch (Exception e) {
            System.err.println("Error connecting to database: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private static void createTables() {
        try (Statement statement = connection.createStatement()) {
            
            // Create products table if it doesn't exist (match actual DB: date_added TEXT)
            String createProductsTable = """
                CREATE TABLE IF NOT EXISTS products (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    price REAL NOT NULL,
                    stock INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    image_path TEXT,
                    date_added TEXT NOT NULL
                )
            """;
            
            // Create orders table if it doesn't exist (match actual DB)
            String createOrdersTable = """
                CREATE TABLE IF NOT EXISTS orders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    customer_name TEXT NOT NULL,
                    order_type TEXT NOT NULL DEFAULT 'Dine-in',
                    payment_method TEXT NOT NULL DEFAULT 'Cash',
                    order_status TEXT NOT NULL DEFAULT 'Completed',
                    total_amount REAL NOT NULL,
                    order_date TEXT NOT NULL,
                    order_time TEXT NOT NULL,
                    notes TEXT
                )
            """;
            
            // Create order_items table if it doesn't exist (match actual DB)
            String createOrderItemsTable = """
                CREATE TABLE IF NOT EXISTS order_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id INTEGER NOT NULL,
                    product_id INTEGER NOT NULL,
                    product_name TEXT NOT NULL,
                    unit_price REAL NOT NULL,
                    quantity INTEGER NOT NULL,
                    total_price REAL NOT NULL,
                    FOREIGN KEY (order_id) REFERENCES orders(id),
                    FOREIGN KEY (product_id) REFERENCES products(id)
                )
            """;
            
            // Execute table creation statements
            statement.execute(createProductsTable);
            statement.execute(createOrdersTable);
            statement.execute(createOrderItemsTable);
            
            System.out.println("Database tables created successfully or already exist.");
            
        } catch (SQLException e) {
            System.err.println("Error creating tables: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed.");
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
        }
    }
}