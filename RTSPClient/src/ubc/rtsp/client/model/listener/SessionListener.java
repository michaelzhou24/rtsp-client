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

package ubc.rtsp.client.model.listener;

import ubc.rtsp.client.exception.RTSPException;
import ubc.rtsp.client.model.Frame;

public interface SessionListener {

	public void exceptionThrown(RTSPException exception);

	public void frameReceived(Frame frame);

	public void videoNameChanged(String videoName);
}