package org.example;

public class ScheduleItem {
	private String period;
	private String contents;

	public ScheduleItem(String period, String contents) {
		this.period = period;
		this.contents = contents;
	}

	public String getPeriod() {
		return period;
	}

	public String getContents() {
		return contents;
	}
}