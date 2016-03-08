package org.apache.ode.bpel.extensions.sync;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.lang.SystemUtils;

//TODO check if this class works
public class SynchronizationUnitLogWriter {
	private static final SynchronizationUnitLogWriter logWriterInstance = new SynchronizationUnitLogWriter();
	
	private SynchronizationUnitLogWriter() {
		super();
	}
	
	public synchronized void writeToLogFile(ArrayList<String> text) {
		try {
			File file;
			if(SystemUtils.IS_OS_UNIX) {
				file = new File("/home/ubuntu/logs/log.txt");
			}
			else {
				file = new File("log.txt");
			}
			if(!file.exists()) {
				file.createNewFile();
			}
			FileWriter fWriter = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bWriter = new BufferedWriter(fWriter);
			for(int i = 0; i < text.size(); i++) {
				bWriter.write((String) text.get(i));
				bWriter.newLine();
			}
			bWriter.close();
		}
		catch(IOException e) {
			
		}
	} 
	
	public static SynchronizationUnitLogWriter getInstance() {
		return logWriterInstance;
	}
	
}