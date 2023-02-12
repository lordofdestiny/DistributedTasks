package rs.ac.bg.etf.kdp.apps.worker;

import java.awt.EventQueue;

import javax.swing.JFrame;
import java.awt.GridBagLayout;
import javax.swing.JLabel;
import java.awt.GridBagConstraints;
import javax.swing.JTextField;
import java.awt.Insets;
import java.awt.Color;
import java.awt.Component;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.SwingConstants;

import rs.ac.bg.etf.kdp.core.worker.WorkerProcess;
import rs.ac.bg.etf.kdp.gui.client.MessageConsole;
import rs.ac.bg.etf.kdp.utils.ConnectionInfo;

import javax.swing.JScrollPane;
import javax.swing.JEditorPane;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class WorkerApp {

	private JFrame frame;
	private JTextField txtIP;
	private JTextField txtPort;
	private JButton btnLogin;
	private WorkerProcess process = null;
	private MessageConsole console = null;
	private JEditorPane editorPane;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					WorkerApp window = new WorkerApp();
					window.frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public WorkerApp() {
		initialize();

		console = new MessageConsole(editorPane);
		console.redirectOut(Color.BLUE, null);
		console.redirectErr(Color.RED, null);
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frame = new JFrame();
		frame.setBounds(100, 100, 568, 508);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[] { 0, 0, 0, 68, 0, 0, 0, 0, 0, 25, 0 };
		gridBagLayout.rowHeights = new int[] { 0, 0, 0, 0, 0, 0 };
		gridBagLayout.columnWeights = new double[] { 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
				0.0, Double.MIN_VALUE };
		gridBagLayout.rowWeights = new double[] { 0.0, 0.0, 0.0, 1.0, 0.0, Double.MIN_VALUE };
		frame.getContentPane().setLayout(gridBagLayout);

		Component verticalStrut = Box.createVerticalStrut(20);
		GridBagConstraints gbc_verticalStrut = new GridBagConstraints();
		gbc_verticalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_verticalStrut.gridx = 3;
		gbc_verticalStrut.gridy = 0;
		frame.getContentPane().add(verticalStrut, gbc_verticalStrut);

		Component horizontalStrut_1 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_1 = new GridBagConstraints();
		gbc_horizontalStrut_1.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut_1.gridx = 0;
		gbc_horizontalStrut_1.gridy = 1;
		frame.getContentPane().add(horizontalStrut_1, gbc_horizontalStrut_1);

		JLabel lblNewLabel = new JLabel("IP");
		GridBagConstraints gbc_lblNewLabel = new GridBagConstraints();
		gbc_lblNewLabel.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel.gridx = 1;
		gbc_lblNewLabel.gridy = 1;
		frame.getContentPane().add(lblNewLabel, gbc_lblNewLabel);

		txtIP = new JTextField();
		GridBagConstraints gbc_txtIP = new GridBagConstraints();
		gbc_txtIP.fill = GridBagConstraints.HORIZONTAL;
		gbc_txtIP.insets = new Insets(0, 0, 5, 5);
		gbc_txtIP.gridx = 2;
		gbc_txtIP.gridy = 1;
		frame.getContentPane().add(txtIP, gbc_txtIP);
		txtIP.setColumns(10);

		Component horizontalStrut = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut = new GridBagConstraints();
		gbc_horizontalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut.gridx = 4;
		gbc_horizontalStrut.gridy = 1;
		frame.getContentPane().add(horizontalStrut, gbc_horizontalStrut);

		JLabel lblNewLabel_1 = new JLabel("Port");
		GridBagConstraints gbc_lblNewLabel_1 = new GridBagConstraints();
		gbc_lblNewLabel_1.anchor = GridBagConstraints.EAST;
		gbc_lblNewLabel_1.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel_1.gridx = 5;
		gbc_lblNewLabel_1.gridy = 1;
		frame.getContentPane().add(lblNewLabel_1, gbc_lblNewLabel_1);

		txtPort = new JTextField();
		txtPort.setHorizontalAlignment(SwingConstants.RIGHT);
		txtPort.setText("8080");
		GridBagConstraints gbc_txtPort = new GridBagConstraints();
		gbc_txtPort.insets = new Insets(0, 0, 5, 5);
		gbc_txtPort.anchor = GridBagConstraints.NORTHEAST;
		gbc_txtPort.gridx = 6;
		gbc_txtPort.gridy = 1;
		frame.getContentPane().add(txtPort, gbc_txtPort);
		txtPort.setColumns(10);

		Component horizontalStrut_2 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_2 = new GridBagConstraints();
		gbc_horizontalStrut_2.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut_2.gridx = 7;
		gbc_horizontalStrut_2.gridy = 1;
		frame.getContentPane().add(horizontalStrut_2, gbc_horizontalStrut_2);

		btnLogin = new JButton("Connect");
		btnLogin.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (process != null) {
					return;
				}
				String ip = txtIP.getText().strip();
				int port = Integer.valueOf(txtPort.getText().strip());
				process = new WorkerProcess(new ConnectionInfo(ip, port));
				if (!process.connectToServer()) {
					process = null;
				}
			}
		});
		GridBagConstraints gbc_btnLogin = new GridBagConstraints();
		gbc_btnLogin.insets = new Insets(0, 0, 5, 5);
		gbc_btnLogin.gridx = 8;
		gbc_btnLogin.gridy = 1;
		frame.getContentPane().add(btnLogin, gbc_btnLogin);

		Component horizontalGlue = Box.createHorizontalGlue();
		GridBagConstraints gbc_horizontalGlue = new GridBagConstraints();
		gbc_horizontalGlue.insets = new Insets(0, 0, 5, 0);
		gbc_horizontalGlue.gridx = 9;
		gbc_horizontalGlue.gridy = 1;
		frame.getContentPane().add(horizontalGlue, gbc_horizontalGlue);

		JScrollPane scrollPane = new JScrollPane();
		GridBagConstraints gbc_scrollPane = new GridBagConstraints();
		gbc_scrollPane.gridwidth = 8;
		gbc_scrollPane.insets = new Insets(0, 0, 5, 5);
		gbc_scrollPane.fill = GridBagConstraints.BOTH;
		gbc_scrollPane.gridx = 1;
		gbc_scrollPane.gridy = 3;
		frame.getContentPane().add(scrollPane, gbc_scrollPane);

		editorPane = new JEditorPane();
		scrollPane.setViewportView(editorPane);

		Component horizontalStrut_3 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_3 = new GridBagConstraints();
		gbc_horizontalStrut_3.insets = new Insets(0, 0, 5, 0);
		gbc_horizontalStrut_3.gridx = 9;
		gbc_horizontalStrut_3.gridy = 3;
		frame.getContentPane().add(horizontalStrut_3, gbc_horizontalStrut_3);

		Component verticalStrut_1 = Box.createVerticalStrut(20);
		GridBagConstraints gbc_verticalStrut_1 = new GridBagConstraints();
		gbc_verticalStrut_1.insets = new Insets(0, 0, 0, 5);
		gbc_verticalStrut_1.gridx = 2;
		gbc_verticalStrut_1.gridy = 4;
		frame.getContentPane().add(verticalStrut_1, gbc_verticalStrut_1);

		Component verticalStrut_2 = Box.createVerticalStrut(20);
		GridBagConstraints gbc_verticalStrut_2 = new GridBagConstraints();
		gbc_verticalStrut_2.insets = new Insets(0, 0, 0, 5);
		gbc_verticalStrut_2.gridx = 3;
		gbc_verticalStrut_2.gridy = 4;
		frame.getContentPane().add(verticalStrut_2, gbc_verticalStrut_2);
	}

}
