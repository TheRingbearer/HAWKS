package synchronizationUnit;

import java.util.ArrayList;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.RegisterRequestMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.RequestRegistrationInformation;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Skip_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Start_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Write_Variable;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Ready;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.RegisterResponseMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.RegistrationInformationMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification_At_Assign;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Complete;

import java.io.Serializable;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;

public class SynchronizationController {
 
    public static void main(String[] args) throws Exception {
        	thread(new SynchronizationControllerThread(), false);
        	Thread.sleep(1000);
    }
 
    public static void thread(Runnable runnable, boolean daemon) {
        Thread brokerThread = new Thread(runnable);
        brokerThread.setDaemon(daemon);
        brokerThread.start();
    }
 
    public static class SynchronizationControllerThread implements Runnable {
   	
        public void run() {
        	
        	synchronized(this){
        	
            try {
            	//TODO read ip's automatically
            	String my_ip = "129.69.185.166";
            	
                ActiveMQConnectionFactory connectionFactory_local = new ActiveMQConnectionFactory("tcp://localhost:61616");
                ActiveMQConnectionFactory connectionFactory_remote = new ActiveMQConnectionFactory("tcp://129.69.185.159:61616");
                
                Connection connection_local = connectionFactory_local.createConnection();
                connection_local.start();
                
                Connection connection_remote = connectionFactory_remote.createConnection();
                connection_remote.start();

                Session session_local = connection_local.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Session session_remote = connection_remote.createSession(false, Session.AUTO_ACKNOWLEDGE);
                
                //sending registration information request
                Destination destination_local = session_local.createQueue("org.apache.ode.in");
                Destination source_local = session_local.createTopic("org.apache.ode.events");
                
                Destination destination_remote = session_local.createQueue("custom_controller_1_queue");
                Destination source_remote = session_remote.createQueue("custom_controller_2_queue");
                
                //creating message producer
                MessageProducer producer_local = session_local.createProducer( destination_local );
                MessageProducer producer_remote = session_local.createProducer(destination_remote);
                
                //send request registration information message to local ode
    			RequestRegistrationInformation request_message_local = new RequestRegistrationInformation();	
    			
                ObjectMessage request_producer_local = session_local.createObjectMessage( request_message_local );
                request_producer_local.setJMSReplyTo(source_local);
                
                System.out.println("blub0");
    
                producer_local.send( request_producer_local );
    			
                System.out.println("blub1");
                
                //creating message consumer
                MessageConsumer consumer_local = session_local.createConsumer(source_local);
                MessageConsumer consumer_remote = session_remote.createConsumer(source_remote);
                
                
                boolean master = false;
                boolean synchronized_activity = false;
                ArrayList messageList = new ArrayList();
                
                //receiving
                boolean x = true;
                int y = 0;
                while(x){
                	
                	try {
                		//receive the message and cast it to Serializable type
                        Message message_consumer_local = consumer_local.receive();    
                        ObjectMessage message_local = (ObjectMessage) message_consumer_local;
        				Serializable aObject = message_local.getObject();
        				
        				if(aObject instanceof Activity_Ready){
        					//for all replicable activities -> run them on every EE
        					if(y != 3){
        						System.out.println("blub4");
       						
        						//wait(1000);     						
        						Start_Activity start_message = new Start_Activity();
        						start_message.setReplyToMsgID(((Activity_Ready) aObject).getMessageID());
                        
        						ObjectMessage message_producer_local = session_local.createObjectMessage( start_message );
        						producer_local.send( message_producer_local );
        						
        						y++;
        					}
        					else{
        						System.out.println("blub5");
        						
        						if(master){
        							synchronized_activity = true;
        							
        							Start_Activity start_message = new Start_Activity();
        							start_message.setReplyToMsgID(((Activity_Ready) aObject).getMessageID());
        							
        							ObjectMessage message_producer_local = session_local.createObjectMessage(start_message);
        							producer_local.send(message_producer_local);
        						}
        						else{
        							synchronized_activity = true;
        						
        							while(synchronized_activity){
        								
        								Message message_consumer_remote = consumer_remote.receive();
        								ObjectMessage message_remote = (ObjectMessage) message_consumer_remote;
        								Serializable remoteObject = message_remote.getObject();
        								
        							
        								if(remoteObject instanceof ArrayList){
        									
        									messageList = (ArrayList) remoteObject;
        								
        									for(int i = 0; i < messageList.size(); i++){									
        										if(messageList.get(i) instanceof Write_Variable){
        											Activity_Ready activity_ready_message = (Activity_Ready) aObject;
        											
        											Write_Variable old_write_message = (Write_Variable) messageList.get(i);
        											Write_Variable write_message = new Write_Variable();
        											
        											write_message.setProcessID(activity_ready_message.getProcessID());
        											write_message.setScopeID(activity_ready_message.getScopeID());
        											write_message.setVariableName(old_write_message.getVariableName());
        											write_message.setChanges(old_write_message.getChanges());
        										
        		        							ObjectMessage message_producer_local = session_local.createObjectMessage( write_message );
        		        							producer_local.send(message_producer_local);
        										}
        										else if(messageList.get(i) instanceof Skip_Activity){
        											
        		        							Skip_Activity skip_message = new Skip_Activity();
        		        							skip_message.setReplyToMsgID(((Activity_Ready) aObject).getMessageID());
        		        						
        		        							ObjectMessage message_producer_local = session_local.createObjectMessage( skip_message );
        		        							producer_local.send( message_producer_local );
        		        							
        		        							synchronized_activity = false;
        		        							y++;
        										}
        									}
                						}
                					}
                				}   
        					}							
        				}
        				//add variable changes during execution of the non-replicable activity to the message list
        				else if((aObject instanceof Variable_Modification) && synchronized_activity && master){
        					Variable_Modification a = (Variable_Modification) aObject;
        					
        					Write_Variable write_message = new Write_Variable();
        					write_message.setVariableName(a.getVariableName());
        					write_message.setChanges(a.getValue());
        					
        					messageList.add(write_message);
        				}
        				else if((aObject instanceof Variable_Modification_At_Assign) && synchronized_activity && master){
        					Variable_Modification_At_Assign a = (Variable_Modification_At_Assign) aObject;
        					
        					Write_Variable write_message = new Write_Variable();
        					write_message.setVariableName(a.getVariableName());
        					write_message.setChanges(a.getValue());
        					
        					messageList.add(write_message);
        				}
        				else if((aObject instanceof Activity_Complete) && synchronized_activity && master){        					
        					
        					Skip_Activity skip_message = new Skip_Activity();
        					messageList.add(skip_message);
        					
        					ObjectMessage object_message = session_local.createObjectMessage(messageList);
        					producer_remote.send(object_message);
        					
        					System.out.println("Status Update send.");
        					
        					synchronized_activity = false;
        					y++;
        				}
        				else if(aObject instanceof RegistrationInformationMessage){
        					System.out.println("blub2");
        				
        					RegisterRequestMessage register_message = new RegisterRequestMessage(); 
        				
                        	ObjectMessage register_producer_local = session_local.createObjectMessage( register_message );
                        	register_producer_local.setJMSReplyTo(source_local);
                        	producer_local.send( register_producer_local );
        				}
        				else if(aObject instanceof RegisterResponseMessage){
        					System.out.println("blub3");
        				}
                	}
                	catch(JMSException e){
        				System.out.println("");
        				System.out.println("Unable to handle an incoming/outgoing Message.");
        				System.out.println("");
        				e.printStackTrace();
                	}
                
                }
                
                // Clean up
                session_local.close();
                connection_local.close();
                
                
                session_remote.close();
                connection_remote.close();
            }
            catch (Exception e) {
                System.out.println("Caught: " + e);
                e.printStackTrace();
            }
                
        }
        	
        	}	
    }
}


//RequestRegistrationInformation;
//RegisterRequestMessage;