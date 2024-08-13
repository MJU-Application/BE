package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Main implements RequestHandler<Map<String, Object>, String> {
	private static final String DB_URL = System.getenv("DB_URL");
	private static final String DB_USER = System.getenv("DB_USER");
	private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");
	private final ObjectMapper objectMapper = new ObjectMapper();
	private LambdaLogger logger;

	@Override
	public String handleRequest(Map<String, Object> input, Context context) {
		logger = context.getLogger();
		logger.log("INFO: Starting method handleRequest\n");

		logger.log(String.format("INFO: input values size is %s \n", input.size()));

		String result = "";
		try {
			logger.log(String.format("INFO: input %s \n", objectMapper.writeValueAsString(input)));

			Map<String, String> queryStringParameters = (Map<String, String>) input.get("queryStringParameters");
			if (!queryStringParameters.containsKey("year") || !queryStringParameters.containsKey("month")) {
				throw new IllegalArgumentException("Missing required parameters: year and month");
			}
			int year = Integer.parseInt(queryStringParameters.get("year"));
			int month = Integer.parseInt(queryStringParameters.get("month"));
			logger.log("INFO: Request variable input complete\n");

			List<ScheduleItem> scheduleItems = fetchCalendarData(year, month);
			logger.log("INFO: Successfully created result from ‘fetchCalendarData'\n");
			result = createSuccessResponse(year, month, scheduleItems);
			logger.log("INFO: Successfully created result from ‘createSuccessResponse’\n");

		} catch (Exception e) {
			logger.log("ERROR: " + e.getMessage());
			result = createErrorResponse();
			logger.log("INFO: Successfully created result from ‘createErrorResponse’\n");
		}
		return result;
	}

	private List<ScheduleItem> fetchCalendarData(int year, int month) throws SQLException {
		logger.log(String.format("INFO: Starting method 'fetchCalendarData' for year=%s, month=%s\n", year, month));

		String sql = "SELECT * FROM calendar WHERE c_year = ? AND c_month = ?";
		List<ScheduleItem> scheduleItems = new ArrayList<>();

		try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
			 PreparedStatement pstmt = conn.prepareStatement(sql)) {
			logger.log("INFO: DB connection successful\n");

			pstmt.setInt(1, year);
			pstmt.setInt(2, month);

			try (ResultSet rs = pstmt.executeQuery()) {
				logger.log("INFO: SQL result receiving successful\n");

				while (rs.next()) {
					ScheduleItem item = new ScheduleItem(
						rs.getString("c_period"),
						rs.getString("c_contents")
					);
					scheduleItems.add(item);
				}
				logger.log("INFO: SQL result customizing successful\n");
			}
		}
		return scheduleItems;
	}

	private String createSuccessResponse(int year, int month, List<ScheduleItem> scheduleItems) throws Exception {
		logger.log(String.format(
			"INFO: Starting method 'createSuccessResponse' for year=%s, month=%s, scheduleItems(size)=%s\n", year,
			month, scheduleItems.size()));

		ObjectNode responseNode = objectMapper.createObjectNode();
		responseNode.put("success", true);

		ObjectNode dataNode = responseNode.putObject("data");
		dataNode.put("id", 1); // Assuming id is always 1, adjust if needed
		dataNode.put("year", year);
		dataNode.put("month", month);

		ArrayNode scheduleArray = dataNode.putArray("schedule");
		for (ScheduleItem item : scheduleItems) {
			ObjectNode itemNode = scheduleArray.addObject();
			itemNode.put("period", item.getPeriod());
			itemNode.put("contents", item.getContents());
		}

		return objectMapper.writeValueAsString(responseNode);
	}

	private String createErrorResponse() {
		logger.log("INFO: Starting method 'createErrorResponse'\n");

		ObjectNode errorNode = objectMapper.createObjectNode();
		errorNode.put("success", false);
		errorNode.put("message", "An error occurred while processing the request.");
		try {
			return objectMapper.writeValueAsString(errorNode);
		} catch (Exception e) {
			return "{\"success\":false,\"message\":\"Error creating error response.\"}";
		}
	}

}
