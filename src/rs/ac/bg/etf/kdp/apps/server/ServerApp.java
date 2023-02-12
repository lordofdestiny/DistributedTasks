package rs.ac.bg.etf.kdp.apps.server;

import java.awt.EventQueue;

import javax.swing.JFrame;
import java.awt.GridBagLayout;
import javax.swing.JLabel;
import java.awt.GridBagConstraints;
import java.awt.Color;
import java.awt.Component;
import javax.swing.Box;
import java.awt.Insets;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.RemoteException;

import javax.swing.JButton;
import javax.swing.JTextField;

import rs.ac.bg.etf.kdp.core.server.ServerProcess;
import rs.ac.bg.etf.kdp.gui.client.MessageConsole;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JSeparator;
import javax.swing.JTextPane;
import javax.swing.JScrollPane;
import javax.swing.JEditorPane;

public class ServerApp {

	private JFrame frmServerApp;
	private JLabel lblIpValue;
	private JEditorPane editorPane;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					ServerApp window = new ServerApp();
					window.frmServerApp.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	ServerProcess process = null;
	MessageConsole console = null;

	public ServerApp() {

		initialize();

		lblIpValue = new JLabel("localhostp");
		GridBagConstraints gbc_lblIpValue = new GridBagConstraints();
		gbc_lblIpValue.insets = new Insets(0, 0, 5, 5);
		gbc_lblIpValue.anchor = GridBagConstraints.EAST;
		gbc_lblIpValue.gridwidth = 3;
		gbc_lblIpValue.gridx = 2;
		gbc_lblIpValue.gridy = 1;
		frmServerApp.getContentPane().add(lblIpValue, gbc_lblIpValue);

		JLabel lblServerIp = new JLabel("Server IP address:");
		GridBagConstraints gbc_lblServerIp = new GridBagConstraints();
		gbc_lblServerIp.insets = new Insets(0, 0, 5, 5);
		gbc_lblServerIp.gridx = 1;
		gbc_lblServerIp.gridy = 1;
		frmServerApp.getContentPane().add(lblServerIp, gbc_lblServerIp);
		try {
			lblIpValue.setText(InetAddress.getLocalHost().getHostAddress());

			Component horizontalGlue = Box.createHorizontalGlue();
			GridBagConstraints gbc_horizontalGlue = new GridBagConstraints();
			gbc_horizontalGlue.insets = new Insets(0, 0, 5, 5);
			gbc_horizontalGlue.gridx = 5;
			gbc_horizontalGlue.gridy = 1;
			frmServerApp.getContentPane().add(horizontalGlue, gbc_horizontalGlue);

			Component horizontalStrut = Box.createHorizontalStrut(20);
			GridBagConstraints gbc_horizontalStrut = new GridBagConstraints();
			gbc_horizontalStrut.insets = new Insets(0, 0, 5, 0);
			gbc_horizontalStrut.gridx = 13;
			gbc_horizontalStrut.gridy = 1;
			frmServerApp.getContentPane().add(horizontalStrut, gbc_horizontalStrut);

			JScrollPane scrollPane = new JScrollPane();
			GridBagConstraints gbc_scrollPane = new GridBagConstraints();
			gbc_scrollPane.gridwidth = 12;
			gbc_scrollPane.insets = new Insets(0, 0, 5, 5);
			gbc_scrollPane.fill = GridBagConstraints.BOTH;
			gbc_scrollPane.gridx = 1;
			gbc_scrollPane.gridy = 3;
			frmServerApp.getContentPane().add(scrollPane, gbc_scrollPane);

			editorPane = new JEditorPane();
			editorPane.setEditable(false);
			scrollPane.setViewportView(editorPane);

			Component verticalStrut = Box.createVerticalStrut(20);
			GridBagConstraints gbc_verticalStrut = new GridBagConstraints();
			gbc_verticalStrut.insets = new Insets(0, 0, 0, 5);
			gbc_verticalStrut.gridx = 5;
			gbc_verticalStrut.gridy = 4;
			frmServerApp.getContentPane().add(verticalStrut, gbc_verticalStrut);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			process = new ServerProcess();
			console = new MessageConsole(editorPane);
			console.redirectErr(Color.RED, null);
			console.redirectOut(Color.GREEN, null);
		} catch (RemoteException e) {
			System.exit(0);
		}
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmServerApp = new JFrame();
		frmServerApp.setTitle("Server app");
		frmServerApp.setBounds(100, 100, 568, 495);
		frmServerApp.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[] { 0, 0, 0, 0, 35, 24, 47, 0, 0, 0, 0, 0, 0, 0, 0 };
		gridBagLayout.rowHeights = new int[] { 0, 28, 19, 0, 0, 0 };
		gridBagLayout.columnWeights = new double[] { 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
				1.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
		gridBagLayout.rowWeights = new double[] { 0.0, 0.0, 0.0, 1.0, 0.0, Double.MIN_VALUE };
		frmServerApp.getContentPane().setLayout(gridBagLayout);

		Component verticalStrut = Box.createVerticalStrut(20);
		GridBagConstraints gbc_verticalStrut = new GridBagConstraints();
		gbc_verticalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_verticalStrut.gridx = 1;
		gbc_verticalStrut.gridy = 0;
		frmServerApp.getContentPane().add(verticalStrut, gbc_verticalStrut);

		Component horizontalStrut = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut = new GridBagConstraints();
		gbc_horizontalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut.gridx = 0;
		gbc_horizontalStrut.gridy = 1;
		frmServerApp.getContentPane().add(horizontalStrut, gbc_horizontalStrut);

	}

}
