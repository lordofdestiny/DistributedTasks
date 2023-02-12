package rs.ac.bg.etf.kdp.gui.client;

import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.PLAIN_MESSAGE;
import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static javax.swing.JOptionPane.YES_NO_OPTION;
import static javax.swing.JOptionPane.YES_OPTION;
import static javax.swing.JOptionPane.showConfirmDialog;
import static javax.swing.JOptionPane.showMessageDialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import rs.ac.bg.etf.kdp.core.IClientServer.JobTreeNode;
import rs.ac.bg.etf.kdp.core.IPingable;
import rs.ac.bg.etf.kdp.utils.ConnectionInfo;
import rs.ac.bg.etf.kdp.utils.ConnectionProvider;
import rs.ac.bg.etf.kdp.utils.ConnectionProvider.ServerUnavailableException;
import rs.ac.bg.etf.kdp.utils.FileOperations;
import rs.ac.bg.etf.kdp.utils.JarValidator;
import rs.ac.bg.etf.kdp.utils.JobDescriptor;
import rs.ac.bg.etf.kdp.utils.JobDescriptor.JobCreationException;

public class ClientAppFrame extends JFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					class Temp {
						public UUID id = null;
					}
					Temp temp = new Temp();
					ClientAppFrame window = new ClientAppFrame(new ReentrantLock(),
							UUID::randomUUID, () -> temp.id);
					window.setUUIDReadyListener((uuid) -> {
						temp.id = uuid;
					});
					window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

					window.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	private final Timer timer = new Timer();

	private final Supplier<UUID> newUUID;
	private final Supplier<UUID> clientUUID;
	private final ReentrantLock singleDialogLock;
	private final AtomicReference<CompletableFuture<Integer>> taskFailedResponse = new AtomicReference<>();

	/**
	 * Create the application.
	 */
	public ClientAppFrame(ReentrantLock singleDialogLock, Supplier<UUID> newUUID,
			Supplier<UUID> clientUUID) {
		Objects.requireNonNull(newUUID);
		Objects.requireNonNull(clientUUID);
		this.newUUID = newUUID;
		this.clientUUID = clientUUID;
		this.singleDialogLock = singleDialogLock;
		initialize();
	}

	private Consumer<UUID> uuidReady = (v) -> {
	};
	private Consumer<ConnectionInfo> connectionInfoReady = (v) -> {
	};
	private Runnable connectReady = () -> {
	};
	private Consumer<JobDescriptor> jobReady = (job) -> {
	};
	private Runnable fetchResults = () -> {

	};
	private boolean connected = false;

	public void setUUIDReadyListener(Consumer<UUID> listener) {
		uuidReady = Objects.requireNonNull(listener);
	}

	public void setConnectInfoReadyListener(Consumer<ConnectionInfo> listener) {
		connectionInfoReady = Objects.requireNonNull(listener);
	}

	public void setUserConnectListener(Runnable listener) {
		connectReady = Objects.requireNonNull(listener);
	}

	public void setJobDescriptorListener(Consumer<JobDescriptor> listener) {
		jobReady = Objects.requireNonNull(listener);
	}

	public void setFetchResultsListener(Runnable listener) {
		fetchResults = Objects.requireNonNull(listener);
	}

	private JPanel panelPing;
	private JLabel lblPingValue;
	private JLabel lblConnectionStatus;
	private JTextField txtClassName;
	private JTextField txtJAR;
	private JTextField txtJobName;
	private JPanel panelNewJob;
	private JTable tableInFiles;
	private JTable tableOutFiles;
	private JTextArea txtAreaArguments;
	private JMenuItem mntmConnect;
	private JMenuItem mntmDisplay;
	private JMenuItem mntmGenerate;
	private JMenuItem mntmAuth;
	private JButton btnLoadConfig;
	private JButton btnVerifyConfig;
	private JButton btnClearConfig;
	private JButton btnSaveConfig;
	private JButton btnSubmit;
	private JFileChooser chooser;
	private FileNameExtensionFilter jsonFilter;
	private JTabbedPane tabbedPane;
	private JToggleButton tglBtnTemp;
	private GridBagConstraints gbc_lblProgressText;
	private JProgressBar uploadProgressBar;
	private JLabel lblFileSizeValue;
	private JLabel lblSizeTransfered;
	private JTree jobTree;

	private MessageConsole console;

	private JobDescriptor jobDescriptor = null;

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		setTitle("Client");
		setBounds(100, 100, 500, 550);
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu mnUUID = new JMenu("UUID");
		menuBar.add(mnUUID);
		mntmDisplay = new JMenuItem("Display");
		mnUUID.add(mntmDisplay);
		mntmDisplay.setEnabled(false);

		mntmDisplay.addActionListener(e -> displayUUID());

		mntmGenerate = new JMenuItem("Generate");
		mnUUID.add(mntmGenerate);
		mntmAuth = new JMenuItem("Authenticate");
		mnUUID.add(mntmAuth);

		JMenu mnConnection = new JMenu("Connection");
		menuBar.add(mnConnection);

		mntmConnect = new JMenuItem("Connect");
		mntmConnect.addActionListener(this::promptGetConnectionInfo);
		mntmConnect.setEnabled(false);
		mnConnection.add(mntmConnect);

		JMenuItem mntmFetchResult = new JMenuItem("Fetch Results");
		mntmFetchResult.addActionListener(e -> {
			if (!connected) {
				return;
			}
			fetchResults.run();
		});
		mnConnection.add(mntmFetchResult);

		Component horizontalGlue = Box.createHorizontalGlue();
		menuBar.add(horizontalGlue);
		getContentPane().setLayout(new BorderLayout(0, 0));

		JPanel panelSouth = new JPanel();
		getContentPane().add(panelSouth, BorderLayout.SOUTH);
		panelSouth.setLayout(new BoxLayout(panelSouth, BoxLayout.Y_AXIS));

		JSeparator separator = new JSeparator();
		panelSouth.add(separator);

		panelPing = new JPanel();
		panelSouth.add(panelPing);
		GridBagLayout gbl_panelPing = new GridBagLayout();
		gbl_panelPing.columnWidths = new int[] { 0, 116, 222, 29, 31, 0, 30, 0 };
		gbl_panelPing.rowHeights = new int[] { 25, 0 };
		gbl_panelPing.columnWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0,
				Double.MIN_VALUE };
		gbl_panelPing.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
		panelPing.setLayout(gbl_panelPing);

		Component horizontalStrut_1 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_1 = new GridBagConstraints();
		gbc_horizontalStrut_1.insets = new Insets(0, 0, 0, 5);
		gbc_horizontalStrut_1.gridx = 0;
		gbc_horizontalStrut_1.gridy = 0;
		panelPing.add(horizontalStrut_1, gbc_horizontalStrut_1);

		lblConnectionStatus = new JLabel("Connection status");
		lblConnectionStatus.setFont(new Font("Tahoma", Font.BOLD, 11));
		GridBagConstraints gbc_lblConnectionStatus = new GridBagConstraints();
		gbc_lblConnectionStatus.anchor = GridBagConstraints.WEST;
		gbc_lblConnectionStatus.insets = new Insets(0, 0, 0, 5);
		gbc_lblConnectionStatus.gridx = 1;
		gbc_lblConnectionStatus.gridy = 0;
		panelPing.add(lblConnectionStatus, gbc_lblConnectionStatus);

		JLabel lblPingText = new JLabel("Ping: ");
		lblPingText.setHorizontalAlignment(SwingConstants.LEFT);
		GridBagConstraints gbc_lblPingText = new GridBagConstraints();
		gbc_lblPingText.fill = GridBagConstraints.VERTICAL;
		gbc_lblPingText.anchor = GridBagConstraints.EAST;
		gbc_lblPingText.insets = new Insets(0, 0, 0, 5);
		gbc_lblPingText.gridx = 3;
		gbc_lblPingText.gridy = 0;
		panelPing.add(lblPingText, gbc_lblPingText);

		lblPingValue = new JLabel("-\u221E");
		lblPingValue.setHorizontalAlignment(SwingConstants.RIGHT);
		GridBagConstraints gbc_lblPingvValue = new GridBagConstraints();
		gbc_lblPingvValue.insets = new Insets(0, 0, 0, 5);
		gbc_lblPingvValue.fill = GridBagConstraints.VERTICAL;
		gbc_lblPingvValue.anchor = GridBagConstraints.EAST;
		gbc_lblPingvValue.gridx = 4;
		gbc_lblPingvValue.gridy = 0;
		panelPing.add(lblPingValue, gbc_lblPingvValue);

		JLabel lblMs = new JLabel("ms");
		GridBagConstraints gbc_lblMs = new GridBagConstraints();
		gbc_lblMs.insets = new Insets(0, 0, 0, 5);
		gbc_lblMs.gridx = 5;
		gbc_lblMs.gridy = 0;
		panelPing.add(lblMs, gbc_lblMs);

		Component horizontalStrut = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut = new GridBagConstraints();
		gbc_horizontalStrut.anchor = GridBagConstraints.WEST;
		gbc_horizontalStrut.gridx = 6;
		gbc_horizontalStrut.gridy = 0;
		panelPing.add(horizontalStrut, gbc_horizontalStrut);

		JPanel panelCenter = new JPanel();
		getContentPane().add(panelCenter, BorderLayout.CENTER);
		panelCenter.setLayout(new BorderLayout(0, 0));

		tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		panelCenter.add(tabbedPane);

		jsonFilter = new FileNameExtensionFilter("JavaScript Object Notation", "json");
		chooser = new JFileChooser("Select configuration file");
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setCurrentDirectory(new File(System.getProperty("user.home")));

		panelNewJob = new JPanel();
		tabbedPane.addTab("Submit job", null, panelNewJob, null);
		GridBagLayout gbl_panelNewJob = new GridBagLayout();
		gbl_panelNewJob.columnWidths = new int[] { 0, 0, 0, 0, 183, 27, 0, 0, 0, 0, 0, 0 };
		gbl_panelNewJob.rowHeights = new int[] { 25, 25, 30, 30, 25, 25, 25, 0, 0, 30, 30, 14, 25,
				25, 15, 0 };
		gbl_panelNewJob.columnWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.0, 0.0,
				0.0, 0.0, Double.MIN_VALUE };
		gbl_panelNewJob.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
				0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
		panelNewJob.setLayout(gbl_panelNewJob);

		Component verticalGlue_1 = Box.createVerticalGlue();
		GridBagConstraints gbc_verticalGlue_1 = new GridBagConstraints();
		gbc_verticalGlue_1.insets = new Insets(0, 0, 5, 5);
		gbc_verticalGlue_1.gridx = 4;
		gbc_verticalGlue_1.gridy = 0;
		panelNewJob.add(verticalGlue_1, gbc_verticalGlue_1);

		JLabel lblJobInfo = new JLabel("Job Parameters");
		GridBagConstraints gbc_lblJobInfo = new GridBagConstraints();
		gbc_lblJobInfo.insets = new Insets(0, 0, 5, 5);
		gbc_lblJobInfo.gridx = 4;
		gbc_lblJobInfo.gridy = 1;
		panelNewJob.add(lblJobInfo, gbc_lblJobInfo);

		Component horizontalStrut_2 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_2 = new GridBagConstraints();
		gbc_horizontalStrut_2.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut_2.gridx = 0;
		gbc_horizontalStrut_2.gridy = 2;
		panelNewJob.add(horizontalStrut_2, gbc_horizontalStrut_2);

		JLabel lblJobName = new JLabel("Job Name");
		GridBagConstraints gbc_lblJobName = new GridBagConstraints();
		gbc_lblJobName.anchor = GridBagConstraints.WEST;
		gbc_lblJobName.gridwidth = 3;
		gbc_lblJobName.insets = new Insets(0, 0, 5, 5);
		gbc_lblJobName.gridx = 1;
		gbc_lblJobName.gridy = 2;
		panelNewJob.add(lblJobName, gbc_lblJobName);

		txtJobName = new JTextField();
		txtJobName.setDisabledTextColor(Color.BLACK);
		lblJobName.setLabelFor(txtJobName);
		GridBagConstraints gbc_txtJobName = new GridBagConstraints();
		gbc_txtJobName.insets = new Insets(0, 0, 5, 5);
		gbc_txtJobName.fill = GridBagConstraints.HORIZONTAL;
		gbc_txtJobName.gridx = 4;
		gbc_txtJobName.gridy = 2;
		panelNewJob.add(txtJobName, gbc_txtJobName);
		txtJobName.setColumns(10);

		btnLoadConfig = new JButton("Load configuration");
		btnLoadConfig.addActionListener(this::loadConfigHandler);
		GridBagConstraints gbc_btnLoadConfig = new GridBagConstraints();
		gbc_btnLoadConfig.fill = GridBagConstraints.HORIZONTAL;
		gbc_btnLoadConfig.gridwidth = 4;
		gbc_btnLoadConfig.insets = new Insets(0, 0, 5, 5);
		gbc_btnLoadConfig.gridx = 6;
		gbc_btnLoadConfig.gridy = 2;
		panelNewJob.add(btnLoadConfig, gbc_btnLoadConfig);

		Component horizontalStrut_3 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_3 = new GridBagConstraints();
		gbc_horizontalStrut_3.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut_3.gridx = 0;
		gbc_horizontalStrut_3.gridy = 3;
		panelNewJob.add(horizontalStrut_3, gbc_horizontalStrut_3);

		JLabel lblJobJar = new JLabel("JAR");
		GridBagConstraints gbc_lblJobJar = new GridBagConstraints();
		gbc_lblJobJar.anchor = GridBagConstraints.WEST;
		gbc_lblJobJar.gridwidth = 3;
		gbc_lblJobJar.insets = new Insets(0, 0, 5, 5);
		gbc_lblJobJar.gridx = 1;
		gbc_lblJobJar.gridy = 3;
		panelNewJob.add(lblJobJar, gbc_lblJobJar);

		txtJAR = new JTextField();
		txtJAR.setDisabledTextColor(Color.BLACK);
		GridBagConstraints gbc_txtJAR = new GridBagConstraints();
		gbc_txtJAR.insets = new Insets(0, 0, 5, 5);
		gbc_txtJAR.fill = GridBagConstraints.HORIZONTAL;
		gbc_txtJAR.gridx = 4;
		gbc_txtJAR.gridy = 3;
		panelNewJob.add(txtJAR, gbc_txtJAR);
		txtJAR.setColumns(10);

		btnVerifyConfig = new JButton("Verify configuration");
		btnVerifyConfig.addActionListener(e -> {
			final var jdo = verifyCustomConfig();
			if (jdo.isPresent()) {
				jobDescriptor = jdo.get();
				btnLoadConfig.setEnabled(false);
				btnSaveConfig.setEnabled(true);
				btnSubmit.setEnabled(true);
				inputsSetEnabled(false);
			}
		});
		GridBagConstraints gbc_btnVerifyConfig = new GridBagConstraints();
		gbc_btnVerifyConfig.fill = GridBagConstraints.HORIZONTAL;
		gbc_btnVerifyConfig.gridwidth = 4;
		gbc_btnVerifyConfig.insets = new Insets(0, 0, 5, 5);
		gbc_btnVerifyConfig.gridx = 6;
		gbc_btnVerifyConfig.gridy = 3;
		panelNewJob.add(btnVerifyConfig, gbc_btnVerifyConfig);

		Component horizontalStrut_8 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_8 = new GridBagConstraints();
		gbc_horizontalStrut_8.gridheight = 8;
		gbc_horizontalStrut_8.insets = new Insets(0, 0, 5, 0);
		gbc_horizontalStrut_8.gridx = 10;
		gbc_horizontalStrut_8.gridy = 3;
		panelNewJob.add(horizontalStrut_8, gbc_horizontalStrut_8);

		JLabel lblClassName = new JLabel("Class name");
		GridBagConstraints gbc_lblClassName = new GridBagConstraints();
		gbc_lblClassName.anchor = GridBagConstraints.WEST;
		gbc_lblClassName.gridwidth = 3;
		gbc_lblClassName.insets = new Insets(0, 0, 5, 5);
		gbc_lblClassName.gridx = 1;
		gbc_lblClassName.gridy = 4;
		panelNewJob.add(lblClassName, gbc_lblClassName);

		txtClassName = new JTextField();
		txtClassName.setDisabledTextColor(Color.BLACK);
		GridBagConstraints gbc_txtClassName = new GridBagConstraints();
		gbc_txtClassName.insets = new Insets(0, 0, 5, 5);
		gbc_txtClassName.fill = GridBagConstraints.HORIZONTAL;
		gbc_txtClassName.gridx = 4;
		gbc_txtClassName.gridy = 4;
		panelNewJob.add(txtClassName, gbc_txtClassName);
		txtClassName.setColumns(10);

		btnClearConfig = new JButton("Clear configuration");
		btnClearConfig.addActionListener(e -> {
			clearNewJobFields();
			jobDescriptor = null;
			btnVerifyConfig.setEnabled(true);
			btnSaveConfig.setEnabled(false);
			btnLoadConfig.setEnabled(true);
			btnSubmit.setEnabled(false);
			inputsSetEnabled(true);
		});
		GridBagConstraints gbc_btnClearConfig = new GridBagConstraints();
		gbc_btnClearConfig.anchor = GridBagConstraints.SOUTH;
		gbc_btnClearConfig.fill = GridBagConstraints.HORIZONTAL;
		gbc_btnClearConfig.gridwidth = 4;
		gbc_btnClearConfig.insets = new Insets(0, 0, 5, 5);
		gbc_btnClearConfig.gridx = 6;
		gbc_btnClearConfig.gridy = 4;
		panelNewJob.add(btnClearConfig, gbc_btnClearConfig);

		JLabel lblArguments = new JLabel("Arguments");
		GridBagConstraints gbc_lblArguments = new GridBagConstraints();
		gbc_lblArguments.anchor = GridBagConstraints.NORTH;
		gbc_lblArguments.gridwidth = 3;
		gbc_lblArguments.insets = new Insets(0, 0, 5, 5);
		gbc_lblArguments.gridx = 1;
		gbc_lblArguments.gridy = 5;
		panelNewJob.add(lblArguments, gbc_lblArguments);

		txtAreaArguments = new JTextArea();
		txtAreaArguments.setDisabledTextColor(Color.BLACK);
		txtAreaArguments.setBorder(new CompoundBorder(new LineBorder(new Color(171, 173, 179)),
				new EmptyBorder(5, 5, 5, 5)));
		txtAreaArguments.setLineWrap(true);
		txtAreaArguments.setWrapStyleWord(true);
		GridBagConstraints gbc_txtAreaArguments = new GridBagConstraints();
		gbc_txtAreaArguments.gridheight = 3;
		gbc_txtAreaArguments.insets = new Insets(0, 0, 5, 5);
		gbc_txtAreaArguments.fill = GridBagConstraints.BOTH;
		gbc_txtAreaArguments.gridx = 4;
		gbc_txtAreaArguments.gridy = 5;
		panelNewJob.add(txtAreaArguments, gbc_txtAreaArguments);

		btnSaveConfig = new JButton("Export configuration");
		btnSaveConfig.setEnabled(false);
		btnSaveConfig.addActionListener(this::saveConfigHandler);
		GridBagConstraints gbc_btnSaveConfig = new GridBagConstraints();
		gbc_btnSaveConfig.anchor = GridBagConstraints.NORTH;
		gbc_btnSaveConfig.fill = GridBagConstraints.HORIZONTAL;
		gbc_btnSaveConfig.gridwidth = 4;
		gbc_btnSaveConfig.insets = new Insets(0, 0, 5, 5);
		gbc_btnSaveConfig.gridx = 6;
		gbc_btnSaveConfig.gridy = 5;
		panelNewJob.add(btnSaveConfig, gbc_btnSaveConfig);

		tglBtnTemp = new JToggleButton("Temp on Desktop");
		GridBagConstraints gbc_tglBtnTemp = new GridBagConstraints();
		gbc_tglBtnTemp.fill = GridBagConstraints.HORIZONTAL;
		gbc_tglBtnTemp.gridwidth = 4;
		gbc_tglBtnTemp.insets = new Insets(0, 0, 5, 5);
		gbc_tglBtnTemp.gridx = 6;
		gbc_tglBtnTemp.gridy = 6;
		panelNewJob.add(tglBtnTemp, gbc_tglBtnTemp);

		btnSubmit = new JButton("Submit");
		btnSubmit.setEnabled(false);
		btnSubmit.addActionListener((e) -> {
			inputsSetEnabled(true);
			jobReady.accept(jobDescriptor);
			jobDescriptor = null;
		});
		GridBagConstraints gbc_btnSubmit = new GridBagConstraints();
		gbc_btnSubmit.anchor = GridBagConstraints.SOUTH;
		gbc_btnSubmit.fill = GridBagConstraints.HORIZONTAL;
		gbc_btnSubmit.gridwidth = 4;
		gbc_btnSubmit.insets = new Insets(0, 0, 5, 5);
		gbc_btnSubmit.gridx = 6;
		gbc_btnSubmit.gridy = 7;
		panelNewJob.add(btnSubmit, gbc_btnSubmit);

		Component verticalStrut = Box.createVerticalStrut(20);
		GridBagConstraints gbc_verticalStrut = new GridBagConstraints();
		gbc_verticalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_verticalStrut.gridx = 4;
		gbc_verticalStrut.gridy = 8;
		panelNewJob.add(verticalStrut, gbc_verticalStrut);

		JLabel lblNewLabel_1 = new JLabel("IN Files");
		GridBagConstraints gbc_lblNewLabel_1 = new GridBagConstraints();
		gbc_lblNewLabel_1.anchor = GridBagConstraints.NORTHWEST;
		gbc_lblNewLabel_1.gridwidth = 3;
		gbc_lblNewLabel_1.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel_1.gridx = 1;
		gbc_lblNewLabel_1.gridy = 9;
		panelNewJob.add(lblNewLabel_1, gbc_lblNewLabel_1);

		tableInFiles = new JTable();
		tableInFiles.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tableInFiles.setBorder(UIManager.getBorder("TextField.border"));
		tableInFiles.setFont(new Font("Tahoma", Font.PLAIN, 12));
		tableInFiles.setModel(new DefaultTableModel(
				new Object[][] { { null, null }, { null, null }, { null, null }, },
				new String[] { "New column", "New column" }));
		GridBagConstraints gbc_tableInFiles = new GridBagConstraints();
		gbc_tableInFiles.gridwidth = 6;
		gbc_tableInFiles.insets = new Insets(0, 0, 5, 5);
		gbc_tableInFiles.fill = GridBagConstraints.BOTH;
		gbc_tableInFiles.gridx = 4;
		gbc_tableInFiles.gridy = 9;
		panelNewJob.add(tableInFiles, gbc_tableInFiles);

		JLabel lblNewLabel_2 = new JLabel("OUT Files");
		GridBagConstraints gbc_lblNewLabel_2 = new GridBagConstraints();
		gbc_lblNewLabel_2.anchor = GridBagConstraints.NORTHWEST;
		gbc_lblNewLabel_2.gridwidth = 3;
		gbc_lblNewLabel_2.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel_2.gridx = 1;
		gbc_lblNewLabel_2.gridy = 10;
		panelNewJob.add(lblNewLabel_2, gbc_lblNewLabel_2);

		tableOutFiles = new JTable();
		tableOutFiles.setBorder(UIManager.getBorder("TextField.border"));
		tableOutFiles.setModel(new DefaultTableModel(
				new Object[][] { { null, null }, { null, null }, { null, null }, },
				new String[] { "New colqumn", "New column" }));
		tableOutFiles.setFont(new Font("Tahoma", Font.PLAIN, 12));
		GridBagConstraints gbc_tableOutFiles = new GridBagConstraints();
		gbc_tableOutFiles.gridwidth = 6;
		gbc_tableOutFiles.insets = new Insets(0, 0, 5, 5);
		gbc_tableOutFiles.fill = GridBagConstraints.BOTH;
		gbc_tableOutFiles.gridx = 4;
		gbc_tableOutFiles.gridy = 10;
		panelNewJob.add(tableOutFiles, gbc_tableOutFiles);

		JSeparator separator_1 = new JSeparator();
		GridBagConstraints gbc_separator_1 = new GridBagConstraints();
		gbc_separator_1.fill = GridBagConstraints.BOTH;
		gbc_separator_1.gridwidth = 9;
		gbc_separator_1.insets = new Insets(0, 0, 5, 5);
		gbc_separator_1.gridx = 1;
		gbc_separator_1.gridy = 11;
		panelNewJob.add(separator_1, gbc_separator_1);

		JLabel lblProgressText = new JLabel("Upload progress");
//		GridBagConstraints gbc_lblProgress;
		gbc_lblProgressText = new GridBagConstraints();
		gbc_lblProgressText.anchor = GridBagConstraints.WEST;
		gbc_lblProgressText.gridwidth = 3;
		gbc_lblProgressText.insets = new Insets(0, 0, 5, 5);
		gbc_lblProgressText.gridx = 2;
		gbc_lblProgressText.gridy = 12;
		panelNewJob.add(lblProgressText, gbc_lblProgressText);

		JLabel lblFileSize = new JLabel("File Size: ");
		GridBagConstraints gbc_lblFileSize = new GridBagConstraints();
		gbc_lblFileSize.anchor = GridBagConstraints.WEST;
		gbc_lblFileSize.insets = new Insets(0, 0, 5, 5);
		gbc_lblFileSize.gridx = 6;
		gbc_lblFileSize.gridy = 12;
		panelNewJob.add(lblFileSize, gbc_lblFileSize);

		lblFileSizeValue = new JLabel("???");
		GridBagConstraints gbc_lblFileSizeValue = new GridBagConstraints();
		gbc_lblFileSizeValue.gridwidth = 3;
		gbc_lblFileSizeValue.insets = new Insets(0, 0, 5, 5);
		gbc_lblFileSizeValue.gridx = 7;
		gbc_lblFileSizeValue.gridy = 12;
		panelNewJob.add(lblFileSizeValue, gbc_lblFileSizeValue);

		uploadProgressBar = new JProgressBar();
		GridBagConstraints gbc_uploadProgressBar = new GridBagConstraints();
		gbc_uploadProgressBar.gridwidth = 3;
		gbc_uploadProgressBar.fill = GridBagConstraints.BOTH;
		gbc_uploadProgressBar.insets = new Insets(0, 0, 5, 5);
		gbc_uploadProgressBar.gridx = 2;
		gbc_uploadProgressBar.gridy = 13;
		panelNewJob.add(uploadProgressBar, gbc_uploadProgressBar);

		JLabel lblTransfered = new JLabel("Transfered:");
		GridBagConstraints gbc_lblTransfered = new GridBagConstraints();
		gbc_lblTransfered.anchor = GridBagConstraints.WEST;
		gbc_lblTransfered.insets = new Insets(0, 0, 5, 5);
		gbc_lblTransfered.gridx = 6;
		gbc_lblTransfered.gridy = 13;
		panelNewJob.add(lblTransfered, gbc_lblTransfered);

		lblSizeTransfered = new JLabel("0B");
		GridBagConstraints gbc_lblSizeTransfered = new GridBagConstraints();
		gbc_lblSizeTransfered.gridwidth = 3;
		gbc_lblSizeTransfered.insets = new Insets(0, 0, 5, 5);
		gbc_lblSizeTransfered.gridx = 7;
		gbc_lblSizeTransfered.gridy = 13;
		panelNewJob.add(lblSizeTransfered, gbc_lblSizeTransfered);

		Component horizontalStrut_4 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_4 = new GridBagConstraints();
		gbc_horizontalStrut_4.insets = new Insets(0, 0, 0, 5);
		gbc_horizontalStrut_4.gridx = 4;
		gbc_horizontalStrut_4.gridy = 14;
		panelNewJob.add(horizontalStrut_4, gbc_horizontalStrut_4);
		JPanel panelActivity = new JPanel();
		tabbedPane.addTab("Job activity", null, panelActivity, null);
		GridBagLayout gbl_panelActivity = new GridBagLayout();
		gbl_panelActivity.columnWidths = new int[] { 30, 74, 102, 1, 0, 30, 0 };
		gbl_panelActivity.rowHeights = new int[] { 0, 0, 140, 0, 25, 0, 33, 0, 0 };
		gbl_panelActivity.columnWeights = new double[] { 0.0, 1.0, 1.0, 0.0, 0.0, 0.0,
				Double.MIN_VALUE };
		gbl_panelActivity.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0,
				Double.MIN_VALUE };
		panelActivity.setLayout(gbl_panelActivity);

		Component verticalStrut_1 = Box.createVerticalStrut(20);
		GridBagConstraints gbc_verticalStrut_1 = new GridBagConstraints();
		gbc_verticalStrut_1.fill = GridBagConstraints.HORIZONTAL;
		gbc_verticalStrut_1.gridwidth = 4;
		gbc_verticalStrut_1.insets = new Insets(0, 0, 5, 5);
		gbc_verticalStrut_1.gridx = 1;
		gbc_verticalStrut_1.gridy = 0;
		panelActivity.add(verticalStrut_1, gbc_verticalStrut_1);

		JLabel lblFailedJobs = new JLabel("Current failed job");
		GridBagConstraints gbc_lblFailedJobs = new GridBagConstraints();
		gbc_lblFailedJobs.anchor = GridBagConstraints.WEST;
		gbc_lblFailedJobs.insets = new Insets(0, 0, 5, 5);
		gbc_lblFailedJobs.gridx = 1;
		gbc_lblFailedJobs.gridy = 1;
		panelActivity.add(lblFailedJobs, gbc_lblFailedJobs);

		Component horizontalGlue_1 = Box.createHorizontalGlue();
		GridBagConstraints gbc_horizontalGlue_1 = new GridBagConstraints();
		gbc_horizontalGlue_1.fill = GridBagConstraints.HORIZONTAL;
		gbc_horizontalGlue_1.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalGlue_1.gridx = 2;
		gbc_horizontalGlue_1.gridy = 1;
		panelActivity.add(horizontalGlue_1, gbc_horizontalGlue_1);

		JLabel lblFailureCountL = new JLabel("Failed job count:");
		GridBagConstraints gbc_lblFailureCountL = new GridBagConstraints();
		gbc_lblFailureCountL.anchor = GridBagConstraints.EAST;
		gbc_lblFailureCountL.insets = new Insets(0, 0, 5, 5);
		gbc_lblFailureCountL.gridx = 3;
		gbc_lblFailureCountL.gridy = 1;
		panelActivity.add(lblFailureCountL, gbc_lblFailureCountL);

		lblFailureCountValue = new JLabel("0");
		GridBagConstraints gbc_lblFailureCountValue = new GridBagConstraints();
		gbc_lblFailureCountValue.anchor = GridBagConstraints.EAST;
		gbc_lblFailureCountValue.insets = new Insets(0, 0, 5, 5);
		gbc_lblFailureCountValue.gridx = 4;
		gbc_lblFailureCountValue.gridy = 1;
		panelActivity.add(lblFailureCountValue, gbc_lblFailureCountValue);

		Component horizontalStrut_6 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_6 = new GridBagConstraints();
		gbc_horizontalStrut_6.fill = GridBagConstraints.VERTICAL;
		gbc_horizontalStrut_6.gridheight = 3;
		gbc_horizontalStrut_6.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut_6.gridx = 0;
		gbc_horizontalStrut_6.gridy = 2;
		panelActivity.add(horizontalStrut_6, gbc_horizontalStrut_6);

		JScrollPane scrollPane = new JScrollPane();
		GridBagConstraints gbc_scrollPane = new GridBagConstraints();
		gbc_scrollPane.gridwidth = 4;
		gbc_scrollPane.insets = new Insets(0, 0, 5, 5);
		gbc_scrollPane.fill = GridBagConstraints.BOTH;
		gbc_scrollPane.gridx = 1;
		gbc_scrollPane.gridy = 2;
		panelActivity.add(scrollPane, gbc_scrollPane);

		jobTree = new JTree();
		jobTree.setModel(new DefaultTreeModel(null));
		scrollPane.setViewportView(jobTree);

		Component horizontalStrut_5 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_5 = new GridBagConstraints();
		gbc_horizontalStrut_5.fill = GridBagConstraints.BOTH;
		gbc_horizontalStrut_5.gridheight = 3;
		gbc_horizontalStrut_5.insets = new Insets(0, 0, 5, 0);
		gbc_horizontalStrut_5.gridx = 5;
		gbc_horizontalStrut_5.gridy = 2;
		panelActivity.add(horizontalStrut_5, gbc_horizontalStrut_5);

		JButton btnAbandonJob = new JButton("Abandon job");
		btnAbandonJob.addActionListener((e) -> {
			if (taskFailedResponse.get() == null) {
				return;
			}

//			process.abandonJob();
//			Empty the queue!
		});
		GridBagConstraints gbc_btnAbandonJob = new GridBagConstraints();
		gbc_btnAbandonJob.insets = new Insets(0, 0, 5, 5);
		gbc_btnAbandonJob.gridx = 3;
		gbc_btnAbandonJob.gridy = 3;
		panelActivity.add(btnAbandonJob, gbc_btnAbandonJob);

		JButton btnRestart = new JButton("Restart");
		btnRestart.addActionListener(e -> {
			if (taskFailedResponse.get() == null) {
				return;
			}
//			process.restartTask()
		});
		GridBagConstraints gbc_btnRestart = new GridBagConstraints();
		gbc_btnRestart.insets = new Insets(0, 0, 5, 5);
		gbc_btnRestart.gridx = 4;
		gbc_btnRestart.gridy = 3;
		panelActivity.add(btnRestart, gbc_btnRestart);

		JSeparator separator_2 = new JSeparator();
		GridBagConstraints gbc_separator_2 = new GridBagConstraints();
		gbc_separator_2.fill = GridBagConstraints.HORIZONTAL;
		gbc_separator_2.gridwidth = 4;
		gbc_separator_2.insets = new Insets(0, 0, 5, 5);
		gbc_separator_2.gridx = 1;
		gbc_separator_2.gridy = 4;
		panelActivity.add(separator_2, gbc_separator_2);

		JLabel lblLog = new JLabel("Event log");
		GridBagConstraints gbc_lblLog = new GridBagConstraints();
		gbc_lblLog.insets = new Insets(0, 0, 5, 5);
		gbc_lblLog.gridx = 1;
		gbc_lblLog.gridy = 5;
		panelActivity.add(lblLog, gbc_lblLog);

		JScrollPane scrollPane_1 = new JScrollPane();
		GridBagConstraints gbc_scrollPane_1 = new GridBagConstraints();
		gbc_scrollPane_1.gridwidth = 4;
		gbc_scrollPane_1.insets = new Insets(0, 0, 5, 5);
		gbc_scrollPane_1.fill = GridBagConstraints.BOTH;
		gbc_scrollPane_1.gridx = 1;
		gbc_scrollPane_1.gridy = 6;
		panelActivity.add(scrollPane_1, gbc_scrollPane_1);

		JTextPane textPaneConsole = new JTextPane();
		textPaneConsole.setEditable(false);
		scrollPane_1.setViewportView(textPaneConsole);
		console = new MessageConsole(textPaneConsole);

		JPopupMenu popupMenu = new JPopupMenu();
		addPopup(textPaneConsole, popupMenu);

		JMenuItem mntmLogCopyAll = new JMenuItem("Copy all");
		mntmLogCopyAll.addActionListener(e -> {
			final var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(new StringSelection(textPaneConsole.getText()), null);
		});
		popupMenu.add(mntmLogCopyAll);

		JMenuItem mntmLogClear = new JMenuItem("Clear");
		mntmLogClear.addActionListener(e -> {
			textPaneConsole.setText(null);
		});
		popupMenu.add(mntmLogClear);
		console.redirectErr(Color.RED, null);
		console.redirectOut(Color.BLUE, null);

		Component verticalStrut_2 = Box.createVerticalStrut(20);
		GridBagConstraints gbc_verticalStrut_2 = new GridBagConstraints();
		gbc_verticalStrut_2.fill = GridBagConstraints.VERTICAL;
		gbc_verticalStrut_2.gridwidth = 4;
		gbc_verticalStrut_2.insets = new Insets(0, 0, 0, 5);
		gbc_verticalStrut_2.gridx = 1;
		gbc_verticalStrut_2.gridy = 7;
		panelActivity.add(verticalStrut_2, gbc_verticalStrut_2);

		mntmGenerate.addActionListener(e -> {
			if (clientUUID.get() != null) {
				uuidNotNull();
				return;
			}
			uuidReady.accept(newUUID.get());
			moveUItoConnectionStage();
			displayUUID();
		});

		mntmAuth.addActionListener(e -> {
			if (clientUUID.get() != null) {
				uuidNotNull();
				return;
			}
			singleDialogLock.lock();
			final var dialog = new EnterUUID(ClientAppFrame.this);
			dialog.setVisible(true);
			singleDialogLock.unlock();
			dialog.getUUID().ifPresent(value -> {
				uuidReady.accept(value);
				moveUItoConnectionStage();
			});
		});
	}

	private void moveUItoConnectionStage() {
		mntmGenerate.setEnabled(false);
		mntmDisplay.setEnabled(true);
		mntmAuth.setEnabled(false);
		mntmConnect.setEnabled(true);
	}

	private void uuidNotNull() {
		final var msg = "You already have a UUID, you can't generate a new one!";
		showMessage(ERROR_MESSAGE, "UUID already exists", msg);
	}

	private void displayUUID() {
		singleDialogLock.lock();
		final var dialog = new DisplayUUID(ClientAppFrame.this, clientUUID.get());
		dialog.setVisible(true);
		singleDialogLock.unlock();
	}

	public void setConnected() {
		lblConnectionStatus.setText("Online");
		lblConnectionStatus.setForeground(Color.GREEN);
	}

	public void setReconnectionFailed() {
		getContentPane().setEnabled(false);
		lblConnectionStatus.setText("Offline");
		lblConnectionStatus.setForeground(Color.RED);
	}

	public void setReconnecting() {
		class DrawTask extends TimerTask {
			int i;
			String text;

			DrawTask(String text, int iteration) {
				this.text = text;
				i = iteration;
			}

			@Override
			public void run() {
				lblConnectionStatus.setText(text);
				String nextText = String.format("%s.", text);
				if (i < 3) {
					timer.schedule(new DrawTask(nextText, i + 1), 400);
				}
			}

		}
		timer.schedule(new DrawTask("Reconnecting", 0), 0);
	}

	public void setReconnected(long ping) {
		updatePing(ping);
		lblConnectionStatus.setText("Online");
		lblConnectionStatus.setForeground(Color.GREEN);
	}

	public void updatePing(long ping) {
		lblPingValue.setText(String.valueOf(ping));
	}

	public void setConnectionLost() {
		lblPingValue.setText("-\u221E");
		lblConnectionStatus.setText("Reconnecting");
		lblConnectionStatus.setForeground(Color.ORANGE);
	}

	private void loadConfigHandler(ActionEvent e) {
		singleDialogLock.lock();
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setFileFilter(jsonFilter);
		try {
			int result = chooser.showOpenDialog(ClientAppFrame.this);
			if (result != JFileChooser.APPROVE_OPTION)
				return;
			File selectedFile = chooser.getSelectedFile();
			try {
				final var jd = JobDescriptor.parse(selectedFile);
				if (!verifyLoadedConfig(jd)) {
					return;
				}
				jobDescriptor = jd;
				populateNewJobFields();
				btnSubmit.setEnabled(true);
				btnVerifyConfig.setEnabled(false);
				btnClearConfig.setEnabled(true);
				inputsSetEnabled(false);
			} catch (FileNotFoundException e1) {
				showErrorToClient("File error", "Selected file could not be read");
			} catch (JobCreationException | JsonSyntaxException | JsonIOException e2) {
				showErrorToClient("File format error", "Bad JSON format.");
			} catch (IOException e3) {
				showErrorToClient("File error", "Unknown file error occured...");
			}
		} finally {
			singleDialogLock.unlock();
		}
	}

	private void saveConfigHandler(ActionEvent e) {
		singleDialogLock.lock();
		try {
			if (jobDescriptor == null) {
				final var jdo = verifyCustomConfig();
				if (jdo.isEmpty())
					return;
				jobDescriptor = jdo.get();
			}
			chooser.removeChoosableFileFilter(jsonFilter);
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int option = chooser.showDialog(ClientAppFrame.this, "Select output directory");
			if (option != JFileChooser.APPROVE_OPTION)
				return;

			String defaultName = jobDescriptor.getName();
			String timestampName = String.valueOf(new Date().getTime());
			String name = defaultName.isBlank() ? timestampName : defaultName;
			String result = (String) JOptionPane.showInputDialog(ClientAppFrame.this,
					"Enter file name", "Save", PLAIN_MESSAGE, null, null, name.concat(".json"));
			if (result == null) {
				return;
			}

			File location = new File(chooser.getSelectedFile(), result);
			if (location.exists()) {
				int yesno = showConfirmDialog(ClientAppFrame.this, "File already exist, overwrite?",
						"Save", YES_NO_OPTION);
				if (yesno != YES_OPTION) {
					return;
				}
			}

			if (!FileOperations.generate(location, jobDescriptor)) {
				showErrorToClient("ERROR", "Invalid pathname, try again");
				return;
			}
		} finally {
			singleDialogLock.unlock();
		}
		jobDescriptor = null;
		btnLoadConfig.setEnabled(true);
		btnVerifyConfig.setEnabled(true);
		btnSaveConfig.setEnabled(false);
		btnSubmit.setEnabled(false);
		clearNewJobFields();
	}

	private boolean verifyLoadedConfig(JobDescriptor jd) {
		if (!jd.isValidFormat()) {
			showMessage(ERROR_MESSAGE, "File format error", "Some required fileds are missing.");
			return false;
		}
		if (!jd.isValidJob()) {
			showMessage(ERROR_MESSAGE, "Bad JAR file", "JAR file does not exist or is corrupted");
			return false;
		}
		if (!jd.hasValidMainClass()) {
			showMessage(ERROR_MESSAGE, "Bad JAR file",
					"JAR file does not have requrested .class file");
			return false;
		}
		if (!jd.hasValidFiles()) {
			showMessage(ERROR_MESSAGE, "File exists error",
					"Some of the specified files do not exist.");
			return false;
		}
		return true;
	}

	private Optional<JobDescriptor> verifyCustomConfig() {
		final var jarName = txtJAR.getText();
		if (jarName.isBlank()) {
			showMessage(WARNING_MESSAGE, "Bad input", "JAR file path can't be empty");
			return Optional.empty();
		}
		File jarFile = new File(jarName);
		if (!jarFile.exists()) {
			showMessage(WARNING_MESSAGE, "Bad input", "JAR file with given path does not exist");
			return Optional.empty();
		}
		if (!JarValidator.isValidJar(jarFile)) {
			showMessage(WARNING_MESSAGE, "Bad input", "Provided JAR file is corrupted");
			return Optional.empty();
		}
		if (txtClassName.getText().isBlank()) {
			showMessage(WARNING_MESSAGE, "Bad input", "Class name can't be empty");
			return Optional.empty();
		}
		String className = txtClassName.getText();
		if (!JarValidator.hasClass(jarFile, className)) {
			showMessage(WARNING_MESSAGE, "Bad input",
					"Class does not exist in the provided JAR file");
			return Optional.empty();
		}
		for (int i = 0; i < 6; i++) {
			final var path = (String) tableInFiles.getValueAt(i / 2, i % 2);
			if (path == null || path.isBlank())
				continue;
			File file;
			if (!(file = new File(path)).exists()) {
				showMessage(WARNING_MESSAGE, "Bad input",
						String.format("Path does not exist: %s", file.getAbsoluteFile()));
				return Optional.empty();
			}
		}

		try {
			JobDescriptor jd = new JobDescriptor(txtJobName.getText(), jarFile, className,
					txtAreaArguments.getText(), getFileNames(tableInFiles),
					getFileNames(tableOutFiles));
			return Optional.of(jd);
		} catch (JobCreationException e) {
			return Optional.empty();
		}
	}

	private void promptGetConnectionInfo(ActionEvent e) {
		final var dialog = new GetConnectionInfo(this, connectionInfoReady, (info) -> {
			try {
				final var server = ConnectionProvider.connect(info, IPingable.class);
				return IPingable.getPing(server);
			} catch (ServerUnavailableException ex) {
				return Optional.empty();
			}
		}, () -> {
			connectReady.run();
			connected = true;
			mntmConnect.setEnabled(false);
		});
		dialog.setVisible(true);
		dialog.dispose();
	}

	private ArrayList<String> getFileNames(JTable table) {
		return IntStream.range(0, 6).mapToObj(i -> table.getValueAt(i / 2, i % 2))
				.map(String.class::cast).filter(Objects::nonNull)
				.filter(Predicate.not(String::isBlank))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	private void populateFileTable(JTable table, String[] fileNames) {
		for (int i = 0; i < fileNames.length; i++) {
			table.setValueAt(fileNames[i], i / 2, i % 2);
		}
	}

	private void clearNewJobFields() {
		{
			txtJobName.setText(null);
			txtJAR.setText(null);
			txtClassName.setText(null);
			txtAreaArguments.setText(null);
			for (int i = 0; i < 6; i++) {
				tableInFiles.setValueAt(null, i / 2, i % 2);
				tableOutFiles.setValueAt(null, i / 2, i % 2);
			}
			btnVerifyConfig.setEnabled(true);
		}
	}

	private void populateNewJobFields() {
		final var jd = jobDescriptor;
		txtJobName.setText(jd.getName());
		txtJAR.setText(jd.getJAR());
		txtClassName.setText(jd.getMainClassName());
		final var fileStream = Stream.of(jd.getArgs()).map(Object::toString);
		txtAreaArguments.setText(fileStream.collect(Collectors.joining(" ")));
		populateFileTable(tableInFiles, jd.getInFiles());
		populateFileTable(tableOutFiles, jd.getOutFiles());

	}

	private void inputsSetEnabled(boolean enabled) {
		txtJobName.setEditable(enabled);
		txtJAR.setEnabled(enabled);
		txtClassName.setEnabled(enabled);
		txtAreaArguments.setEnabled(enabled);
		tableInFiles.setEnabled(enabled);
		tableOutFiles.setEnabled(enabled);
	}

	private void showMessage(int type, String title, String text) {
		singleDialogLock.lock();
		showMessageDialog(ClientAppFrame.this, text, title, type);
		singleDialogLock.unlock();
	}

	public void showNotification(String title, String text) {
		showMessage(PLAIN_MESSAGE, title, text);
	}

	public void showErrorToClient(String title, String text) {
		singleDialogLock.lock();
		showMessage(ERROR_MESSAGE, title, text);
		singleDialogLock.unlock();
	}

	public boolean tempOnDesktop() {
		return tglBtnTemp.isSelected();
	}

	public void setFileSizeText(String text) {
		lblFileSizeValue.setText(text);
	}

	public void setProgressBar(int percent) {
		uploadProgressBar.setValue(percent);
	}

	public void setTransferedSize(String text) {
		lblSizeTransfered.setText(text);
	}

	public void promptTransferCompleteSucessfully() {
		singleDialogLock.lock();
		showMessage(PLAIN_MESSAGE, "Complete", "Job sent sucessfully, you are free to log out.");
		singleDialogLock.unlock();
		clearNewJobFields();
		lblFileSizeValue.setText("???");
		uploadProgressBar.setValue(0);
		lblSizeTransfered.setText("0B");
	}

	public void promptTransferFailed(String message) {
		singleDialogLock.lock();
		showMessage(WARNING_MESSAGE, "Upload failed", message);
		singleDialogLock.unlock();
		lblFileSizeValue.setText("???");
		uploadProgressBar.setValue(0);
		lblSizeTransfered.setText("0B");
	}

	private Lock treeLock = new ReentrantLock();
	private JLabel lblFailureCountValue;

	public void acceptTree(JobTreeNode node, CompletableFuture<Integer> future) {
		// TODO REMEMBER TO DECREMENT ELSEWHERE
		synchronized (lblFailureCountValue) {
			final var current = Integer.valueOf(lblFailureCountValue.getText().strip());
			lblFailureCountValue.setText(String.valueOf(current + 1));
		}
		class Pair {
			JobTreeNode dataNode;
			DefaultMutableTreeNode displayNode;

			Pair(JobTreeNode data, DefaultMutableTreeNode display) {
				this.dataNode = data;
				this.displayNode = display;
			}
		}

		taskFailedResponse.set(future);

		treeLock.lock();
		try {

			Deque<Pair> stack = new ArrayDeque<>();
			var root = new DefaultMutableTreeNode(node);
			stack.push(new Pair(node, root));

			while (!stack.isEmpty()) {
				var pair = stack.pop();

				var children = pair.dataNode.children;

				for (int i = children.length - 1; i >= 0; i--) {
					var child = children[i];
					var displayChild = new DefaultMutableTreeNode(child);
					pair.displayNode.add(displayChild);

					stack.push(new Pair(child, displayChild));
				}
			}
			jobTree.setModel(new DefaultTreeModel(root));
		} finally {
			treeLock.unlock();
		}
	}

	private static void addPopup(Component component, final JPopupMenu popup) {
		component.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				if (e.isPopupTrigger()) {
					showMenu(e);
				}
			}

			public void mouseReleased(MouseEvent e) {
				if (e.isPopupTrigger()) {
					showMenu(e);
				}
			}

			private void showMenu(MouseEvent e) {
				popup.show(e.getComponent(), e.getX(), e.getY());
			}
		});
	}
}
