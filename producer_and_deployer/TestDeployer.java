package krawczls.deploy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;

import krawczls.messages.ProcessDeploymentMessage;
import krawczls.messages.Process;

public class TestDeployer {
	
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
		//1
		ActiveMQConnectionFactory connection_factory = new ActiveMQConnectionFactory("tcp://192.168.209.6:61616");
		Connection connection = connection_factory.createConnection();
		connection.start();
		Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		Destination destinationDeploy = session.createQueue("deploy");
		producerList.add(session.createProducer(destinationDeploy));
		//2
		ActiveMQConnectionFactory connection_factory2 = new ActiveMQConnectionFactory("tcp://192.168.209.89:61616");
		Connection connection2 = connection_factory2.createConnection();
		connection2.start();
		Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);
		Destination destinationDeploy2 = session2.createQueue("deploy");
		producerList.add(session2.createProducer(destinationDeploy2));
		//3
		ActiveMQConnectionFactory connection_factory3 = new ActiveMQConnectionFactory("tcp://192.168.209.52:61616");
		Connection connection3 = connection_factory3.createConnection();
		connection3.start();
		Session session3 = connection3.createSession(false, Session.AUTO_ACKNOWLEDGE);
		Destination destinationDeploy3 = session3.createQueue("deploy");
		producerList.add(session3.createProducer(destinationDeploy3));
		//4
		ActiveMQConnectionFactory connection_factory4 = new ActiveMQConnectionFactory("tcp://192.168.209.9:61616");
		Connection connection4 = connection_factory4.createConnection();
		connection4.start();
		Session session4 = connection4.createSession(false, Session.AUTO_ACKNOWLEDGE);
		Destination destinationDeploy4 = session4.createQueue("deploy");
		producerList.add(session4.createProducer(destinationDeploy4));
		//5
		ActiveMQConnectionFactory connection_factory5 = new ActiveMQConnectionFactory("tcp://192.168.209.73:61616");
		Connection connection5 = connection_factory5.createConnection();
		connection5.start();
		Session session5 = connection5.createSession(false, Session.AUTO_ACKNOWLEDGE);
		Destination destinationDeploy5 = session5.createQueue("deploy");
		producerList.add(session5.createProducer(destinationDeploy5));
		//6
		ActiveMQConnectionFactory connection_factory6 = new ActiveMQConnectionFactory("tcp://192.168.209.81:61616");
		Connection connection6 = connection_factory6.createConnection();
		connection6.start();
		Session session6 = connection6.createSession(false, Session.AUTO_ACKNOWLEDGE);
		Destination destinationDeploy6 = session6.createQueue("deploy");
		producerList.add(session6.createProducer(destinationDeploy6));
		//7
		ActiveMQConnectionFactory connection_factory7 = new ActiveMQConnectionFactory("tcp://192.168.209.82:61616");
		Connection connection7 = connection_factory7.createConnection();
		connection7.start();
		Session session7 = connection7.createSession(false, Session.AUTO_ACKNOWLEDGE);
		Destination destinationDeploy7 = session7.createQueue("deploy");
		producerList.add(session7.createProducer(destinationDeploy7));
		//8
		ActiveMQConnectionFactory connection_factory8 = new ActiveMQConnectionFactory("tcp://192.168.209.79:61616");
		Connection connection8 = connection_factory8.createConnection();
		connection8.start();
		Session session8 = connection8.createSession(false, Session.AUTO_ACKNOWLEDGE);
		Destination destinationDeploy8 = session8.createQueue("deploy");
		producerList.add(session8.createProducer(destinationDeploy8));
		//9
		ActiveMQConnectionFactory connection_factory9 = new ActiveMQConnectionFactory("tcp://192.168.209.71:61616");
		Connection connection9 = connection_factory9.createConnection();
		connection9.start();
		Session session9 = connection9.createSession(false, Session.AUTO_ACKNOWLEDGE);
		Destination destinationDeploy9 = session9.createQueue("deploy");
		producerList.add(session9.createProducer(destinationDeploy9));
		//10
		ActiveMQConnectionFactory connection_factory10 = new ActiveMQConnectionFactory("tcp://192.168.209.69:61616");
		Connection connection10 = connection_factory10.createConnection();
		connection10.start();
		Session session10 = connection10.createSession(false, Session.AUTO_ACKNOWLEDGE);
		Destination destinationDeploy10 = session10.createQueue("deploy");
		producerList.add(session10.createProducer(destinationDeploy10));
		
		for(int i = 0; i < producerList.size(); i++) {
			File file = new File("HelloBPELProcess.zip");
			byte[] zipFile = fileToByteArray(file);
			Process process = new Process();
			process.setProcessName("HelloBPELProcess");
			process.setProcessFileName("HelloBPELProcess");
			process.setProcessFolderZip(zipFile);
			ProcessDeploymentMessage message = new ProcessDeploymentMessage();
			message.setProcess(process);
			if(i == 0) {
				message.setDeploy(true);
			}
			ObjectMessage deployMessage = session.createObjectMessage(message);
			producerList.get(i).send(deployMessage);
		}	
		try {
			   Thread.sleep(10000);
			   System.exit(0);
			}
		catch (InterruptedException e) {
		}
		
		
		//create the message body and serialize to string
    	/*ServiceClientUtil _client = new ServiceClientUtil();
		OMFactory _factory = OMAbstractFactory.getOMFactory();
		OMNamespace sample = _factory.createOMNamespace("http://eclipse.org/bpel/sample", "sample");
		OMElement request = _factory.createOMElement("HelloBPELProcessRequest", sample);
		OMElement input = _factory.createOMElement("input", sample);
		input.setText("Hello");
		request.addChild(input);*/
		
		/*SOAPFactory fac = OMAbstractFactory.getSOAP11Factory();
		SOAPEnvelope soapEnvelope = fac.getDefaultEnvelope();
		OMFactory omFactory = soapEnvelope.getOMFactory();
		OMNamespace sample = omFactory.createOMNamespace("http://eclipse.org/bpel/sample", "sample");
		OMElement request = omFactory.createOMElement("HelloBPELProcessRequest", sample);
		OMElement input = omFactory.createOMElement("input", sample);
		input.setText("Hello");
		request.addChild(input);
		soapEnvelope.getBody().addChild(request);
		String st = soapEnvelope.toString();
		System.out.println(st);*/
		
		/*try {
			   Thread.sleep(10000);
			}
		catch (InterruptedException e) {
			e.printStackTrace();
		}*/
		
		//TODO delete registry entries when finished
		
		
		//Deployment deployer = new Deployment();
		//deployer.setUp();
	}
}