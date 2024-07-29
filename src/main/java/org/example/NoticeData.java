package org.example;

import java.util.List;

public class NoticeData {

	private List<Notice> notices;
	private PaginationInfo paginationInfo;

	public NoticeData() {

	}

	public void setNotices(List<Notice> notices) {
		this.notices = notices;
	}

	public void setPagination(PaginationInfo paginationInfo) {
		this.paginationInfo = paginationInfo;
	}
}
