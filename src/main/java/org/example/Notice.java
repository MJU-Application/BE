package org.example;

import java.time.LocalDate;
import java.util.Date;

public class Notice {

	private int id; private String category;
	private int noticeNum; private String title;
	private String writer; private Date noticedAt;
	private int views; private String link;

	public Notice() {

	}

	public void setId(int id) {
		this.id = id;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public void setNoticeNo(int number) {
		this.noticeNum = number;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public void setWriter(String writer) {
		this.writer = writer;
	}

	public void setNoticedAt(Date noticedAt) {
		this.noticedAt = noticedAt;
	}

	public void setViews(int views) {
		this.views = views;
	}

	public void setLink(String link) {
		this.link = link;
	}


}
