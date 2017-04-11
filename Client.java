/**
 * 
 */
package cs.tcd.ie;

/**
 * 
 * @author Daniel Cosgrove
 * @student_number 15329669
 *
 */

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketException;

import tcdIO.*;

/**
 *
 * Client class
 * 
 * An instance accepts user input 
 *
 */
public class Client extends Node {
	//CONSTANT DECLARATIONS
	static final int DEFAULT_SRC_PORT = 50000;
	static final int DEFAULT_DST_PORT = 50001;
	static final String DEFAULT_DST_NODE = "localhost";	
	
	static final int KILOBYTE = 1000;
	static final int KIBIBYTE = 1024;
	static final int DEFAULT_FRAME_SIZE = KILOBYTE/4;
	static final int TIMEOUT_PERIOD = 1000;	//minimum: 1000
	static final String DEFAULT_MESSAGE = "\nThe first step is the implementation of a Stop&Wait protocol. \nYour implementation should allow the Client to send a number of packets, one after the other, \nusing Stop&Wait to ensure that the receiver is able to handle the incoming packet. \n\nThe implementation will have to assign alternating numbers, 0 and 1, to packets\n and the acknowledgements by the Server will have to indicate the number of the packet the Server expects next.\n\nA good solution should include: \nan implementation of a timer at the sender and the receiver to indicate time-outs  \nand a retransmission mechanism of an already transmitted packet. \n\nThe setSoTimeout() method of the DatagramSocket class causes an exception to be thrown if a receive method has not returned within a given time.\n";
	//static final byte TERMINATE_BYTE = -1;
	
	//FIELD DECLARATIONS
	Terminal terminal;
	InetSocketAddress dstAddress;
	
	//LOOP STATUS VARIABLES (USED FOR COMMUNICATION BETWEEN THREADS)
	boolean waiting;
	boolean timedOut;
	boolean resend;
	boolean expectedACK;
	boolean unexpectedACK;
	
	boolean startedSending = false;
	boolean finishedSending = false;
	
	//PACKET VARIABLES
	byte currentID;
	int frameSize = DEFAULT_FRAME_SIZE;
	
	/**
	 * Constructor
	 * 	 
	 * Attempts to create socket at given port and create an InetSocketAddress for the destinations
	 */
	Client(Terminal terminal, String dstHost, int dstPort, int srcPort) {
		try {
			currentID = 0;
			
			this.terminal= terminal;
			dstAddress= new InetSocketAddress(dstHost, dstPort);
			socket= new DatagramSocket(srcPort);
			listener.go();
		}
		catch(java.lang.Exception e) {e.printStackTrace();}
	}
	
	//METHODS FOR USING CLIENT ID
	public void setID(int i)
	{
		if(i==1)
		{
			currentID = 1;
		}
		else if(i==0)
		{
			currentID = 0;
		}
	}
	
	public void incrementID()
	{
		if(currentID==0)
		{
			currentID=1;
		}
		else
		{
			currentID=0;
		}
	}

	public byte nextID(byte ID)
	{
		if(ID == 0)
		{
			return (byte)1;
		}
		return (byte)0;
	}
	
	//METHODS FOR PROCESSING AN ACKNOWLEDGEMENT
	public byte getAcknowledgeID(DatagramPacket packet)
	{
		return packet.getData()[packet.getLength()-1];
	}
	
	public byte getAcknowledgeID(byte[] array)
	{
		return array[array.length-1];
	}
	
	public boolean processResponse(byte ID, byte nextID)
	{
		if(ID == nextID)
		{
			return true;
		}
		return false;
	}
	
	public boolean processResponse(DatagramPacket packet, byte nextID)
	{
		if(getAcknowledgeID(packet)==nextID)
		{
			return true;
		}
		return false;
	}
	
	//METHODS FOR USING BYTE ARRAYS
	/**
	 * prints a byte array as a string of characters
	 * @param data
	 */
	public void printByteArray(byte[] data)
	{
		terminal.println("\nPrinting bytes in data:");
		for(byte character : data)
		{
			terminal.print(""+(char) character);
		}
		terminal.readString("\n\nPress enter to continue>");
	}
	/**
	 * returns a byte array representation of the input string
	 * each character in the string is one element(one byte) of the array
	 * @param message
	 * @return
	 */
	public byte[] stringToByteArray(String message)
	{
		return message.getBytes();
	}
	
	//METHODS FOR PACKETISING DATA
	/**
	 * set the number of bytes per frame
	 * @param bytes
	 */
	public void setFrameSize(int bytes)
	{
		if(bytes > PACKETSIZE-1)
		{
			frameSize = PACKETSIZE-1;
		}
		else if(bytes < 1)
		{
			frameSize = 1;
		}
		else
		{
			frameSize = bytes;
		}
	}
	/**
	 * returns an array of byte arrays, each byte array being a frame within the datum
	 * @param datum
	 * @param frameSize
	 * @return
	 */
	public byte[][] splitData(byte[] datum, int frameSize)
	{	
		byte[][] frames = new byte[datum.length/frameSize+1][frameSize];
		for(int index = 0; index < (int)(datum.length/frameSize) + 1; index++)
		{
			for(int j = 0; j < frameSize & (index*frameSize + j) < datum.length ; j++)
			{
				frames[index][j] = datum[index*frameSize  + j];
			}
		}
		return frames;
	}

	//TIMEOUT METHODS
	/**
	 * activates the timeout functionality. Timeout exception will be thrown by socket if packet not received in TIMEOUT_PERIOD
	 */
	public void applyTimeoutSetting()
	{
		try{
			synchronized(this){
				socket.setSoTimeout(TIMEOUT_PERIOD);
			}
		}catch (Exception e) {if (!(e instanceof SocketException )){ e.printStackTrace();}}
	}
	
	public void setTimeoutOn()
	{
		try {
			//wait until socket.receive() is exited to change timeout settings
			synchronized (this)
			{
				if(socket.getSoTimeout() == 0)	//if timeout functionality turned off
				{
					socket.setSoTimeout(TIMEOUT_PERIOD);
					timeoutOn = true;
				}
			}
		} catch (SocketException e) {
			System.out.println("socket exception in setTimeoutOn:" + e.getMessage());
		}
	}
	
	public void setTimeoutOff()
	{
		try {
			timeoutOn = false;
			synchronized(this)
			{
				socket.setSoTimeout(0);
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}
	/**
	 * this method provides the functionality required upon a timeout occurring
	 * 
	 */
	public void onTimeout()
	{
		if(startedSending & !finishedSending)
		{	
			//UPDATE GLOBAL LOOP BOOLEAN timedOut TO NOTIFY OTHER METHODS
			timedOut=true;
			if(waiting){
				synchronized(this){
					notify(); 
					}
				}
			
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
	
	//SENDING AND RECEIVING METHODS
	/**
	 * if unexpected ACK indicate to main method that packet should be resent
	 * if expected ACK indicate to main method that next packet should be sent
	 * 
	 */
	public void onReceipt(DatagramPacket packet) 
	{
		//DISABLE TIMEOUT
		setTimeoutOff();
		
		StringContent content= new StringContent(packet);
		terminal.println("\nonReceipt(): received packet with string content: "+ content.toString());
		/*
		CHECK IF THE ACK ID IS THE NEXT ID
		IF SO, 
			INCREMENT ID, NOTIFY MAIN THREAD THAT POSITIVE ACK RECEIVED
		ELSE, 
			NOTIFY MAIN THREAD THAT PACKET MUST BE RESENT
		*/
		if(!processResponse(packet, nextID(currentID)))
		{
			unexpectedACK = true;
			
			terminal.println("\n\nReceived unexpected ACK: " + content.toString());
			terminal.println("\nreceived ID == "+getAcknowledgeID(packet)+"\nnextID == "+nextID(currentID));
		}
		else //if ACK is as expected
		{
			expectedACK = true;
			incrementID();
			
			terminal.println("Received expected ACK: " + content.toString() + "\ncurrentID incremented to: "+currentID);
		}
		
		//NOTIFY MAIN THREAD THAT onReceipt IS COMPLETE
		terminal.println("\n\nonReceipt finished, \nnotifying...");
		synchronized(this)
		{
			notify();
		}
		terminal.println("\nonReceipt: notified main thread");
	}

	/*
	public void sendMonoByteFrames(byte[] datum, DatagramPacket packet) throws Exception{
		//FOR EACH PACKET, SEND PACKET TO SERVER
		//IF TIMEOUT OR UNEXPECTED ACK, RESEND PACKET
		for(int i = 0; i<datum.length ; i++)
		{
			terminal.println("\n***************\nFor loop cycle:"+i);
			
			//RESET RECEIPT STATUS VARIABLES
			timedOut = false;
			resend = false;
			expectedACK = false;
			unexpectedACK = false;
			
			//PREPARE BYTE ARRAY TO SEND AS PACKET
			byte[] frame = {datum[i] , currentID};
			frame[0] = datum[i];
			frame[1] = currentID;
			packet = new DatagramPacket(frame, frame.length,  dstAddress);
			
			//SEND PACKET TO SERVER VIA SOCKET
			socket.send(packet);
			StringContent content = new StringContent(packet);
			terminal.println("Sent Packet with string content :"+content.toString()+"\nand ID : "+currentID);
			
			terminal.println("\nMain thread: waiting for acknowledgement");
			//WAIT FOR ACK
			//WAIT STOPS WHEN 
			//	-onReceipt() METHOD NOTIFIES THAT IT IS COMPLETED
			//	or
			//	-timeout Occurs (to be implemented)
			waiting = true;
			setTimeoutOn();
			synchronized(this)
			{
				this.wait();
			}
			setTimeoutOff();
			waiting = false;
			terminal.println("\nMain thread: Wait Over");
			
			//WHILE LOOP PROVIDES A RESEND MECHANISM
			terminal.println("\nMain thread: while loop ");
			//positiveACK and negativeACK can only be modified by the onReceipt() method. 
			//if a positive ACK is received before timeout, while loop terminates and for loop moves on to sending next packet
			//if a negative ACK is received before timeout, if condition is true and packet is resent
			//if timeout occurs, if condition is true and packet is resent
			while(!expectedACK & !resend)	//while there is no response and no timeout, wait for response or timeout
			{
				//IF TIMEOUT, RESEND PACKET
				if(unexpectedACK | timedOut)
				{
					resend = true;
					i--;
				}
			}
			//IF NO TIMEOUT AND POSITIVEACK, SEND NEXT PACKET	
			terminal.println("\n\nMain thread: exited while loop\nresend =="+resend+"\nexpectedACK =="+expectedACK);
		}

	}
	*/
	/**
	 * provided with an array of bytes, sends the array to server in frames of size frameSize
	 * @param datum
	 * @param packet
	 * @param frameSize
	 * @throws Exception
	 */
	
	public void sendMultiByteFrames(byte[] datum, DatagramPacket packet, int frameSize) throws Exception{
		//BREAK UP DATA FOR PACKETIZING (data in packets must be in the form of byte array)
		byte[][] frames = splitData(datum, frameSize);

		//FOR EACH FRAME, SEND PACKET TO SERVER
		//	IF TIMEOUT OR UNEXPECTED ACK, RESEND PACKET
		for(int i = 0; i<frames.length ; i++)
		{
			terminal.println("\n***************\nFor loop cycle:"+i);
			
			//RESET RECEIPT STATUS VARIABLES
			timedOut = false;
			resend = false;
			expectedACK = false;
			unexpectedACK = false;
			
			byte[] frame;
			//PREPARE BYTE ARRAY TO SEND AS PACKET, 
			//COMBINE frames[i] ELEMENTS WITH ID BYTE
			/*
			if(i == frames.length)
			{
				frame = new byte[1];
				frame[0] = TERMINATE_BYTE;
			}
			else
			{
			*/	
			
			frame = new byte[frames[i].length + 1]; //frame = {elems of frames[i] + currentID};
			//COPY ELEMENTS OF FRAMES[i] TO FRAME
			for(int index = 0; index < frame.length -1 ; index++){ frame[index] = frames[i][index]; }
			frame[frame.length -1] = currentID;
			
			packet = new DatagramPacket(frame, frame.length, dstAddress);	
			
			
			
			//SEND PACKET TO SERVER VIA SOCKET
			socket.send(packet);
			StringContent content = new StringContent(packet);
			terminal.println("Sent Packet with string content :"+content.toString()+"\nand ID : "+currentID);
			
			
			//WAIT FOR ACK
			//WAIT STOPS WHEN 
			//	-onReceipt() METHOD NOTIFIES THAT IT IS COMPLETED
			//	or
			//	-timeout Occurs (to be implemented)
			waiting = true;
			setTimeoutOn();
			terminal.println("\nMain thread: waiting for acknowledgement");
			synchronized(this)
			{
				this.wait();
			}
			setTimeoutOff();
			waiting = false;
			terminal.println("\nMain thread: Wait Over");
			
			//WHILE LOOP PROVIDES A RESEND MECHANISM
			terminal.println("\nMain thread: while loop ");
			//positiveACK and negativeACK can only be modified by the onReceipt() method. 
			//if a positive ACK is received before timeout, while loop terminates and for loop moves on to sending next packet
			//if a negative ACK is received before timeout, if condition is true and packet is resent
			//if timeout occurs, if condition is true and packet is resent
			while(!expectedACK & !resend)	//while there is no response and no timeout, wait for response or timeout
			{
				//IF TIMEOUT, RESEND PACKET
				if(unexpectedACK | timedOut)
				{
					resend = true;
					i--;
				}
			}
			//IF NO TIMEOUT AND POSITIVEACK, SEND NEXT PACKET	
			terminal.println("\n\nMain thread: exited while loop\nresend =="+resend+"\nexpectedACK =="+expectedACK);
		
		}
	}
	/*
	public void sendTerminateByte()
	{
		
		byte[] terminateMessage = {TERMINATE_BYTE};
		DatagramPacket terminate = new DatagramPacket(terminateMessage, terminateMessage.length);
		try{
		socket.send(terminate);
		}catch(Exception e){System.out.println(e.getMessage());}
	}
	*/
	
	/**
	 * Sender Method
	 * 
	 */
	/**
	 * @throws Exception
	 */
	public synchronized void start() throws Exception 
	{
		//GET DATA TO SEND
		DatagramPacket packet= null;
		
		byte[] datum = stringToByteArray(DEFAULT_MESSAGE); //datum is an array of the character bytes of the string 
		terminal.println("Message to send: ");
		printByteArray(datum);
		
		//SPLIT DATUM INTO FRAMES (OF SIZE DEFAULT_FRAME_SIZE)
		//FOR EACH FRAME, 
		//	SEND PACKET TO SERVER
		//IF TIMEOUT OR UNEXPECTED ACK, 
		//	RESEND PACKET
		terminal.println("Sending packet...");
		startedSending = true;
		//sendMonoByteFrames(datum, packet);
		sendMultiByteFrames(datum, packet, DEFAULT_FRAME_SIZE);
		//sendTerminateByte();
		finishedSending = true;
	}


	/**
	 * Test method
	 * 
	 * Sends a packet to a given address
	 */

	public static void main(String[] args) {
		try {					
			Terminal terminal= new Terminal("Client");		
			(new Client(terminal, DEFAULT_DST_NODE, DEFAULT_DST_PORT, DEFAULT_SRC_PORT)).start();
			terminal.println("Program completed");
		} catch(java.lang.Exception e) {
			System.out.println("in main, outside other functions:");
			e.printStackTrace();}
	}
}
