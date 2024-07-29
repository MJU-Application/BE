package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
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
		int page = input.containsKey("page") ? ((Number)input.get("page")).intValue() : 0;
		int size = input.containsKey("size") ? ((Number)input.get("size")).intValue() : 10;

		try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
			context.getLogger().log("Connected to database");

			ObjectNode responseNode = objectMapper.createObjectNode();
			ArrayNode noticesArray = objectMapper.createArrayNode();

			// 전체 아이템 수 조회
			int totalItems = getTotalItems(conn);
			context.getLogger().log("Total Items: " + totalItems);

			// 페이지네이션된 데이터 조회
			String sql = "SELECT * FROM notice ORDER BY id DESC LIMIT ? OFFSET ?";
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setInt(1, size);
				pstmt.setInt(2, page * size);
				try (ResultSet rs = pstmt.executeQuery()) {
					while (rs.next()) {
						ObjectNode noticeNode = objectMapper.createObjectNode();
						noticeNode.put("id", rs.getInt("id"));
						noticeNode.put("category", rs.getString("category"));
						noticeNode.put("notice_no", rs.getInt("notice_no"));
						noticeNode.put("title", rs.getString("title"));
						noticeNode.put("writer", rs.getString("writer"));
						noticeNode.put("noticedAt", rs.getDate("noticedAt").toString());
						noticeNode.put("views", rs.getInt("views"));
						noticeNode.put("link", rs.getString("link"));
						noticesArray.add(noticeNode);

						context.getLogger().log("Added notice: " + noticeNode.toString());
					}
				}
			}

			ObjectNode paginationNode = objectMapper.createObjectNode();
			paginationNode.put("currentPage", page);
			paginationNode.put("pageSize", size);
			paginationNode.put("totalItems", totalItems);
			paginationNode.put("hasNextPage", (page + 1) * size < totalItems);

			ObjectNode dataNode = objectMapper.createObjectNode();
			dataNode.set("notices", noticesArray);
			dataNode.set("pagination", paginationNode);

			responseNode.put("status", "success");
			responseNode.set("data", dataNode);

			return objectMapper.writeValueAsString(responseNode);
		} catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
			ObjectNode errorResponseNode = objectMapper.createObjectNode();
			errorResponseNode.put("status", "error");

			ObjectNode errorDataNode = objectMapper.createObjectNode();
			errorDataNode.set("notices", objectMapper.createArrayNode());

			ObjectNode errorPaginationNode = objectMapper.createObjectNode();
			errorPaginationNode.put("currentPage", 0);
			errorPaginationNode.put("pageSize", 0);
			errorPaginationNode.put("totalItems", 0);
			errorPaginationNode.put("hasNextPage", false);

			errorDataNode.set("pagination", errorPaginationNode);
			errorResponseNode.set("data", errorDataNode);

			try {
				return objectMapper.writeValueAsString(errorResponseNode);
			} catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
				return "{\"status\":\"error\",\"data\":{\"notices\":[],\"pagination\":{\"currentPage\":0,\"pageSize\":0,\"totalItems\":0,\"hasNextPage\":false}}}";
			}
		}
	}

	private int getTotalItems(Connection conn) throws SQLException {
		String sql = "SELECT COUNT(*) FROM notice";
		try (Statement stmt = conn.createStatement();
			 ResultSet rs = stmt.executeQuery(sql)) {
			if (rs.next()) {
				return rs.getInt(1);
			}
		}
		return 0;
	}
}
