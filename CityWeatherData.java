/* 
 * File: CityWeatherData.java
 * Contain weather data about specific city for 3 days 
 * 
 */

public class CityWeatherData {
	private String name;		// City name
	private String today;		// Weather info for today
	private String tomorrow;	// Weather info for tomorrow
	private String in2days;		// Weather info for 2 days ahead

	// Constructor
	public CityWeatherData(String name, String today, String tomorrow, String in2days) {
		this.setName(name);
		this.setToday(today);
		this.setTomorrow(tomorrow);
		this.setIn2days(in2days);
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the today
	 */
	public String getToday() {
		return today;
	}

	/**
	 * @param today the today to set
	 */
	public void setToday(String today) {
		this.today = today;
	}

	/**
	 * @return the tomorrow
	 */
	public String getTomorrow() {
		return tomorrow;
	}

	/**
	 * @param tomorrw the tomorrow to set
	 */
	public void setTomorrow(String tomorrow) {
		this.tomorrow = tomorrow;
	}

	/**
	 * @return the in2days
	 */
	public String getIn2days() {
		return in2days;
	}

	/**
	 * @param in2days the in2days to set
	 */
	public void setIn2days(String in2days) {
		this.in2days = in2days;
	}

}
