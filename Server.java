package cs.tcd.ie;
import java.util.ArrayList;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import tcdIO.Terminal;

/**
 * 
 * @author Daniel Cosgrove
 * @student_number 15329669
 *
 */

public class Server extends Node {
	static final int DEFAULT_PORT = 50001;
	static final int CLIENT_ID_SIZE = 1;
	static final int ACK_LENGTH = 4;
	static final int TIMEOUT_PERIOD = 1000;
	
	//VARIABLES USED TO CAUSE TIMEOUT FOR TESTING
	static final int TIMEOUT_ON_NUMBER = 3;
	int loopCount;
	boolean causeTimeOut = false;
	
	//VARIABLES FOR COMMUNICATION BETWEEN THREADS
	boolean startedSending = false;
	boolean finishedSending = false;
	boolean timedOut;
	boolean lastPacketReceived;

	//RECEIVED PACKET VARIABLES
	DatagramPacket mostRecentPacket;
	byte nextID;
	ArrayList<Byte> queue = new ArrayList<Byte>();
	//public final byte TERMINATE_BYTE = -1;
	
	Terminal terminal;
	
	/*
	 * constructor
	 */
	Server(Terminal terminal, int port) {
		try {
			nextID = 0;
			this.terminal= terminal;
			socket= new DatagramSocket(port);
			listener.go();
			
		}
		catch(java.lang.Exception e) {e.printStackTrace();}
	}
	/**
	 * add each byte of the frame, except the ID, to the arrayList acting as the storage queue
	 * @param frame
	 */
	public void enqueueData(byte[] frame)
	{
		//place each received byte at the top of the queue
		for(int i = 0; i<frame.length; i++)
		{
			queue.add((Byte)frame[i]);
		}
	}
	/**
	 * returns the number of data bytes in a packet
	 * while packet byte arrays have size PACKETSIZE,
	 * all elements in the byte arrays may not be data
	 * this method returns the number of elements which are 
	 * actually part of the message
	 * @return
	 */
	public int getNoMessageBytesInPacket(DatagramPacket packet)
	{
		int count = 0;
		byte[] bytes = packet.getData();
		for(int i = 0; i < bytes.length ; i++)
		{
			//STOP COUNTING IF CURRENT BYTE IS THE ACK
			if(bytes[i] == 0 | bytes[i] == 1)
			{
				break;
			}
			count++;
		}
		return count;
	}
	/**
	 * Save message data in packet to queue
	 * @param packet
	 */
	public void saveData(DatagramPacket packet)
	{
		int dataBytesInPacket = getNoMessageBytesInPacket(packet);
		
		byte[] data = new byte[dataBytesInPacket];
		//COPY ALL MESSAGE BYTES BUT NOT ACK BYTES TO NEW ARRAY
		for(int i = 0; i < dataBytesInPacket ; i++)
		{
			data[i] = packet.getData()[i];
		}
		enqueueData(data);
	}
	/**
	 * Print the data received so far
	 * @param packet
	 */
	public void printReceived()
	{
		byte[] bytes = new byte[queue.size()];
		
		try{
			for(int i = 0; i< queue.size() ; i++)
			{
				bytes[i] = queue.get(i);
				//terminal.print(""+(char)(byte)queue.get(i));
			}
		}catch(java.lang.NullPointerException e)
		{
			terminal.println("caught nullpointer exception");
		}catch(IndexOutOfBoundsException e)
		{
			System.out.println("\ncaught out of bounds exception\n printing arrayList");
			for(int i = 0; i < queue.size() ; i++)
			{
				System.out.println(""+queue.get(i));
			}
			throw e;
		}
		terminal.println(new String(bytes));
	}
	/**
	 * return byte array of all data received and saved so far
	 * (ie: the bytes stored in the queue)
	 * @return
	 */
	public byte[] getReceived()
	{
		byte[] byteArray = new byte[queue.size()];
		for(int i = 0; i < queue.size() ; i++)
		{
			byteArray[i] = queue.get(i);
		}
		return byteArray;
	}
	/**
	 * returns the ID byte in a data array received from a DatagramPacket
	 * @param array
	 * @return
	 */
	public byte getID(byte[] array)
	{
		return array[array.length-1];
	}
	
	public byte getID(DatagramPacket packet)
	{
		return packet.getData()[packet.getLength()-1];
	}
	
	public void setID(int i)
	{
		if(i==1)
		{
			nextID = 1;
		}
		else if(i==0)
		{
			nextID = 0;
		}
	}
	/**
	 * increment nextID to the value of the expected ID of the next packet
	 */
	public void incrementID()
	{
		byte mask = 0b0000001;
		nextID = (byte)(nextID ^ mask);
	}
	/**
	 * called on timeout or incorrect packet, resends the previous acknowledgement
	 */
	public void resendACK()
	{
		try{
			//SEND ACKNOWLEDGEMENT
			//ACKNOWLEDGEMENT CONTAINS "ACK" PLUS THE EXPECTED ID BYTE
			terminal.println("\nsending ACK"+nextID);
			
			//response= (new StringContent("ACK"+nextID)).toDatagramPacket();
			byte[] ack = "ACK ".getBytes();
			ack[ACK_LENGTH-1] = nextID;
	
			DatagramPacket response;
			response = new DatagramPacket(ack, ack.length);
			response.setSocketAddress(mostRecentPacket.getSocketAddress());
			socket.send(response);
			
		}catch(IOException e){
			System.out.println(e.getMessage());
		}catch(NullPointerException e){
			System.out.println("server timedout with unintialised mostRecentPacket" + e.getMessage());
		}	
	}
	
	public void applyTimeoutSetting()
	{
		try{
			socket.setSoTimeout(TIMEOUT_PERIOD);
		}catch (Exception e) {if (!(e instanceof SocketException )){ e.printStackTrace();}}
	}
	
	public void setTimeoutOn()
	{
		try{
			synchronized(this)
			{
				socket.setSoTimeout(TIMEOUT_PERIOD);
			}
		}
		catch (Exception e) 
		{
			if (!(e instanceof SocketException )){ e.printStackTrace();}
		}
	};
	
	public void setTimeoutOff()
	{
		try{
			synchronized(this)
			{
				socket.setSoTimeout(0);
			}
		}
		catch (Exception e) 
		{
			if (!(e instanceof SocketException )){ e.printStackTrace();}
		}
	};
	
	/**
	 * called on timeout, resends ACK, updates loop variables to communicate with other threads
	 */
	public void onTimeout()
	{
		if(startedSending & !finishedSending){
			
			if(startedSending){
				terminal.println("Start of timeout:\nstartedSending: "+startedSending+"finishedSending: "+finishedSending);
			}
			
			//UPDATE TIMEOUT BOOLEAN TO NOTIFY OTHER METHODS
			timedOut=true;
			resendACK();
			
			if(startedSending & !finishedSending){terminal.println("\nTimeout occured");}
		}
		else if(finishedSending){
			try{
				//DISABLE TIMEOUT
				socket.setSoTimeout(0);
				}catch(Exception e){
					System.out.println(e.getMessage());}
		}
	
	}
	/**
	 * Debugging method: used to cause a timeout in client by not sending an ACK
	 */
	public void causeTimeout()
	{
		loopCount++;
		if(loopCount == TIMEOUT_ON_NUMBER){causeTimeOut = true;}
		else{causeTimeOut = false;}
	}
	/*
	public boolean packetIsTerminate(DatagramPacket packet)
	{
		terminal.print("\n inside packetIsTerminate");
		if(packet.getLength()==1 && packet.getData()[0] == TERMINATE_BYTE)
		{
			terminal.print("\nReturned true");
			return true;
		}
		terminal.print("\nReturned false");
		return false;
	}
	/**
	 * Check packet ID
	 * if as expected, send ACK
	 * else discard packet
	 */

	public void onReceipt(DatagramPacket packet) {
		setTimeoutOff();
		startedSending = true;
		/*
		if(startedSending && packetIsTerminate(packet)){
			terminal.println("\nin onReceipt(): finishedSending == true\n");
			finishedSending = true; 
		}
		else{
			*/
		
		try {
			if(causeTimeOut)
			{
				terminal.println("\nTrying to cause timeout now \nby not sending ACK...");
				causeTimeOut = false;
			}
			else{
				causeTimeout();
				mostRecentPacket = packet;	//save packet in case resend is required
				StringContent content= new StringContent(packet);
				
				//PRINT STATEMENTS FOR DEBUGGING
				terminal.println("\nReceived packet ");
				terminal.println(content.toString());
				terminal.println("\nonReceipt(): packet.getLength() =="+ packet.getLength());
				terminal.println("onReceipt(): packet.getData()[0] =="+ packet.getData()[0]);
				terminal.println("onReceipt(): packet.getData()[packet.getLength()] =="+ packet.getData()[packet.getLength()]);
				terminal.println("onReceipt(): getID(packet) =="+getID(packet));
				terminal.println("onReceipt(): nextID =="+ nextID);
					
				//VERIFY THAT PACKET HAS CORRECT ID
				//IF ID IS AS EXPECTED: SAVE THE PACKET DATA; INCREMENT nextID
				//ELSE; DISCARD THE PACKET; WAIT FOR NEXT PACKET
				if(getID(packet) == nextID)
				{
					//SAVE THE PACKET DATA
					saveData(packet);
					
					//INCREMENT THE EXPECTED ID
					incrementID();
				}
				else
				{
					//DISCARD THE PACKET, PRINT STATUS
					terminal.println("\nID MISMATCH:\nResponse ID is "+ getID(packet)+", unexpectedID");
					terminal.println("\nexpected "+nextID);
				}
				
				//SEND ACKNOWLEDGEMENT (EVERY RECEIVED FRAME IS ACKNOWLEDGED)
				//ACKNOWLEDGEMENT CONTAINS "ACK" PLUS THE EXPECTED ID BYTE
				terminal.println("\nsending ACK"+nextID);
				
				byte[] ack = "ACK ".getBytes();
				ack[ACK_LENGTH-1] = nextID;
							DatagramPacket response;
				response = new DatagramPacket(ack, ack.length);
				response.setSocketAddress(packet.getSocketAddress());
				socket.send(response);	
				
				//INDICATE END OF FUNCTION, PRINT THE DATA RECEIVED SO FAR AS A STRING
				terminal.println("\nFinished responding to receipt\n********************\n");
				terminal.println("data received so far: ");
				printReceived();
				
			}
		}
		catch(Exception e) {e.printStackTrace();}
		setTimeoutOn();		
	}

	public synchronized void start() throws Exception {
		socket.setSoTimeout(0);
		terminal.println("\nWaiting for contact");
		while(!finishedSending)
		{	
			this.wait(1000);
		}
	}
	
	
	/*
	 * 
	 */
	public static void main(String[] args) {
		try {			
			Terminal terminal= new Terminal("Server");
			(new Server(terminal, DEFAULT_PORT)).start();
			terminal.println("Program completed");
		} catch(java.lang.Exception e) {e.printStackTrace();}
	}
}
