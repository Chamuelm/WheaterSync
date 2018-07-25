
/* 
 * File: WeatherServer.java
 * Manages server object of weather server 
 * 
 */
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeatherServer {
	private static String dataFilePath = "data.txt"; // Data file location
	private static int serverPort = 12345; // Server port

	private List<CityWeatherData> data; // Array holds data
	private DatagramSocket socket; // Socket for connections
	private ExecutorService executor; // Manages threads
	private int reqCount = 0; // Requests counter

	public WeatherServer() {
		// Create thread-safe list and read data into it
		data = Collections.synchronizedList(new ArrayList<CityWeatherData>());
		readData();

		// Initialize socket
		try {
			socket = new DatagramSocket(serverPort);
		} catch (SocketException e) {
			System.out.println("Error binding to port.");
			System.exit(1);
		}

		// Setup executor
		executor = Executors.newCachedThreadPool();

		System.out.println("Server started");
	}

	// Reload weather data from file
	private void readData() {
		Scanner scanner = null;
		try {
			scanner = new Scanner(new File(dataFilePath));
			data.clear();

			while (scanner.hasNext()) {
				data.add(new CityWeatherData(scanner.nextLine(), scanner.nextLine(), scanner.nextLine(),
						scanner.nextLine()));
			}

		} catch (IOException e) {
			System.out.println("Error while reading data file.");
		} catch (NoSuchElementException e) {
			System.out.println("Data file is not formatted correctly.");
		}

		if (scanner != null) {
			scanner.close();
			System.out.println("Data have been read from file");
		}
	}

	// Wait for incoming requests and take care for them
	public void waitForPackets() {
		while (true) {
			try {
				// Receive packet
				reqCount++; // Increment request number
				byte[] buf = new byte[100];
				DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);
				socket.receive(receivePacket);

				System.out.println("Received request (" + reqCount + "): " + (new String(buf)).trim() + "\" from "
						+ receivePacket.getAddress());

				if ((new String(buf)).trim().equals("REFRESH-DATA")) {
					// Asked to refresh internal data
					executor.execute(new Runnable() {

						@Override
						public void run() {
							readData();

							// Inform client that data has been updated
							String toSend = "DATA-REFRESHED";
							DatagramPacket sendPacket = new DatagramPacket(toSend.getBytes(), toSend.length(),
									receivePacket.getAddress(), receivePacket.getPort());
							try {
								socket.send(sendPacket);
								System.out.println("Request " + reqCount + " has been completed");
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					});
				} else if ((new String(buf)).trim().startsWith("RELOAD-CITY")) {
					// Received request to get specific city information
					executor.execute(new Runnable() {

						@Override
						public void run() {
							String city = new String(buf, 11, buf.length - 12);
							city = city.trim();
							if (sendData(city, receivePacket.getAddress(), receivePacket.getPort()))
								System.out.println("Request " + reqCount + " has been completed");
						}
					});
				} else if ((new String(buf)).trim().equals("GET-CITIES")) {
					// Received request to get available cities list
					executor.execute(new Runnable() {

						@Override
						public void run() {
							sendCities(receivePacket.getAddress(), receivePacket.getPort());
							System.out.println("Request " + reqCount + " has been completed");
						}
					});
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// Send data about available cities
	protected void sendCities(InetAddress address, int port) {
		String cities = "CITIES:"; // Holds string to send

		// Add all cities to string to send
		Iterator<CityWeatherData> iterator = data.iterator();
		while (iterator.hasNext()) {
			cities += iterator.next().getName() + ",";
		}

		// Create packet and send
		DatagramPacket sendPacket = new DatagramPacket(cities.getBytes(), cities.length(), address, port);
		try {
			socket.send(sendPacket);
			System.out.println("Cities list has been sent to " + address + " on port " + port);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Send weather data about requested city
	protected boolean sendData(String city, InetAddress address, int port) {
		CityWeatherData cityData = null; // Holds found city
		boolean cityNotFound = true; // false if city has been found

		// Look for city in list
		Iterator<CityWeatherData> iterator = data.iterator();
		while (iterator.hasNext() && cityNotFound) {
			cityData = iterator.next();
			if (cityData.getName().equals(city)) {
				cityNotFound = false;
			}
		}

		// Create packet and send data
		if (!cityNotFound) { // Send string of weather semi-colon separated per day
			String toSend = String.format("%s;%s;%s", cityData.getToday(), cityData.getTomorrow(),
					cityData.getIn2days());
			DatagramPacket sendPacket = new DatagramPacket(toSend.getBytes(), toSend.length(), address, port);
			try {
				socket.send(sendPacket);
				return true;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return false;
	}

}
