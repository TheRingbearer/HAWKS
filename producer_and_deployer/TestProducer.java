package krawczls.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
//import java.util.Timer;
import java.util.TimerTask;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.lang.SystemUtils;

//import krawczls.messages.ProcessDeploymentMessage;
//import krawczls.messages.Process;
import krawczls.messages.ProcessStartMessage;

public class TestProducer {
	
	public static Connection connection = null;
	public static Connection connection2 = null;
	public static Connection connection3 = null;
	public static Connection connection4 = null;
	public static Connection connection5 = null;
	public static Connection connection6 = null;
	public static Connection connection7 = null;
	public static Connection connection8 = null;
	public static Connection connection9 = null;
	public static Connection connection10 = null;
	
	public class ScheduledTask extends TimerTask {
		public void run() {
			
		}
	}
	
	//TODO change this functions a lot
	public static synchronized byte[] fileToByteArray(File file){
	
		byte[] fileArray = null;
		try {
			fileArray = org.apache.commons.io.IOUtils.toByteArray(new FileInputStream(file));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
		return fileArray;
	}
	
	public static void main(String[] args) throws Exception {
			
			ArrayList<MessageProducer> producerList = new ArrayList<MessageProducer>();
		
			ActiveMQConnectionFactory connection_factory = new ActiveMQConnectionFactory("tcp://192.168.209.6:61616");
			connection = connection_factory.createConnection();
			connection.start();
			Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Destination destinationStart = session.createQueue("start");
			//MessageProducer producer = session.createProducer(destinationStart);
			producerList.add(session.createProducer(destinationStart));
			
			ActiveMQConnectionFactory connection_factory2 = new ActiveMQConnectionFactory("tcp://192.168.209.89:61616");
			connection2 = connection_factory2.createConnection();
			connection2.start();
			Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Destination destinationStart2 = session2.createQueue("start");
			producerList.add(session2.createProducer(destinationStart2));
			
			ActiveMQConnectionFactory connection_factory3 = new ActiveMQConnectionFactory("tcp://192.168.209.52:61616");
			connection3 = connection_factory3.createConnection();
			connection3.start();
			Session session3 = connection3.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Destination destinationStart3 = session3.createQueue("start");
			producerList.add(session3.createProducer(destinationStart3));
			
			ActiveMQConnectionFactory connection_factory4 = new ActiveMQConnectionFactory("tcp://192.168.209.9:61616");
			connection4 = connection_factory4.createConnection();
			connection4.start();
			Session session4 = connection4.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Destination destinationStart4 = session4.createQueue("start");
			producerList.add(session4.createProducer(destinationStart4));
			
			ActiveMQConnectionFactory connection_factory5 = new ActiveMQConnectionFactory("tcp://192.168.209.73:61616");
			connection5 = connection_factory5.createConnection();
			connection5.start();
			Session session5 = connection5.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Destination destinationStart5 = session5.createQueue("start");
			producerList.add(session5.createProducer(destinationStart5));
			
			ActiveMQConnectionFactory connection_factory6 = new ActiveMQConnectionFactory("tcp://192.168.209.81:61616");
			connection6 = connection_factory6.createConnection();
			connection6.start();
			Session session6 = connection6.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Destination destinationStart6 = session6.createQueue("start");
			producerList.add(session6.createProducer(destinationStart6));
			
			ActiveMQConnectionFactory connection_factory7 = new ActiveMQConnectionFactory("tcp://192.168.209.82:61616");
			connection7 = connection_factory7.createConnection();
			connection7.start();
			Session session7 = connection7.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Destination destinationStart7 = session7.createQueue("start");
			producerList.add(session7.createProducer(destinationStart7));
			
			ActiveMQConnectionFactory connection_factory8 = new ActiveMQConnectionFactory("tcp://192.168.209.79:61616");
			connection8 = connection_factory8.createConnection();
			connection8.start();
			Session session8 = connection8.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Destination destinationStart8 = session8.createQueue("start");
			producerList.add(session8.createProducer(destinationStart8));
			
			ActiveMQConnectionFactory connection_factory9 = new ActiveMQConnectionFactory("tcp://192.168.209.71:61616");
			connection9 = connection_factory9.createConnection();
			connection9.start();
			Session session9 = connection9.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Destination destinationStart9 = session9.createQueue("start");
			producerList.add(session9.createProducer(destinationStart9));
			
			ActiveMQConnectionFactory connection_factory10 = new ActiveMQConnectionFactory("tcp://192.168.209.69:61616");
			connection10 = connection_factory10.createConnection();
			connection10.start();
			Session session10 = connection10.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Destination destinationStart10 = session10.createQueue("start");
			producerList.add(session10.createProducer(destinationStart10));
			
			final ArrayList<MessageProducer> finalProducerList = producerList;
			
		BufferedReader reader = null;
		int replicationDegree = 3;
		int amountOfWorkflows = 10;
		int timeBetweenExecution = 60000;
		int maximumLoad = 100;
		int withoutShorteningOfTimeIntervals = 0;
		int timeBetweenReplicationGrades = 600000;
		int changeReplicationDegreeDynamically = 0;
		int numberOfIterations = 15;
		try {
			if(SystemUtils.IS_OS_UNIX) {
				reader = new BufferedReader(new FileReader ("/home/ubuntu/configs/test.txt"));
			}
			else {
				reader = new BufferedReader(new FileReader ("test.txt"));
			}
			replicationDegree = Integer.parseInt(reader.readLine());
			amountOfWorkflows = Integer.parseInt(reader.readLine());
			timeBetweenExecution = Integer.parseInt(reader.readLine());
			maximumLoad = Integer.parseInt(reader.readLine());
			withoutShorteningOfTimeIntervals = Integer.parseInt(reader.readLine());
			timeBetweenReplicationGrades = Integer.parseInt(reader.readLine());
			changeReplicationDegreeDynamically = Integer.parseInt(reader.readLine());
			numberOfIterations = Integer.parseInt(reader.readLine());
			
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

		String start = "<?xml version='1.0' encoding='utf-8'?>"
					   + "<soapenv:Envelope "
                       + "xmlns:q0=\"http://eclipse.org/bpel/sample\" " 
                       + "xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" "  
                       + "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" "  
                       + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"  
                       + "<soapenv:Header>"
                       + "</soapenv:Header>"  
                       + "<soapenv:Body>"
                       + "<q0:HelloBPELProcessRequest>"
                       + "<q0:input>hello</q0:input>"
                       + "</q0:HelloBPELProcessRequest>"
                       + "</soapenv:Body>"  
                       + "</soapenv:Envelope>";
		
		//String start = request.toString();
		
		/*
		if(withAllReplicationGrades == 0) {
			if(withTimerTask == 1) {
				for(int i = 0; i < numberOfIterations; i++) {
					final int number = i;
					final int finalWorkflows = amountOfWorkflows;
					final String finalStart = start;
					final int finalReplicationDegree = replicationGrade;
			        Timer timer = new Timer();
			        timer.schedule(new TimerTask() {
			            public void run() {
							System.out.println("Start execution with " + Math.pow(2, number)*finalWorkflows + " workflows.");
							//TODO -change to check for the process name rather than the id	
							Session mySession = null;
							Destination destination = null;
							MessageProducer producer = null;
							try {
								mySession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
								destination = mySession.createQueue("start");
								producer = mySession.createProducer(destination);
							} catch (JMSException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
							for(int i = 0; i < (Math.pow(2, number)*finalWorkflows); i++) {
								ProcessStartMessage message2 = new ProcessStartMessage();		
								message2.setProcessName("HelloBPELProcess");
								message2.setProcessServiceName("HelloBPELService/process");
								message2.setNamespace("{http://eclipse.org/bpel/sample}");
								message2.setMessage(finalStart);
								message2.setReplicationGrade(finalReplicationDegree);
								try {
									ObjectMessage startMessage = mySession.createObjectMessage(message2);
								
									producer.send(startMessage);
								}
								catch(Exception e) {
									e.printStackTrace();
								}
							}
			            }
			        }, (i)*timeBetweenExecution);
				}
			}
			else {
				while(amountOfWorkflows <= maximumLoad) {
					System.out.println("Start execution with " + amountOfWorkflows + " workflows.");
					//TODO -change to check for the process name rather than the id	
					for(int i = 0; i < amountOfWorkflows; i++) {
						ProcessStartMessage message2 = new ProcessStartMessage();		
						message2.setProcessName("HelloBPELProcess");
						message2.setProcessServiceName("HelloBPELService/process");
						message2.setNamespace("{http://eclipse.org/bpel/sample}");
						message2.setMessage(start);
						message2.setReplicationGrade(replicationGrade);
						
						ObjectMessage startMessage = session.createObjectMessage(message2);
						
						producerStart.send(startMessage);	
					}
					amountOfWorkflows = 2*amountOfWorkflows;
					try {
						Thread.sleep(timeBetweenExecution);
					}
					catch (InterruptedException e) {
					}
				}
			}
		}
		else {
			if(withTimerTask == 1) {
				for(int i = 0; i < 5; i++) {
					switch(i) {
					case(0):replicationGrade = 1;
							System.out.println("replication grade 1");
							break;
					case(1):replicationGrade = 3;
							System.out.println("replication grade 3");
							break;
					case(2):replicationGrade = 5;
							System.out.println("replication grade 5");
							break;
					case(3):replicationGrade = 9;
							System.out.println("replication grade 9");
							break;
					case(4):replicationGrade = 17;
							System.out.println("replication grade 17");
					}
	
					for(int j = 0; j < numberOfIterations; j++) {
						final int number = j;
						final int finalWorkflows = amountOfWorkflows;
						final String finalStart = start;
						final int finalReplicationDegree = replicationGrade;
					    Timer timer = new Timer();
					    timer.schedule(new TimerTask() {
					    	public void run() {
								System.out.println("Start execution with " + Math.pow(2, number)*finalWorkflows + " workflows.");
								//TODO -change to check for the process name rather than the id	
								Session mySession = null;
								Destination destination = null;
								MessageProducer producer = null;
								try {
									mySession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
									destination = mySession.createQueue("start");
									producer = mySession.createProducer(destination);
								} catch (JMSException e1) {
									// TODO Auto-generated catch block
									e1.printStackTrace();
								}
								for(int k = 0; k < (Math.pow(2, number)*finalWorkflows); k++) {
									ProcessStartMessage message2 = new ProcessStartMessage();		
									message2.setProcessName("HelloBPELProcess");
									message2.setProcessServiceName("HelloBPELService/process");
									message2.setNamespace("{http://eclipse.org/bpel/sample}");
									message2.setMessage(finalStart);
									message2.setReplicationGrade(finalReplicationDegree);
									try {
										
										ObjectMessage startMessage = mySession.createObjectMessage(message2);
										
										producer.send(startMessage);
									}
									catch(Exception e) {
										e.printStackTrace();
									}
								}
					    	}
					    }, (j)*timeBetweenExecution);	
					}
					System.out.println("done with replication grade " + replicationGrade);
					try {
						Thread.sleep(timeBetweenReplicationGrades);
					}
					catch (InterruptedException e) {
					}
				}
			}
			else {
				for(int i = 0; i < 5; i++) {
					switch(i) {
					case(0):replicationGrade = 1;
							System.out.println("replication grade 1");
							break;
					case(1):replicationGrade = 3;
							System.out.println("replication grade 3");
							break;
					case(2):replicationGrade = 5;
							System.out.println("replication grade 5");
							break;
					case(3):replicationGrade = 9;
							System.out.println("replication grade 9");
							break;
					case(4):replicationGrade = 17;
							System.out.println("replication grade 17");
					}
					int buffer = amountOfWorkflows;
					while(buffer <= maximumLoad) {
						System.out.println("Start execution with " + buffer + " workflows.");
	
						for(int j = 0; j < buffer; j++) {
							ProcessStartMessage message2 = new ProcessStartMessage();		
							message2.setProcessName("HelloBPELProcess");
							message2.setProcessServiceName("HelloBPELService/process");
							message2.setNamespace("{http://eclipse.org/bpel/sample}");
							message2.setMessage(start);
							message2.setReplicationGrade(replicationGrade);
							//TODO -change to check for the process name rather than the id	
							ObjectMessage startMessage = session.createObjectMessage(message2);
							
							producerStart.send(startMessage);	
						}
						buffer = 2*buffer;
						try {
							Thread.sleep(timeBetweenExecution);
						}
						catch (InterruptedException e) {
						}
					}
					System.out.println("done with replication grade " + replicationGrade);
					try {
						Thread.sleep(timeBetweenReplicationGrades);
					}
					catch (InterruptedException e) {
					}
				}
			}
		}
		*/
		int workflowCounter = 0;
		final String finalStart = start;
		final int finalReplicationDegree = replicationDegree;
		
		
		Session mySession = null;
		Destination destination = null;	
		try {
			mySession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			destination = mySession.createQueue("start");
			//producer = mySession.createProducer(destination);
		} catch (JMSException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		final Session finalSession = mySession;
		//final MessageProducer finalProducer = producer;
		//A loop that continuously increases the load on the middleware, without
		//starting workflows concurrently
		int producerListIndex = 0;
		while(workflowCounter < maximumLoad) {
			/*if(producerListIndex > 5) {
				producerListIndex = 0;
			}*/
			if(withoutShorteningOfTimeIntervals == 1) {
				if(producerListIndex >= producerList.size()) {
					producerListIndex = 0;
				}
				final MessageProducer messageProducer = producerList.get(producerListIndex);
				Thread thread = new Thread( ){
					public void run() {
						ProcessStartMessage message2 = new ProcessStartMessage();		
						message2.setProcessName("HelloBPELProcess");
						message2.setProcessServiceName("HelloBPELService/process");
						message2.setNamespace("{http://eclipse.org/bpel/sample}");
						message2.setMessage(finalStart);
						message2.setReplicationGrade(finalReplicationDegree);
						
						try {
							ObjectMessage startMessage = finalSession.createObjectMessage(message2);
									
							messageProducer.send(startMessage);
						}
						catch(Exception e) {
							e.printStackTrace();
						}
					}
				};
				thread.start();
				workflowCounter++;
				Thread.sleep(timeBetweenExecution);
				producerListIndex++;
			}
			else {
				//final MessageProducer messageProducer = producerList.get(producerListIndex);
				//final MessageProducer messageProducer = producer;
				long startTime = (new Date()).getTime();
				while((new Date()).getTime() - startTime < 300000) {
					if(producerListIndex >= producerList.size()) {
						producerListIndex = 0;
					}
					final MessageProducer messageProducer = producerList.get(producerListIndex);
					//Starting a new thread that sends the start message to the middleware, everytime
					//we reach the limit in timeBetweenExecution
					Thread thread = new Thread( ){
						public void run() {
							ProcessStartMessage message2 = new ProcessStartMessage();		
							message2.setProcessName("HelloBPELProcess");
							message2.setProcessServiceName("HelloBPELService/process");
							message2.setNamespace("{http://eclipse.org/bpel/sample}");
							message2.setMessage(finalStart);
							message2.setReplicationGrade(finalReplicationDegree);
									
							try {
								ObjectMessage startMessage = finalSession.createObjectMessage(message2);
										
								messageProducer.send(startMessage);
							}
							catch(Exception e) {
								e.printStackTrace();
							}
						}
					};
					thread.start();
					workflowCounter++;
					Thread.sleep(timeBetweenExecution);
					producerListIndex++;
				}
				timeBetweenExecution = timeBetweenExecution / 2;
			}
			//producerListIndex++;
		}
		
		boolean x = true;
		while(x) {
			Thread.sleep(100000);
		}
		try {
			   Thread.sleep(10000);
			}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
		session.close();
		connection.close();
		session2.close();
		connection2.close();
		session3.close();
		connection3.close();
		session4.close();
		connection4.close();
		session5.close();
		connection5.close();
		session6.close();
		connection6.close();
		session7.close();
		connection7.close();
		session8.close();
		connection8.close();
		session9.close();
		connection9.close();
		session10.close();
		connection10.close();
		System.out.println("Done");
		
		//TODO delete registry entries when finished
	}
}