/* 
 * File: WeatherClientTest.java
 * Driver application for weather client 
 * 
 */
import javax.swing.JFrame;

public class WeatherClientTest {

	public static void main(String[] args) {
		WeatherClient application;
		application = new WeatherClient();
		application.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		application.waitForPackets();
	}

}
