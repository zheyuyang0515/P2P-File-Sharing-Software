/**
 * main
 */
package P2pFileSystem;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;

import Client.ClientManager;
import Helper.Common;
import Helper.PeerInfo;
import Helper.inputLog;
import Server.Server;

public class peerProcess {
	//only for test
	private static int isComplete;
	private static int portNum;
	private static String ipAddr;
	public static int serverPeerId;
	public static String fileName;
	public static int fileSize;
	public static int pieceSize;
	public static int pieceSum;
	public static int preferredNeighbor = 0;
	public static int UnchokingInterval = 0;
	public static int OptimisticUnchokingInterval = 0;
	public static int lastPieceSize = 0;
	public static String workSpace;
	public static ArrayList<Integer> peerList = new ArrayList<Integer>();
	public static ArrayList<String[]> peerInfoArr = new ArrayList<String[]>();	
	public static String[] commonInfoArr;
	public static ArrayList<Integer> clientList = new ArrayList<Integer>();
	//host bitfield
	public static ArrayList<Integer> hostBitfield = new ArrayList<Integer>();
	public static byte[] intToByte(int i) {  
	    byte[] result = new byte[4];
	    result[0] = (byte) ((i >> 24) & 0xFF);  
	    result[1] = (byte) ((i >> 16) & 0xFF);  
	    result[2] = (byte) ((i >> 8) & 0xFF);  
	    result[3] = (byte) (i & 0xFF);  
	    return result;  
	}  
	/*public static int byteToInt(byte[] bytes) {   
		return   bytes[3] & 0xFF |   
		            (bytes[2] & 0xFF) << 8 |   
		            (bytes[1] & 0xFF) << 16 |   
		            (bytes[0] & 0xFF) << 24;   
	}  */
	public static int byteToInt(byte[] b) {
        int intValue = 0;
        for (int i = 0; i < b.length; i++) {
        	 intValue = intValue | (b[i] & 0xFF) << (8 * (3 - i));
        }
        return intValue;
	}
	public static void main(String args[])
	{
		//init program
		serverPeerId = Integer.parseInt(args[0]);
		peerInfoArr = PeerInfo.read("PeerInfo.cfg");
		//fetch host name(ipaddr) and port number
		for(int i = 0; i < peerInfoArr.size(); i++) {
			//Server.serverList.add(Integer.parseInt(peerInfoArr.get(i)[0]));
			clientList.add(Integer.parseInt(peerInfoArr.get(i)[0]));
			//peerList.add(Integer.parseInt(peerInfoArr.get(i)[0]));
			if(Integer.parseInt(peerInfoArr.get(i)[0]) == serverPeerId) {
				portNum = Integer.parseInt(peerInfoArr.get(i)[2]);
				ipAddr = peerInfoArr.get(i)[1];
				isComplete = Integer.parseInt(peerInfoArr.get(i)[3]);
			}
		}
		if(isComplete == 1) {
			Server.completionStatus = 1;
		}
		commonInfoArr = Common.fileRead("Common.cfg");		
		//create peer work directory
		File directory = new File("peer_" + serverPeerId + "/");
		if(!directory.exists()) {
			directory.mkdir();
		}
		workSpace = "peer_" + serverPeerId + "/";
		//init logPath
		inputLog.logPath = "log_peer_" + serverPeerId + ".log";
		//init bitfield
		//extract info from commoninfoarray
		if((preferredNeighbor = Common.fetchInfo(commonInfoArr[0], 27)) == -1 || 
			(UnchokingInterval = Common.fetchInfo(commonInfoArr[1], 18)) == -1 ||
			(OptimisticUnchokingInterval = Common.fetchInfo(commonInfoArr[2], 28)) == -1 ||
			(fileName = Common.fetchFileName(commonInfoArr[3])) == null ||
			(fileSize = Common.fetchInfo(commonInfoArr[4], 9)) == -1 || 
			(pieceSize = Common.fetchInfo(commonInfoArr[5], 10)) == -1) { 				
			System.err.println("error when extract info from commonInfoArray");
			return;
		}
		fileName = workSpace + fileName;
		if(fileSize % pieceSize == 0) {
			pieceSum = fileSize / pieceSize;
			lastPieceSize = pieceSize;
		} else {
			pieceSum = fileSize / pieceSize + 1;
			lastPieceSize = fileSize % pieceSize;
		}
		File file = new File(fileName);
		if(file.exists() && isComplete == 1) {
			for(int i = 0; i < pieceSum; i++) {
				hostBitfield.add(1);
			}
		} else if(!file.exists() && isComplete == 0) {
			for(int i = 0; i < pieceSum; i++) {
				hostBitfield.add(0);
			}
			//create temperary file
			System.out.println("Allocating space for the file...");
			try {
				RandomAccessFile out = new RandomAccessFile(fileName, "rw");;
				//FileOutputStream out = new FileOutputStream(fileName);
				out.setLength(fileSize);
				out.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}						
		} else if(file.exists() && isComplete == 0){
			//read the file to check which piece is complete
			try {
				RandomAccessFile in = new RandomAccessFile(fileName, "r");
				byte[] buffer = new byte[pieceSize];				
				while(in.read(buffer) > 0) {
					int i;
					for(i = 0; i < buffer.length; i++) {
						if(buffer[i] != 0x00) {
							break;
						}
					}
					if(i >= buffer.length) {
						hostBitfield.add(0);
					} else {
						hostBitfield.add(1);
					}
				}
				in.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
						
		} else {
			System.err.println("bitfield init failed, file existing state: " + file.exists() + ", isComplete field in PeerInfo.cfg " + isComplete);
			return;
		}
		System.out.println("bitfield init successfully, size: " + hostBitfield.size() + ", piece sum: " + pieceSum);
		//init server thread	
		Server server = new Server(portNum, serverPeerId);
		PipedOutputStream sPo = server.getPipedOutputStream();	
		//init client manager thread
		ClientManager cm = new ClientManager(ipAddr, portNum, serverPeerId);
		PipedInputStream mPi = cm.getPipedInputputStream();
		try {
			sPo.connect(mPi);
			server.initServer();
			cm.initManager();
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*while(true) {
			ArrayList<String[]> peerInfo = PeerInfo.read("PeerInfo.cfg");
			int i = 0;
			for(i = 0; i < peerInfo.size(); i++) {
				if(Integer.parseInt(peerInfo.get(i)[3]) == 0) {
					break;
				}
			}
			if(i == peerInfo.size()) {
				System.exit(0);
			}
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}*/
		while(true) {
			System.out.println("threadSum: "+Thread.activeCount());
			System.out.println(clientList.size());
			if(clientList.size() == 0) {
				System.exit(0);
			}
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
