/**
 * read peerInfo.cfg
 */
package Helper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;

//[peer ID] [host name] [listening port] [has file or not]
public class PeerInfo {
	/*public static void main(String[] args) {	
	  String filePath = "PeerInfo.cfg"; 
	  ArrayList<String[]> peerInfoArr = read(filePath);	
	}*/
	//read peerinfo and return a list including a string array of peer info
    public static ArrayList<String[]> read(String filePath){
	      BufferedReader buffer = null;
	      String line =null;	
	      String[] arrays;
	      ArrayList<String[]> arrayList = new ArrayList<String[]>();
	      try {
		      buffer = new BufferedReader(new FileReader(filePath));
		      String str = "";
	          //while loop to read file until end
		      while ((line = buffer.readLine()) != null) {
			          str = line + "\r\n";
			          arrays = str.trim().split("\\s+");
			          arrayList.add(arrays);
		      }
	         } catch (Exception e) {
		              e.printStackTrace();
		              }finally {
		        	   if (buffer != null) {
		        		   try {
		        			   buffer.close();
		        		   }catch (IOException e) {
                                buffer = null;
                                }
		        		   }
		        	   }
	      return arrayList;
    }
    public static void modifyCompleteStatus(String filename, int hostPeerId) throws IOException {
    	RandomAccessFile raf = new RandomAccessFile(filename, "rw");
    	long lastPoint = 0;
    	String content = null;
    	String[] arrays;
    	String str;
    	while ((content = raf.readLine()) != null) {
    		long currentPoint = raf.getFilePointer();
    		str = content + "\r\n";
    		arrays = str.trim().split("\\s+");
    		if(Integer.parseInt(arrays[0]) == hostPeerId) {
    			arrays[3] = 1 + "";
    			 StringBuffer sb = new StringBuffer();
                 for (String s :arrays){
                	 sb.append(s);
                	 sb.append(" ");
                 }
                 sb.append("\n");
    			raf.seek(lastPoint);
    			raf.writeBytes(sb.toString());
    		}
    		lastPoint = currentPoint; 		
    	}    	    	 
    	raf.close();
	}


}
