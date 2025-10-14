module Sebucha {
	// JavaFX modules
	requires javafx.controls;
	requires javafx.fxml;
	requires javafx.base;
	requires javafx.graphics;
	
	// SQLite database
	requires java.sql;
	
	// Ikonli for icons
	requires org.kordamp.ikonli.core;
	requires org.kordamp.ikonli.javafx;
	requires org.kordamp.ikonli.bootstrapicons;
	
	// Export packages that contain Java classes
	exports model;
	exports controller;
	
	// Open packages to JavaFX for reflection access
	opens model to javafx.base, javafx.fxml;
	opens controller to javafx.fxml;
}