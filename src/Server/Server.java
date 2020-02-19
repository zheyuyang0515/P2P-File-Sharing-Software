/**
 * server process, receive and handle msg
 */
package Server;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PipedOutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

import Helper.inputLog;
import P2pFileSystem.peerProcess;

public class Server {
	//test port number
	int portNum;
	//0 not complete, 1 complete, 2 processed
	public static int completionStatus = 0;
	//default first peerId
	//final int defaultPeerId = 10001;
	private ArrayList<String> ipList = new ArrayList<String>();
	private ArrayList<Integer> portList = new ArrayList<Integer>();
	final int handshakeMsgLen = 32;
	final int minActualMsgLen = 5;
	private int serverPeerId;
	public static int downloadedPieceNum = 0;
	private final String handshakeHeaderDef = "P2PFILESHARINGPROJ";
	//server socket list
	//public static ArrayList<Integer> serverList = new ArrayList<Integer>();
	//stored piece downloaded in one timeinterval
	public static HashMap<Integer, Integer> speedReg = new HashMap<Integer, Integer>();
	//peers status which connect to the host
	public static HashMap<Integer, Boolean> chokeStatus = new HashMap<Integer, Boolean>();
	public static HashMap<Integer, Boolean> interestStatus = new HashMap<Integer, Boolean>();
	//host status to other peers
	public static HashMap<Integer, Boolean> hostChokeStatus = new HashMap<Integer, Boolean>();
	public static HashMap<Integer, Boolean> hostInterestedList = new HashMap<Integer, Boolean>();
	//host status
	public static ArrayList<Integer> preferedList = new ArrayList<Integer>();
	public static int optimisticNeighbor = -1;
	//constructor
	public Server(int portNum, int serverPeerId) {
		this.portNum = portNum;
		this.serverPeerId = serverPeerId;
	}
	//pipe
	PipedOutputStream sPo = null;

	public PipedOutputStream getPipedOutputStream() {
		sPo = new PipedOutputStream();
	    return sPo;
	}

	public void initServer() throws IOException {
		//init thread
		InitServerThread ist = new InitServerThread();
		//test pipe
		//sPo.write("Hello, Reciver!".getBytes());
		//sPo.close();
		ist.start();	
	}
	
	//write pipe msg
	private synchronized void writePipe(PipedOutputStream sPo, int msg, int serverPeerId, int clientPeerId, Integer payload) throws IOException {
		byte[] threadMsg ;
		if(payload != null) {
			threadMsg = (serverPeerId + " " + clientPeerId + " " + msg + " " + payload + " ").getBytes();
		} else {
			threadMsg = (serverPeerId + " " + clientPeerId + " " + msg + " ").getBytes();
		}
		byte[] length;			
		byte[] sendMsg;							
		length = peerProcess.intToByte(threadMsg.length);
		sendMsg = new byte[threadMsg.length + length.length];
		System.arraycopy(length, 0, sendMsg, 0, length.length);
		System.arraycopy(threadMsg, 0, sendMsg, length.length, threadMsg.length);		
		//System.out.println("lengthpre: "+threadMsg.length);
		sPo.write(sendMsg);	
	}
	class InitServerThread extends Thread {
		//test port number
		//private String fileName;
		private Socket socket;
		//constructor
		public InitServerThread() {
			//this.fileName = fileName;
		}
		//run method
		@Override
		public void run(){
			try {
				//create socket
				ServerSocket serverSocket = new ServerSocket(portNum);
				System.out.println("Server start successfully, listening port: " + portNum);
				while(true) {
					socket = serverSocket.accept();
					System.out.println("Incoming connection " + socket.getInetAddress() + ":" + socket.getPort());
					ServerThread st = new ServerThread(socket, serverPeerId);
					st.start();
				}				
			}catch (Exception e) {
				// TODO: handle exception
				e.printStackTrace();
			}
		}
	}
	//terminate server
	private void terminateServer(Socket socket) throws IOException {
		socket.close();
	}
	class ServerThread extends Thread {
		private Socket socket;
		private int serverPeerId;
		private int clientPeerId;
		//client's bitfield
		private ArrayList<Integer> clientBitfield = new ArrayList<Integer>();
		//constructor
		public ServerThread(Socket socket, int serverPeerId) {
			this.socket = socket;
			this.serverPeerId = serverPeerId;
			//init the bitfield array list
			for(int i = 0; i < peerProcess.pieceSum; i++) {
				clientBitfield.add(0);
			}
		}
		//choose next request index from bitfield
		private int chooseRandomIndex() {
			//if(downloadedPieceNum >= peerProcess.pieceSum / 2) {
				for(int i = 0; i < peerProcess.hostBitfield.size(); i++) {
					if(clientBitfield.get(i) == 1 && peerProcess.hostBitfield.get(i) == 0) {
						peerProcess.hostBitfield.set(i, 2);
						return i;
					}
				}
				return -1;
			/*} else {
				int rand;
				Random random = new Random();
				while(true) {
					rand = random.nextInt(clientBitfield.size());			
					if(peerProcess.hostBitfield.get(rand) == 0 && clientBitfield.get(rand) == 1) {
						System.out.println(rand);
						peerProcess.hostBitfield.set(rand, 2);
						return rand;
					}
				}
			}*/		
		}		
		//handle handshake msg(already checked the msg format)
		public int handleHandshakeMsg(byte[] msg) {
			String handshakeHeader = new String(msg ,0, 18);
			//String zeroBit = new String(msg, 18, 28);
			byte[] peerIdArray = new byte[4];
			int count = 28;
			for(; count < 32; count ++) {
				peerIdArray[count - 28] = msg[count];
			}
			int peerId = peerProcess.byteToInt(peerIdArray);
			if(!handshakeHeader.equals(handshakeHeaderDef)) {
				return -1;
			}
			return peerId;
		}
		
		//check if this client is the first time to request
		public boolean checkIfFirstRequest() {
			int port = socket.getPort();
			String ipAddr = socket.getInetAddress().toString();
			return !(ipList.contains(ipAddr)) || !(portList.contains(port));
		}
		/*private synchronized int downloadPiecePlus() {
			return downloadedPieceNum + 1;
		}*/
		//check if the handshake msg length format is correct
		public byte[] checkHandMsgFormat(DataInputStream in, byte[] handshakeBuf) throws IOException {
			int length = 0;
			byte[] bufferInfo = new byte[1];
			//read msg from socket
			while(in.read(bufferInfo) != -1) {
				handshakeBuf[length] = bufferInfo[0];
				length++;
				if(length >= handshakeMsgLen) {
					break;
				}
			}
			//check the length of the msg
			if(length != handshakeMsgLen) {
				System.err.println("Handshake msg format illegal: Msg length is " + length + ", but the expected is 32");
				return null;
			} else {
				return handshakeBuf;
			}
			
		}
		
		//fetch Actual Msg Length field
		public int fetchActualMsgLength(DataInputStream in, byte[] actualMsgLen) throws IOException {
			int msgLen = 0;
			in.read(actualMsgLen);
			msgLen = peerProcess.byteToInt(actualMsgLen);
			//System.out.println("msgLength: " + msgLen);
			return msgLen;
		}
		
		//fetch Actual Msg Type field
		public int fetchActualMsgType(DataInputStream in) throws IOException {
			int msgType = 0;
			byte[] actualMsgType = new byte[1];
			in.read(actualMsgType);
			Byte act = actualMsgType[0];
			msgType = act.intValue();
			//System.out.println("msgType: " + msgType);
			return msgType;
		}
		private boolean handleBitfieldMsg(DataInputStream in, int msgLen, int msgType) throws IOException {
			byte[] bitfieldBuf = new byte[msgLen - 1];
			int length = clientBitfield.size();
			int j = 7;
			in.read(bitfieldBuf);
			byte b[] = new byte[4];
			b[0] = 0x00;
			b[1] = 0x00;
			b[2] = 0x00;
			System.out.println("clientBitfield.size():" + clientBitfield.size() + ", length: " + (msgLen - 1) * 8);
			//check length
			if((msgLen - 1) * 8 < clientBitfield.size()) {
				return false;
			}
			int index = 0;
			for(int i = 0; i < (msgLen - 1); i++) {
				for(j = 7; j >= 0 ; j--) {
					b[3] = (byte) ((bitfieldBuf[i]  >> j) & 1);				
					clientBitfield.set(index, peerProcess.byteToInt(b));
					System.out.println("bit: " + clientBitfield.get(index) + " index: "+index);
					if(index == length - 1) {
						System.out.println("contain 1?:" + clientBitfield.contains(1));
						return true;
					}
					index++;
				}				
			}
			return true;
		}
		
		
		//check if there are any pieces the host doesn't have
		private boolean ifInterested(ArrayList<Integer> hostBitfield, ArrayList<Integer> clientBitfield2) {
			for (int i = 0; i < peerProcess.hostBitfield.size(); i++) {
				if(peerProcess.hostBitfield.get(i) == 0 && clientBitfield.get(i) == 1) {
					//interested
					return true;
				}
			}
			return false;
		}
		//handle request msg and return the piece index
		private int handleRequestMsg(DataInputStream in, int msgLen, int msgType) throws IOException {
			byte[] RequestMsgBuf = new byte[msgLen - 1];
			in.read(RequestMsgBuf);
			int index = peerProcess.byteToInt(RequestMsgBuf);
			return index;
		}
		//handleHaveMsg
		private boolean handleHaveMsg(DataInputStream in, int msgLen, int msgType) throws IOException{
			byte[] RequestMsgBuf = new byte[msgLen - 1];
			in.read(RequestMsgBuf);
			int index = peerProcess.byteToInt(RequestMsgBuf);
			clientBitfield.set(index, 1);
			//for(int i = 0; i < clientBitfield.size(); i++) {
				if(peerProcess.hostBitfield.get(index) == 0) {
					//send interested msg
					if(!hostInterestedList.containsKey(clientPeerId)) {
						writePipe(sPo, 5, serverPeerId, clientPeerId, 0);
						hostInterestedList.put(clientPeerId, true);
					}				
					//writeLog
					inputLog.writeLog("Peer " + serverPeerId + " received the 'have' message from " + clientPeerId + " for the piece " + index);
					return true;
				}
			//}
			//send not interested msg
			/*if(hostInterestedList.containsKey(clientPeerId)) {
				writePipe(sPo, 5, serverPeerId, clientPeerId, 1);
				hostInterestedList.remove(clientPeerId);
			}*/				
			//writeLog
			inputLog.writeLog("Peer " + serverPeerId + " received the 'have' message from " + clientPeerId + " for the piece " + index);
			return true;
		}
		//handlePieceMsg
		private int handlePieceMsg(DataInputStream in, int msgLen, int msgType) throws IOException{
			byte[] index = new byte[4];
			int length = msgLen - 1;
			if(length != peerProcess.pieceSize && length != peerProcess.lastPieceSize) {
				System.err.println("Sever: length not match, received: " + length + " pieceSize: "+peerProcess.pieceSize);
				return -1;
			}
			int i = 0;
			byte[] pieceMsg = new byte[1];
			in.read(index);
			int indexInt = peerProcess.byteToInt(index);			
			System.out.println("indexInt: "+ indexInt);
			if(peerProcess.hostBitfield.get(indexInt) != 2) {			
				while(i < length) {
					in.read(pieceMsg);
					i++;
				}
				return -2;
			}
			RandomAccessFile raf = new RandomAccessFile(peerProcess.fileName, "rw");
			long lastPoint = (indexInt) * peerProcess.pieceSize;
			//replace file
			while(i < length) {
				in.read(pieceMsg);
				raf.seek(lastPoint);
                raf.write(pieceMsg);
                lastPoint++;
				i++;				
			}
			raf.close();
			//peerProcess.hostBitfield.set(indexInt, 1);
			return indexInt;
			
		}
		//check the completion of the file
		/*private boolean checkCompletion() {
			for(int i = 0; i < clientBitfield.size(); i++) {
				if(clientBitfield.get(i) != 1) {
					return false;
				}
			}
			return true;
		}*/
		//handle actual msg based on msg type
		public boolean handleActualMsg(ObjectOutputStream out,DataInputStream in, int msgLen, int msgType) throws IOException, InterruptedException {
			switch(msgType) {
			//choke
			case 0: {
				if(chokeStatus.get(clientPeerId) == false) {
					chokeStatus.put(clientPeerId, true);
					inputLog.writeLog("Peer " + serverPeerId + " is choked by " + clientPeerId);
				}								
			}
			break;
			//unchoke
			case 1: {	
				if(downloadedPieceNum >= peerProcess.pieceSum) {
					System.out.println("complete");
					if(completionStatus == 0) {
						completionStatus = 1;
						inputLog.writeLog("Peer " + serverPeerId + " has downloaded the complete file");
					}
					writePipe(sPo, 5, serverPeerId, clientPeerId, 1);
					hostInterestedList.remove(clientPeerId);
					break;
				}
				int index = chooseRandomIndex();
				/*if(index == -1) {
					//send not interested because of the completion of the file					
					if(hostInterestedList.containsKey(clientPeerId)) {
						writePipe(sPo, 5, serverPeerId, clientPeerId, 1);
						hostInterestedList.remove(clientPeerId);
					}
					break;
				} else*/ if(index != -1) {
					writePipe(sPo, 8, serverPeerId, clientPeerId, index);
				}
				if(chokeStatus.get(clientPeerId) == true) {
					chokeStatus.put(clientPeerId, false);
					//writeLog
					inputLog.writeLog("Peer " + serverPeerId + " is unchoked by " + clientPeerId);
				}					
			}
			break;
			//interested
			case 2: {
				if(interestStatus.get(clientPeerId) == false) {
					interestStatus.put(clientPeerId, true);
					speedReg.put(clientPeerId, 0);
					//writeLog
					inputLog.writeLog("Peer " + serverPeerId + " received the 'interested' message from " + clientPeerId);
				}			
			}
			break;
			//not interested
			case 3:{
				if(interestStatus.get(clientPeerId) == true) {
					interestStatus.put(clientPeerId, false);
					speedReg.remove(clientPeerId);
					//writeLog
					inputLog.writeLog("Peer " + serverPeerId + " received the 'not interested' message from " + clientPeerId);
				}
			}
			break;
			//have
			case 4: {
				handleHaveMsg(in, msgLen, msgType);
			}
			break;
			//bitfield
			case 5: {
				if(handleBitfieldMsg(in, msgLen, msgType) == false) {
					System.err.println("Server: handle actual msg, bitfield");
				} else {
					System.out.println("Sever: bitfield msg received: " + clientBitfield.size() + "," + peerProcess.hostBitfield.size());
					//check if there are any pieces the host doesn't have 0:interested 1:not interested
					if(ifInterested(peerProcess.hostBitfield,clientBitfield) == true) {
						writePipe(sPo, 5, serverPeerId, clientPeerId, 0);
						hostInterestedList.put(clientPeerId, true);
						//ClientManager.modifyHashmap(clientPeerId, 2);
					} else {						
						writePipe(sPo, 5, serverPeerId, clientPeerId, 1);
						hostInterestedList.remove(clientPeerId);						
						//ClientManager.modifyHashmap(clientPeerId, 3);
					}
				}
			}
			break;
			//request
			case 6:{
				System.out.println("Server: request");
				int index = handleRequestMsg(in, msgLen, msgType);
				//check if it is unchoked to the host
				/*if(preferedList.contains(clientPeerId) == false && optimisticNeighbor != clientPeerId) {
				//if(unchokeList.contains(clientPeerId) == true) {
					System.err.println("receive a request from a choked peer");
					out.write(peerProcess.intToByte(index));
				} else {*/				
					writePipe(sPo, 6, serverPeerId, clientPeerId, index);
				//}
			}
			break;
			//piece
			case 7:{
				System.out.println("Server: piece");
				int result = handlePieceMsg(in, msgLen, msgType);
				if(result == -1) {
					System.err.println("modify file failed");
				} else if(result == -2) {
					System.err.println("already have the piece");
				} else if(result >= 0){
					//send have
					writePipe(sPo, 7, serverPeerId, clientPeerId, result);
					peerProcess.hostBitfield.set(result, 1);
					downloadedPieceNum ++;
					//writeLog
					inputLog.writeLog("Peer " + serverPeerId + " has downloaded the piece " + result + " from " + clientPeerId + "." + " Now the number of pieces it has is " + downloadedPieceNum);
				}
				if(downloadedPieceNum >= peerProcess.pieceSum) {
					//System.out.print("complete1");
					if(completionStatus == 0) {
						completionStatus = 1;				
						inputLog.writeLog("Peer " + serverPeerId + " has downloaded the complete file");
						//terminateServer(socket);
						//return true;
					}
					writePipe(sPo, 5, serverPeerId, clientPeerId, 1);
					hostInterestedList.remove(clientPeerId);
					//terminate
					//terminateServer();
					//return true;
					break;
				}
				int next = chooseRandomIndex();
				//System.out.println("next: " + next+", bit: "+ peerProcess.hostBitfield.get(next));
				/*if(next == -1) {
					//send not interested because of the completion of the file							
					if(hostInterestedList.containsKey(clientPeerId)) {
						writePipe(sPo, 5, serverPeerId, clientPeerId, 1);
						hostInterestedList.remove(clientPeerId);
						//inputLog.writeLog("Peer " + serverPeerId + " has downloaded the complete file");	
					}*/
					/*while(true) {
						checkCompletime++;
						int checkRes = checkCompletion(checkCompletime);
						if(checkRes == 1) {
							break;
						} else if(checkRes == 0) {
							inputLog.writeLog("Peer " + serverPeerId + " has downloaded the complete file");
							terminateServer();
							return true;
						} else if(checkRes == -2) {
							checkCompletime = 0;
							break;
						}
						sleep(5000);
					}*/
					
					//System.out.print("complete2");
					//terminate
					//terminateServer();
					//return true;
					//break;
				/*} else*/ if(chokeStatus.get(clientPeerId) == false && next != -1) {
					writePipe(sPo, 8, serverPeerId, clientPeerId, next);
				} else if(chokeStatus.get(clientPeerId) == true) {
					peerProcess.hostBitfield.set(next, 0);
					break;
				}
				if(speedReg.containsKey(clientPeerId)) {
					speedReg.put(clientPeerId, (speedReg.get(clientPeerId) + 1));
				}
			}
			break;	
			default: System.err.println("Sever: handle actual msg, msg type not match: " + msgType); return false;			
			}
			return true;
		}
		@Override
		public void run(){
			//System.out.println("enter server thread");
			//this hashmap indicate if the client has already sent handshake msg
			//HashMap<Integer, Boolean> peerState = new HashMap<Integer, Boolean>();			
			try {
				DataInputStream in= new DataInputStream(socket.getInputStream());
				ObjectOutputStream out= new ObjectOutputStream(socket.getOutputStream());
				out.flush();
				//buffer
				byte[] handshakeBuf; 
				byte[] actualMsgLen;
				//while loop is waiting for incoming connection 
				while(true) {
					//rcv message from the socket
					//check if it is the first time for the client to send request msg
					if(checkIfFirstRequest()) {
						handshakeBuf = new byte[64];
						//handshake msg
						//check if the handshake msg format is correct
						handshakeBuf = checkHandMsgFormat(in, handshakeBuf);
						if(handshakeBuf == null) {
							System.err.println("Handshake msg format illegal: Msg length not match");
							out.writeObject("failed");
							out.flush();
							socket.close();
						} else {
							if((clientPeerId = handleHandshakeMsg(handshakeBuf)) == -1) {
								System.err.println("Handshake msg format illegal");
								out.writeObject("failed");
								out.flush();
								socket.close();
							} else {								
								ipList.add(socket.getInetAddress().toString());
								portList.add(socket.getPort());	
								//if client > server, should reply handshake, otherwise, ignore it.
								System.out.println("client: " + clientPeerId + ", server: " + serverPeerId);
								if(clientPeerId > serverPeerId) {
									writePipe(sPo, -1, serverPeerId, clientPeerId, null);
								}
								//write log
								inputLog.writeLog("Peer " + serverPeerId + " is connected from Peer " +clientPeerId+ ".");
								//init peer status(choke and not interested)
								chokeStatus.put(clientPeerId, true);
								interestStatus.put(clientPeerId, false);
								hostChokeStatus.put(clientPeerId, true);
							}						
						}
						//bitfield checker
						bitfCheckThread bt = new bitfCheckThread();
						bt.start();
					} else {
						int msgLen = -1;
						int msgType = -1;
						actualMsgLen = new byte[4];					
						//actual msg
						//fetch message length
						if((msgLen = fetchActualMsgLength(in, actualMsgLen)) == -1) {
							System.err.println("problems occured when fetching ActualMsgLength");
							break;
						}
						//fetch message type
						if((msgType = fetchActualMsgType(in)) == -1) {
							System.err.println("problems occured when fetching ActualMsgLength");
							break;
						}
						//handle actual msg
						if(handleActualMsg(out, in, msgLen, msgType) != true) {
							System.err.println("problems occured when handling ActualMsg");
							break;
						}	
						if(completionStatus == 1) {
							for(int i = 0; i < peerProcess.clientList.size(); i++) {
								if(peerProcess.clientList.get(i) == serverPeerId) {
									peerProcess.clientList.remove(i);
									completionStatus = 3;
								}
							}
						}
						
					}				
				}
			} catch (IOException e) {
				//e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			}
		 }
		class bitfCheckThread extends Thread{
			@Override
			public void run() {
				System.out.println("bitfield checker");
				while(true) {
					int i = 0;
					for(i = 0; i < clientBitfield.size(); i++) {
						if(clientBitfield.get(i) != 1) {
							break;
						}
					}
					if(i >= clientBitfield.size()) {
						for(int j = 0; j < peerProcess.clientList.size(); j++) {
							if(peerProcess.clientList.get(j) == clientPeerId) {
								peerProcess.clientList.remove(j);
								return ;
							}
						}
					}
					try {
						sleep(10000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}
	
}





