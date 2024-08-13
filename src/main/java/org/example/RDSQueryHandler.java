package org.example;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RDSQueryHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private static final String DB_URL = System.getenv("DB_URL");
	private static final String DB_USER = System.getenv("DB_USER");
	private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");

	private final ObjectMapper objectMapper = new ObjectMapper();
	@Override
	public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
		LambdaLogger logger = context.getLogger();
		Map<String, Object> response = new HashMap<>();
		Map<String, Object> data = new HashMap<>();
		List<Map<String, Object>> menuList = new ArrayList<>();

		String body = (String) input.get("body");
		Map<String, String> queryStringParameters = (Map<String, String>) input.get("queryStringParameters");

		String mealDate = queryStringParameters.get("date").toString();
		String restaurantName = queryStringParameters.get("cafeteria").toString();

		String query = "SELECT m.meal_id, m.meal_date, m.meal_day, m.category, m.menu, r.name AS restaurant_name " +
			"FROM meal m " +
			"JOIN restaurant r ON m.restaurant_id = r.restaurant_id " +
			"WHERE m.meal_date = ? AND r.name = ? ";

		try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
			 PreparedStatement pstmt = conn.prepareStatement(query)) {

			pstmt.setString(1, mealDate);
			pstmt.setString(2, restaurantName);

			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					if (data.isEmpty()) {
						data.put("date", rs.getString("meal_date"));
						data.put("day", rs.getString("meal_day"));
						data.put("cafeteria", rs.getString("restaurant_name"));
					}

					Map<String, Object> menuItem = new HashMap<>();
					menuItem.put("id", rs.getInt("meal_id"));
					menuItem.put("category", rs.getString("category"));
					menuItem.put("food", parseFood(rs.getString("menu")));
					menuList.add(menuItem);
				}
			}

			data.put("menu", menuList);
			response.put("success", true);
			response.put("data", data);

		} catch (SQLException e) {
			logger.log("Error executing SQL query: " + e.getMessage());
			response.put("success", false);
			response.put("error", "Database operation failed: " + e.getMessage());
		}
		return response;
	}

	private List<String> parseFood(String foodString) {
		// Remove the square brackets
		String content = foodString.substring(1, foodString.length() - 1);

		if (content.equals("등록된 식단내용이 없습니다")) {
			return Collections.singletonList(content);
		} else {
			// Split the string by comma and trim each item
			return Arrays.stream(content.split(","))
				.map(String::trim)
				.toList();
		}
	}
}
