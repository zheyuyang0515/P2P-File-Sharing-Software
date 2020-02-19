/**
 * read common.cfg file
 */
package Helper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Pattern;

public class Common {

	public int preferredNeighbor = 0;
	public int UnchokingInterval = 0;
	public int OptimisticUnchokingInterval = 0;
	public int fileSize = 0;
	public int pieceSize = 0;
	public String fileName;
	//static private String cfgName = "Common.cfg";;
	//test main method;
	/*public static void main(String[] args) {
		Common c = new Common();
		c.fileRead(cfgName);
	}*/
	//fetch info from a formatted String
	public static int fetchInfo(String msg, int intStart) {
		String fetchInfo;
		int fetchInfoInt;
		Pattern pattern = Pattern.compile("[0-9]*");
		fetchInfo = msg.substring(intStart);
		if((pattern.matcher(fetchInfo).matches()) != true) {
			System.err.println("Common.cfg format illegal: " + intStart);
			return -1;
		}else {
			fetchInfoInt = Integer.parseInt(fetchInfo);
		}
		return fetchInfoInt;
	}
	public static String fetchFileName(String msg) {
		String FileName = null;
		FileName = msg.substring(9);
		return FileName;
	}
	//read from file
	@SuppressWarnings("resource")
	public static String[] fileRead(String filename) {
	        FileReader in = null;
	        String rdStr;
	        String[] commonInfo = new String[6];
	        int i = 0;
	        try {
	        	in = new FileReader(filename);
	            BufferedReader br = new BufferedReader(in);
	            while((rdStr = br.readLine()) != null && i < 6)
	 			{
	 				commonInfo[i] = rdStr;
	 				i++;
	 			}
	            if(i != 6) {
	            	System.err.println("Common.cfg format illegal: row number");
	            	br.close();
		 			in.close();
		 			return null;
	            }
	 			br.close();
	 			in.close();
	 			return commonInfo;
	 			//extract info from String array
	 			/*if((preferredNeighbor = fetchInfo(commonInfo[0], 27)) == -1 || 
	 				(UnchokingInterval = fetchInfo(commonInfo[1], 18)) == -1 ||
	 				(OptimisticUnchokingInterval = fetchInfo(commonInfo[2], 28)) == -1 ||
	 				(fileName = fetchFileName(commonInfo[3])) == null ||
	 				(fileSize = fetchInfo(commonInfo[4], 9)) == -1 || 
	 				(pieceSize = fetchInfo(commonInfo[5], 10)) == -1) { 				
	 				return;
	 			}*/
	        } catch (IOException e) {
	            e.printStackTrace();
	            return null;
	        }
	       
	    }
	
	
}

