package org.apache.ode.bpel.extensions.comm;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueReceiver;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.Topic;
import javax.xml.namespace.QName;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.ode.bpel.extensions.GenericController;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.ActivityEventMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.InstanceEventMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.LinkEventMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.MessageBase;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.ProcessEventMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification_At_Assign;
import org.apache.ode.bpel.extensions.handler.IncomingMessageHandler;
import org.apache.ode.bpel.extensions.handler.InstanceEventHandler;
import org.apache.ode.bpel.extensions.sync.Constants;

//@stmz: establishes a JMS Connection. Uses outbound topic for event publishing and an 
//inbound queue for receiving messages
public class Communication implements IConstants {
	private static Communication instance;

	private ConnectionFactory factory;
	private Connection outConnection;
	private Session outSession;
	private Topic outTopic;
	private MessageProducer outPublisher;

	private QueueConnectionFactory queueConnectionFactory;
	private QueueConnection inConnection;
	private QueueSession inSession;
	private Queue inQueue;
	private QueueReceiver inReceiver;

	private Boolean initialized;
	
	// @hahnml: Buffer the IP address of the local machine
	private String odeHostIP;

	private Long msgID = 0L;

	// @stmz: startup the connections
	private Communication() {
		initialized = false;

		if (Constants.DEBUG_LEVEL > 0) {
			System.out.println("Communication instantiated.");
		}
	}
	
	public void startup(String activeMQURL) {
		factory = new ActiveMQConnectionFactory(activeMQURL);
		try {
			outConnection = factory.createConnection();
			outSession = outConnection.createSession(false,
					Session.AUTO_ACKNOWLEDGE);
			outTopic = outSession.createTopic(ENGINE_OUT);
			outPublisher = outSession.createProducer(outTopic);
			outPublisher.setDeliveryMode(DeliveryMode.PERSISTENT);

			queueConnectionFactory = new ActiveMQConnectionFactory(activeMQURL);
			inConnection = queueConnectionFactory.createQueueConnection();
			inSession = inConnection.createQueueSession(false,
					QueueSession.AUTO_ACKNOWLEDGE);
			inQueue = inSession.createQueue(ENGINE_IN);
			inReceiver = inSession.createReceiver(inQueue);

			MessageDispatcher messRec = new MessageDispatcher();
			inReceiver.setMessageListener(messRec);
			inConnection.start();
			initialized = true;
			
			// @hahnml: Get the ip address of the machine
			InetAddress inetAddress = InetAddress.getLocalHost();
			odeHostIP = inetAddress.getHostAddress();
		} catch (JMSException e) {
			System.out.println("\nInitialization of JMS-Connection failed.");
			System.out.println(e + "\n");
		} catch (UnknownHostException e) {
			System.out.println("\nInitialization of JMS-Connection failed.");
			System.out.println(e + "\n");
		}
	}

	// @stmz: shutdown the connections
	public void shutdown() {
		try {
			if (outConnection != null) {
				outConnection.stop();
				outConnection.close();
				outConnection = null;
			}
			if (inConnection != null) {
				inConnection.stop();
				inConnection.close();
				inConnection = null;
			}
			initialized = false;
			if (Constants.DEBUG_LEVEL > 0) {
				System.out.println("\nJMS Connection shutdown.\n");
			}
		} catch (JMSException e) {
			System.out.println(e);
			initialized = false;
		}
		factory = null;
	}

	// @stmz: create a unique message id for every message that is sent
	public synchronized Long getMessageID() {
		msgID++;
		return msgID;
	}

	public void fillMessageBase(MessageBase message, Long genConID) {
		message.setGenericControllerID(genConID);
		message.setTimeStamp(new Long(System.currentTimeMillis()));
		message.setMessageID(getMessageID());
		message.setBlocking(false);
	}

	public void fillProcessEventMessage(ProcessEventMessage message,
			Long genConID, QName processName) {
		fillMessageBase(message, genConID);
		message.setProcessName(processName);
		
		// @hahnml: Set the host ip
		message.setODEHostIP(odeHostIP);
	}

	public void fillInstanceEventMessage(InstanceEventMessage message,
			Long genConID, QName processName, Long processID) {
		fillProcessEventMessage(message, genConID, processName);
		message.setProcessID(processID);
		// @hahnml: Set the process version in every InstanceEventMessage
		message.setVersion(InstanceEventHandler.getInstance().getVersion(
				processID));
	}

	public void fillLinkEventMessage(LinkEventMessage message, Long genConID,
			QName processName, Long processID, Long scopeID, String scopeXPath,
			String linkName, String linkXPath) {
		fillInstanceEventMessage(message, genConID, processName, processID);
		message.setLinkName(linkName);
		message.setLinkXPath(linkXPath);
		message.setScopeID(scopeID);
		message.setScopeXPath(scopeXPath);
	}

	public void fillActivityEventMessage(ActivityEventMessage message,
			Long genConID, QName processName, Long processID, Long scopeID,
			String scopeXPath, Long activityID, String activityXPath,
			String activityName) {
		fillInstanceEventMessage(message, genConID, processName, processID);
		message.setScopeID(scopeID);
		message.setScopeXPath(scopeXPath);
		message.setActivityID(activityID);
		message.setActivityXPath(activityXPath);
		// @haupt
		message.setActivityName(activityName);
	}

	public void fillVariableModificationMessage(Variable_Modification message,
			Long genConID, QName processName, Long processID, Long scopeID,
			String scopeXPath, String activityXPath, String variableName,
			String variableXPath, String value) {
		fillInstanceEventMessage(message, genConID, processName, processID);
		message.setActivityXPath(activityXPath);
		message.setScopeID(scopeID);
		message.setScopeXPath(scopeXPath);
		message.setVariableName(variableName);
		message.setVariableXPath(variableXPath);
		message.setValue(value);
	}

	/**
	 * @author hahnml
	 * 
	 *         Fills the extended Variable_Modification_At_Assign message
	 */
	public void fillVariableModificationAtAssignMessage(
			Variable_Modification_At_Assign message, Long genConID,
			QName processName, Long processID, Long scopeID, String scopeXPath,
			String activityXPath, String variableName, String variableXPath,
			String value, int numberOfCopyStatement) {
		fillVariableModificationMessage(message, genConID, processName,
				processID, scopeID, scopeXPath, activityXPath, variableName,
				variableXPath, value);
		message.setNumberOfCopyStatement(numberOfCopyStatement);
	}

	// @stmz: for custom controller, we want to get a clean QName, without
	// "-version" at the end
	public QName cropQName(QName qname) {
		String name = qname.getLocalPart();
		String namespace = qname.getNamespaceURI();
		String name2;

		String[] parts = name.split("\\-");

		name2 = parts[0];

		for (int i = 1; i < parts.length; i++) {
			if (i < parts.length - 1) {
				name2 = name2 + "-";
				name2 = name2 + parts[i];
			}
		}

		QName newName = new QName(namespace, name2);

		return newName;
	}

	// @stmz: send a message to the topic
	public void sendMessageToTopic(Serializable aObject) {
		if (!initialized)
			return;
		else {
			try {
				ObjectMessage aObjectMessage = outSession.createObjectMessage();
				aObjectMessage.setObject(aObject);
				outPublisher.send(aObjectMessage);
			} catch (JMSException e) {
				System.out.println("\nMessage could not be send to Topic.");
				System.out.println(e + "\n");
			} catch (Exception e) {
				System.out.println("\nException while sending message to Topic.");
				System.out.println(e + "\n");
			}
		}
	}

	// @stmz: send a message to a specific destination
	public void sendMessageToDestination(Destination aDestination,
			Serializable aObject) {
		if (!initialized)
			return;
		else {
			try {
				Connection tempConnection = factory.createConnection();
				Session controllerTempSession = tempConnection.createSession(
						false, Session.AUTO_ACKNOWLEDGE);
				MessageProducer controllerTempProducer = controllerTempSession
						.createProducer(aDestination);
				tempConnection.start();

				ObjectMessage aObjectMessage = controllerTempSession
						.createObjectMessage();
				aObjectMessage.setObject(aObject);
				controllerTempProducer.send(aObjectMessage);
				controllerTempSession.close();
				tempConnection.stop();
				tempConnection.close();
			} catch (JMSException e) {
				System.out.println("\ninvalid destination ==> controller removed.");
				System.out.println(e + "\n");
				IncomingMessageHandler incMess = IncomingMessageHandler
						.getInstance();
				incMess.unregister(aDestination);
			} catch (Exception e) {
				System.out.println("\nException while sending message to given Destination.");
				System.out.println(e + "\n");
			}
		}
	}

	public static Communication getInstance() {
		if (instance == null) {
			instance = new Communication();
		}
		return instance;
	}
}
