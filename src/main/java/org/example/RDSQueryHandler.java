package org.example;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.*;

public class RDSQueryHandler implements RequestHandler<Object, String> {

	private static final String DB_URL = System.getenv("DB_URL");
	private static final String DB_USER = System.getenv("DB_USER");
	private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String handleRequest(Object input, Context context) {
		try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
			 Statement stmt = conn.createStatement();
			 ResultSet rs = stmt.executeQuery("SELECT * FROM notice")) {

			ArrayNode resultArray = objectMapper.createArrayNode();

			ResultSetMetaData metaData = rs.getMetaData();
			int columnCount = metaData.getColumnCount();

			while (rs.next()) {
				ObjectNode rowObject = objectMapper.createObjectNode();
				for (int i = 1; i <= columnCount; i++) {
					String columnName = metaData.getColumnName(i);
					rowObject.put(columnName, rs.getString(i));
				}
				resultArray.add(rowObject);
			}

			ObjectNode jsonResult = objectMapper.createObjectNode();
			jsonResult.put("status", "success");
			jsonResult.set("data", resultArray);

			return objectMapper.writeValueAsString(jsonResult);

		} catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
			ObjectNode errorResult = objectMapper.createObjectNode();
			errorResult.put("status", "error");
			errorResult.put("message", "Database error: " + e.getMessage());
			try {
				return objectMapper.writeValueAsString(errorResult);
			} catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
				return "{\"status\":\"error\",\"message\":\"JSON processing error\"}";
			}
		}
	}
}
