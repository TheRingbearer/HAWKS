package org.apache.ode.bpel.extensions.sync;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class ConfigReader {
	
	private int threadPoolSize;
	private int waitTimeForInputQueueToContainMessage;
	private int viewChangeCheckInterval;
	private int checkForWorkflowMessageInterval;
	private int checkForMiddlewareMessageInterval;
	
	public ConfigReader() {
		readConfig();
	}
	
	private void writeToVars(String line) {
        String[] parts = line.split(" | ");
//        for (int i = 0; i < parts.length; i++) {
//        }
		if (parts.length > 1) {
			if (Constants.DEBUG_LEVEL > 0) {
				System.out.println(parts[0] + " | " + parts[2]);
			}
        	if (parts[2].equals("ThreadPoolSize")) {
        		threadPoolSize = Integer.parseInt(parts[0]);
        		if (Constants.DEBUG_LEVEL > 0) {
    				System.out.println("ThreadPoolSize: " + threadPoolSize);
    			}
        	} else if (parts[2].equals("WaitTimeForInputQueueToContainMessage")) {
        		waitTimeForInputQueueToContainMessage = Integer.parseInt(parts[0]);
        		if (Constants.DEBUG_LEVEL > 0) {
    				System.out.println("WaitTimeForInputQueueToContainMessage: " + waitTimeForInputQueueToContainMessage);
    			}
        	} else if (parts[2].equals("ViewChangeCheckInterval")) {
        		viewChangeCheckInterval = Integer.parseInt(parts[0]);
        		if (Constants.DEBUG_LEVEL > 0) {
    				System.out.println("ViewChangeCheckInterval: " + viewChangeCheckInterval);
    			}
        	} else if (parts[2].equals("CheckForWorkflowMessageInterval")) {
        		checkForWorkflowMessageInterval = Integer.parseInt(parts[0]);
        		if (Constants.DEBUG_LEVEL > 0) {
    				System.out.println("CheckForWorkflowMessageInterval: " + checkForWorkflowMessageInterval);
    			}
        	} else if (parts[2].equals("CheckForMiddlewareMessageInterval")) {
        		checkForMiddlewareMessageInterval = Integer.parseInt(parts[0]);
        		if (Constants.DEBUG_LEVEL > 0) {
    				System.out.println("CheckForMiddlewareMessageInterval: " + checkForMiddlewareMessageInterval);
    			}
        	}
        }
	}
	
	
	private void readConfig() {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader("/home/ubuntu/configs/config.txt"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    try {
	        String line = br.readLine();
	        
	        while (line != null) {
	            //System.out.println(line);
	            writeToVars(line);
	            line = br.readLine();
	        }
	    } catch (IOException e) {
			e.printStackTrace();
		} finally {
	        try {
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
	    }
	}
	

	public int getThreadPoolSize() {
		return threadPoolSize;
	}

	public int getWaitTimeForInputQueueToContainMessage() {
		return waitTimeForInputQueueToContainMessage;
	}

	public int getViewChangeCheckInterval() {
		return viewChangeCheckInterval;
	}

	public int getCheckForWorkflowMessageInterval() {
		return checkForWorkflowMessageInterval;
	}

	public int getCheckForMiddlewareMessageInterval() {
		return checkForMiddlewareMessageInterval;
	}

	
}

