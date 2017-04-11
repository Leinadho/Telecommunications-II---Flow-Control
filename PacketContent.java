package cs.tcd.ie;

import java.net.DatagramPacket;

/**
 * 
 * @author Daniel Cosgrove
 * @student_number 15329669
 *
 */

public interface PacketContent {
	public String toString();
	public DatagramPacket toDatagramPacket();
}
