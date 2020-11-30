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
import java.util.Timer;
import java.util.TimerTask;

import ubc.rtsp.client.exception.RTSPException;
import ubc.rtsp.client.model.Frame;
import ubc.rtsp.client.model.Session;

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

	private boolean isPlaying;

	// RTP
	private static int RTP_RCV_PORT = 25000;
	private DatagramSocket rtpSocket; // UDP to receive data
	private DatagramPacket rcvdPacket;

	// RTSP variables
	private Socket streamSocket; // TCP, RTSP to send/receive commands
	private int cseq;
	private String rtspSessionId;

	// I/O
	private BufferedReader rtspReader;
	private BufferedWriter rtspWriter;
	private String videoName;

	// Playback stats:
	double dataRate;        //Rate of video data received in bytes/s
	double lastPktReceivedTime;
	int statTotalBytes;         //Total number of bytes received in a session
	double statStartTime;       //Time in milliseconds when start is pressed
	double statTotalPlayTime;   //Time in milliseconds of video playing since beginning
	float statOutofOrder;     //Fraction of RTP data packets from sender lost since the prev packet was sent
	float statPacketLoss;
	int statCumLost;            //Number of packets lost
	int statExpRtpNb;           //Expected Sequence number of RTP messages within the session
	int statHighSeqNb;          //Highest sequence number received in session
	int statFramesRecvd;
	double statFrameRate;

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

			rtspReader = new BufferedReader(new InputStreamReader(streamSocket.getInputStream()));
			rtspWriter = new BufferedWriter(new OutputStreamWriter(streamSocket.getOutputStream()));

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
		System.out.println("Client: " + request);
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
			System.out.println("Server: " + value);
		}
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
			statStartTime = System.currentTimeMillis();
			this.isPlaying = true;
		}
		sendRequestHeader("PLAY");
		String request = "Session: " + rtspSessionId + CRLF + CRLF;
		if (sendRequest(request) == 200) {
			startRTPTimer();
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

			lastPktReceivedTime = System.currentTimeMillis();
			int seq = rtpPacket.getSequenceNumber();
			if (seq > statHighSeqNb) {
				statHighSeqNb = seq;
			}
			if (statExpRtpNb != seq) {
				statCumLost++;

			}
			System.out.printf("[INFO] Got packet with sequence number %d, expected %d.\n", seq, statExpRtpNb);
			statFramesRecvd++;
			statTotalBytes += rtpPacket.getPayloadLength();
			statExpRtpNb++;
			session.processReceivedFrame(rtpPacket);
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
			statHighSeqNb = 0;
			statCumLost = 0;
			statStartTime = 0;
			statExpRtpNb = 0;
			statTotalBytes = 0;
			statTotalPlayTime = 0;
			dataRate = 0;
			statOutofOrder = 0;
			rtpTimer.cancel();
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
			System.out.print("Client: " + request);

			//write the CSeq line:
			request = "CSeq: " + cseq + CRLF;
			rtspWriter.write(request);
			System.out.print("Client: " + request);
			cseq++;
		} catch(Exception ex) {
			String exception = "Could not send RTSP message with type: " + request_type;
			throw new RTSPException(exception);
		}
	}

	private void printStatistics() {
		statTotalPlayTime = lastPktReceivedTime - statStartTime;
		dataRate = statTotalPlayTime == 0 ? 0 : (statTotalBytes / (statTotalPlayTime / 1000.0));
		statOutofOrder = (float)statCumLost / statHighSeqNb;
		statFrameRate = statTotalPlayTime == 0 ? 0 : (statFramesRecvd / (statTotalPlayTime / 1000.0));
		statPacketLoss = 1- ((float)statFramesRecvd / statHighSeqNb);
		DecimalFormat formatter = new DecimalFormat("###,###.##");
		// TODO: packet loss rate
		System.out.println("[INFO] Packet Loss: " + formatter.format(statPacketLoss));
		System.out.println("[INFO] Packet Out of Order Rate: " + formatter.format(statOutofOrder) + " = " +statCumLost+"/"+statHighSeqNb);
		System.out.println("[INFO] Data Rate: " + formatter.format(dataRate) + " B/s");
		System.out.println("[INFO] Frame Rate: " + formatter.format(statFrameRate));
	}
}
