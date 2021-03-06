/*
 * University of British Columbia
 * Department of Computer Science
 * CPSC417 - Computer Networking
 * Programming Assignment - RTSP Client
 * 
 * Author: Jonatan Schroeder
 * Created: January 2013
 * Updated: November 2020
 * 
 * This code may not be used without written consent of the authors, except for 
 * current and future projects and assignments of the CPSC417 course at UBC.
 */

package ubc.rtsp.client.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;

import ubc.rtsp.client.exception.RTSPException;

public class VideoControlToolbar extends JToolBar {

	private MainWindow main;
	private JButton openButton, playButton, pauseButton;
	private JButton closeButton;
	private JButton disconnectButton;

	public VideoControlToolbar(MainWindow mainWindow) {

		this.main = mainWindow;

		setFloatable(false);

		openButton = new JButton("Open");
		openButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					String videoName = JOptionPane
							.showInputDialog("Video file:");
					if (videoName != null)
						main.getSession().open(videoName);
				} catch (RTSPException | IOException ex) {
					JOptionPane.showMessageDialog(main, ex.getMessage());
				}
			}
		});
		this.add(openButton);

		this.addSeparator();

		playButton = new JButton("Play");
		playButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					main.getSession().play();
				} catch (RTSPException | IOException ex) {
					JOptionPane.showMessageDialog(main, ex.getMessage());
				}
			}
		});
		this.add(playButton);

		pauseButton = new JButton("Pause");
		pauseButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					main.getSession().pause();
				} catch (RTSPException ex) {
					JOptionPane.showMessageDialog(main, ex.getMessage());
				}
			}
		});
		this.add(pauseButton);

		this.addSeparator();

		closeButton = new JButton("Close");
		closeButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					main.getSession().close();
				} catch (RTSPException ex) {
					JOptionPane.showMessageDialog(main, ex.getMessage());
				}
			}
		});
		this.add(closeButton);

		this.addSeparator();

		disconnectButton = new JButton("Disconnect");
		disconnectButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				main.disconnect(true);
			}
		});
		this.add(disconnectButton);
	}
}
