/**
 * write log
 */
package Helper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

/**
 * insert log in to the file
 * @author zheyu
 *
 */
public class inputLog {
	public static String logPath;
	//get timestamp
	private static String getTimestamp() {
		Date timestamp = new Date();
		return timestamp.toString();
	}
	
	//writting log
	public static synchronized void writeLog(String content) {
		String timestamp;
		FileWriter fw = null;
		try {
			File f = new File(logPath);
			fw = new FileWriter(f, true);
		} catch (IOException e) {
			e.printStackTrace();
		}
		PrintWriter pw = new PrintWriter(fw);
		timestamp = getTimestamp();
		pw.println("[" + timestamp + "]: " + content);
		pw.flush();
		System.out.println("LOG: [" + timestamp + "]: " + content);
		try {
			fw.flush();
			pw.close();
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
