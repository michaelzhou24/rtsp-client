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
import java.text.DecimalFormat;
import java.util.*;

import ubc.rtsp.client.exception.RTSPException;
import ubc.rtsp.client.model.Frame;
import ubc.rtsp.client.model.Session;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * This class represents a connection with an RTSP server.
 */
public class RTSPConnection {

	private static final int BUFFER_LENGTH = 0x10000;
	private static final long MINIMUM_DELAY_READ_PACKETS_MS = 20;
	final static String CRLF = "\r\n";
	private static DecimalFormat df = new DecimalFormat("0.00");

	byte[] buf;  //buffer used to store data received from the server


	private Session session;
	private Timer rtpTimer;
	private Timer playbackTimer;
	private InetAddress address;

	private boolean isPlaying;

	// RTP
	private static int RTP_RCV_PORT = 25000;
	private DatagramSocket rtpSocket; // UDP to receive data
	private DatagramPacket rcvdPacket;
	private PriorityQueue<Frame> videoBuffer;
	private long currentTime;
	private long oldTime;
	private long playbackSpeed = 1000/24;
	private int playbackSeqNum;

	// RTSP variables
	private Socket streamSocket; // TCP, RTSP to send/receive commands
	private int cseq;
	private String rtspSessionId;

	// I/O
	private BufferedReader rtspReader;
	private BufferedWriter rtspWriter;
	private String videoName;

	// Playback stats:
	double lastPktReceivedTime;
	double startTime;
	double totalPlayTime;
	double frameRate;
	float outOfOrderProportion;
	float pktLossProportion;
	int totalOutOfOrder;
	int expectedSeq;
	int highestSeqReceived;
	int pktsReceived;
	boolean firstPacketReceived;


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
		this.isPlaying = false;
		try {
			address = InetAddress.getByName(server);
			cseq = 1; // initialize RTSP sequence number

			streamSocket = new Socket(address, port);
			rtpSocket = new DatagramSocket(RTP_RCV_PORT);

			rtpSocket.setReceiveBufferSize(1000000);

			rtspReader = new BufferedReader(new InputStreamReader(streamSocket.getInputStream()));
			rtspWriter = new BufferedWriter(new OutputStreamWriter(streamSocket.getOutputStream()));

			videoBuffer = new PriorityQueue<>(100, Comparator.comparingInt(Frame::getSequenceNumber));

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
	public synchronized void setup(String videoName) throws RTSPException {
		this.videoName = videoName;

		sendRequestHeader("SETUP");
		String request = "Transport: RTP/UDP; client_port= " + RTP_RCV_PORT + CRLF + CRLF;
		sendRequest(request);
	}

	private int sendRequest(String request) throws RTSPException {
		System.out.print("[CLIENT] " + request);
		try {
			rtspWriter.write(request);
			rtspWriter.flush();
		} catch (IOException e) {
			throw new RTSPException("Could not send to buffered writer.");
		}

		try {
			return readFromServer();
		} catch (IOException e) {
			throw new RTSPException("Could not read from buffered reader.");
		}
	}

	private int readFromServer() throws IOException {
		String value;
		int responseCode = -1;
		while((value = rtspReader.readLine()) != null) {
			if (value.isEmpty()) {
				break;
			}
			if (value.startsWith("Session: ")) {
				rtspSessionId = value.substring(9);
			}
			if (value.startsWith("RTSP")) {
				String[] split = value.split(" ");
				if (split.length > 1) {
					responseCode = Integer.parseInt(split[1]);
				}
			}
			System.out.println("[SERVER] " + value);
		}
		System.out.println();
		return responseCode;
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
	public synchronized void play() throws RTSPException {
		if (!this.isPlaying) {
			startTime = System.currentTimeMillis();
			this.isPlaying = true;
		}
		sendRequestHeader("PLAY");
		String request = "Session: " + rtspSessionId + CRLF + CRLF;
		if (sendRequest(request) == 200) {
			playbackSeqNum = 0;
			startRTPTimer();
			firstPacketReceived = false;
			startPlaybackTimer();
		}
	}

	/**
	 * Starts a timer that reads RTP packets repeatedly. The timer will wait at
	 * least MINIMUM_DELAY_READ_PACKETS_MS after receiving a packet to read the
	 * next one.
	 */
	private void startRTPTimer() {
		rtpTimer = new Timer();
		rtpTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					receiveRTPPacket();
				} catch (IOException | RTSPException | InterruptedException e) {
					e.printStackTrace();
				}
			}
		}, 0, MINIMUM_DELAY_READ_PACKETS_MS);
	}

	private void startPlaybackTimer() {
		playbackTimer = new Timer();
		playbackTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					if (!firstPacketReceived) {
						firstPacketReceived = true;
						Thread.sleep(3000);
					}

					if (!videoBuffer.isEmpty()) {
//						System.out.println("BUFFER SEQ: " + videoBuffer.peek().getSequenceNumber());
//						System.out.println("GLOBAL SEQ: " + playbackSeqNum);
						while (!videoBuffer.isEmpty() && videoBuffer.peek().getSequenceNumber() == playbackSeqNum) {
							session.processReceivedFrame(videoBuffer.poll());
						}
						playbackSeqNum++;
					} else {
						Thread.sleep(1000);
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}, 0, playbackSpeed);
	}

	/**
	 * Receives a single RTP packet and processes the corresponding frame. The
	 * data received from the datagram socket is assumed to be no larger than
	 * BUFFER_LENGTH bytes. This data is then parsed into a Frame object (using
	 * the parseRTPPacket method) and the method session.processReceivedFrame is
	 * called with the resulting packet. In case of timeout no exception should
	 * be thrown and no frame should be processed.
	 */
	private void receiveRTPPacket() throws IOException, RTSPException, InterruptedException {
		updateStatistics();

		try {
			buf = new byte[BUFFER_LENGTH];
			rcvdPacket = new DatagramPacket(buf, BUFFER_LENGTH);
			rtpSocket.receive(rcvdPacket);
			Frame rtpPacket = parseRTPPacket(rcvdPacket.getData(), rcvdPacket.getLength());

			videoBuffer.add(rtpPacket);

			if (firstPacketReceived == false) {
				Thread.sleep(500);
				firstPacketReceived = true;
				System.out.println("[INFO] Initial buffering done. Playing back video.");
			}

			lastPktReceivedTime = System.currentTimeMillis();
			int seq = rtpPacket.getSequenceNumber();
			if (seq > highestSeqReceived) {
				highestSeqReceived = seq;
			}
			if (expectedSeq != seq) {
				totalOutOfOrder++;
			}

//			System.out.printf("[INFO] Got packet with sequence number %d, expected %d, total frames received: %d\n", seq, expectedSeq, pktsReceived);
			pktsReceived++;
			expectedSeq++;
		} catch (Exception e) {
			// just try again.
			e.printStackTrace();
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
		sendRequestHeader("PAUSE");
		String request = "Session: " + rtspSessionId + CRLF + CRLF;
		if (sendRequest(request) == 200) {
			rtpTimer.cancel();
			playbackTimer.cancel();
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
		printStatistics();
		sendRequestHeader("TEARDOWN");
		String request = "Session: " + rtspSessionId + CRLF + CRLF;
		if (sendRequest(request) == 200) {
			this.isPlaying = false;
			highestSeqReceived = 0;
			totalOutOfOrder = 0;
			startTime = 0;
			expectedSeq = 0;
			totalPlayTime = 0;
			pktsReceived = 0;
			outOfOrderProportion = 0;
			rtpTimer.cancel();
			playbackTimer.cancel();
			firstPacketReceived = false;
			videoBuffer.clear();
			playbackSeqNum = 0;
		}
	}

	/**
	 * Closes the connection with the RTSP server. This method should also close
	 * any open resource associated to this connection, such as the RTP
	 * connection, if it is still open.
	 */
	public synchronized void closeConnection() {
		try {
			if (this.isPlaying) {
				this.teardown();
			}
			rtspReader.close();
			rtspWriter.close();
			streamSocket.close();
		} catch (Exception e) {
			System.out.println("Couldn't close RTSP connection.");
		}
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
		int offset; // offset?
		int len;
		byte[] header;

		int mark;

		if (length >= 12) {
			header = new byte[12];
			for (int i = 0; i < 12; i++) {
				header[i] = packet[i];
			}

			len = length - 12;
			payload = new byte[len];
			for (int i = 12; i < length; i++) {
				payload[i-12] = packet[i];
			}

			payloadType = (byte) (header[1] & 0x7F);
			sequenceNumber = (short) ((header[3] & 0xFF) + ((header[2] & 0xFF) << 8));
			timestamp = (header[7] & 0xFF) + ((header[6] & 0xFF) << 8) + ((header[5] & 0xFF) << 16) + ((header[4] & 0xFF) << 24);
			offset = 0;
			mark = ((header[1] >> 7) & 0x01);

			if (mark == 1) {
				marker = true;
			} else {
				marker = false;
			}

			return new Frame(payloadType, marker, sequenceNumber, timestamp, payload, offset, len);
		} else {
			throw new RTSPException("Could not parse RTP packet.");
		}
	}

	private void sendRequestHeader(String request_type) throws RTSPException {
		try {
			//write the request line:
			String request = request_type + " " + videoName + " RTSP/1.0" + CRLF;
			rtspWriter.write(request);
			System.out.print("[CLIENT] " + request);

			//write the CSeq line:
			request = "CSeq: " + cseq + CRLF;
			rtspWriter.write(request);
			System.out.print("[CLIENT] " + request);
			cseq++;
		} catch(Exception ex) {
			String exception = "Could not send RTSP message with type: " + request_type;
			throw new RTSPException(exception);
		}
	}

	private void updateStatistics() {
		totalPlayTime = lastPktReceivedTime - startTime;
		outOfOrderProportion = (float) totalOutOfOrder / highestSeqReceived;
		frameRate = totalPlayTime == 0 ? 0 : (pktsReceived / (totalPlayTime / 1000.0));
		pktLossProportion = (float)1 -((float) pktsReceived / (float) highestSeqReceived);
	}

	private void printStatistics() {
		updateStatistics();

		System.out.printf("[INFO] Packet Loss: %s\n", df.format(pktLossProportion));
		System.out.printf("[INFO] Packet Out of Order Rate: %s = %d/%d\n", df.format(outOfOrderProportion), totalOutOfOrder, highestSeqReceived);
		System.out.printf("[INFO] Frame Rate: %s\n\n", df.format(frameRate));
	}
}
