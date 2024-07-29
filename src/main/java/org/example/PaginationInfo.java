package org.example;

public class PaginationInfo {

	private int currentPage;
	private int pageSize;
	private int totalItems;
	private boolean hasNextPage;

	public PaginationInfo() {

	}

	public void setCurrentPage(int page) {
		this.currentPage = page;
	}

	public void setPageSize(int size) {
		this.pageSize = size;
	}

	public void setTotalItems(int totalItems) {
		this.totalItems = totalItems;
	}

	public void setHasNextPage(boolean b) {
		this.hasNextPage = b;
	}
}
