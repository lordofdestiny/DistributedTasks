package rs.ac.bg.etf.kdp.gui.client;

import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static javax.swing.JOptionPane.YES_NO_OPTION;
import static javax.swing.JOptionPane.showConfirmDialog;
import static javax.swing.JOptionPane.showMessageDialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import rs.ac.bg.etf.kdp.utils.ConnectionInfo;
import rs.ac.bg.etf.kdp.utils.ConnectionListener;
import rs.ac.bg.etf.kdp.utils.JarVerificator;
import rs.ac.bg.etf.kdp.utils.JobRequestDescriptor;
import rs.ac.bg.etf.kdp.utils.JobRequestDescriptor.JobCreationException;

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
					ClientAppFrame window = new ClientAppFrame(UUID::randomUUID, () -> temp.id);
					window.setUUIDReadyListener((uuid) -> {
						temp.id = uuid;
					});

					window.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	private final Supplier<UUID> newUUID;
	private final Supplier<UUID> clientUUID;

	/**
	 * Create the application.
	 */
	public ClientAppFrame(Supplier<UUID> newUUID, Supplier<UUID> clientUUID) {
		Objects.requireNonNull(newUUID);
		Objects.requireNonNull(clientUUID);
		this.newUUID = newUUID;
		this.clientUUID = clientUUID;
		initialize();
	}

	private Consumer<UUID> uuidReady = (v) -> {
	};
	private Consumer<ConnectionInfo> connectionInfoReady = (v) -> {
	};
	private Runnable connectReady = () -> {
	};
	private Consumer<JobRequestDescriptor> jobReady = (job) -> {
	};

	public void setUUIDReadyListener(Consumer<UUID> listener) {
		uuidReady = Objects.requireNonNull(listener);
	}

	public void setConnectInfoReadyListener(Consumer<ConnectionInfo> listener) {
		connectionInfoReady = Objects.requireNonNull(listener);
	}

	public void setConnectListener(Runnable listener) {
		connectReady = Objects.requireNonNull(listener);
	}

	public void setJobDescriptorListener(Consumer<JobRequestDescriptor> listener) {
		jobReady = Objects.requireNonNull(listener);
	}

	private JMenuItem mntmShowPing;
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

	private boolean pingShown = false;
	private JobRequestDescriptor jobDescriptor = null;
	private Map<Component, Boolean> jobPanelHistory = null;

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		setTitle("Client");
		setBounds(100, 100, 500, 450);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

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

		mntmShowPing = new JMenuItem("Show ping");
		mntmShowPing.addActionListener((e) -> {
			if (pingShown) {
				panelPing.setVisible(false);
				mntmShowPing.setText("Show ping");
			} else {
				panelPing.setVisible(true);
				mntmShowPing.setText("Hide ping");
			}

		});
		mntmShowPing.setEnabled(false);
		mnConnection.add(mntmShowPing);

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
		gbl_panelPing.columnWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, Double.MIN_VALUE };
		gbl_panelPing.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
		panelPing.setLayout(gbl_panelPing);
		panelPing.setVisible(false);

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

		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		panelCenter.add(tabbedPane);

		jsonFilter = new FileNameExtensionFilter("JavaScript Object Notation", "json");
		chooser = new JFileChooser("Select configuration file");
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setCurrentDirectory(new File(System.getProperty("user.home")));

		panelNewJob = new JPanel();
		tabbedPane.addTab("Submit job", null, panelNewJob, null);
		GridBagLayout gbl_panelNewJob = new GridBagLayout();
		gbl_panelNewJob.columnWidths = new int[] { 0, 0, 0, 0, 0, 27, 0, 0, 0, 0, 0, 0 };
		gbl_panelNewJob.rowHeights = new int[] { 20, 0, 25, 25, 25, 0, 0, 25, 0, 25, 25, 0, 0, 0 };
		gbl_panelNewJob.columnWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0,
				Double.MIN_VALUE };
		gbl_panelNewJob.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0,
				Double.MIN_VALUE };
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
		gbc_horizontalStrut_8.gridheight = 7;
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
		GridBagConstraints gbc_lblNewLabel = new GridBagConstraints();
		gbc_lblNewLabel.anchor = GridBagConstraints.NORTH;
		gbc_lblNewLabel.gridwidth = 3;
		gbc_lblNewLabel.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel.gridx = 1;
		gbc_lblNewLabel.gridy = 5;
		panelNewJob.add(lblArguments, gbc_lblNewLabel);

		txtAreaArguments = new JTextArea();
		txtAreaArguments.setDisabledTextColor(Color.BLACK);
		txtAreaArguments
				.setBorder(new CompoundBorder(new LineBorder(new Color(171, 173, 179)), new EmptyBorder(5, 5, 5, 5)));
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

		btnSubmit = new JButton("Submit");
		btnSubmit.setEnabled(false);
		btnSubmit.addActionListener((e) -> {
			inputsSetEnabled(true);
			clearNewJobFields();
			jobReady.accept(jobDescriptor);
			jobDescriptor = null;
		});
		GridBagConstraints gbc_btnSubmit = new GridBagConstraints();
		gbc_btnSubmit.anchor = GridBagConstraints.SOUTH;
		gbc_btnSubmit.fill = GridBagConstraints.HORIZONTAL;
		gbc_btnSubmit.gridwidth = 4;
		gbc_btnSubmit.insets = new Insets(0, 0, 5, 5);
		gbc_btnSubmit.gridx = 6;
		gbc_btnSubmit.gridy = 6;
		panelNewJob.add(btnSubmit, gbc_btnSubmit);

		tglBtnTemp = new JToggleButton("Temp on Desktop");
		GridBagConstraints gbc_tglBtnTemp = new GridBagConstraints();
		gbc_tglBtnTemp.fill = GridBagConstraints.HORIZONTAL;
		gbc_tglBtnTemp.gridwidth = 4;
		gbc_tglBtnTemp.insets = new Insets(0, 0, 5, 5);
		gbc_tglBtnTemp.gridx = 6;
		gbc_tglBtnTemp.gridy = 7;
		panelNewJob.add(tglBtnTemp, gbc_tglBtnTemp);

		Component verticalStrut_1 = Box.createVerticalStrut(20);
		GridBagConstraints gbc_verticalStrut_1 = new GridBagConstraints();
		gbc_verticalStrut_1.insets = new Insets(0, 0, 5, 5);
		gbc_verticalStrut_1.gridx = 4;
		gbc_verticalStrut_1.gridy = 8;
		panelNewJob.add(verticalStrut_1, gbc_verticalStrut_1);

		JLabel lblNewLabel_1 = new JLabel("IN Files");
		GridBagConstraints gbc_lblNewLabel_1 = new GridBagConstraints();
		gbc_lblNewLabel_1.anchor = GridBagConstraints.WEST;
		gbc_lblNewLabel_1.gridwidth = 3;
		gbc_lblNewLabel_1.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel_1.gridx = 1;
		gbc_lblNewLabel_1.gridy = 9;
		panelNewJob.add(lblNewLabel_1, gbc_lblNewLabel_1);

		tableInFiles = new JTable();
		tableInFiles.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tableInFiles.setBorder(UIManager.getBorder("TextField.border"));
		tableInFiles.setFont(new Font("Tahoma", Font.PLAIN, 12));
		tableInFiles.setModel(new DefaultTableModel(new Object[][] { { null, null, null }, { null, null, null }, },
				new String[] { "New column", "New column", "New column" }));
		GridBagConstraints gbc_tableInFiles = new GridBagConstraints();
		gbc_tableInFiles.gridwidth = 6;
		gbc_tableInFiles.insets = new Insets(0, 0, 5, 5);
		gbc_tableInFiles.fill = GridBagConstraints.BOTH;
		gbc_tableInFiles.gridx = 4;
		gbc_tableInFiles.gridy = 9;
		panelNewJob.add(tableInFiles, gbc_tableInFiles);

		JLabel lblNewLabel_2 = new JLabel("OUT Files");
		GridBagConstraints gbc_lblNewLabel_2 = new GridBagConstraints();
		gbc_lblNewLabel_2.anchor = GridBagConstraints.WEST;
		gbc_lblNewLabel_2.gridwidth = 3;
		gbc_lblNewLabel_2.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel_2.gridx = 1;
		gbc_lblNewLabel_2.gridy = 10;
		panelNewJob.add(lblNewLabel_2, gbc_lblNewLabel_2);

		tableOutFiles = new JTable();
		tableOutFiles.setBorder(UIManager.getBorder("TextField.border"));
		tableOutFiles.setModel(new DefaultTableModel(new Object[][] { { null, null, null }, { null, null, null }, },
				new String[] { "New colqumn", "New column", "New column" }));
		tableOutFiles.setFont(new Font("Tahoma", Font.PLAIN, 12));
		GridBagConstraints gbc_tableOutFiles = new GridBagConstraints();
		gbc_tableOutFiles.gridwidth = 6;
		gbc_tableOutFiles.insets = new Insets(0, 0, 5, 5);
		gbc_tableOutFiles.fill = GridBagConstraints.BOTH;
		gbc_tableOutFiles.gridx = 4;
		gbc_tableOutFiles.gridy = 10;
		panelNewJob.add(tableOutFiles, gbc_tableOutFiles);

		Component verticalStrut = Box.createVerticalStrut(20);
		GridBagConstraints gbc_verticalStrut = new GridBagConstraints();
		gbc_verticalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_verticalStrut.gridx = 4;
		gbc_verticalStrut.gridy = 11;
		panelNewJob.add(verticalStrut, gbc_verticalStrut);

		Component verticalGlue = Box.createVerticalGlue();
		GridBagConstraints gbc_verticalGlue = new GridBagConstraints();
		gbc_verticalGlue.insets = new Insets(0, 0, 0, 5);
		gbc_verticalGlue.gridx = 4;
		gbc_verticalGlue.gridy = 12;
		panelNewJob.add(verticalGlue, gbc_verticalGlue);
		JPanel panel2 = new JPanel();
		tabbedPane.addTab("New tab", null, panel2, null);

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
			final var dialog = new EnterUUID(ClientAppFrame.this);
			dialog.setVisible(true);
			dialog.getUUID().ifPresent(value -> {
				uuidReady.accept(value);
				moveUItoConnectionStage();
			});
		});

		jobPanelHistory = setEnabled(panelNewJob, false);
	}

	private void moveUItoConnectionStage() {
		mntmGenerate.setEnabled(false);
		mntmDisplay.setEnabled(true);
		mntmAuth.setEnabled(false);
		mntmConnect.setEnabled(true);
	}

	private void uuidNotNull() {
		final var msg = "You already have a UUID, you can't generate a new one!";
		showMessageDialog(ClientAppFrame.this, msg, "UUID already exists", ERROR_MESSAGE);
	}

	private void displayUUID() {
		final var dialog = new DisplayUUID(ClientAppFrame.this, clientUUID.get());
		dialog.setVisible(true);
	}

	public final ConnectionListener connectionListener = new ConnectionListener() {
		@Override
		public void onConnected() {
			lblConnectionStatus.setText("Online");
			lblConnectionStatus.setForeground(Color.GREEN);
		}

		@Override
		public void onReconnectionFailed() {
			getContentPane().setEnabled(false);
			lblConnectionStatus.setText("Offline");
			lblConnectionStatus.setForeground(Color.RED);
		}

		@Override
		public void onReconnecting() {
			Timer timer = new Timer();
			Integer value = 0;
			timer.schedule(new TimerTask() {
				Integer v = value;

				@Override
				public void run() {
					String nextValue = String.format("Reconnecting%s", ".".repeat(v));
					lblConnectionStatus.setText(nextValue);
					if (++v > 3) {
						timer.cancel();
						timer.purge();
					}
				}
			}, 0, 400);
		}

		@Override
		public void onReconnected(long ping) {
			lblPingValue.setText(String.valueOf(ping));
			lblConnectionStatus.setText("Online");
			lblConnectionStatus.setForeground(Color.GREEN);
		}

		@Override
		public void onPingComplete(long ping) {
			lblPingValue.setText(String.valueOf(ping));
		}

		@Override
		public void onConnectionLost() {
			lblPingValue.setText("-\u221E");
			lblConnectionStatus.setText("Reconnecting");
			lblConnectionStatus.setForeground(Color.ORANGE);
		}
	};
	private JToggleButton tglBtnTemp;

	public ConnectionListener getConnectionListener() {
		return connectionListener;
	}

	private void loadConfigHandler(ActionEvent e) {
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setFileFilter(jsonFilter);
		int result = chooser.showOpenDialog(ClientAppFrame.this);
		if (result != JFileChooser.APPROVE_OPTION)
			return;
		File selectedFile = chooser.getSelectedFile();
		try {
			final var jd = JobRequestDescriptor.parse(selectedFile);
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
			showMessageDialog(ClientAppFrame.this, "Selected file could not be read", "File error", ERROR_MESSAGE);
		} catch (JobCreationException | JsonSyntaxException | JsonIOException e2) {
			showMessageDialog(ClientAppFrame.this, "Bad JSON format.", "File format error", ERROR_MESSAGE);
		}
	}

	private void saveConfigHandler(ActionEvent e) {
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
		String result = (String) JOptionPane.showInputDialog(ClientAppFrame.this, "Enter file name", "Save",
				JOptionPane.PLAIN_MESSAGE, null, null, name.concat(".json"));

		if (result == null) {
			return;
		}

		File location = new File(chooser.getSelectedFile(), result);
		if (location.exists()) {
			int yesno = showConfirmDialog(ClientAppFrame.this, "File already exist, overwrite?", "Save", YES_NO_OPTION);
			if (yesno != JOptionPane.YES_OPTION) {
				return;
			}
		}

		if (!JobRequestDescriptor.generate(location, jobDescriptor)) {
			showMessageDialog(ClientAppFrame.this, "Invalid pathname, try again", "ERROR", ERROR_MESSAGE);
			return;
		}
		jobDescriptor = null;
		btnLoadConfig.setEnabled(true);
		btnVerifyConfig.setEnabled(true);
		btnSaveConfig.setEnabled(false);
		btnSubmit.setEnabled(false);
		clearNewJobFields();
	}

	private boolean verifyLoadedConfig(JobRequestDescriptor jd) {
		if (!jd.isFormatValid()) {
			showMessage(ERROR_MESSAGE, "File format error", "Some required fileds are missing.");
			return false;
		}
		if (!jd.isValidJob()) {
			showMessage(ERROR_MESSAGE, "Bad JAR file", "JAR file does not exist or is corrupted");
			return false;
		}
		if (!jd.hasClass()) {
			showMessage(ERROR_MESSAGE, "Bad JAR file", "JAR file does not have requrested .class file");
			return false;
		}
		if (!jd.validInFiles()) {
			showMessage(ERROR_MESSAGE, "File exists error", "Some of the specified files do not exist.");
			return false;
		}
		return true;
	}

	private Optional<JobRequestDescriptor> verifyCustomConfig() {
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
		if (!JarVerificator.isValidJar(jarFile)) {
			showMessage(WARNING_MESSAGE, "Bad input", "Provided JAR file is corrupted");
			return Optional.empty();
		}
		if (txtClassName.getText().isBlank()) {
			showMessage(WARNING_MESSAGE, "Bad input", "Class name can't be empty");
			return Optional.empty();
		}
		String className = txtClassName.getText();
		if (!JarVerificator.hasClass(jarFile, className)) {
			showMessage(WARNING_MESSAGE, "Bad input", "Class does not exist in the provided JAR file");
			return Optional.empty();
		}
		for (int i = 0; i < 6; i++) {
			final var path = (String) tableInFiles.getValueAt(i / 3, i % 3);
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
			JobRequestDescriptor jd = new JobRequestDescriptor(txtJobName.getText(), jarFile, className,
					txtAreaArguments.getText(), getFileNames(tableInFiles), getFileNames(tableOutFiles));
			return Optional.of(jd);
		} catch (JobCreationException e) {
			return Optional.empty();
		}
	}

	private void promptGetConnectionInfo(ActionEvent e) {
		final var dialog = new GetConnectionInfo(this, connectionInfoReady, () -> {
			connectReady.run();
			mntmConnect.setEnabled(false);
			mntmShowPing.setEnabled(true);
		});
		dialog.setVisible(true);
		restoreStates(jobPanelHistory);
		jobPanelHistory = null;
		dialog.dispose();
	}

	private ArrayList<String> getFileNames(JTable table) {
		ArrayList<String> names = new ArrayList<>(6);
		for (int i = 0; i < 6; i++) {
			final var fileName = (String) table.getValueAt(i / 3, i % 3);
			names.add(i, fileName);
		}
		return names;
	}

	private void populateFileTable(JTable table, String[] fileNames) {
		for (int i = 0; i < fileNames.length; i++) {
			table.setValueAt(fileNames[i], i / 3, i % 3);
		}
	}

	private void clearNewJobFields() {
		{
			txtJobName.setText(null);
			txtJAR.setText(null);
			txtClassName.setText(null);
			txtAreaArguments.setText(null);
			for (int i = 0; i < 6; i++) {
				tableInFiles.setValueAt(null, i / 3, i % 3);
				tableOutFiles.setValueAt(null, i / 3, i % 3);
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
		populateFileTable(tableInFiles, jd.getFiles().getIn());
		populateFileTable(tableOutFiles, jd.getFiles().getOut());

	}

	private void inputsSetEnabled(boolean enabled) {
		txtJobName.setEditable(enabled);
		txtJAR.setEnabled(enabled);
		txtClassName.setEnabled(enabled);
		txtAreaArguments.setEnabled(enabled);
		tableInFiles.setEnabled(enabled);
		tableOutFiles.setEnabled(enabled);
	}

	private void restoreStates(Map<Component, Boolean> history) {
		if (history == null) {
			return;
		}
		for (final var entry : history.entrySet()) {
			entry.getKey().setEnabled(entry.getValue());
		}
	}

	private Map<Component, Boolean> setEnabled(Component component, boolean enabled) {
		Map<Component, Boolean> pastStates = new HashMap<>();
		pastStates.put(component, component.isEnabled());
		component.setEnabled(enabled);
		if (component instanceof Container) {
			for (final var child : ((Container) component).getComponents()) {
				pastStates.put(child, child.isEnabled());
				final var past = setEnabled(child, enabled);
				pastStates.putAll(past);
			}
		}
		return pastStates;
	}

	private void showMessage(int type, String title, String text) {
		showMessageDialog(ClientAppFrame.this, text, title, type);
	}

	public void showErrorToClient(String title, String text) {
		showMessage(ERROR_MESSAGE, title, text);
	}

	public boolean tempOnDesktop() {
		return tglBtnTemp.isSelected();
	}
}
