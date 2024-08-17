package org.example;

public class NoticeItem {
	String category;
	String number;
	String title;
	String writer;
	String date;
	String accessCount;
	String link;

	public NoticeItem(String category, String number, String title, String writer, String date, String accessCount, String link) {
		this.category = category;
		this.number = number;
		this.title = title;
		this.writer = writer;
		this.date = date;
		this.accessCount = accessCount;
		this.link = link;
	}

	public String toString() {
		return this.category + ", " + this.number + ", " + this.title + ", " + this.writer + ", " + this.date + ", " + this.accessCount + ", " + this.link;
	}

}
