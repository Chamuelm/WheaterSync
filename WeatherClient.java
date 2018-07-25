/* 
 * File: WeatherClient.java
 * Manages client object for managing GUI and connection to weather server 
 * 
 */
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public class WeatherClient extends JFrame {
	private static long msInDay = 86400000;		// Milliseconds in day
	private static int timeout = 5; // Seconds to wait for server connection

	// GUI components
	private JButton todayButton;
	private JButton tomorrowButton;
	private JButton in2daysButton;
	private JMenuBar menuBar;
	private JMenu serverMenu;
	private JMenu citiesMenu;
	private JTextArea displayArea;	// Main display area
	private Color defaultButtonColor;	// Color of unpressed button
	private static Color pressedButtonColor = Color.WHITE; // Color of pressed button
	private ServerMenuItemListener menuListener;		// Menu items listener for Server menu
	private CityMenuItemListener cityMenuItemListener;	// Menu items listener for City menu

	private InetSocketAddress serverSocketAddress;	// Socket address of server
	private String serverAddress = "localhost";		// default address is localhost
	private int serverPort = 12345;					// default port is 12345
	private DatagramSocket socket;

	private String[] cities = null; // List of cities available
	private Lock citiesListLock = new ReentrantLock();	// Lock for cities variable
	private CityWeatherData chosenCity;	// Hold chosen city data
	private CountDownLatch waitForCities; // Manage waiting for server to send city data
	private CountDownLatch waitForServerUpdate; // Manage waiting for server to update his data
	private Thread bgThread;	// Background thread 

	public WeatherClient() {
		super("Weather");

		// Menu creation
		menuBar = new JMenuBar();
		menuListener = new ServerMenuItemListener();
		cityMenuItemListener = new CityMenuItemListener();

		createMenu(); // Create GUI menu
		setJMenuBar(menuBar);

		// Make connection to server and get initial data
		try {
			socket = new DatagramSocket();
			serverSocketAddress = new InetSocketAddress(serverAddress, serverPort);
		} catch (SocketException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (IllegalArgumentException e) {
			displayText("Could not connect to given server.");
			setGUIEnabled(false);
		}

		// Ask server for cities and update menu if received cities
		bgThread = new Thread(new Runnable() {

			@Override
			public void run() {
				waitForCities = new CountDownLatch(1);	// Controller for if received cities information
				askForCitiesList(); // Ask server to send cities list

				try {
					// Wait for cities information
					if (!waitForCities.await(timeout, TimeUnit.SECONDS)) {
						displayText("Error: cannot receive cities list from server.");
					}
				} catch (InterruptedException e) {
					// Ignore
				}
			}
		});
		bgThread.start();

		// Build GUI 
		JPanel northPanel = new JPanel();
		northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.X_AXIS));
		
		// Variables to get current date and day of week in specific format
		Date date = new Date();
		SimpleDateFormat dayInWeekFormat = new SimpleDateFormat("EEEE");
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM");
		DayButtonsListener buttonsListener = new DayButtonsListener();

		// Setup and add todayButton
		todayButton = new JButton(String.format("<html>Today<br/>%s</html>", dayInWeekFormat.format(date)));
		todayButton.addActionListener(buttonsListener);
		northPanel.add(todayButton);

		// Setup and add tomorrowButton
		date.setTime(date.getTime() + msInDay); // Increment date in one day
		tomorrowButton = new JButton(String.format("<html>Tomorrow<br/>%s</html>", dayInWeekFormat.format(date)));
		tomorrowButton.addActionListener(buttonsListener);
		northPanel.add(tomorrowButton);

		// Setup and add in2daysButton
		date.setTime(date.getTime() + msInDay); // Increment date in one day
		in2daysButton = new JButton(
				String.format("<html>%s<br/>%s</html>", dateFormat.format(date), dayInWeekFormat.format(date)));
		in2daysButton.addActionListener(buttonsListener);
		northPanel.add(in2daysButton);

		// Get default button color
		defaultButtonColor = todayButton.getBackground();

		// Add northPanel (Days buttons) to north
		add(northPanel, BorderLayout.NORTH);

		// Create display area
		displayArea = new JTextArea("Select city");
		displayArea.setEditable(false);
		displayArea.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
		add(displayArea, BorderLayout.CENTER);

		// Disable buttons until have a connection with server
		setButtonsEnabled(false);

		setSize(500, 200);
		setVisible(true);
	}

	// Send message to server to send cities list
	public void askForCitiesList() {
		// Create packet and send
		String cmd = "GET-CITIES";
		try {
			DatagramPacket sendPacket = new DatagramPacket(cmd.getBytes(), cmd.length(), serverSocketAddress);
			socket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean askRefreshData() {
		int numOfTry = 0;
		try {
			// Ask server to reload his data
			waitForServerUpdate = new CountDownLatch(1); // Controller to manage if server refreshed his data
			String cmd = "REFRESH-DATA";
			DatagramPacket sendPacket = new DatagramPacket(cmd.getBytes(), cmd.length(), serverSocketAddress);
			socket.send(sendPacket);

			// Wait for server to inform to have updated data
			while (!waitForServerUpdate.await(timeout, TimeUnit.SECONDS) && numOfTry < 5) {
				socket.send(sendPacket);
				numOfTry++;
			}

			waitForCities = new CountDownLatch(1); // Controller to manage if received cities information
			askForCitiesList(); // ask server to send cities list
			// Wait for server to inform to send cities list
			while (!waitForCities.await(timeout, TimeUnit.SECONDS) && numOfTry < 5) {
				askForCitiesList();
				numOfTry++;
			}
			if (numOfTry < 5)
				updateCitiesMenu();
			else
				return false;

		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return true;
	}

	// Ask server to send data about city
	public void askCityData(String city) {
		String cmd = "RELOAD-CITY" + city;
		try {
			DatagramPacket sendPacket = new DatagramPacket(cmd.getBytes(), cmd.length(), serverSocketAddress);
			socket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Wait for incoming messages from server
	public void waitForPackets() {
		while (true) {
			try {
				// Receive packet
				byte[] buf = new byte[100];
				DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);
				socket.receive(receivePacket);
				String receivedString = (new String(buf)).trim();
				
				if (receivedString.startsWith(("CITIES:"))) {
					// Received cities list
					String list = receivedString.substring(7); // Skip 'CITIES:' header
					citiesListLock.lock();
					try {
						cities = list.split(","); // Insert to cities list
						updateCitiesMenu();
						displayText("Data Updated. Choose city.");
						updateButtons(null);
					} finally {
						citiesListLock.unlock();
					}

					if (waitForCities != null)
						waitForCities.countDown(); // Inform threads waiting for cities update

				} else if (receivedString.equals("DATA-REFRESHED")) {
					// Received message from server that his data has been refreshed
					waitForServerUpdate.countDown();
				} else {
					// Got specific city information
					String[] info = receivedString.split(";");
					chosenCity = new CityWeatherData("name", new String(info[0]), new String(info[1]),
							new String(info[2]));
					updateButtons(todayButton);
					setButtonsEnabled(true);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// Update buttons state and text area after button press
	public void updateButtons(JButton buttonToShowAsPress) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				if (buttonToShowAsPress != null && buttonToShowAsPress == todayButton) {
					todayButton.setBackground(pressedButtonColor);
					displayText(chosenCity.getToday());
				} else
					todayButton.setBackground(defaultButtonColor);

				if (buttonToShowAsPress != null && buttonToShowAsPress == tomorrowButton) {
					tomorrowButton.setBackground(pressedButtonColor);
					displayText(chosenCity.getTomorrow());
				} else
					tomorrowButton.setBackground(defaultButtonColor);

				if (buttonToShowAsPress != null && buttonToShowAsPress == in2daysButton) {
					in2daysButton.setBackground(pressedButtonColor);
					displayText(chosenCity.getIn2days());
				} else
					in2daysButton.setBackground(defaultButtonColor);

			}
		});
	}

	// Initiate menu bar with no cities (Cities are being updated in updateCitiesMenu) 
	public void createMenu() {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				serverMenu = new JMenu("Server");
				serverMenu.setMnemonic(KeyEvent.VK_S);
				menuBar.add(serverMenu);

				JMenuItem changeServerAddressItem = new JMenuItem("Change server address");
				changeServerAddressItem.setActionCommand("Change server address");
				changeServerAddressItem.addActionListener(menuListener);
				serverMenu.add(changeServerAddressItem);

				JMenuItem changeServerPortItem = new JMenuItem("Change Port");
				changeServerPortItem.setActionCommand("Change server port");
				changeServerPortItem.addActionListener(menuListener);
				serverMenu.add(changeServerPortItem);

				JMenuItem refreshDataItem = new JMenuItem("Refresh data");
				refreshDataItem.setActionCommand("Refresh Data");
				refreshDataItem.addActionListener(menuListener);
				serverMenu.add(refreshDataItem);

				citiesMenu = new JMenu("City");
				citiesMenu.setMnemonic(KeyEvent.VK_C);
				menuBar.add(citiesMenu);

				JLabel noItems = new JLabel("No items to show");
				citiesMenu.add(noItems);
			}
		});
	}

	// Update City menu with cities list
	public void updateCitiesMenu() {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				citiesListLock.lock();
				try {
					if (cities != null) {
						citiesMenu.removeAll(); // Remove old data
						for (String city : cities) {
							JMenuItem cityItem = new JMenuItem(city);
							cityItem.setActionCommand(city);
							cityItem.addActionListener(cityMenuItemListener);
							citiesMenu.add(cityItem);
						}
					} else {
						JLabel noItems = new JLabel("No items to show");
						citiesMenu.add(noItems);
					}
				} finally {
					citiesListLock.unlock();
				}
			}
		});
	}

	// Change server address and reload data from new server
	public void changeServerAddress() {
		serverAddress = (String) JOptionPane.showInputDialog(this, "Please enter server address", "Connect",
				JOptionPane.INFORMATION_MESSAGE, null, null, "127.0.0.1");
		tryNewConnection();
	}

	// Change server port and reload data from new server
	public void changeServerPort() {
		try {
			serverPort = Integer.parseInt((String) JOptionPane.showInputDialog(this, "Please enter server port",
					"Connect", JOptionPane.INFORMATION_MESSAGE, null, null, "12345"));
			tryNewConnection();
		} catch (NumberFormatException e) {
			displayText("Could not connect to given server.");
			setGUIEnabled(false);
		}
	}

	// Try connection after server details has changed.
	public void tryNewConnection() {
		displayText("Initiating new connection to server. \nPlease wait. (" + (6*timeout) +" Seconds)");
		serverSocketAddress = new InetSocketAddress(serverAddress, serverPort);
		if (serverSocketAddress.isUnresolved()) {
			displayText("Could not connect to given server.");
		}
		
		// Set GUI as not active until finished with connection change
		setGUIEnabled(false);
		setServerMenuEnabled(false);

		// Try new connection with request to reload data
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				if (!askRefreshData()) {
					displayText("Could not connect to given server.");
				}
				setServerMenuEnabled(true);
				return null;
			}
		}.execute();
	}

	// Set GUI buttons as enabled/disabled and removes city list if neede 
	private void setGUIEnabled(boolean enabled) {
		todayButton.setEnabled(enabled);
		tomorrowButton.setEnabled(enabled);
		in2daysButton.setEnabled(enabled);

		if (!enabled) {
			// Remove cities list if old connection is not relevant
			citiesMenu.removeAll();
			citiesMenu.add(new JLabel("No items to show"));
		}
	}
	
	// Set Server menu as enabled/disabled
	private void setServerMenuEnabled(boolean enabled) {
		serverMenu.setEnabled(enabled);
	}

	// Set buttons as enabled/disabled
	private void setButtonsEnabled(boolean enabled) {
		todayButton.setEnabled(enabled);
		tomorrowButton.setEnabled(enabled);
		in2daysButton.setEnabled(enabled);
	}
	
	// Menu items listener for Server menu
	class ServerMenuItemListener implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent e) {
			if (e.getActionCommand().equals("Change server address"))
				changeServerAddress();
			else if (e.getActionCommand().equals("Change server port"))
				changeServerPort();
			else if (e.getActionCommand().equals("Refresh Data"))
				askRefreshData();
		}
	}

	// Menu items listener for City menu
	class CityMenuItemListener implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent e) {
			askCityData(e.getActionCommand());
		}
	}

	// GUI Buttons listener
	class DayButtonsListener implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent e) {
			updateButtons((JButton) e.getSource());
		}

	}

	// Display text in displat area
	private void displayText(String text) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				displayArea.setText(new String(text));
			}
		});
	}
}
