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

package ubc.rtsp.client.exception;

public class RTSPException extends Exception {

	public RTSPException(String message) {
		super(message);
	}

	public RTSPException(Throwable cause) {
		super(cause);
	}

	public RTSPException(String message, Throwable cause) {
		super(message, cause);
	}
}
