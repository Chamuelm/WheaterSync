/* 
 * File: WeatherServerTest.java
 * Driver application for weather server 
 * 
 */
public class WeatherServerTest {

	public static void main(String[] args) {
		WeatherServer server = new WeatherServer();
		server.waitForPackets();
	}
}
