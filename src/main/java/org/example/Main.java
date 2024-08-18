package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Main implements RequestHandler<Map<String, Object>, String> {

	private static final String DB_URL = System.getenv("DB_URL");
	private static final String DB_USER = System.getenv("DB_USER");
	private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String handleRequest(Map<String, Object> input, Context context) {

		Map<String, String> queryStringParameters = (Map<String, String>) input.get("queryStringParameters");

		String type = queryStringParameters.get("category");
		try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
			context.getLogger().log("Connected to database");

			ObjectNode responseNode = objectMapper.createObjectNode();
			ArrayNode contentArray = objectMapper.createArrayNode();

			String sql = "SELECT * FROM notice WHERE category = ? ORDER BY notice_id DESC LIMIT 5";
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setString(1, type); // type 파라미터 설정
				try (ResultSet rs = pstmt.executeQuery()) {
					while (rs.next()) {
						ObjectNode noticeNode = objectMapper.createObjectNode();
						noticeNode.put("id", rs.getInt("notice_id"));
						noticeNode.put("category", rs.getString("category"));
						noticeNode.put("notice_no", rs.getInt("notice_no"));
						noticeNode.put("title", rs.getString("title"));
						noticeNode.put("writer", rs.getString("writer"));
						noticeNode.put("noticedAt", rs.getString("noticedAt"));
						noticeNode.put("views", rs.getInt("views"));
						noticeNode.put("link", rs.getString("link"));
						contentArray.add(noticeNode);
					}
				}
			}

			ObjectNode dataNode = objectMapper.createObjectNode();
			dataNode.set("content", contentArray);

			responseNode.put("success", true);
			responseNode.set("data", dataNode);

			return objectMapper.writeValueAsString(responseNode);
		} catch (SQLException | JsonProcessingException e) {
			ObjectNode errorResponseNode = objectMapper.createObjectNode();
			errorResponseNode.put("success", false);
			errorResponseNode.set("data", objectMapper.createObjectNode());

			try {
				return objectMapper.writeValueAsString(errorResponseNode);
			} catch (JsonProcessingException ex) {
				return "{\"success\":false,\"data\":{}}";
			}
		}
	}
}
