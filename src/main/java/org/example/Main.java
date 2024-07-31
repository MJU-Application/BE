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

		try {
			context.getLogger().log(String.format("INFO: input %s \n", objectMapper.writeValueAsString(input)));
			String body = (String)input.get("body");
			Map<String, Object> requestValues = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
			});

			int page = ((Number)requestValues.get("page")).intValue();
			int size = ((Number)requestValues.get("size")).intValue();
			String category = requestValues.get("category").toString();

			context.getLogger().log("page: " + page + " ,size: " + size + " ,category" + category);

			try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
				context.getLogger().log("Connected to database");

				ObjectNode responseNode = objectMapper.createObjectNode();
				ArrayNode noticesArray = objectMapper.createArrayNode();

				// 전체 아이템 수 조회
				int totalItems = getTotalItems(conn, context);
				context.getLogger().log("Total Items: " + totalItems);

				// 페이지네이션된 데이터 조회
				String sql = "SELECT * FROM notice WHERE category = ?ORDER BY notice_id DESC LIMIT ? OFFSET ?";
				try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
					context.getLogger().log("Input Success 001");
					pstmt.setString(1, category);
					pstmt.setInt(2, size);
					pstmt.setInt(3, page * size);
					try (ResultSet rs = pstmt.executeQuery()) {
						context.getLogger().log("Input Success 002");
						while (rs.next()) {
							context.getLogger().log("Success While 001");
							ObjectNode noticeNode = objectMapper.createObjectNode();
							noticeNode.put("notice_id", rs.getInt("notice_id"));
							noticeNode.put("category", rs.getString("category"));
							noticeNode.put("notice_no", rs.getInt("notice_no"));
							noticeNode.put("title", rs.getString("title"));
							noticeNode.put("writer", rs.getString("writer"));
							noticeNode.put("noticedAt", rs.getString("noticedAt"));
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
				// paginationNode.put("totalItems", totalItems);
				// paginationNode.put("hasNextPage", (page + 1) * size < totalItems);

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
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}

	}

	private int getTotalItems(Connection conn, Context context) throws SQLException {
		context.getLogger().log("Success Input 1");
		try (Statement stmt = conn.createStatement();
			 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM notice")) {
			context.getLogger().log("ResultSet: " + rs);
			if (rs.next()) {
				context.getLogger().log("Success Output 1" + rs);
				return rs.getInt(1);
			}
		}
		return 0;
	}
}
