package org.example;

public class PaginatedNoticeResponse {

	private String status;
	private NoticeData noticeData;

	public PaginatedNoticeResponse() {

	}

	public void setStatus(String status) {
		this.status = status;
	}

	public void setData(NoticeData noticeData) {
		this.noticeData = noticeData;
	}
}
