package org.apache.ode.bpel.extensions.sync;

import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;


/**
 * This object stores all the information about a SynchronizationController instance 
 * the SynchronizationMiddleware needs to know.
 * 
 * @author krawczls
 *
 */
public class SynchronizationControllerRepresentation{
	
	public long this_processID;
	public String this_replicated_workflow_id;
	
	public Destination this_source_process;
	public Destination this_destination_process;
	
	public Destination this_source_middleware;
	public Destination this_destination_middleware;
	
	public MessageConsumer this_consumer_process;
	public MessageProducer this_producer_process;
	
	public MessageConsumer this_consumer_middleware;
	public MessageProducer this_producer_middleware;
	
	/**
	 * A constructor used to associate a SynchronizationController instance with a specific process.
	 * Also sets up communication with the WorkflowReplicationMiddleware through the SynchronizationMiddleware.
	 * 
	 * @param processID
	 * @param session
	 */
	public SynchronizationControllerRepresentation(long processID, Session session, String replicated_workflow_id) {
		try {
			this_processID = processID;
			this_replicated_workflow_id = replicated_workflow_id;
		
			Session this_session = session;
		
			this_source_process = this_session.createQueue("to process " + String.valueOf(this_processID));
			this_destination_process = this_session.createQueue("from process " + String.valueOf(this_processID));
			
			this_consumer_process = this_session.createConsumer(this_source_process);
			this_producer_process = this_session.createProducer(this_destination_process);
			
			this_source_middleware = this_session.createQueue("to Middleware from " + this_replicated_workflow_id);
			this_destination_middleware =this_session.createQueue("from Middleware to " + this_replicated_workflow_id);
			
			this_consumer_middleware = this_session.createConsumer(this_source_middleware);
			this_producer_middleware = this_session.createProducer(this_destination_middleware);
		}
		catch(Exception e) {
			System.out.println("SynchronizationControllerRepresentation could not be instantiated.");
		}
		
	}

}