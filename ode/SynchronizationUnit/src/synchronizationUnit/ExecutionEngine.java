package synchronizationUnit;

import java.util.ArrayList;

import org.apache.activemq.ActiveMQConnectionFactory;

import java.io.Serializable;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;

public class ExecutionEngine{
	
	private String this_ip;
	private int this_view;
	protected ActiveMQConnectionFactory this_connection_factory;
	protected Connection this_connection;
	protected Session this_session;
	protected Destination this_destination;
	protected Destination this_source;
	protected MessageProducer this_producer;
	protected MessageConsumer this_consumer;
	
	
	public ExecutionEngine(String ip, int view){
		this_ip = ip;
		this_view = view;
	}
	
	public String get_ip(){
		return this_ip;
	}
	
	public int get_view(){
		return this_view;
	}
	
	public void setup_connection(String ip, Session session_local) throws JMSException{
		this_connection_factory = new ActiveMQConnectionFactory("tcp://" + this_ip + ":61616");
		this_connection = this_connection_factory.createConnection();
		this_connection.start();
		this_session = this_connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		this_destination = this_session.createQueue("to: " + this_ip);
		this_source = session_local.createQueue("to: " + ip);
		this_producer = this_session.createProducer(this_destination);
		this_consumer = session_local.createConsumer(this_source);
	}
	
	public void close_connection() throws JMSException{
		this_session.close();
		this_connection.close();
	}
	
	public void send_message(SynchronizationMessage message) throws JMSException{
		ObjectMessage object_message = this_session.createObjectMessage(message);
		this_producer.send(object_message);
	}
	
	public void send_message(ArrayList message) throws JMSException{
		ObjectMessage object_message = this_session.createObjectMessage(message);
		this_producer.send(object_message);
	}
	
	public Serializable receive_message() throws JMSException{
		Message message = this_consumer.receive();
		ObjectMessage object_message = (ObjectMessage) message;
		return object_message.getObject();
	}
	
	public Serializable receive_message(long timeout) throws JMSException{
		Message message = this_consumer.receive(timeout);
		if(message != null){
			ObjectMessage object_message = (ObjectMessage) message;
			return object_message.getObject();
		}
		else{
			return null;
		}
	}
}