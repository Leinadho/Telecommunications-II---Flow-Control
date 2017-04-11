package cs.tcd.ie;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;

/**
 * 
 * @author Daniel Cosgrove
 * @student_number 15329669
 *
 */

public abstract class Node {
	static final int PACKETSIZE = 65536;
	boolean timeoutOn = false;
	
	DatagramSocket socket;
	Listener listener;
	CountDownLatch latch;
	
	Node() {
		latch= new CountDownLatch(1);
		listener= new Listener();
		listener.setDaemon(true);
		listener.start();
	}
	
	
	public abstract void onReceipt(DatagramPacket packet);
	/**
	 * Method used by a node child to turn timeout on
	 */
	public abstract void applyTimeoutSetting();
	/**
	 * called when timeout exception is thrown
	 */
	public abstract void onTimeout();
	/**
	 *
	 * Listener thread
	 * 
	 * Listens for incoming packets on a datagram socket and informs registered receivers about incoming packets.
	 */
	class Listener extends Thread {
		
		/*
		 *  Telling the listener that the socket has been initialized 
		 */
		public void go() {
			//applyTimeoutSetting();
			latch.countDown();
		}
		
		/*
		 * Listen for incoming packets and inform receivers
		 */
		public void run() {
			try {
				latch.await();
				// Endless loop: attempt to receive packet, notify receivers, etc
				while(true) {
					DatagramPacket packet = new DatagramPacket(new byte[PACKETSIZE], PACKETSIZE);
					/*if timeout is on, 
					 * exit socket.receive() intermittently to allow socket's timeout parameter to be changed
					 *
					 */
					if(!timeoutOn)
					{
						//IF TIMEOUT IS OFF, EXIT RECEIVE METHOD EVERY 0.5 SECONDS, 
						//TO ALLOW OTHER THREADS TO MODIFY THE SOCKET
						try{
							synchronized(this)
							{	
								socket.setSoTimeout(500);
								socket.receive(packet);
								socket.setSoTimeout(0);
							}
							onReceipt(packet);
						}catch(java.net.SocketTimeoutException t)
						{
							socket.setSoTimeout(0);
						}
							
					}
					else{
						//IF TIMEOUT IS ON, ENTER RECEIVE, BLOCK UNTIL PACKET IS RECEIVED OR TIMEOUT EXCEPTION IS THROWN
						try{
							synchronized(this)
							{	
								socket.receive(packet);
							}
							onReceipt(packet);
							
						}catch (java.net.SocketTimeoutException timeout) 
						{
							System.out.println("Timeout caught in node: "+timeout.getMessage());
							onTimeout(); 
						}	
					}
				}
			}
			catch (Exception e) 
			{
				if (!(e instanceof SocketException )){ e.printStackTrace();}
			}
			
		}
	}
}
