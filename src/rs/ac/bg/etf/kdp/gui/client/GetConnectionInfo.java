package rs.ac.bg.etf.kdp.gui.client;

import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.INFORMATION_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import rs.ac.bg.etf.kdp.core.IPingable;
import rs.ac.bg.etf.kdp.core.ServerUnavailableException;
import rs.ac.bg.etf.kdp.utils.Configuration;
import rs.ac.bg.etf.kdp.utils.ConnectionInfo;
import rs.ac.bg.etf.kdp.utils.ConnectionInfo.ConnectionProvider;
import rs.ac.bg.etf.kdp.utils.ConnectionMonitor;

public class GetConnectionInfo extends JDialog {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private final String ipRegex = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$";

	private JTextField txtIP;
	private JTextField txtPort;
	private JButton btnConfirm;
	private JButton btnVerify;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					GetConnectionInfo dialog = new GetConnectionInfo(null, (info) -> {
					}, () -> {
					});
					dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
					dialog.setVisible(true);
					dialog.dispose();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	ActionListener invokeBtnVerify = null;

	private Consumer<ConnectionInfo> infoReady = null;

	/**
	 * Create the dialog.
	 */
	public GetConnectionInfo(JFrame owner, Consumer<ConnectionInfo> infoReady, Runnable connected) {
		super(owner);
		Objects.requireNonNull(infoReady);
		Objects.requireNonNull(connected);
		this.infoReady = infoReady;
		setResizable(false);
		setModal(true);
		setTitle("Connection parameters");
		setBounds(100, 100, 400, 200);
		setLocationRelativeTo(owner);
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[] { 0, 95, 141, 95, 30, 0 };
		gridBagLayout.rowHeights = new int[] { 0, 20, 20, 0, 0, 0, 0 };
		gridBagLayout.columnWeights = new double[] { 0.0, 0.0, 1.0, 0.0, 1.0, Double.MIN_VALUE };
		gridBagLayout.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
		getContentPane().setLayout(gridBagLayout);
		getRootPane().registerKeyboardAction(e -> {
			GetConnectionInfo.this.dispose();
		}, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

		Component verticalStrut = Box.createVerticalStrut(20);
		GridBagConstraints gbc_verticalStrut = new GridBagConstraints();
		gbc_verticalStrut.gridwidth = 2;
		gbc_verticalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_verticalStrut.gridx = 2;
		gbc_verticalStrut.gridy = 0;
		getContentPane().add(verticalStrut, gbc_verticalStrut);

		Component horizontalStrut = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut = new GridBagConstraints();
		gbc_horizontalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut.gridx = 0;
		gbc_horizontalStrut.gridy = 1;
		getContentPane().add(horizontalStrut, gbc_horizontalStrut);

		JLabel lblp = new JLabel("Server IP");
		GridBagConstraints gbc_lblp = new GridBagConstraints();
		gbc_lblp.insets = new Insets(0, 0, 5, 5);
		gbc_lblp.anchor = GridBagConstraints.WEST;
		gbc_lblp.gridx = 1;
		gbc_lblp.gridy = 1;
		getContentPane().add(lblp, gbc_lblp);

		txtIP = new JTextField();
		lblp.setLabelFor(txtIP);
		txtIP.setHorizontalAlignment(SwingConstants.RIGHT);
		GridBagConstraints gbc_txtIP = new GridBagConstraints();
		gbc_txtIP.gridwidth = 2;
		gbc_txtIP.insets = new Insets(0, 0, 5, 5);
		gbc_txtIP.fill = GridBagConstraints.HORIZONTAL;
		gbc_txtIP.gridx = 2;
		gbc_txtIP.gridy = 1;
		getContentPane().add(txtIP, gbc_txtIP);
		txtIP.setColumns(10);
		txtIP.setText("localhost");

		Component horizontalStrut_1 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_1 = new GridBagConstraints();
		gbc_horizontalStrut_1.insets = new Insets(0, 0, 5, 0);
		gbc_horizontalStrut_1.gridx = 4;
		gbc_horizontalStrut_1.gridy = 1;
		getContentPane().add(horizontalStrut_1, gbc_horizontalStrut_1);

		JLabel lblPort = new JLabel("Port");
		GridBagConstraints gbc_lblPort = new GridBagConstraints();
		gbc_lblPort.anchor = GridBagConstraints.WEST;
		gbc_lblPort.insets = new Insets(0, 0, 5, 5);
		gbc_lblPort.gridx = 1;
		gbc_lblPort.gridy = 2;
		getContentPane().add(lblPort, gbc_lblPort);

		txtPort = new JTextField();
		lblPort.setLabelFor(txtPort);
		txtPort.setHorizontalAlignment(SwingConstants.RIGHT);
		GridBagConstraints gbc_txtPort = new GridBagConstraints();
		gbc_txtPort.gridwidth = 2;
		gbc_txtPort.insets = new Insets(0, 0, 5, 5);
		gbc_txtPort.fill = GridBagConstraints.HORIZONTAL;
		gbc_txtPort.gridx = 2;
		gbc_txtPort.gridy = 2;
		getContentPane().add(txtPort, gbc_txtPort);
		txtPort.setColumns(10);
		txtPort.setText(String.valueOf(Configuration.SERVER_PORT));

		Component horizontalStrut_2 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_2 = new GridBagConstraints();
		gbc_horizontalStrut_2.insets = new Insets(0, 0, 5, 0);
		gbc_horizontalStrut_2.gridx = 4;
		gbc_horizontalStrut_2.gridy = 2;
		getContentPane().add(horizontalStrut_2, gbc_horizontalStrut_2);

		btnVerify = new JButton("Verify");
		btnVerify.addActionListener(this::verifyAction);
		GridBagConstraints gbc_btnVerify = new GridBagConstraints();
		gbc_btnVerify.fill = GridBagConstraints.HORIZONTAL;
		gbc_btnVerify.insets = new Insets(0, 0, 5, 5);
		gbc_btnVerify.gridx = 3;
		gbc_btnVerify.gridy = 3;
		getContentPane().add(btnVerify, gbc_btnVerify);

		btnConfirm = new JButton("Confirm");
		btnConfirm.setEnabled(false);
		btnConfirm.addActionListener(e -> {
			if (connected != null) {
				connected.run();
			}
			dispose();
		});
		GridBagConstraints gbc_btnConfirm = new GridBagConstraints();
		gbc_btnConfirm.fill = GridBagConstraints.HORIZONTAL;
		gbc_btnConfirm.insets = new Insets(0, 0, 0, 5);
		gbc_btnConfirm.gridx = 2;
		gbc_btnConfirm.gridy = 5;
		getContentPane().add(btnConfirm, gbc_btnConfirm);
	}

	private static boolean isNumeric(String str) {
		NumberFormat nf = NumberFormat.getInstance();
		ParsePosition pos = new ParsePosition(0);
		nf.parse(str, pos);
		return str.length() == pos.getIndex();
	}

	private static boolean isValidPort(String str) {
		try {
			Short.valueOf(str);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static String getConnectionMessage(ConnectionInfo info, long ping) {
		StringBuilder sb = new StringBuilder(100);
		sb.append("Server available at ");
		sb.append(info.getIp());
		sb.append(':');
		sb.append(info.getPort());
		sb.append(".\nPing: ");
		sb.append(ping);
		sb.append(" ms");
		return sb.toString();
	}

	private void verifyAction(ActionEvent e) {
		final var ip = txtIP.getText();
		final var portText = txtPort.getText();
		if (ip.isBlank()) {
			showMessageDialog(GetConnectionInfo.this, "Please provide server IP address!", "No IP", ERROR_MESSAGE);
			return;
		}
		if (portText.isBlank()) {
			showMessageDialog(GetConnectionInfo.this, "Please provide server port!", "No Port", ERROR_MESSAGE);
			return;
		}
		if (!ip.matches(ipRegex) && !ip.equals("localhost")) {
			showMessageDialog(GetConnectionInfo.this, "Please provide a valid IP address!", "No Invalid",
					ERROR_MESSAGE);
			return;
		}
		if (!isNumeric(portText) || !isValidPort(portText)) {
			showMessageDialog(GetConnectionInfo.this, "Please provide a valid port!", "No Invalid", ERROR_MESSAGE);
			return;
		}
		try {
			int port = Integer.valueOf(txtPort.getText());
			final var info = new ConnectionInfo(ip, port);
			final var server = ConnectionProvider.connect(info, IPingable.class)
					.orElseThrow(ServerUnavailableException::new);

			final var ping = ConnectionMonitor.getPing(server).orElseThrow(ServerUnavailableException::new);

			showMessageDialog(GetConnectionInfo.this, getConnectionMessage(info, ping), "Connected!",
					INFORMATION_MESSAGE);

			infoReady.accept(info);

			btnConfirm.setEnabled(true);
			btnVerify.setEnabled(false);
			txtIP.setEnabled(false);
			txtPort.setEnabled(false);
			btnVerify.getInputMap().remove(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
			btnVerify.getActionMap().remove("ENTER");
		} catch (ServerUnavailableException e1) {
			showMessageDialog(GetConnectionInfo.this, "Failed to connect to the server!", "No connection",
					ERROR_MESSAGE);
		}
	}
}
