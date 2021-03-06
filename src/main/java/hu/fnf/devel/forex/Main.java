package hu.fnf.devel.forex;

import hu.fnf.devel.forex.utils.Info;
import hu.fnf.devel.forex.utils.WebInfo;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Future;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.dukascopy.api.DataType;
import com.dukascopy.api.Filter;
import com.dukascopy.api.IChart;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IStrategies;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.LoadingProgressListener;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.feed.FeedDescriptor;
import com.dukascopy.api.feed.IFeedDescriptor;
import com.dukascopy.api.system.ClientFactory;
import com.dukascopy.api.system.IClient;
import com.dukascopy.api.system.ISystemListener;
import com.dukascopy.api.system.ITesterClient;
import com.dukascopy.api.system.ITesterClient.DataLoadingMethod;
import com.dukascopy.api.system.TesterFactory;
import com.dukascopy.api.system.tester.ITesterExecution;
import com.dukascopy.api.system.tester.ITesterExecutionControl;
import com.dukascopy.api.system.tester.ITesterGui;
import com.dukascopy.api.system.tester.ITesterUserInterface;

public class Main {

	/**
	 * 
	 */
	private static final Logger logger = Logger.getLogger(Main.class);
	private static String jnlpUrl = "https://www.dukascopy.com/client/demo/jclient/jforex.jnlp";
	private static String userName = "DEMO10037VbnccEU";
	private static String password = "Vbncc";
	private static IClient client;
	private static Info info;

	private static String phase;
	private static String lastDLog;
	private static String lastILog;

	/**
	 * GUI
	 */

	public static void setPhase(String phase) {
		if (Main.phase != null) {
			logger.info("--- Stopping " + getPhase() + "phase ---");
		}
		Main.phase = phase;
		if (Main.phase != null) {
			logger.info("----------------------------------------");
			logger.info("--- Starting " + getPhase() + "phase ---");
		}
	}

	public static String getPhase() {
		return phase + " ";
	}

	public static void main(String[] args) {

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				Main.setPhase("Interrupted");
				closing();
				logger.info("------------------------- Done. ----------------------------");
			}
		}));

		if (args.length > 0) {
			PropertyConfigurator.configure(args[0]);
			logger.info("Using config from file.");
			logger.debug("log4j.properties: " + args[0]);
		} else {
			BasicConfigurator.configure();
			logger.info("Using basic configuration for logging.");
		}
		logger.info("-------------- Forex robot written by johnnym  --------------");

		setPhase("Initalization");
		info = new WebInfo();
		if (args.length > 1 && args[1].equalsIgnoreCase("test")) {
			test();
		} else {
			try {
				client = ClientFactory.getDefaultInstance();
				client.setSystemListener(new ISystemListener() {

					public void onStop(long arg0) {
						setPhase(null);
					}

					public void onStart(long arg0) {
					}

					public void onDisconnect() {
						logger.info("Client has been disconnected...");
					}

					public void onConnect() {
						logger.info("Client has been connected...");
					}
				});
			} catch (Exception e) {
				logger.fatal("Cannot instanciate client!", e);
				return;
			}

			setPhase("Connection");
			try {
				client.connect(jnlpUrl, userName, password);
			} catch (Exception e) {
				logger.fatal("Cannot connect to " + jnlpUrl + "@" + userName + ":" + password, e);
				closing();
				return;
			}

			// wait for it to connect
			int i = 50; // wait max ten seconds
			while (i > 0 && !client.isConnected()) {
				logger.debug("waiting for connection ...");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					logger.fatal("Connection process has been aborted!", e);
					closing();
					return;
				}
				i--;
			}

			// workaround for LoadNumberOfCandlesAction for JForex-API versions
			// >
			// 2.6.64
			try {
				int ms = 5000;
				logger.info("Wainting " + ms + "ms for candles to load.");
				logger.debug("workaround: JForex-API versions 2.6.64.");
				Thread.sleep(ms);
			} catch (InterruptedException e) {
				logger.error(
						"Workaround for LoadNumberOfCandlesAction for JForex-API versions 2.6.64 has been aborted.", e);
			}
			logger.info("Number of candles loaded.");

			setPhase("Strategy starting");
			try {
				client.startStrategy(StateMachine.getInstance());

			} catch (Exception e) {
				logger.fatal("Cannot start strategy, possibly connection error or no strategy!", e);
				closing();
				return;
			}
			setPhase("Running");
		}
	}

	private static void closing() {
		setPhase("Closing");
		if (client != null && client.isConnected()) {
			client.disconnect(); // onStop called
		}
	}

	public static boolean isMarketOpen(String market) {
		return info.isMarketOpen(market);
	}

	public static void massDebug(Logger logger, String msg) {
		if (msg.equalsIgnoreCase(lastDLog)) {
			return;
		}
		lastDLog = msg;
		logger.debug(msg);
	}

	public static void massInfo(Logger logger, String msg) {
		if (msg.equalsIgnoreCase(lastILog)) {
			return;
		}
		lastILog = msg;
		logger.info(msg);
	}

	public static void test() {
		String[] args = new String[2];
		args[0] = "/home/johnnym/git/forex-strategy/res/log4j.properties";
		args[1] = "test";

		TesterMainGUIMode2 gui = new TesterMainGUIMode2();
		try {
			gui.main(args);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}

@SuppressWarnings("serial")
class TesterMainGUIMode2 extends JFrame implements ITesterUserInterface, ITesterExecution {
	private final Logger logger = Logger.getLogger(TesterMainGUIMode2.class);

	private final int frameWidth = 1000;
	private final int frameHeight = 600;
	private final int controlPanelHeight = 40;

	private JPanel currentChartPanel = null;
	private ITesterExecutionControl executionControl = null;

	private JPanel controlPanel = null;
	private JButton startStrategyButton = null;
	private JButton pauseButton = null;
	private JButton continueButton = null;
	private JButton cancelButton = null;

	private String jnlpUrl = "https://www.dukascopy.com/client/demo/jclient/jforex.jnlp";
	private String userName = "DEMO10037VbnccEU";
	private String password = "Vbncc";

	private IContext context;

	public TesterMainGUIMode2() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
	}
	
	@Override
	public void setChartPanels(Map<IChart, ITesterGui> chartPanels) {
		setTitle("Tester");

		if (chartPanels != null && chartPanels.size() > 0) {
			for (IChart chart : chartPanels.keySet()) {
				StateMachine.getInstance().addChart(chart);
				if (context != null) {
					chart.add(context.getIndicators().getIndicator("RSI"), new Object[] { 15 });
				} else {
					logger.info("context is null");
				}
				logger.debug("Chart for instrument " + chart.getInstrument().toString());
				Instrument instrument = chart.getInstrument();

				// show ticks for EURUSD and 10 min bars for other instruments
				IFeedDescriptor feedDescriptor = new FeedDescriptor();
				if (chart.getInstrument().equals(Instrument.EURUSD)) {
					/*
					 * MACDSample452
					 */
					feedDescriptor.setInstrument(Instrument.EURUSD);
					feedDescriptor.setPeriod(Period.ONE_HOUR);
				} else {
					/*
					 * Scalper7
					 */
					feedDescriptor.setInstrument(Instrument.USDJPY);
					feedDescriptor.setPeriod(Period.ONE_MIN);
				}
				feedDescriptor.setDataType(DataType.TIME_PERIOD_AGGREGATION);
				feedDescriptor.setInstrument(instrument);
				feedDescriptor.setOfferSide(OfferSide.BID);
				

				feedDescriptor.setFilter(Filter.WEEKENDS);
				chartPanels.get(chart).getTesterChartController().addOHLCInformer();
				chartPanels.get(chart).getTesterChartController().setFeedDescriptor(feedDescriptor);

				JPanel chartPanel = chartPanels.get(chart).getChartPanel();
				addChartPanel(chartPanel);
			}
		}
		return;
		// if (chartPanels != null && chartPanels.size() > 0) {
		// IChart chart = chartPanels.keySet().iterator().next();
		// Instrument instrument = chart.getInstrument();
		//
		// IFeedDescriptor feedDescriptor = new FeedDescriptor();
		// feedDescriptor.setDataType(DataType.TIME_PERIOD_AGGREGATION);
		// feedDescriptor.setOfferSide(OfferSide.BID);
		// feedDescriptor.setInstrument(Instrument.USDJPY);
		// feedDescriptor.setPeriod(Period.ONE_MIN);
		// feedDescriptor.setFilter(Filter.WEEKENDS);
		// chartPanels.get(chart).getTesterChartController().setFeedDescriptor(feedDescriptor);
		//
		// setTitle(instrument.toString() + " " + chart.getSelectedOfferSide() +
		// " " + chart.getSelectedPeriod());
		//
		// JPanel chartPanel = chartPanels.get(chart).getChartPanel();
		// addChartPanel(chartPanel);
		// }
	}

	@Override
	public void setExecutionControl(ITesterExecutionControl executionControl) {
		this.executionControl = executionControl;
	}

	public void startStrategy() throws Exception {
		// get the instance of the IClient interface
		final ITesterClient client = TesterFactory.getDefaultInstance();
		// set the listener that will receive system events
		client.setSystemListener(new ISystemListener() {
			
			@Override
			public void onStart(long processId) {
				logger.info("Strategy started: " + processId);
				updateButtons();
				if (StateMachine.getInstance().getContext() == null) {
					logger.debug("context is null");
				} else {
					logger.debug("context is NOT null");
				}
			}

			@Override
			public void onStop(long processId) {
				logger.info("Strategy stopped: " + processId);
				resetButtons();

				File reportFile = new File("/tmp/report.html");
				try {
					client.createReport(processId, reportFile);
				} catch (Exception e) {
					logger.error(e.getMessage(), e);
				}
				if (client.getStartedStrategies().size() == 0) {
					// Do nothing
				}
			}

			@Override
			public void onConnect() {
				logger.info("Connected");
			}

			@Override
			public void onDisconnect() {
				// tester doesn't disconnect
			}
		});

		logger.info("Connecting...");
		// connect to the server using jnlp, user name and password
		// connection is needed for data downloading
		client.connect(jnlpUrl, userName, password);

		// wait for it to connect
		int i = 50; // wait max ten seconds
		while (i > 0 && !client.isConnected()) {
			Thread.sleep(1000);
			i--;
		}
		if (!client.isConnected()) {
			logger.error("Failed to connect Dukascopy servers");
			System.exit(1);
		}

		// set instruments that will be used in testing
		final Set<Instrument> instruments = new HashSet<Instrument>();
		instruments.add(Instrument.USDJPY);
		instruments.add(Instrument.EURUSD);

		logger.info("Subscribing instruments...");
		client.setSubscribedInstruments(instruments);
		// setting initial deposit
		client.setInitialDeposit(Instrument.EURUSD.getSecondaryCurrency(), 50000);
		final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

		Date dateFrom = dateFormat.parse("10/01/2013 00:00:00");
		Date dateTo = dateFormat.parse("10/31/2013 00:00:00");
		client.setDataInterval(DataLoadingMethod.ALL_TICKS, dateFrom.getTime(), dateTo.getTime());
		// client.setDataInterval(Period.FIFTEEN_MINS, OfferSide.BID,
		// InterpolationMethod.CLOSE_TICK, dateFrom.getTime(),
		// dateTo.getTime());
		// load data
		logger.info("Downloading data");
		Future<?> future = client.downloadData(null);
		// wait for downloading to complete
		future.get();
		// start the strategy
		logger.info("Starting strategy");
		/*
		 * client.startStrategy( new SMAStrategy() );
		 */

		client.startStrategy(StateMachine.getInstance(), new LoadingProgressListener() {
			@Override
			public void dataLoaded(long startTime, long endTime, long currentTime, String information) {
			}

			@Override
			public void loadingFinished(boolean allDataLoaded, long startTime, long endTime, long currentTime) {
			}

			@Override
			public boolean stopJob() {
				return false;
			}
		}, this, this);
		// now it's running
		while ( StateMachine.getInstance().getContext() == null ) {
			Thread.sleep(1000);
		}
		context = StateMachine.getInstance().getContext();
		logger.info("context is set");
	}

	/**
	 * Center a frame on the screen
	 */
	private void centerFrame() {
		Toolkit tk = Toolkit.getDefaultToolkit();
		Dimension screenSize = tk.getScreenSize();
		int screenHeight = screenSize.height;
		int screenWidth = screenSize.width;
		setSize(screenWidth / 2, screenHeight / 2);
		setLocation(screenWidth / 4, screenHeight / 4);
	}

	/**
	 * Add chart panel to the frame
	 * 
	 * @param panel
	 */
	private void addChartPanel(JPanel chartPanel) {
		// removecurrentChartPanel();

		this.currentChartPanel = chartPanel;
		chartPanel.setPreferredSize(new Dimension(frameWidth, frameHeight - controlPanelHeight));
		chartPanel.setMinimumSize(new Dimension(frameWidth, 200));
		chartPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
		getContentPane().add(chartPanel);
		this.validate();
		chartPanel.repaint();
	}

	/**
	 * Add buttons to start/pause/continue/cancel actions
	 */
	private void addControlPanel() {

		controlPanel = new JPanel();
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		controlPanel.setLayout(flowLayout);
		controlPanel.setPreferredSize(new Dimension(frameWidth, controlPanelHeight));
		controlPanel.setMinimumSize(new Dimension(frameWidth, controlPanelHeight));
		controlPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, controlPanelHeight));

		startStrategyButton = new JButton("Start strategy");
		startStrategyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				startStrategyButton.setEnabled(false);
				Runnable r = new Runnable() {
					public void run() {
						try {
							startStrategy();
						} catch (Exception e2) {
							logger.error(e2.getMessage(), e2);
							e2.printStackTrace();
							resetButtons();
						}
					}
				};
				Thread t = new Thread(r);
				t.start();
			}
		});

		pauseButton = new JButton("Pause");
		pauseButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (executionControl != null) {
					executionControl.pauseExecution();
					updateButtons();
				}
			}
		});

		continueButton = new JButton("Continue");
		continueButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (executionControl != null) {
					executionControl.continueExecution();
					updateButtons();
				}
			}
		});

		cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (executionControl != null) {
					executionControl.cancelExecution();
					updateButtons();
				}
			}
		});

		controlPanel.add(startStrategyButton);
		controlPanel.add(pauseButton);
		controlPanel.add(continueButton);
		controlPanel.add(cancelButton);
		getContentPane().add(controlPanel);

		pauseButton.setEnabled(false);
		continueButton.setEnabled(false);
		cancelButton.setEnabled(false);
	}

	private void updateButtons() {
		if (executionControl != null) {
			startStrategyButton.setEnabled(executionControl.isExecutionCanceled());
			pauseButton.setEnabled(!executionControl.isExecutionPaused() && !executionControl.isExecutionCanceled());
			cancelButton.setEnabled(!executionControl.isExecutionCanceled());
			continueButton.setEnabled(executionControl.isExecutionPaused());
		}
	}

	private void resetButtons() {
		startStrategyButton.setEnabled(true);
		pauseButton.setEnabled(false);
		continueButton.setEnabled(false);
		cancelButton.setEnabled(false);
	}

	// private void removecurrentChartPanel() {
	// if (this.currentChartPanel != null) {
	// try {
	// SwingUtilities.invokeAndWait(new Runnable() {
	// @Override
	// public void run() {
	// TesterMainGUIMode2.this.getContentPane().remove(TesterMainGUIMode2.this.currentChartPanel);
	// TesterMainGUIMode2.this.getContentPane().repaint();
	// }
	// });
	// } catch (Exception e) {
	// LOGGER.error(e.getMessage(), e);
	// }
	// }
	// }

	public void showChartFrame() {
		setSize(frameWidth, frameHeight);
		centerFrame();
		addControlPanel();
		setVisible(true);
	}

	public void main(String[] args) throws Exception {
		TesterMainGUIMode2 testerMainGUI = new TesterMainGUIMode2();
		testerMainGUI.showChartFrame();
	}
}