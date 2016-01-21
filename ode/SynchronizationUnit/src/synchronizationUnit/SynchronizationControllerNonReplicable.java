package synchronizationUnit;

import java.util.ArrayList;
import java.util.Random;
import java.util.Date;

import org.apache.activemq.ActiveMQConnectionFactory;

import org.apache.ode.bpel.extensions.comm.messages.engineIn.RegisterRequestMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.RequestRegistrationInformation;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Skip_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Complete_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Start_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Write_Variable;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.RegisterRequestMessage.Requested_Blocking_Events;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Ready;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.RegisterResponseMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.RegistrationInformationMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification_At_Assign;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Complete;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_Suspended;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.ActivityEventMessage;

import java.io.Serializable;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;

public class SynchronizationControllerNonReplicable {
 
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
    	
    	public String getActivityType(Serializable aObject){
			int from = (((ActivityEventMessage) aObject).getActivityXPath()).lastIndexOf("/");
			int to = -1;
			to = (((ActivityEventMessage) aObject).getActivityXPath()).lastIndexOf("[");
			if(to == -1){
				return ((((ActivityEventMessage) aObject).getActivityXPath()).substring(from + 1, (((ActivityEventMessage) aObject).getActivityXPath()).length()));
			}
			else{
				return ((((ActivityEventMessage) aObject).getActivityXPath()).substring(from + 1, to));
				
			}
    	}
    	
        public void run() {
        	
        	synchronized(this){
        	
	            try {
	            	//TODO read ip's automatically
	            	//define all important variables
	            	String my_ip = "192.168.2.100";
	            	
	            	boolean planning_master = true;
	                boolean activity_master = false;
	                String master_status = "--F ";
	                String activity_master_ip = "-";
	                int next_activity_master;
	                
	                int majority;
	                boolean invoke = false;
	                boolean firstActivities = false;
	                long processID = 0;
	                
	                ArrayList message_list = new ArrayList();
	                ArrayList ee_list = new ArrayList();
	                ArrayList activity_list = new ArrayList();
	                //state_list contains all variables and their values
	                ArrayList state_list = new ArrayList();
	                ArrayList vote_list = new ArrayList();
	                ArrayList vote_update = new ArrayList();
	                
	                int current_view = 0;
	                int last_committed_view = 0;
	                
	                Serializable local_object = null;
	                ObjectMessage message_local = null;
	                
	                Random random_generator = new Random();
	            	
	                //setup the connections
	                ActiveMQConnectionFactory connection_factory_local = new ActiveMQConnectionFactory("tcp://localhost:61616");
	                
	                Connection connection_local = connection_factory_local.createConnection();
	                connection_local.start();
	
	                Session session_local = connection_local.createSession(false, Session.AUTO_ACKNOWLEDGE);
	                
	                //sending registration information request
	                Destination destination_local = session_local.createQueue("org.apache.ode.in");
	                Destination source_local = session_local.createTopic("org.apache.ode.events");
	                
	                //creating message producer
	                MessageProducer producer_local = session_local.createProducer( destination_local );
	                
	                //send request registration information message to local ode
	    			RequestRegistrationInformation request_message_local = new RequestRegistrationInformation();	
	    			
	                ObjectMessage request_producer_local = session_local.createObjectMessage( request_message_local );
	                request_producer_local.setJMSReplyTo(source_local);
	                
	                producer_local.send( request_producer_local );   			
	                
	                //creating message consumer
	                MessageConsumer consumer_local = session_local.createConsumer(source_local);
	                
                    Message message_consumer_local = consumer_local.receive(1000);
                    
                    if(message_consumer_local != null){
	                    message_local = (ObjectMessage) message_consumer_local;
	                    local_object = message_local.getObject();
		                
	    				System.out.println(master_status + "RegistrationInformationMessage received.");
	    				
	    				RegisterRequestMessage register_message = new RegisterRequestMessage(); 
	    					
	    				//set Activity_Ready to be blocking
	    				//TODO needs testing
	    				/*Requested_Blocking_Events blocked_events = register_message.new Requested_Blocking_Events();
	    				blocked_events.Activity_Ready = true;
	    				register_message.setGlobalEventBlockings(blocked_events);*/
	    					
	                    ObjectMessage register_producer_local = session_local.createObjectMessage( register_message );
	                    register_producer_local.setJMSReplyTo(source_local);
	                    producer_local.send( register_producer_local );
                    }
                    
                    local_object = null;
                
	                
	                //adding the other replicas to a list and settting up a 2-way connection to them
	                //ee_list.add(new ExecutionEngine("129.69.185.210", 0));
	                ee_list.add(new ExecutionEngine("192.168.2.105", 0));
	                for(int i = 0; i < ee_list.size(); i++){
	                	System.out.println(master_status + "Setting up connection to other replicas.");
	                	((ExecutionEngine)ee_list.get(i)).setup_connection(my_ip, session_local);
	                }
	                //deciding the majority
	                majority = (ee_list.size() + 1) / 2;
	                System.out.println(majority);
	                //choosing the next activity master, if we are planning master
	                if(planning_master){
	                	System.out.println(master_status + "Choosing next activity master.");
						next_activity_master = random_generator.nextInt(ee_list.size() + 1);
						if(next_activity_master == ee_list.size()){
							activity_master_ip = my_ip;
							activity_master = true;
							master_status = "--M ";
							System.out.println(master_status + activity_master_ip);
						}
						else{
							activity_master_ip = (String) ((ExecutionEngine) ee_list.get(next_activity_master)).get_ip();
							System.out.println(master_status + activity_master_ip);
						}
						//send information about new activity master to all replicas
						SynchronizationMessage activity_master_message = new SynchronizationMessage(my_ip, "Master", activity_master_ip);
						for(int i = 0; i < ee_list.size(); i++){				
							((ExecutionEngine) ee_list.get(i)).send_message(activity_master_message);
						}
						planning_master = false;
	                }
	                //waits for message announcing the first activity master
	                while(activity_master_ip.equals("-")){
	                	System.out.println(master_status + "Waiting for the information about next activity master.");
	                	Serializable aObject = null;
	                	for(int i = 0; i < ee_list.size(); i++){
	                		aObject = ((ExecutionEngine)ee_list.get(i)).receive_message(1000);
	                	}        	
						if(aObject instanceof SynchronizationMessage){					
							SynchronizationMessage get_master = (SynchronizationMessage) aObject;
							if(get_master.get_message().equals("Master")){
								System.out.println(master_status + "Getting information about next activity master.");
								if(get_master.get_next_master().equals(my_ip)){
									activity_master = true;
									activity_master_ip = my_ip;
									master_status = "--M ";
								}
								else{
									activity_master_ip = get_master.get_next_master();
								}
							}
						}
	                }
	                
	                
	                boolean x = true;
	                int y = 0;
	                SynchronizationMessage get_message = null;
	                ArrayList ip_list = new ArrayList();
	                int counter_ok = 0;
	                Date timer = new Date();
	                long old_time = timer.getTime();
	                String master_ip = "-";
	                Serializable remote_object = null;
	                
	                while(x){
	                	
	                	try {
	                		System.out.println(master_status);
	                		//activity master sends heartbeat on every cycle
	                		if(activity_master){
    							SynchronizationMessage heartbeat = new SynchronizationMessage(my_ip, "Heartbeat");
								for(int j = 0; j < ee_list.size(); j++){
									((ExecutionEngine)ee_list.get(j)).send_message(heartbeat);
								}
	                		}   
	                		//receive a new message from replicas if the previous message has been applied
	                		if(remote_object == null){
	                			for(int i = 0; i < ee_list.size(); i++){
	                				remote_object = ((ExecutionEngine)ee_list.get(i)).receive_message(1000);
	                				if(remote_object != null){
	                					break;
	                				}
	                			}
	                		}
	                		//we get a synchronization message
	                		//----------------------------------------------
							if(remote_object instanceof  SynchronizationMessage && get_message == null){
								get_message = (SynchronizationMessage) remote_object;
								old_time = timer.getTime();
								remote_object = null;
							}
							//handling of replicas getting the status update
							//----------------------------------------------
							else if(remote_object instanceof ArrayList){
								System.out.println(master_status + "Status Update received.");
									
	    						int test_view = -1;
	    						int new_view = -1;
	    						int index = -1;
	    							
	    						boolean do_nothing = false;
	    						boolean not_ready = false;
	    							
								message_list = (ArrayList) remote_object;									
								Activity new_activity = new Activity();
									
								int view_change_counter = 0;
									
								//working through the status update
								for(int i = 0; i < message_list.size(); i++){
									//
									if(message_list.get(i) instanceof Activity){
										new_activity = (Activity) message_list.get(i);
										for(int j = 0; j < activity_list.size(); j++){
											if(new_activity.get_xpath().equals(((Activity) activity_list.get(j)).get_xpath())){
												test_view = ((Activity) activity_list.get(j)).get_view();
												index = j;
											}
										}
										if(index == -1){
											not_ready = true;
										}
										else{
											new_view = new_activity.get_view();
												
											//do nothing
											if((new_view < current_view) && (new_view == last_committed_view)){
												System.out.println(master_status + "Update received.Option 1: We do nothing.");
												do_nothing = true;
											}
											//reply with reject
											else if((new_view < current_view) && (new_view < last_committed_view) && (test_view != new_view)){
												System.out.println(master_status + "Update received.Option 2: We reply with reject.");
												SynchronizationMessage reject_message = new SynchronizationMessage(my_ip, "Reject");									
												String get_ip = new_activity.get_ip();
												for(int j = 0; j < ee_list.size(); j++){
													if(get_ip.equals(((ExecutionEngine)ee_list.get(j)).get_ip())){
														((ExecutionEngine)ee_list.get(j)).send_message(reject_message);
													}
												}
												do_nothing = true;
											}
											//reply with OK (case 1) other cases are being handled below
											else{
												if((new_view > current_view) || (new_view > last_committed_view)){
													current_view = new_view;
													last_committed_view = new_view;
													System.out.println(master_status + "Update received. Option 3: Everything is okay.");	
														
													Activity activity = (Activity) activity_list.get(index);
													if((activity.get_status()).equals("Uncommitted")){
														//compensate the activity
														//TODO compensation logic
														System.out.println(master_status + "Update received. Option 3.a: We do need to compensate.");
													}
												}
												//if any subsequent updates have been applied
												if(index != (activity_list.size() - 1)){
													do_nothing = true;
												}
												Activity activity = (Activity) activity_list.get(index);
												activity.set_status("UncommittedSkipped");
												activity.set_view(new_view);
											}
										}
									}
									//
									else if((message_list.get(i) instanceof Write_Variable) && !do_nothing && !not_ready){
										System.out.println(master_status + "Update received. We have to update a variable.");
										Write_Variable old_write_message = (Write_Variable) message_list.get(i);
										Write_Variable write_message = new Write_Variable();
											
										write_message.setProcessID(((Activity)activity_list.get(activity_list.size() - 1)).get_process_id());
										write_message.setScopeID(((Activity)activity_list.get(activity_list.size() - 1)).get_scope_id());
										write_message.setVariableName(old_write_message.getVariableName());
										write_message.setChanges(old_write_message.getChanges());
																				
										//update state list
										boolean found = false;
										for(int j = 0; j < state_list.size(); j++){
											if((write_message.getVariableName()).equals(((Write_Variable) state_list.get(j)).getVariableName())){
												((Write_Variable) state_list.get(j)).setChanges(write_message.getChanges());
												found = true;
											}
										}
										if(!found){
											state_list.add(write_message);
										}
											
		        						ObjectMessage message_producer_local = session_local.createObjectMessage( write_message );
		        						producer_local.send(message_producer_local);		        							
									}
									//
									else if((message_list.get(i) instanceof Skip_Activity) && !do_nothing && !not_ready){	
										Activity activity = (Activity) activity_list.get(activity_list.size() - 1);
										if((activity.get_activity_type()).equals("invoke")){
											System.out.println(master_status + "Update received. We are sending our complete message.");
											Complete_Activity complete_message = new Complete_Activity();
		        							complete_message.setReplyToMsgID(activity.get_message_id());
			        						
		        							ObjectMessage message_producer_local = session_local.createObjectMessage( complete_message );
		        							producer_local.send( message_producer_local ); 
										}
										else{
											System.out.println(master_status + "Update received. We are sending our skip message.");
		        							Skip_Activity skip_message = new Skip_Activity();
		        							skip_message.setReplyToMsgID(activity.get_message_id());
		        						
		        							ObjectMessage message_producer_local = session_local.createObjectMessage( skip_message );
		        							producer_local.send( message_producer_local );   
										}
									}
								}      							
								if(!not_ready){
	        						//send ok to master
	        						SynchronizationMessage ok_message = new SynchronizationMessage(my_ip, "OK");
	        						ok_message.set_activity(new_activity);
									String get_ip = new_activity.get_ip();
									for(int j = 0; j < ee_list.size(); j++){
										if(get_ip.equals(((ExecutionEngine)ee_list.get(j)).get_ip())){
											((ExecutionEngine)ee_list.get(j)).send_message(ok_message);
										}
									}
									master_ip = get_ip;
	        						System.out.println(master_status + "OK send.");	
									remote_object = null;
								}
								old_time = timer.getTime();
							}
							//----------------------------------------------
							//status update end
								
							//no new messages -> view change
							else if(!activity_master && (activity_list.size() == 0 || (((Activity)activity_list.get(activity_list.size() - 1)).get_status()).equals("Ready"))){
								if(timer.getTime() - old_time >= 2*60*1000 + random_generator.nextInt(5000)){
									//TODO initiate view change vote
									current_view++;
									SynchronizationMessage view_change_message = new SynchronizationMessage(my_ip, "Viewchange");
									view_change_message.set_view(current_view);
									ip_list = new ArrayList();
									vote_list = new ArrayList();
									counter_ok = 0;
									for(int i = 0; i < ee_list.size(); i++){								
										((ExecutionEngine) ee_list.get(i)).send_message(view_change_message);
									}
									old_time = timer.getTime();
								}
									
							}
		                	//receive the message from workflow
							if(local_object == null){
								message_consumer_local = null;
		                        message_consumer_local = consumer_local.receive(1000);
		                        if(message_consumer_local != null){
		                        	System.out.println(master_status + "Local message received.");
		                        	message_local = (ObjectMessage) message_consumer_local;
		                        	local_object = message_local.getObject();
		                        	System.out.println(master_status + (local_object.getClass()).toString());
		                        }
							}
								
							//block for handling workflow messages
		        			//if a new activity is ready to be executed
							//----------------------------------------------
		        			if(local_object instanceof Activity_Ready){      					
		        				System.out.println(master_status + "activity_ready");
		        				System.out.println(master_status + getActivityType(local_object));
		        				if(!invoke){
		        					processID = ((Activity_Ready)local_object).getProcessID();
		        				}
		        				if(processID == ((Activity_Ready)local_object).getProcessID()){		        						   
		        					if((getActivityType(local_object)).equals("process") || (getActivityType(local_object)).equals("sequence") || (getActivityType(local_object)).equals("receive")){
		        						firstActivities = true;
		        						
	        							//adding the new activity to our activity list
	        							Activity_Ready activity_status = (Activity_Ready) local_object;
	        							Activity activity = new Activity(activity_status.getActivityName(), activity_status.getActivityXPath(), activity_status.getScopeID(), activity_status.getProcessID(), activity_status.getMessageID(), current_view, "Ready", my_ip);
	        							activity_list.add(activity);
	        							
		        						
		        						wait(1000);
		        						System.out.println(master_status + "Executing: " + ((Activity_Ready)local_object).getActivityName());
		        						//executing the new activity
		        						Start_Activity start_message = new Start_Activity();
		        						start_message.setReplyToMsgID(((Activity_Ready) local_object).getMessageID());
        							
		        						ObjectMessage message_producer_local = session_local.createObjectMessage(start_message);
		        						producer_local.send(message_producer_local);
		        						
		        						local_object = null;
		        					}
		        					else{
		        						firstActivities = false;
		        						//activity master starts new activity
		        						if((activity_master && (activity_list.size() == 0)) || (activity_master && ((((Activity)activity_list.get(activity_list.size() - 1)).get_status()).equals("Skipped") || (((Activity)activity_list.get(activity_list.size() - 1)).get_status()).equals("Executed")))){
		        							if((getActivityType(local_object)).equals("invoke")){
		        								invoke = true;
		        							}
		        							wait(1000);
		        							System.out.println(master_status + "Executing: " + ((Activity_Ready)local_object).getActivityName());
		        							//executing the new activity
		        							Start_Activity start_message = new Start_Activity();
		        							start_message.setReplyToMsgID(((Activity_Ready) local_object).getMessageID());
	        							
		        							ObjectMessage message_producer_local = session_local.createObjectMessage(start_message);
		        							producer_local.send(message_producer_local);
	        							
		        							//adding the new activity to our activity list
		        							Activity_Ready activity_status = (Activity_Ready) local_object;
		        							Activity activity = new Activity(activity_status.getActivityName(), activity_status.getActivityXPath(), activity_status.getScopeID(), activity_status.getProcessID(), activity_status.getMessageID(), current_view, "Ready", my_ip);
		        							activity_list.add(activity);
		        							//adding the new activity to the start of our status update message
		        							message_list = new ArrayList();
		        							message_list.add(activity);
		        							local_object = null;
	        							
		        						}
		        						//activity master has yet to get commit messages from the majority of the other replicas
		        						//send status update again if necessary
		        						else if(activity_master && (((Activity)activity_list.get(activity_list.size() - 1)).get_status()).equals("Uncommitted")){
		        							if((timer.getTime() - old_time >= 1000) && counter_ok < majority){
		        								for(int i = 0; i < ee_list.size(); i++){
		        									((ExecutionEngine) ee_list.get(i)).send_message(message_list);
		        								}
		        								old_time = timer.getTime();
		        								System.out.println(master_status + "Status Update send again.");
		        								ip_list = new ArrayList();
		        								counter_ok = 0;
		        							}
		        						}
		        						else if((getActivityType(local_object)).equals("invoke") && (((Activity) activity_list.get(activity_list.size() - 1)).get_status()).equals("Ready")){
		        							Start_Activity start_message = new Start_Activity();
		        							start_message.setReplyToMsgID(((Activity_Ready) local_object).getMessageID());
	        							
		        							ObjectMessage message_producer_local = session_local.createObjectMessage(start_message);
		        							producer_local.send(message_producer_local);
		        							local_object = null;
		        						}
		        						//activity ready event for other replicas
		        						else if(!activity_master && (((((Activity)activity_list.get(activity_list.size() - 1)).get_status()).equals("Skipped"))|| (((Activity)activity_list.get(activity_list.size() - 1)).get_status()).equals("Executed"))){
		        							//adding the new activity to our activity list
		        							Activity_Ready activity_status = (Activity_Ready) local_object;
		        							Activity activity = new Activity(activity_status.getActivityName(), activity_status.getActivityXPath(), activity_status.getScopeID(), activity_status.getProcessID(), activity_status.getMessageID(), current_view, "Ready", my_ip);
		        							if(activity_list.size() == 0 || !((activity_status.getActivityXPath()).equals(((Activity)activity_list.get(activity_list.size() - 1)).get_xpath()))){
		        								activity_list.add(activity);
		        							}
		        							//start view change countdown
		        							old_time = timer.getTime();
		        							local_object = null;
		        						}
		        					}
		        				}
		        				else{
	        						System.out.println(master_status + "Executing: " + ((Activity_Ready)local_object).getActivityName());
	        						//executing the new activity
	        						Start_Activity start_message = new Start_Activity();
	        						start_message.setReplyToMsgID(((Activity_Ready) local_object).getMessageID());
    							
	        						ObjectMessage message_producer_local = session_local.createObjectMessage(start_message);
	        						producer_local.send(message_producer_local);
	        						
	        						local_object = null;
		        				}
		        			}	
		        			//add variable changes during execution of the non-replicable activity to the message list
		        			//----------------------------------------------
		        			else if((local_object instanceof Variable_Modification) && activity_master){
		        				if(firstActivities || !(processID == ((Variable_Modification)local_object).getProcessID())){
		        					//do nothing
		        					local_object = null;
		        				}
		        				else{
		        					Variable_Modification a = (Variable_Modification) local_object;
		        					
		        					Write_Variable write_message = new Write_Variable();
		        					write_message.setVariableName(a.getVariableName());
		        					write_message.setChanges(a.getValue());
		        					
		        					//update state list
		        					boolean found = false;
		        					for(int j = 0; j < state_list.size(); j++){
		        						if((write_message.getVariableName()).equals(((Write_Variable) state_list.get(j)).getVariableName())){
		        							((Write_Variable) state_list.get(j)).setChanges(write_message.getChanges());
		        							found = true;
		        						}
		        					}
		        					if(!found){
		        						state_list.add(write_message);
		        					}
		        					
		        					message_list.add(write_message);
		        					
		        					local_object = null;
		        				}
		        			}
		        			//----------------------------------------------
		        			else if((local_object instanceof Variable_Modification_At_Assign) && activity_master){
		        				if(firstActivities || !(processID == ((Variable_Modification_At_Assign)local_object).getProcessID())){
		        					//do nothing
		        					local_object = null;
		        				}
		        				else{
		        					Variable_Modification_At_Assign a = (Variable_Modification_At_Assign) local_object;
		        					
		        					Write_Variable write_message = new Write_Variable();
		        					write_message.setVariableName(a.getVariableName());
		        					write_message.setChanges(a.getValue());
		        				
		        					//update state list
		        					boolean found = false;
		        					for(int j = 0; j < state_list.size(); j++){
		        						if((write_message.getVariableName()).equals(((Write_Variable) state_list.get(j)).getVariableName())){
		        							((Write_Variable) state_list.get(j)).setChanges(write_message.getChanges());
		        							found = true;
		       							}
		       						}
		        					if(!found){
		        						state_list.add(write_message);
		        					}
		        				
		        					message_list.add(write_message);
		        				
		       						local_object = null;
		       					}
		       				}
		       				//----------------------------------------------
		       				else if(local_object instanceof Activity_Complete){
		       					if(activity_master){  			       					
		       						if((getActivityType(local_object)).equals("invoke") && (processID == ((Activity_Complete)local_object).getProcessID())){
		       							invoke = false;
		       						}
		       						if(firstActivities || !(processID == ((Activity_Complete)local_object).getProcessID())){
		       							//do nothing
		       							if(firstActivities){
		       								((Activity) activity_list.get(activity_list.size() - 1)).set_status("Executed");
		       								firstActivities = false;
		       							}
		       							local_object = null;
		       						}
		       						else{      						
		       							((Activity) activity_list.get(activity_list.size() - 1)).set_status("Uncommitted");
	        						
		       							Skip_Activity skip_message = new Skip_Activity();
		       							message_list.add(skip_message);
		       							//send status update
		       							System.out.println("Status Update send");
		       							for(int i = 0; i < ee_list.size(); i++){
		       								((ExecutionEngine) ee_list.get(i)).send_message(message_list);
		       							}
		       							
		       							ip_list = new ArrayList();
		       							counter_ok = 0;
		       							local_object = null;	 
		       							old_time = timer.getTime();
		       						}
		       					}
		       					else{
		       						if(firstActivities){
		       							((Activity) activity_list.get(activity_list.size() - 1)).set_status("Executed");
		       							firstActivities = false;
		       						}
		       						local_object = null;
		       					}
		       				}
		        			//----------------------------------------------
		       				else if((local_object instanceof Instance_Suspended) && (((Instance_Suspended) local_object).getProcessID()).equals(processID)){
		       					x = false;
		       					for(int i = 0; i < state_list.size(); i++){
		       						System.out.println(((Write_Variable) state_list.get(i)).getVariableName());
		       						System.out.println(((Write_Variable) state_list.get(i)).getChanges());
		       					}
		       				}
		        			//----------------------------------------------
		       				else{
		       					local_object = null;
		       				}
		        			
		       				if(get_message != null){	       				
			       				//block for handling replica messages
			       				//activity master gets OK message from a replica
		        				//----------------------------------------------
		        				if(get_message.get_message().equals("OK") && activity_master && (((Activity)activity_list.get(activity_list.size() - 1)).get_status()).equals("Uncommitted")){        					
									Boolean added = false;
									String get_ip = get_message.get_ip();
									for(int i = 0; i < ip_list.size(); i++){
										if(get_ip.equals(ip_list.get(i))){
											added = true;
										}
									}
									//add all the ips of the OK messages
									if(!added){
										ip_list.add(get_ip);
										counter_ok += 1;
										System.out.println(master_status + "OK from " + get_ip + " received.");
									}
									//send Commit to the other EE's
									if(counter_ok >= majority){
										//calculate next master out of the received ip's 
										next_activity_master = random_generator.nextInt(ip_list.size() + 1);
										if(next_activity_master == ip_list.size()){
											activity_master_ip = my_ip;
											activity_master = true;
											master_status = "--M ";
										}
										else{
											activity_master_ip = (String) ip_list.get(next_activity_master);
											activity_master = false;
											master_status = "--F ";
										}
										//update activity status to executed
										System.out.println(master_status + "All OK's received.");
										((Activity) activity_list.get(activity_list.size() - 1)).set_status("Committed");
										//inform all replicas about the new activity master? TODO
										for(int i = 0; i < ee_list.size(); i++){
											SynchronizationMessage commit_message = new SynchronizationMessage(my_ip, "Commit", activity_master_ip);									
											((ExecutionEngine) ee_list.get(i)).send_message(commit_message);
										}
										System.out.println(master_status + "Commit send.");
									}
								get_message = null;
			        			}
			        			//follower receives commit and sends Ack
			        			//----------------------------------------------
			       				else if(get_message.get_message().equals("Commit") && get_message.get_ip().equals(master_ip) && !((((Activity) activity_list.get(activity_list.size() - 1)).get_status()).equals("Skipped"))){
		    						System.out.println(master_status + "Commit received.");
									
		    						((Activity) activity_list.get(activity_list.size() - 1)).set_status("Skipped");
		    						
		    						SynchronizationMessage ack_message = new SynchronizationMessage(my_ip, "Ack");
									String get_ip = get_message.get_ip();
									for(int i = 0; i < ee_list.size(); i++){
										if(get_ip.equals(((ExecutionEngine)ee_list.get(i)).get_ip())){
											((ExecutionEngine)ee_list.get(i)).send_message(ack_message);
										}
									}									
		    						System.out.println(master_status + "Ack send.");
		    						
		    						// (maybe) starting with execution of next activity
		    						if((get_message.get_next_master()).equals(my_ip)){
		    							activity_master = true;
		    							activity_master_ip = my_ip;
		    							master_status = "--M ";
		    						}
		    						else{
		    							activity_master_ip = get_message.get_next_master();
		    						}
			        				//message from master so reset timer
			        				old_time = timer.getTime();
		    						get_message = null;
			        			}
			       				//activity master gets all the Ack's from the replicas
			       				//TODO how to continue
			       				//----------------------------------------------
			       				else if(get_message.get_message().equals("Ack") && (((Activity)activity_list.get(activity_list.size() - 1)).get_status()).equals("Committed")){  					
			       					System.out.println(master_status + "Ack received.");
			       					counter_ok--;
			       					if(counter_ok == 0 ){
			       						((Activity)activity_list.get(activity_list.size() - 1)).set_status("Executed");
			       					}
			       					get_message = null;
			       				}
			       				//TODO logic for compensate
			       				//----------------------------------------------
			       				else if(get_message.get_message().equals("Reject")){
			       					
			       					get_message = null;	
			       				}
			       				//TODO viewchange
			       				//----------------------------------------------
			       				else if(get_message.get_message().equals("Viewchange")){
			       					//reply with vote
			       					if(current_view < get_message.get_view()){
			       						current_view = get_message.get_view();
			       						
			   							SynchronizationMessage vote_message = new SynchronizationMessage(my_ip, "Vote for Viewchange");
			   							vote_message.set_state_list(state_list);
			   							//getting the last executed activity
			   							vote_message.set_activity(null);
			   							for (int i = activity_list.size() - 1; i > 0; i--){
			   								if((((Activity) activity_list.get(i)).get_status()).equals("Skipped") || (((Activity) activity_list.get(i)).get_status()).equals("Executed")){
			   									vote_message.set_activity((Activity) activity_list.get(i));
			   								}
			   							}
			   							vote_message.set_last_committed_view(last_committed_view);
			   							
										String get_ip = get_message.get_ip();
										for(int i = 0; i < ee_list.size(); i++){
											if(get_ip.equals(((ExecutionEngine)ee_list.get(i)).get_ip())){
												((ExecutionEngine)ee_list.get(i)).send_message(vote_message);
											}
										}	
			       					}
			       					//reply with reject
			       					else{
			       						SynchronizationMessage reject_message = new SynchronizationMessage(my_ip, "Reject Viewchange");
										String get_ip = get_message.get_ip();
										for(int i = 0; i < ee_list.size(); i++){
											if(get_ip.equals(((ExecutionEngine)ee_list.get(i)).get_ip())){
												((ExecutionEngine)ee_list.get(i)).send_message(reject_message);
											}
										}	
			       					}
			       					old_time = timer.getTime();
			       					get_message = null;
			       				}
			       				//----------------------------------------------
			       				else if(get_message.get_message().equals("Vote for Viewchange")){
			       					vote_list.add(get_message);
									Boolean added = false;
									String get_ip = get_message.get_ip();
									for(int i = 0; i < ip_list.size(); i++){
										if(get_ip.equals(ip_list.get(i))){
											added = true;
										}
									}
									//add all the ips of the Vote messages
									if(!added){
										ip_list.add(get_ip);
										counter_ok += 1;
										System.out.println(master_status + "Viewchange Vote from " + get_ip + " received.");
									}
									//Viewchange is successful
									//voting starts in several stages
									//TODO implement all voting stages -> enter_next_voting_stage
									if(counter_ok >= majority){
										System.out.println(master_status + "Viewchange successful.");
										int number = last_committed_view;
										boolean enter_next_voting_stage = false;
										SynchronizationMessage vote_winner = new SynchronizationMessage();
										for(int i = 0; i < vote_list.size(); i++){
											if(((SynchronizationMessage)vote_list.get(i)).get_last_committed_view() > number){
												enter_next_voting_stage = false;
												number = ((SynchronizationMessage)vote_list.get(i)).get_last_committed_view();
												vote_winner = (SynchronizationMessage)vote_list.get(i);
											}
											else if(((SynchronizationMessage)vote_list.get(i)).get_last_committed_view() == number){
												enter_next_voting_stage = true;
											}
										}											
										vote_winner.set_message("Viewchange Update");
										for(int i = 0; i < ee_list.size(); i++){								
											((ExecutionEngine) ee_list.get(i)).send_message(vote_winner);
										}
										remote_object = null;
										//TODO status update needs to be applied by theplanning master too
									}
									old_time = timer.getTime();
									get_message = null;
			       				}
			       				//----------------------------------------------
			       				else if(get_message.get_message().equals("Reject Viewchange")){
									//TODO initiate view change vote again after the usual time
			       					old_time = timer.getTime();
			       					get_message = null;
			       				}
			       				//----------------------------------------------
			       				else if(get_message.get_message().equals("Viewchange Update")){
			       					boolean not_ready = false;
			       					Activity new_activity = (Activity) get_message.get_activity();
			       					if(!(((Activity) (activity_list.get(activity_list.size() - 1))).get_xpath()).equals(new_activity.get_xpath())){
			       						not_ready = true;
										Activity activity = (Activity) activity_list.get(activity_list.size() - 1);
		        						Skip_Activity skip_message = new Skip_Activity();
		        						skip_message.setReplyToMsgID(activity.get_message_id());
		        					
		        						ObjectMessage message_producer_local = session_local.createObjectMessage( skip_message );
		        						producer_local.send( message_producer_local ); 
			       					}
			       					else{
			       						((Activity) activity_list.get(activity_list.size() - 1)).set_view(new_activity.get_view());
			       						((Activity) activity_list.get(activity_list.size() - 1)).set_status("Skipped");
			       						last_committed_view = get_message.get_last_committed_view();
			       						for(int i = 0; i < get_message.get_state_list().size(); i++){
			       							for(int j = 0; j < state_list.size(); j++){
			       								if(((Write_Variable) state_list.get(j)).getVariableName().equals(((Write_Variable) get_message.get_state_list().get(i)).getVariableName())){
			       									if(!((Write_Variable) state_list.get(j)).getChanges().equals(((Write_Variable) get_message.get_state_list().get(i)).getChanges())){
			       										((Write_Variable) state_list.get(j)).setChanges(((Write_Variable) get_message.get_state_list().get(i)).getChanges());
			       										
			    										Write_Variable write_message = new Write_Variable();
			    										
			    										write_message.setProcessID(((Activity)activity_list.get(activity_list.size() - 1)).get_process_id());
			    										write_message.setScopeID(((Activity)activity_list.get(activity_list.size() - 1)).get_scope_id());
			    										write_message.setVariableName(((Write_Variable) state_list.get(j)).getVariableName());
			    										write_message.setChanges(((Write_Variable) state_list.get(j)).getChanges());
			       										
			       									}
			       								}
			       							}
			       						}
			       					}			        							  
			       					
			       					old_time = timer.getTime();
			       					if(!not_ready){
			       						get_message = null;
			       						//TODO send OK
			       					}
			       				}
			       				//----------------------------------------------
			       				else if(get_message.get_message().equals("Heartbeat")){
			       					old_time = timer.getTime();
			       					get_message = null;
			       				}
			       				else{
			       					if(get_message.get_message().equals("OK")){
										SynchronizationMessage commit_message = new SynchronizationMessage(my_ip, "Commit", "-");
										for(int i = 0; i < ee_list.size(); i++){
											if((((ExecutionEngine) ee_list.get(i)).get_ip()).equals(get_message.get_ip())){
												((ExecutionEngine) ee_list.get(i)).send_message(commit_message);
											}
										}
										get_message = null;
			       					}
			       					else if(get_message.get_message().equals("Ack")) {
			       						get_message = null;
			       					}
			       				}
		        			}	   
	                	}
	                	catch(JMSException e){
	        				System.out.println("");
	        				System.out.println(master_status + "Unable to handle an incoming/outgoing Message.");
	        				System.out.println("");
	        				e.printStackTrace();
	                	}                
	                }	                
	                // Clean up
	                session_local.close();
	                connection_local.close();
	                
	                for(int i = 0; i < ee_list.size(); i++){
	                	((ExecutionEngine) ee_list.get(i)).close_connection();
	                }
	            }
	            catch (Exception e) {
	                System.out.println("Caught: " + e);
	                e.printStackTrace();
	            }        	
        	}	
        }
    }
}