package org.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class Main implements RequestHandler<Object, String> {
	private static final String DB_URL = System.getenv("DB_URL");
	private static final String DB_USER = System.getenv("DB_USER");
	private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");
	private final ObjectMapper objectMapper = new ObjectMapper();
	private LambdaLogger logger;

	public Main() {
	}

	public String handleRequest(Object input, Context context) {
		this.logger = context.getLogger();
		this.logger.log("INFO: Starting method handleRequest\n");
		String result = "Success Crawling!";

		try {
			List<String> urlList = new ArrayList<>();
			urlList.add("https://www.mju.ac.kr/mjukr/255/subview.do");
			urlList.add("https://www.mju.ac.kr/mjukr/256/subview.do");
			urlList.add("https://www.mju.ac.kr/mjukr/257/subview.do");
			urlList.add("https://www.mju.ac.kr/mjukr/259/subview.do");
			urlList.add("https://www.mju.ac.kr/mjukr/260/subview.do");

			for(String url : urlList) this.notice(url);

		} catch (Exception var7) {
			this.logger.log("ERROR: " + var7.getMessage());
			result = "Something Wrong for Crawling";
		}

		return result;
	}

	private void notice(String baseUrl) throws Exception {
		this.logger.log(String.format("INFO: Starting method 'notice' for baseUrl=%s\n", baseUrl));
		String baseLink = "https://www.mju.ac.kr";
		List<NoticeItem> allNotices = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		sb.append("insert into notice (category, notice_no, title, writer, noticedAt, views, link) values\n");
		this.logger.log("INFO: create stringBuilder");
		Document docu = null;
		int maxRetries = 3;
		int tryCount = 0;

		while(tryCount < maxRetries) {
			this.logger.log("INFO: try connect jsoup");

			try {
				docu = Jsoup.connect(baseUrl).timeout(10000).get();
				break;
			} catch (IOException var34) {
				if (tryCount == maxRetries - 1) {
					throw var34;
				}

				Thread.sleep(1000L);
				++tryCount;
			}
		}

		this.logger.log("INFO: Successfully connect jsoup");
		assert docu != null;
		String element = docu.select("div._inner a._last").first().attr("href").replaceAll("\\D", "");
		int totalPage = Integer.parseInt(element);
		String category = docu.select("div.h1Title h1").text();
		this.logger.log("INFO: bring category from url");
		int latestNoticeNum = this.getLastNoticeNum(category);
		boolean isStop = false;

		for(int page = 1; page <= totalPage && !isStop; ++page) {
			String url = baseUrl + "?page=" + page;
			Document document = Jsoup.connect(url).get();
			Elements numbers = document.getElementsByClass("_artclTdNum");
			Elements titles = document.getElementsByClass("_artclTdTitle");
			Elements writers = document.getElementsByClass("_artclTdWriter");
			Elements dates = document.getElementsByClass("_artclTdRdate");
			Elements access = document.getElementsByClass("_artclTdAccess");
			Elements linkViews = document.getElementsByClass("artclLinkView");
			List<NoticeItem> pageNotices = new ArrayList<>();

			for(int i = 0; i < numbers.size(); ++i) {
				if (numbers.get(i).text().matches("[+-]?\\d*(\\.\\d+)?")) {
					if (Integer.parseInt(numbers.get(i).text()) <= latestNoticeNum) {
						isStop = true;
						break;
					}

					String number = numbers.get(i).text();
					String title = titles.get(i).text().replaceAll("'", "\\\\'");
					String writer = writers.get(i).text();
					String date = dates.get(i).text();
					String accessCount = access.get(i).text();
					String link = baseLink + linkViews.get(i).select("a").first().attr("href");
					NoticeItem item = new NoticeItem(category, number, title, writer, date, accessCount, link);
					pageNotices.add(item);
					allNotices.add(item);
				}
			}
		}

		this.logger.log("INFO: Complete Crawling of '" + category + "'. Total notice count= " + allNotices.size());
		sb.replace(sb.length() - 2, sb.length(), ";");
		String sql = sb.toString();
		this.logger.log("INFO: Create SQL -> "+sql);
		if (allNotices.size() != 0) {
			Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

			try {
				Statement statement = conn.createStatement();

				try {
					this.logger.log("INFO: DB connection successful\n");
					statement.executeUpdate(sql);
					this.logger.log("INFO: SQL Execute successful\n");
				} catch (Throwable var32) {
					if (statement != null) {
						try {
							statement.close();
						} catch (Throwable var31) {
							var32.addSuppressed(var31);
						}
					}

					throw var32;
				}

				statement.close();
			} catch (Throwable var33) {
				if (conn != null) {
					try {
						conn.close();
					} catch (Throwable var30) {
						var33.addSuppressed(var30);
					}
				}

				throw var33;
			}

			conn.close();
		}

	}

	private int getLastNoticeNum(String category) throws Exception {
		this.logger.log(String.format("INFO: Starting method 'getLastNoticeNum' for category=%s\n", category));
		String sql = "SELECT notice_no FROM notice WHERE category = ? ORDER BY notice_no DESC LIMIT 1;";
		int latestNoticeNum = 0;
		Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

		try {
			PreparedStatement pstmt = conn.prepareStatement(sql);

			try {
				this.logger.log("INFO: DB connection successful\n");
				pstmt.setString(1, category);
				ResultSet resultSet = pstmt.executeQuery();

				try {
					this.logger.log("INFO: SQL result receiving successful\n");
					if (resultSet.next()) {
						latestNoticeNum = Integer.parseInt(resultSet.getString("notice_no"));
					}

					this.logger.log("INFO: Category = " + category + ", Recently saved notice number = " + latestNoticeNum);
				} catch (Throwable var12) {
					if (resultSet != null) {
						try {
							resultSet.close();
						} catch (Throwable var11) {
							var12.addSuppressed(var11);
						}
					}

					throw var12;
				}

				resultSet.close();
			} catch (Throwable var13) {
				if (pstmt != null) {
					try {
						pstmt.close();
					} catch (Throwable var10) {
						var13.addSuppressed(var10);
					}
				}

				throw var13;
			}

			pstmt.close();
		} catch (Throwable var14) {
			if (conn != null) {
				try {
					conn.close();
				} catch (Throwable var9) {
					var14.addSuppressed(var9);
				}
			}

			throw var14;
		}

		conn.close();

		return latestNoticeNum;
	}

}
