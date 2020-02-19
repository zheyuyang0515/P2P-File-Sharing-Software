/**
 * the responsibility of this class is to manage the clients including create, choose neighbor etc.
 */
package Client;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import Helper.inputLog;
import P2pFileSystem.peerProcess;
import Server.Server;

public class ClientManager {
	//host ip address
	String ipAddr;
	//host listening port num and peer id
	int portNum, peerId;
	public static HashMap<Integer, Client1> clientObject = new HashMap<>();
	public static HashMap<Integer, PipedOutputStream> clientPipe = new HashMap<>();
	//constructor
	public ClientManager(String ipAddr, int portNum, int peerId) {
		this.ipAddr = ipAddr;
		this.portNum = portNum;
		this.peerId = peerId;
	}
	//pipe declaration
	PipedInputStream mPi = null;
	PipedOutputStream mPo;
	//init pipe
	public PipedInputStream getPipedInputputStream() {
		mPi = new PipedInputStream();
	    return mPi;
	}
	 private void writePipe(PipedOutputStream sPo, int msg, int serverPeerId, int clientPeerId, Integer payload) throws IOException {
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
	public void initManager() throws IOException {
		//create client for previous peers
		for(int i = 0; i < peerProcess.peerInfoArr.size(); i++) {
			if(Integer.parseInt(peerProcess.peerInfoArr.get(i)[0]) >= peerId) {
				break;
			}
			Client1 client = new Client1(peerProcess.peerInfoArr.get(i)[1], Integer.parseInt(peerProcess.peerInfoArr.get(i)[2]), Integer.parseInt(peerProcess.peerInfoArr.get(i)[0]), peerId);
			client.startClient();
			mPo = new PipedOutputStream();
			mPo.connect(client.tPi);
			clientObject.put(Integer.parseInt(peerProcess.peerInfoArr.get(i)[0]), client);
			clientPipe.put(Integer.parseInt(peerProcess.peerInfoArr.get(i)[0]), mPo);
		}	
		ClientManageThread manager = new ClientManageThread();
		manager.start();
		OpNeighborThread on = new OpNeighborThread();
		on.start();
		PreferedNeighbor pf = new PreferedNeighbor();
		pf.start();
		//test pipe
		/*byte[] bys = new byte[1024];
		mPi.read(bys);
	    mPi.close();*/
	}
	class PreferedNeighbor extends Thread{
		 private int[] order(Map<Integer, Integer> hashmap, int num) {
		        int[] res = new int[num];
		        List<Integer> valueList = new ArrayList<>();
		        //sort value in descending order
		        List<Map.Entry<Integer,Integer>> list = new ArrayList<Map.Entry<Integer,Integer>>(hashmap.entrySet());
		        Collections.sort(list,new Comparator<Map.Entry<Integer,Integer>>() {
		            public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
		                return o2.getValue().compareTo(o1.getValue());
		            }
		        });
		            for (Map.Entry<Integer, Integer> mapping : list) {
		                valueList.add(mapping.getKey());
		            }
		            for(int i = 0; i < num; i++){
		                res[i] = valueList.get(i);
		            }
		     return res;
		    }
		
		 @Override
		public void run(){ 
			 PipedOutputStream out;	
			 int[] preferedNei; 
			 ArrayList<Integer> newPreferNei;
			 while(true) {
				 System.out.println("change prefered neighbor");
				 preferedNei = new int[peerProcess.preferredNeighbor];
				 newPreferNei = new ArrayList<>();
				 if(Server.speedReg.size() > 0) {
					 if((Server.speedReg.size() <= peerProcess.preferredNeighbor)) {
						for(Map.Entry<Integer, Integer> entry: Server.speedReg.entrySet()) {
							out =  clientPipe.get(entry.getKey());
							try {
								writePipe(out, 1, peerId, entry.getKey(), null);
								if(!Server.preferedList.contains(entry.getKey())) {
									Server.preferedList.add(entry.getKey());									
								}	
								newPreferNei.add(entry.getKey());
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					 } else {
						 preferedNei = order(Server.speedReg, peerProcess.preferredNeighbor);
						 for(int i: preferedNei) {
							 out = clientPipe.get(i);
							 System.out.println("prefered: " + i);
							 try {
								writePipe(out, 1, peerId, i, null);
								if(!Server.preferedList.contains(i)) {
									Server.preferedList.add(i);								
								}
								newPreferNei.add(i);
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						 }
					 }
					 //choke old preferedneighbor
					 for(int i = 0; i < Server.preferedList.size(); i++) {
						 int clientPeerId = Server.preferedList.get(i);
						 if(!newPreferNei.contains(clientPeerId)) {
							 out = clientPipe.get(clientPeerId);
							 try {
								writePipe(out, 0, peerId, clientPeerId, null);
								Server.preferedList.remove(i);
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						 }
					 }					
					 //clear hashmap
					 for(Map.Entry<Integer, Integer> entry: Server.speedReg.entrySet()) {
						 entry.setValue(0);
					 }
					 //writelog
					 String logContent = "Peer " + peerId + " has the preferred neighbors ";
					 for(int i = 0; i < newPreferNei.size(); i++) {
						 //last element in the list
						 if(i == newPreferNei.size() - 1) {
							 logContent += newPreferNei.get(i) + "."; 
						 } else {
							 logContent += newPreferNei.get(i) + ","; 
						 }
					 }
					 inputLog.writeLog(logContent);
				 }
				 try {
					sleep(peerProcess.UnchokingInterval * 1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			 }
		}
		 
	}
	class OpNeighborThread extends Thread{
		public int optimisticNeighborId(ArrayList<Integer> chokedNeighbor) {
            int index = (int)(Math.random() * chokedNeighbor.size());
            int optimisticId = chokedNeighbor.get(index);
            return optimisticId;
		}
		@Override
		public void run(){
			ArrayList<Integer> chokedNeighbor;
			PipedOutputStream preOut;
			PipedOutputStream out;
			while(true) {				
				System.out.println("change optimistic");
				if(Server.optimisticNeighbor != -1 && !Server.preferedList.contains(Server.optimisticNeighbor)) {
					Server.hostChokeStatus.put(Server.optimisticNeighbor, true);
					preOut = clientPipe.get(Server.optimisticNeighbor);
					try {
						writePipe(preOut, 0, peerId, Server.optimisticNeighbor, null);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				if(Server.hostChokeStatus.size() != 0 && Server.interestStatus.size() != 0) {
					chokedNeighbor = new ArrayList<Integer>();
					for(Map.Entry<Integer, Boolean> entry: Server.hostChokeStatus.entrySet())
			        {
						if(entry.getValue() == true) {
							for(Map.Entry<Integer, Boolean> entry2: Server.interestStatus.entrySet()) {
								if(entry2.getValue() == true) {
									chokedNeighbor.add(entry2.getKey());
								}
							}
						}
			        }
					if(chokedNeighbor.size() != 0) {
						for(int s:chokedNeighbor) {
							System.out.println("chokedNeighbor: " + s);
						}
						Server.optimisticNeighbor = optimisticNeighborId(chokedNeighbor);
						System.out.println("new OptimisticNeighbor: " + Server.optimisticNeighbor);
						out = clientPipe.get(Server.optimisticNeighbor);
						//send pipe unchoke
						try {
							writePipe(out, 1, peerId, Server.optimisticNeighbor, null);
							Server.hostChokeStatus.put(Server.optimisticNeighbor, false);						
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						inputLog.writeLog("Peer " + peerId + " has the optimistically unchoked neighbor " + Server.optimisticNeighbor);
					}				
				} else {
					Server.optimisticNeighbor = -1;
				}
				try {
					sleep(peerProcess.OptimisticUnchokingInterval * 1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	class ClientManageThread extends Thread{
		@Override
		public void run(){
			//Client1 client = new Client1("localhost", 6008, 1001, 1002);
			//client.startClient();
			while(true) {
				//lookup hashmap
				System.out.println("CM: wait for msg");
				byte[] length = new byte[4];
				int lengthInt = 0;
				byte[] bys = null;
				String pipeMsg;
				String ipAddr = null;
				int portNum = 0;
				try {
					//first read length
					mPi.read(length);
					lengthInt = peerProcess.byteToInt(length);
					bys = new byte[lengthInt];
					//System.out.println("lengthaf: "+lengthInt);
					mPi.read(bys);
					
				} catch (IOException e) {
					e.printStackTrace();
				}
				pipeMsg = new String(bys).trim();
				System.out.println("CM: msg received: " + pipeMsg);
				String[] array = pipeMsg.split("\\s+");	
				//handle pipe msg sent from the server
				switch(Integer.parseInt(array[2])) {
				//handshake
				case -1:{
					//create a client if rcv handshake
					//find host name
					for(int i = 0; i < peerProcess.peerInfoArr.size(); i++) {
						if(Integer.parseInt(peerProcess.peerInfoArr.get(i)[0]) == Integer.parseInt(array[1])) {
							ipAddr = peerProcess.peerInfoArr.get(i)[1];
							portNum = Integer.parseInt(peerProcess.peerInfoArr.get(i)[2]);
						}
					}
					Client1 client1 = new Client1(ipAddr, portNum, Integer.parseInt(array[1]), Integer.parseInt(array[0]));
					client1.startClient();
					try {
						mPo = new PipedOutputStream();
						mPo.connect(client1.tPi);
						clientPipe.put(Integer.parseInt(array[1]), mPo);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					clientObject.put(Integer.parseInt(array[1]), client1);
				}
				break;
				//choke(useless)
				case 0:{
					System.out.println("chock");
				}
				break;
				//unchoke(useless)
				case 1:{
					System.out.println("unchock");
				}
				break;
				//interested(useless)
				case 2:{
					System.out.println("interested");
				}
				break;
				//not interested(useless)
				case 3:{
					System.out.println("not interested");
				}
				break;
				//have
				case 4:{
					System.out.println("have");
				}
				break;
				//bitfield
				case 5:{
					System.out.println("bitfield");
					if(Integer.parseInt(array[3]) == 0) {
						mPo = clientPipe.get(Integer.parseInt(array[1]));
						try {
							writePipe(mPo, 2, Integer.parseInt(array[0]), Integer.parseInt(array[1]), null);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					} else {						
						mPo = clientPipe.get(Integer.parseInt(array[1]));
						try {
							writePipe(mPo, 3, Integer.parseInt(array[0]), Integer.parseInt(array[1]), null);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
				break;
				//request
				case 6:{
					System.out.println("request recieve: " + array[3]);
					mPo = clientPipe.get(Integer.parseInt(array[1]));
					try {
						writePipe(mPo, 7, Integer.parseInt(array[0]), Integer.parseInt(array[1]), Integer.parseInt(array[3]));
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}		
				}
				break;
				//piece(broadcast to everyone)
				case 7:{
					System.out.println("piece recieve: " + array[3]);
					for(Entry<Integer, PipedOutputStream> entry: clientPipe.entrySet()) {
						mPo = entry.getValue();
						try {
							//send have
							writePipe(mPo, 4, Integer.parseInt(array[0]), entry.getKey(), Integer.parseInt(array[3]));
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}		
					}				
				}
				break;
				//next request
				case 8:{
					System.out.println("next request recieve: " + array[3]);
					mPo = clientPipe.get(Integer.parseInt(array[1]));
					try {
						writePipe(mPo, 6, Integer.parseInt(array[0]), Integer.parseInt(array[1]), Integer.parseInt(array[3]));
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				break;
				//send not interested
				case 9:{
					System.out.println("send not interested");
					mPo = clientPipe.get(Integer.parseInt(array[1]));
					try {
						writePipe(mPo, 3, Integer.parseInt(array[0]), Integer.parseInt(array[1]), null);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				break;
				//send interested
				case 10:{
					System.out.println("send interested");
					mPo = clientPipe.get(Integer.parseInt(array[1]));
					try {
						writePipe(mPo, 2, Integer.parseInt(array[0]), Integer.parseInt(array[1]), null);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				break;
				default:System.err.println("CM: type num err");break;
				}						
			}
		}
	}
	
}

