/**
 * Client thread, to send msg
 */
package Client;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PipedInputStream;
import java.io.RandomAccessFile;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;

import Helper.inputLog;
import P2pFileSystem.peerProcess;
public class Client1 {
	//target ip address
	String address;
	//target listening port num
	int port;
	//target peer id
	int peerId;
	//host peer id
	int hostPeerId;
	//socket msg buffer
	byte[] msgBuff;
	PipedInputStream tPi = new PipedInputStream();
	public Client1(String address, int port, int peerId, int hostPeerId) {
		this.address = address;
		this.port = port;
		this.peerId = peerId;
		this.hostPeerId = hostPeerId;
	}
	public void startClient() {
		ClientThread cl = new ClientThread();
		cl.start();
	}
	class ClientThread extends Thread{
		Socket requestSocket;           //socket connect to the server
		DataOutputStream out;         //stream write to the socket
	 	ObjectInputStream in;          //stream read from the socket
		final String  header = "P2PFILESHARINGPROJ";
		//create handshake msg byte array
		private byte[] createHandshakeMsg(int peerId) {
			byte[] handshakeMsg = new byte[32];
			byte[] zeroBit = new byte[10];
			byte[] peerIdByte = new byte[4];
			byte[] headerByte = header.getBytes();
			//create 10 bit zero-bit
			for(int i = 0; i < zeroBit.length; i++) {
				zeroBit[i] = 0x00;
			}
			peerIdByte = peerProcess.intToByte(peerId);
			//create handshake msg
			for(int i = 0; i < 32; i++) {
				if(i < 18) {
					handshakeMsg[i] = headerByte[i];
				} else if(i >= 18 && i < 28) {
					handshakeMsg[i] = zeroBit[i - 18];
				} else {
					handshakeMsg[i] = peerIdByte[i - 28];
				}			
			}
			return handshakeMsg;
		}
		//create actual msg
		private byte[] createActualMsg(byte[] payload, int type) {			
			//length = 1 byte type + x bytes payload
			int length;
			if(payload == null) {
				length = 1;
			} else {
				length = payload.length + 1;
			}		
			byte[] actualMsg = new byte[length + 4];
			byte[] msgLength = peerProcess.intToByte(length);
			byte[] msgType = new byte[1];
			msgType[0] = (byte) (type & 0xFF);
			for(int i = 0; i < actualMsg.length; i++) {
				if(i < 4) {
					actualMsg[i] = msgLength[i];
				} else if(i >= 4 && i < 5) {
					actualMsg[i] = msgType[i - 4];
				} else {
					actualMsg[i] = payload[i - 5];
				}
			}
			return actualMsg;
		}
		//create have or request payload
		private byte[] createHaveorRequestPayload(int index) {
			return peerProcess.intToByte(index);
		}
		//send piece msg
		private boolean sendPieceMsg(DataOutputStream out, int index) throws IOException {
			int pieceSize = 0;
			//check if it is the last piece
			if(index == peerProcess.pieceSum - 1) {
				pieceSize = peerProcess.lastPieceSize;
				out.write(peerProcess.intToByte(peerProcess.lastPieceSize + 1));
			} else {
				pieceSize = peerProcess.pieceSize;
				out.write(peerProcess.intToByte(peerProcess.pieceSize + 1));
			}			
			out.write((byte)(7&0xFF));
			out.write(peerProcess.intToByte(index));
			RandomAccessFile raf = new RandomAccessFile(peerProcess.fileName, "r");
			byte[] buf = new byte[1];
			long lastPoint = (index) * peerProcess.pieceSize;
			int i = 0;
			while(i < pieceSize) {
				raf.seek(lastPoint);
				raf.read(buf);
				out.write(buf);
				lastPoint++;
				i++;			
			}
			out.flush();
			raf.close();
			return true;		
		}
		//create bitfield payload
		private byte[] createBitfieldPayload() {
			int bitfieldSize = 0;
			if(peerProcess.hostBitfield.size() % 8 != 0) {
				bitfieldSize = peerProcess.hostBitfield.size() / 8 + 1;
			} else {
				bitfieldSize = peerProcess.hostBitfield.size() / 8;
			}
			byte[] bitfPayload = new byte[bitfieldSize];
			int isum = 0, jsum = 0;
			int hostBitfieldNum = -1;
			int j = 7;
			if(!peerProcess.hostBitfield.contains(1)) {
				return null;
			} else {
				//convert from byte to bit
				for(int i = 1; i <= bitfPayload.length; i++) {					
					/*if(peerProcess.hostBitfield.get(i - 1) == 1) {
						bSet.set(i-1);
					}*/
					isum = 8 * (i - 1);
					for(j = 7; j >= 0; j--) {
						jsum = (7 - j);
						if(isum + jsum > (peerProcess.hostBitfield.size() - 1)) {
							bitfPayload[i - 1] = (byte) (bitfPayload[i - 1] | ((bitfPayload[i - 1] << j) & 0));
						} else {
							if((hostBitfieldNum = peerProcess.hostBitfield.get(isum + jsum)) == 2) {
								hostBitfieldNum = 0;
							}
							System.out.println(isum + jsum + ":" + peerProcess.hostBitfield.get(isum + jsum));
							bitfPayload[i - 1] =  (byte) (bitfPayload[i - 1] | (hostBitfieldNum << j ));
						}
					}
				}
				//itfPayload = bSet.toByteArray();
				System.out.println("bitfpayload: "+peerProcess.byteToInt(bitfPayload)+"bitfPayloadlength: " + bitfPayload.length);
			}
			//System.out.println("bitfPayload: " + bitfPayload.length);
			return bitfPayload;
		}
		@Override
		public void run() {
			try{
				byte[] bitfieldPayload;
				//create a socket to connect to the server
				requestSocket = new Socket(address, port);
				System.out.println("Connected to "+ address +" in port: " + port);
				//initialize inputStream and outputStream					
				out = new DataOutputStream(requestSocket.getOutputStream());
				//out.flush();
				in = new ObjectInputStream(requestSocket.getInputStream());
				//init(send handshake and bitfield if needed)
				//send handshake msg
				msgBuff = createHandshakeMsg(hostPeerId);
				out.write(msgBuff);	
		        out.flush();
		        inputLog.writeLog("Peer " + hostPeerId + " makes a connection to Peer " + peerId + ".");
		        //send bitfield(return null indicate the host has no piece)
		        if((bitfieldPayload = createBitfieldPayload()) != null) {
		        	System.out.println("Client: needs to send bitfield");
		        	msgBuff = createActualMsg(bitfieldPayload, 5);
		        	out.write(msgBuff);	
		        	out.flush(); 
		        }	
		        //sendPieceMsg(out, 0);
		        //init complete enter while loop
		        while(true) {
		        	byte[] length = new byte[4];
		        	int lengthInt = 0;
		        	byte[] bys = null;
		        	try {
						//first read length
						tPi.read(length);
						lengthInt = peerProcess.byteToInt(length);
						bys = new byte[lengthInt];
						//System.out.println("lengthaf: "+lengthInt);
						tPi.read(bys);
						
					} catch (IOException e) {
						e.printStackTrace();
					}
		        	String pipeMsg;
		        	pipeMsg = new String(bys).trim();
					System.out.println("Client: msg received: " + pipeMsg);
					String[] array = pipeMsg.split("\\s+");
					switch(Integer.parseInt(array[2])) {
					//choke
					case 0:{						
						msgBuff = createActualMsg(null, 0);
						out.write(msgBuff);	
			        	out.flush(); 
					}
					break;
					//unchoke
					case 1:{
						msgBuff = createActualMsg(null, 1);
						out.write(msgBuff);	
			        	out.flush(); 
					}
					break;
					//interested
					case 2:{
						msgBuff = createActualMsg(null, 2);
						out.write(msgBuff);	
			        	out.flush(); 
					}
					break;
					//not interested
					case 3:{
						msgBuff = createActualMsg(null, 3);
						out.write(msgBuff);	
			        	out.flush(); 
					}
					break;
					//have
					case 4:{
						msgBuff = createActualMsg(createHaveorRequestPayload(Integer.parseInt(array[3])), 4);
						out.write(msgBuff);	
			        	out.flush(); 
					}
					break;
					//request
					case 6:{	
						byte[] readFail = new byte[4];
		        		msgBuff = createActualMsg(createHaveorRequestPayload(Integer.parseInt(array[3])), 6);
						out.write(msgBuff);	
			        	out.flush(); 
			        	/*if(in.available() > 0) {
			        		in.read(readFail); 
			        		System.out.println("choked");	
			        		peerProcess.hostBitfield.set(Integer.parseInt(array[3]), 0);
			        	}*/
			        	System.out.println("request piece: " + array[3]);
					}
					break;
					//piece
					case 7:{
						sendPieceMsg(out, Integer.parseInt(array[3]));
					}
					break;
					default:break;
					}
					
		        }
			}
			catch (ConnectException e) {
	    			System.err.println("Connection refused. You need to initiate a server first.");
			} 
			catch(UnknownHostException unknownHost){
				System.err.println("You are trying to connect to an unknown host!");
			}
			catch(IOException ioException){
				//ioException.printStackTrace();
			}
			finally{
				//Close connections
				try{
					in.close();
					out.flush();
					out.close();
					ClientManager.clientObject.remove(peerId);
					ClientManager.clientPipe.remove(peerId);
					tPi.close();
					requestSocket.close();
					return;
				}
				catch(IOException ioException){
					//ioException.printStackTrace();
				}
			}
		}
	}
}

