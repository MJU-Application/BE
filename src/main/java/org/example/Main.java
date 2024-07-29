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
			PaginatedNoticeResponse response = new PaginatedNoticeResponse();
			NoticeData noticeData = new NoticeData();
			List<Notice> notices = new ArrayList<>();

			// 전체 아이템 수 조회
			int totalItems = getTotalItems(conn);

			// 페이지네이션된 데이터 조회
			String sql = "SELECT * FROM notice ORDER BY id DESC LIMIT ? OFFSET ?";
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setInt(1, size);
				pstmt.setInt(2, page * size);
				try (ResultSet rs = pstmt.executeQuery()) {
					while (rs.next()) {
						Notice notice = new Notice();
						notice.setId(rs.getInt("id"));
						notice.setCategory(rs.getString("category"));
						notice.setNoticeNo(rs.getInt("notice_no"));
						notice.setTitle(rs.getString("title"));
						notice.setWriter(rs.getString("writer"));
						notice.setNoticedAt(rs.getDate("noticedAt"));
						notice.setViews(rs.getInt("views"));
						notice.setLink(rs.getString("link"));
						notices.add(notice);
					}
				}
			}

			noticeData.setNotices(notices);

			PaginationInfo paginationInfo = new PaginationInfo();
			paginationInfo.setCurrentPage(page);
			paginationInfo.setPageSize(size);
			paginationInfo.setTotalItems(totalItems);
			paginationInfo.setHasNextPage((page + 1) * size < totalItems);

			noticeData.setPagination(paginationInfo);

			response.setStatus("success");
			response.setData(noticeData);

			return objectMapper.writeValueAsString(response);
		} catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
			PaginatedNoticeResponse errorResponse = new PaginatedNoticeResponse();
			errorResponse.setStatus("error");
			NoticeData errorData = new NoticeData();
			errorData.setNotices(new ArrayList<>());
			PaginationInfo errorPagination = new PaginationInfo();
			errorPagination.setCurrentPage(0);
			errorPagination.setPageSize(0);
			errorPagination.setTotalItems(0);
			errorPagination.setHasNextPage(false);
			errorData.setPagination(errorPagination);
			errorResponse.setData(errorData);

			try {
				return objectMapper.writeValueAsString(errorResponse);
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
