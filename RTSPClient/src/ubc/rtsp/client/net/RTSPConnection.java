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

package ubc.rtsp.client.net;

import java.io.*;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;

import ubc.rtsp.client.exception.RTSPException;
import ubc.rtsp.client.model.Frame;
import ubc.rtsp.client.model.Session;

import javax.xml.crypto.Data;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

/**
 * This class represents a connection with an RTSP server.
 */
public class RTSPConnection {

	private static final int BUFFER_LENGTH = 0x10000;
	private static final long MINIMUM_DELAY_READ_PACKETS_MS = 20;
	final static String CRLF = "\r\n";

	byte[] buf;  //buffer used to store data received from the server

	private Session session;
	private Timer rtpTimer;
	private InetAddress address;

	// RTP
	private static int RTP_RCV_PORT = 64838;
	private DatagramSocket rtpSocket; // UDP to receive data
	private DatagramPacket rcvdPacket;

	//RTSP variables
	private Socket streamSocket; // TCP, RTSP to send/receive commands
	final static int INIT = 0;
	final static int READY = 1;
	final static int PLAYING = 2;
	static int state = INIT; // one of INIT, READY, PLAYING
	private int CSeq;
	private String RTSPid; // ID of the RTSP session (given by the RTSP Server)

	// I/O
	static BufferedReader RTSPBufferedReader;
	static BufferedWriter RTSPBufferedWriter;
	static String VideoFileName; //video file to request to the server

	// TODO Add additional fields, if necessary
	
	/**
	 * Establishes a new connection with an RTSP server. No message is sent at
	 * this point, and no stream is set up.
	 * 
	 * @param session
	 *            The Session object to be used for connectivity with the UI.
	 * @param server
	 *            The hostname or IP address of the server.
	 * @param port
	 *            The TCP port number where the server is listening to.
	 * @throws RTSPException
	 *             If the connection couldn't be accepted, such as if the host
	 *             name or port number are invalid or there is no connectivity.
	 */
	public RTSPConnection(Session session, String server, int port)
			throws RTSPException {

		this.session = session;
		try {

			this.startRTPTimer();

			address = InetAddress.getByName(server);
			CSeq = 0; // initialize RTSP sequence number
			streamSocket = new Socket(address, port);
			RTSPBufferedReader = new BufferedReader(new InputStreamReader(streamSocket.getInputStream()));
			RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(streamSocket.getOutputStream()));

			state = INIT;

		} catch(Exception e) {
			String exception = "An RTSP connection could not be made to port: " + port;
			throw new RTSPException(exception);
		}
	}

	/**
	 * Sends a SETUP request to the server. This method is responsible for
	 * sending the SETUP request, receiving the response and retrieving the
	 * session identification to be used in future messages. It is also
	 * responsible for establishing an RTP datagram socket to be used for data
	 * transmission by the server. The datagram socket should be created with a
	 * random UDP port number, and the port number used in that connection has
	 * to be sent to the RTSP server for setup. This datagram socket should also
	 * be defined to timeout after 1 second if no packet is received.
	 * 
	 * @param videoName
	 *            The name of the video to be setup.
	 * @throws RTSPException
	 *             If there was an error sending or receiving the RTSP data, or
	 *             if the RTP socket could not be created, or if the server did
	 *             not return a successful response.
	 */
	public synchronized void setup(String videoName) throws RTSPException, IOException {
		VideoFileName = videoName;
		try {
			sendRequestHeader("SETUP");
			RTSPBufferedWriter.write("Transport: RTP/UDP; client_port= " + RTP_RCV_PORT);
			RTSPBufferedWriter.newLine();
			RTSPBufferedWriter.flush();
		} catch (Exception e) {
			String exception = "SETUP command could not be sent to the server";
			throw new RTSPException(exception);
		}
	}

	/**
	 * Sends a PLAY request to the server. This method is responsible for
	 * sending the request, receiving the response and, in case of a successful
	 * response, starting the RTP timer responsible for receiving RTP packets
	 * with frames.
	 * 
	 * @throws RTSPException
	 *             If there was an error sending or receiving the RTSP data, or
	 *             if the server did not return a successful response.
	 */
	public synchronized void play() throws RTSPException, IOException {
		try {
			sendRequestHeader("PLAY");
			RTSPBufferedWriter.write("Session: " + RTSPid);
			RTSPBufferedWriter.newLine();
			RTSPBufferedWriter.flush();
		} catch (Exception e) {
			String exception = "PLAY command could not be sent to the server";
			throw new RTSPException(exception);
		}

	}

	/**
	 * Starts a timer that reads RTP packets repeatedly. The timer will wait at
	 * least MINIMUM_DELAY_READ_PACKETS_MS after receiving a packet to read the
	 * next one.
	 */
	private void startRTPTimer() throws SocketException {

		rtpTimer = new Timer();
		rtpSocket = new DatagramSocket(RTP_RCV_PORT);
		rtpTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					receiveRTPPacket();
				} catch (IOException | RTSPException e) {
					e.printStackTrace();
				}
			}
		}, 0, MINIMUM_DELAY_READ_PACKETS_MS);
	}

	/**
	 * Receives a single RTP packet and processes the corresponding frame. The
	 * data received from the datagram socket is assumed to be no larger than
	 * BUFFER_LENGTH bytes. This data is then parsed into a Frame object (using
	 * the parseRTPPacket method) and the method session.processReceivedFrame is
	 * called with the resulting packet. In case of timeout no exception should
	 * be thrown and no frame should be processed.
	 */
	private void receiveRTPPacket() throws IOException, RTSPException {
		try {
			buf = new byte[BUFFER_LENGTH];
			rcvdPacket = new DatagramPacket(buf, BUFFER_LENGTH);
			rtpSocket.receive(rcvdPacket);
			Frame rtpPacket = parseRTPPacket(rcvdPacket.getData(), rcvdPacket.getLength());
			session.processReceivedFrame(rtpPacket);
		} catch (Exception e) {
			String exception = "No RTP packet in buffer.";
			//			throw new RTSPException(exception);
		}

	}

	/**
	 * Sends a PAUSE request to the server. This method is responsible for
	 * sending the request, receiving the response and, in case of a successful
	 * response, cancelling the RTP timer responsible for receiving RTP packets
	 * with frames.
	 * 
	 * @throws RTSPException
	 *             If there was an error sending or receiving the RTSP data, or
	 *             if the server did not return a successful response.
	 */
	public synchronized void pause() throws RTSPException {
		try {
			sendRequestHeader("PAUSE");
			RTSPBufferedWriter.write("Session: " + RTSPid);
			RTSPBufferedWriter.newLine();
			RTSPBufferedWriter.flush();
		} catch (Exception e) {
			String exception = "PAUSE command could not be sent to the server";
			throw new RTSPException(exception);
		}
	}

	/**
	 * Sends a TEARDOWN request to the server. This method is responsible for
	 * sending the request, receiving the response and, in case of a successful
	 * response, closing the RTP socket. This method does not close the RTSP
	 * connection, and a further SETUP in the same connection should be
	 * accepted. Also this method can be called both for a paused and for a
	 * playing stream, so the timer responsible for receiving RTP packets will
	 * also be cancelled.
	 * 
	 * @throws RTSPException
	 *             If there was an error sending or receiving the RTSP data, or
	 *             if the server did not return a successful response.
	 */
	public synchronized void teardown() throws RTSPException {
		try {
			sendRequestHeader("TEARDOWN");
			RTSPBufferedWriter.write("Session: " + RTSPid);
			RTSPBufferedWriter.newLine();
			RTSPBufferedWriter.flush();
		} catch (Exception e) {
			String exception = "TEARDOWN command could not be sent to the server";
			throw new RTSPException(exception);
		}
	}

	/**
	 * Closes the connection with the RTSP server. This method should also close
	 * any open resource associated to this connection, such as the RTP
	 * connection, if it is still open.
	 */
	public synchronized void closeConnection() {
		// TODO
	}

	/**
	 * Parses an RTP packet into a Frame object.
	 * 
	 * @param packet
	 *            the byte representation of a frame, corresponding to the RTP
	 *            packet.
	 * @return A Frame object.
	 */

	private static Frame parseRTPPacket(byte[] packet, int length) throws RTSPException {
		byte payloadType;
		boolean marker;
		short sequenceNumber;
		int timestamp;
		byte[] payload;
		int offset; // ???????? maybe ask TA
		int len;
		byte[] header;

		int mark;

		Frame frame;

		if (length >= 12) {
			// Get Header
			header = new byte[12];
			for (int i = 0; i < 12; i++) {
				header[i] = packet[i];
			}

			// Get Payload
			len = length - 12;
			payload = new byte[len];
			for (int i = 12; i < length; i++) {
				payload[i-12] = packet[i];
			}

			payloadType = (byte)(header[1] & 0x7F);
			sequenceNumber = (short)((header[3] & 0xFF) + ((header[2] & 0xFF) << 8));
			timestamp = (header[7] & 0xFF) + ((header[6] & 0xFF) << 8) + ((header[5] & 0xFF) << 16) + ((header[4] & 0xFF) << 24);
			offset = 0;
			mark = ((header[1] >> 7) & 0x01);

			if (mark == 1) {
				marker = true;
			} else {
				marker = false;
			}

			frame = new Frame(payloadType, marker, sequenceNumber, timestamp, payload, offset, len);
			return frame;
		} else {
			throw new RTSPException("Could not parse RTP packet.");
		}
	}

	private void sendRequestHeader(String request_type) throws RTSPException {
		try {
			//write the request line:
			RTSPBufferedWriter.write(request_type + " " + VideoFileName + " RTSP/1.0");
			RTSPBufferedWriter.newLine();
			//write the CSeq line:
			RTSPBufferedWriter.write("CSeq : " + CSeq);
			RTSPBufferedWriter.newLine();
			RTSPBufferedWriter.flush();
			CSeq ++;
		} catch(Exception ex) {
			String exception = "Could not send RTSP message with type: " + request_type;
			throw new RTSPException(exception);
		}
	}
}
