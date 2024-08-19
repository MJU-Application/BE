package org.example;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class Main implements RequestHandler<Object, String> {
	private static final String DB_URL = System.getenv("DB_URL");
	private static final String DB_USER = System.getenv("DB_USER");
	private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");
	private LambdaLogger logger;

	public Main() {
	}

	public String handleRequest(Object input, Context context) {
		this.logger = context.getLogger();
		this.logger.log("INFO: Starting method handleRequest\n");
		String result = "Success Crawling!";

		try {
			String url = "https://emc.mju.ac.kr/emc/5329/subview.do";

			this.schedule(url);

		} catch (Exception var7) {
			this.logger.log("ERROR: " + var7.getMessage());
			result = "Something Wrong for Crawling";
		}

		return result;
	}

	private void schedule(String baseUrl) throws Exception {
		this.logger.log(String.format("INFO: Starting method 'schedule' for baseUrl=%s\n", baseUrl));

		StringBuilder sb = new StringBuilder();
		sb.append("insert into calendar (c_year, c_month, c_period, c_contents) values\n");
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
		Elements monthList = docu.select("div.scheList");
		int year = Integer.parseInt(docu.getElementById("schdulWrap").select("div.search strong").text().replaceAll("\\D", ""));
		this.logger.log("INFO: bring year from url");
		int[] date = this.getLastDate();
		int cYear = date[0];
		int cMonth = date[1];

		int count=0;
		for(int i=1; i<=monthList.size(); i++){
			int month = i%12==0 ? 12 : i%12;
			if(i == 13) year++;

			if(cYear > year || (cYear == year && cMonth >= month)) continue;

			Elements details =  monthList.get(i-1).select("ul li dl");

			for(int j=0; j<details.size(); j++) {
				sb.append("("
					+year+","
					+month+","
					+"'"+details.select("dt").get(j).text()+"',"
					+"'"+details.select("dd").get(j).text()+"'),\n");
			}
			count++;
		}

		this.logger.log("INFO: Complete Crawling. Total crawling month count= " + count);
		sb.replace(sb.length() - 2, sb.length(), ";");
		String sql = sb.toString();
		this.logger.log("INFO: Create SQL -> "+sql);
		if (count != 0) {
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

	private int[] getLastDate() throws Exception {
		this.logger.log("INFO: Starting method 'getLastDate'\n");
		String sql = "SELECT c_year as year, c_month as month FROM calendar ORDER BY year DESC, month DESC LIMIT 1;";
		int[] date = new int[2];
		Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

		PreparedStatement pstmt = conn.prepareStatement(sql);
		this.logger.log("INFO: DB connection successful\n");
		ResultSet resultSet = pstmt.executeQuery();
		this.logger.log("INFO: SQL result receiving successful\n");
		if (resultSet.next()) {
			date[0] = Integer.parseInt(resultSet.getString("year"));
			date[1] = Integer.parseInt(resultSet.getString("month"));
		}
		this.logger.log(String.format("INFO: Recently saved year = %s, month = %s\n", date[0], date[1]));

		resultSet.close();
		pstmt.close();
		conn.close();

		return date;
	}

}
