package krawczls.deploymentManagement;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import krawczls.deploymentManagement.ReplicationDeploymentProcessor;
import krawczls.deploymentManagement.ReplicationDeploymentRoute;
import krawczls.deploymentManagement.ReplicationStartContext;
import krawczls.deploymentManagement.ReplicationStartProcessor;
import krawczls.executionEngineManagement.WorkflowEngineProcessor;
import krawczls.executionEngineManagement.WorkflowEngineRoute;
import krawczls.executionEngineRegistry.WorkflowEngine;
import krawczls.executionEngineRegistry.WorkflowEngineRegistry;

import org.apache.activemq.ActiveMQConnectionFactory;
//import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.commons.lang.SystemUtils;

import constants.Constants;


public class ReplicationStartContext {
	
	public static ActiveMQConnectionFactory connection_factory = new ActiveMQConnectionFactory("tcp://127.0.0.1:61616");
	public static Connection connection = createMyConnection(connection_factory);
	public static int master = 0;
	public static int counter = 0;
	public static int heartbeatRate = 50000;
	public static int timeout = 100000;
	public static boolean withFailure = false;
	public static boolean initiateFailure = false;
	public static boolean withRoundRobin = false;
	public static boolean startFailureNow = false;
	public static boolean errorOccured = false;
    final static Random randomGenerator = new Random();
    
    public static ArrayList<String> roundRobinEngineList = new ArrayList<String>();
    public static Integer roundRobinIndex = 0;
	
	public static ArrayList<String> logs = new ArrayList<String>();
	
	public static EntityManagerFactory emf = Persistence.createEntityManagerFactory("workflowEngineRegistry");
	
	public static Connection createMyConnection(ActiveMQConnectionFactory factory) {
		Connection connection = null;
		try {
			connection = factory.createConnection();
		} catch (final JMSException e) {
			e.printStackTrace();
		}
		return connection;
	}

    public static void main(String[] args) throws Exception {
        System.out.println("Replication Context started");
        emptyLogFile();
        try {
            ReplicationStartContext.thread(new ReplicationStartContextThread(), false);
            Thread.sleep(1000);
        }
        catch (Exception e) {
            System.out.println("Replication Context could not be started.");
        }
    }

    public static void thread(Runnable runnable, boolean daemon) {
        Thread brokerThread = new Thread(runnable);
        brokerThread.setDaemon(daemon);
        brokerThread.start();
    }
    
	public static void emptyLogFile() {
		try {
			File file;
			if(SystemUtils.IS_OS_UNIX) {
				file = new File("/home/ubuntu/logs/log.txt");
			}
			else {
				file = new File("log.txt");
			}
			if(file.exists()) {
				FileWriter fWriter = new FileWriter(file.getAbsoluteFile(), true);
				BufferedWriter bWriter = new BufferedWriter(fWriter);
				bWriter.write("");
				bWriter.close();
			}
		}
		catch(IOException e) {
			e.printStackTrace();
		}
	}
	 
	public static synchronized void writeToLogFile(String text) {
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
			bWriter.write(text);
			bWriter.newLine();
			bWriter.close();
		}
		catch(IOException e) {
			
		}
	}
	
	public static void decideWhetherAnEngineShouldFail(Random randomGenerator, int currentFail) {
		WorkflowEngineRegistry registry = new WorkflowEngineRegistry();
		ArrayList<WorkflowEngine> workflowEngines = new ArrayList<WorkflowEngine>();
		try {
			workflowEngines = registry.getAllActiveEngines();
		} catch (Exception e) {
			e.printStackTrace();
		}
		int failureProbability = 602;
		for(WorkflowEngine currentEngine : workflowEngines) {
			int randomNumber = randomGenerator.nextInt(failureProbability * workflowEngines.size());
			if (Constants.DEBUG_LEVEL > 0) {
				System.out.println("currentFail: " + currentFail);
				System.out.println("randomNr: " + randomNumber);
			}
			if(currentFail > randomNumber) {
				errorOccured = true;
				//Send a failure message to the engine
				try {
	                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
	               
                	Destination failureDestination = session.createQueue("de.unistuttgart.rep." + currentEngine.getWorkflowEngineIp());
                	MessageProducer failureProducer = session.createProducer(failureDestination);  
                	
//                	SynchronizationMessage syncMessage = new SynchronizationMessage();
//                	syncMessage.set_message("Fail");
                	ObjectMessage failureMessage = session.createObjectMessage(new Boolean(true));
                	failureProducer.send(failureMessage);
	    
				}
				catch(Exception e) {
					
				}		
			}
		}
		
	}
    
	public static void readRoundRobinConfig() {
		BufferedReader reader = null;
		String roundRobin = "false";
		try {
			if(SystemUtils.IS_OS_UNIX) {
				reader = new BufferedReader(new FileReader ("/home/ubuntu/configs/robin.txt"));
			}
			else {
				reader = new BufferedReader(new FileReader ("robin.txt"));
			}
			roundRobin = reader.readLine();
			System.out.println(roundRobin);		
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		finally {
			try {
				if(reader != null) {
					reader.close();
				}
			}
			catch(IOException e) {
				e.printStackTrace();
			}
		}
		if(roundRobin.equals("true")) {
			withRoundRobin = true; 
		}
		else {
			withRoundRobin = false;
		}
	}
	
	public static void readFailureConfig() {
		BufferedReader reader = null;
		String failure = "false";
		String failureInitiator = "false";
		try {
			if(SystemUtils.IS_OS_UNIX) {
				reader = new BufferedReader(new FileReader ("/home/ubuntu/configs/failure.txt"));
			}
			else {
				reader = new BufferedReader(new FileReader ("failure.txt"));
			}
			failure = reader.readLine();
			failureInitiator = reader.readLine();
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		finally {
			try {
				if(reader != null) {
					reader.close();
				}
			}
			catch(IOException e) {
				e.printStackTrace();
			}
		}
		if(failure.equals("true")) {
			withFailure = true; 
		}
		else {
			withFailure = false;
		}
		if(failureInitiator.equals("true")) {
			initiateFailure = true; 
		}
		else {
			initiateFailure = false;
		}
	}
	
	public static void getHeartbeatRateAndTimeout() {
		BufferedReader reader = null;
		try {
			if(SystemUtils.IS_OS_UNIX) {
				reader = new BufferedReader(new FileReader ("/home/ubuntu/configs/timeout.txt"));
			}
			else {
				reader = new BufferedReader(new FileReader ("timeout.txt"));
			}
			heartbeatRate = Integer.parseInt(reader.readLine());
			timeout = Integer.parseInt(reader.readLine());
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		finally {
			try {
				if(reader != null) {
					reader.close();
				}
			}
			catch(IOException e) {
				e.printStackTrace();
			}
		}
	}
	
    public static class ReplicationStartContextThread
    implements Runnable {
    	
        public void run() {
            ReplicationStartContext.ReplicationStartContextThread replicationStartContextThread = this;
            synchronized (replicationStartContextThread) {
                try {
                    SimpleRegistry registry = new SimpleRegistry();
                    registry.put("replicationDeploymentProcessor", new ReplicationDeploymentProcessor());
                    registry.put("replicationStartProcessor", new ReplicationStartProcessor());
                    registry.put("workflowEngineProcessor", new WorkflowEngineProcessor());
                    DefaultCamelContext context = new DefaultCamelContext(registry);
                    //connection = connection_factory.createConnection();
                    connection.start();
                    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    session.createQueue("deploy");
                    session.createQueue("start");
                    session.createQueue("de.unistuttgart.rep");
                    context.addRoutes(new ReplicationDeploymentRoute());
                    context.addRoutes(new WorkflowEngineRoute());
                    
                    emptyLogFile();
                    getHeartbeatRateAndTimeout();
                    System.out.println("This is the timeout: " + timeout);
                    readRoundRobinConfig();
                    readFailureConfig();
                 
                    context.start();  
                    if(Constants.DEBUG_LEVEL > 0) {
                    	System.out.println("context running");
                    }
                    
                    if(withFailure && initiateFailure) {
                    	while(!startFailureNow) {
                    		this.wait(10000);
                    	}
//	                    this.wait(60000);
	                    int currentFail = 0;
	                    boolean x = true;
	                    long time = (new Date()).getTime();
	                    while (x) {
	                    	long curTime = (new Date()).getTime();
	                    	if(curTime - time >= 10000) {
	                    		currentFail = currentFail+5;
	                    		time = curTime;
	                    	}
	                        this.wait(1000);
	                        final int finalFail = currentFail;
	                        Thread thread = new Thread(){
	                            public void run(){
	                            	decideWhetherAnEngineShouldFail(randomGenerator, finalFail);
	                            }
	                        };
	                        thread.start();
	                    }	
                    }
                    else {
                    	boolean x = true;
                    	while (x) {
                    		this.wait(10000);
                    	}
                    }
                    context.stop();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                finally {
                    try {
						connection.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
                    emf.close();
                }
            }
        }
    }
}