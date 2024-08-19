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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
			urlList.add("https://www.mju.ac.kr/mjukr/8595/subview.do");//인문)학생회관식당
			urlList.add("https://www.mju.ac.kr/mjukr/485/subview.do");//자연)명진당
			urlList.add("https://www.mju.ac.kr/mjukr/486/subview.do");//자연)학생회관
			urlList.add("https://www.mju.ac.kr/mjukr/487/subview.do");//자연)생활관식당
			urlList.add("https://www.mju.ac.kr/mjukr/488/subview.do");//자연)교직원식당

			for(String url : urlList) this.menu(url);

		} catch (Exception var7) {
			this.logger.log("ERROR: " + var7.getMessage());
			result = "Something Wrong for Crawling";
		}

		return result;
	}

	private void menu(String baseUrl) throws Exception {
		this.logger.log(String.format("INFO: Starting method 'menu' for baseUrl=%s\n", baseUrl));

		StringBuilder sb = new StringBuilder();
		sb.append("insert into meal (meal_date, meal_day, category, menu, restaurant_id) values\n");

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
		String cafeteria = docu.select("div.sceduleBox p.title").text();
		this.logger.log("INFO: bring cafeteria from url");

		int restaurantId = this.getRestaurantId(cafeteria);
		String mealDate = this.getMealDate(restaurantId);

		//테이블 바디 가져오기
		Elements elements = docu.getElementsByClass("tableWrap marT50");
		//테이블의 바디의 내용(ex.05.20(월) 조식 - 메뉴 -)의 모임
		Elements rows = elements.select("table tbody tr");
		String date = "";//날짜
		String day = "";//요일
		String type = "";//구분(조식, 중식, 석식)
		List<String> menu = new ArrayList<>();//메뉴
		boolean isRegistration = false;//메뉴가 등록되어 있는지

		int year = LocalDate.now().getYear();

		int count = 0;
		for(Element row : rows) {
			//요일 가져오기
			if(row.select("th").hasText()){
				String[] data = row.select("th").text().split(" ");
				date = year+"-"+data[0].replace(".","-");
				day = data.length == 4 ? data[2]+"요일" : data[1].replace("(","").replace(")","")+"요일";
			}
			//저장 대상인지 확인
			if(mealDate != "" && !LocalDate.parse(date).isAfter(LocalDate.parse(mealDate))) continue;

			//(ex.조식 - 메뉴 -) 이 내용 가져오기
			Elements tds = row.select("td");
			type = tds.first().text();
			if(type.equals("점심")) type = "중식";
			else if(type.equals("저녁")) type = "석식";
			String e = "";
			if(tds.size() > 2) isRegistration = true; //내용 길이가 2 이하면 등록 안 된거
			//(- 메뉴 -) 이거 처리 -> 여기서 메뉴만 가져옴.
			//만약 등록 안되어있으면 '등록 안됨' 이거를 가져옴.
			for(int i=1; i<tds.size(); i++){
				if(!tds.get(i).text().equals("-"))
					e = tds.get(i).text();
			}
			//등록이 되어 있다면 메뉴를 List에 담기
			if(isRegistration){
				String[] foods = e.split(" ");
				for(String food : foods){
					if(food.contains("kcal") || food.contains("l")) continue; //중식 메뉴에는 kcal가 포함되어 있어 이거는 제외
					menu.add(food);
				}
			}
			//등록이 안되어 있다면 그냥 '등록 안됨'을 담음
			else menu.add(e);
			//출력
			sb.append("("
				+"'"+date+"',"
				+"'"+day+"',"
				+"'"+type+"',"
				+"'"+menu+"',"
				+restaurantId+"),\n");

			//메뉴 초기화
			menu.clear();
			isRegistration = false;
			count++;
		}

		this.logger.log("INFO: Complete Crawling of '" + cafeteria + "'. Total meal count= " + count);
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

	private int getRestaurantId(String cafeteria) throws Exception {
		this.logger.log(String.format("INFO: Starting method 'getRestaurantId' for cafeteria=%s\n", cafeteria));
		String sql = "SELECT restaurant_id FROM restaurant WHERE name = ?;";
		int restaurantId = 0;
		Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

		try {
			PreparedStatement pstmt = conn.prepareStatement(sql);

			this.logger.log("INFO: DB connection successful\n");
			pstmt.setString(1, cafeteria);
			ResultSet resultSet = pstmt.executeQuery();

			this.logger.log("INFO: SQL result receiving successful\n");
			if (resultSet.next()) {
				restaurantId = Integer.parseInt(resultSet.getString("restaurant_id"));
			}

			this.logger.log("INFO: Cafeteria = " + cafeteria + ", RestaurantId = " + restaurantId);

			resultSet.close();
			pstmt.close();

		} catch (Throwable var14) {
			throw var14;
		}

		conn.close();
		return restaurantId;
	}

	private String getMealDate(int restaurantId) throws Exception {
		this.logger.log(String.format("INFO: Starting method 'getMealDate' for restaurantId=%s\n", restaurantId));
		String sql = "SELECT meal_date FROM meal WHERE restaurant_id = ? ORDER BY meal_date DESC LIMIT 1;";
		String mealDate = "";
		Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

		try {
			PreparedStatement pstmt = conn.prepareStatement(sql);

			this.logger.log("INFO: DB connection successful\n");
			pstmt.setInt(1, restaurantId);
			ResultSet resultSet = pstmt.executeQuery();

			this.logger.log("INFO: SQL result receiving successful\n");
			if (resultSet.next()) {
				mealDate = resultSet.getString("meal_date");
			}

			this.logger.log("INFO: RestaurantId = " + restaurantId + ", Recently saved meal date = " + mealDate);

			resultSet.close();
			pstmt.close();

		} catch (Throwable var14) {
			throw var14;
		}

		conn.close();
		return mealDate;
	}
}
