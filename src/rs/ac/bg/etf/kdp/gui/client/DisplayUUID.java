package rs.ac.bg.etf.kdp.gui.client;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.UUID;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

public class DisplayUUID extends JDialog {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final JPanel contentPanel = new JPanel();
	private JTextField txtUUID;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		try {
			DisplayUUID dialog = new DisplayUUID(null, UUID.randomUUID());
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setVisible(true);
			dialog.dispose();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create the dialog.
	 */
	public DisplayUUID(JFrame owner, UUID uuid) {
		super(owner, true);
		setTitle("UUID Display");
		setBounds(100, 100, 300, 170);
		setLocationRelativeTo(owner);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		getContentPane().setLayout(new BorderLayout());
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		GridBagLayout gbl_contentPanel = new GridBagLayout();
		gbl_contentPanel.columnWidths = new int[] { 0, 0, 0, 0, 0, 0 };
		gbl_contentPanel.rowHeights = new int[] { 22, 0, 0, 0 };
		gbl_contentPanel.columnWeights = new double[] { 0.0, 0.0, 1.0, 0.0, 0.0, Double.MIN_VALUE };
		gbl_contentPanel.rowWeights = new double[] { 0.0, 0.0, 0.0, Double.MIN_VALUE };
		contentPanel.setLayout(gbl_contentPanel);
		getRootPane().registerKeyboardAction(e -> {
			DisplayUUID.this.dispose();
		}, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
			
		{
			Component horizontalStrut = Box.createHorizontalStrut(20);
			GridBagConstraints gbc_horizontalStrut = new GridBagConstraints();
			gbc_horizontalStrut.insets = new Insets(0, 0, 5, 5);
			gbc_horizontalStrut.gridx = 2;
			gbc_horizontalStrut.gridy = 0;
			contentPanel.add(horizontalStrut, gbc_horizontalStrut);
		}
		{
			JLabel lblUUID = new JLabel("Your Current UUID");
			GridBagConstraints gbc_lblUUID = new GridBagConstraints();
			gbc_lblUUID.insets = new Insets(0, 0, 5, 5);
			gbc_lblUUID.gridx = 2;
			gbc_lblUUID.gridy = 1;
			contentPanel.add(lblUUID, gbc_lblUUID);
		}
		{
			txtUUID = new JTextField();
			txtUUID.setHorizontalAlignment(SwingConstants.CENTER);
			txtUUID.setEditable(false);
			GridBagConstraints gbc_txtUUID = new GridBagConstraints();
			gbc_txtUUID.gridwidth = 3;
			gbc_txtUUID.insets = new Insets(0, 0, 0, 5);
			gbc_txtUUID.fill = GridBagConstraints.HORIZONTAL;
			gbc_txtUUID.gridx = 1;
			gbc_txtUUID.gridy = 2;
			contentPanel.add(txtUUID, gbc_txtUUID);
			txtUUID.setColumns(10);
			txtUUID.setText(uuid.toString());
		}
		{
			JPanel buttonPane = new JPanel();
			buttonPane.setLayout(new FlowLayout(FlowLayout.CENTER));
			getContentPane().add(buttonPane, BorderLayout.SOUTH);
			{
				JButton btnCopy = new JButton("Copy");
				btnCopy.addActionListener(e -> {
					final var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					clipboard.setContents(new StringSelection(uuid.toString()), null);
				});
				buttonPane.add(btnCopy);
				getRootPane().setDefaultButton(btnCopy);
			}
			{
				JButton btnClose = new JButton("Close");
				btnClose.addActionListener(e -> {
					dispose();
				});
				buttonPane.add(btnClose);
			}
		}
	}

}
