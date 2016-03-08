package org.apache.ode.bpel.extensions.sync;

import java.io.Serializable;

import java.util.ArrayList;

import org.apache.ode.bpel.extensions.comm.messages.engineIn.Skip_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Write_Variable;


/**
 * A class that represents one of the message types send between execution engines.
 * 
 * @author krawczls
 *
 */
public class SynchronizationMessage implements Serializable {	
    		
	static final long serialVersionUID = 456;    		

    private String message = "-";
    private boolean all = false;
    
    private int engine_number = 0;
    
    private int next_master = 0;
    private Activity activity = new Activity();
    private int view = 0;
    private ArrayList<Write_Variable> state_list = new ArrayList<Write_Variable>();
    private int last_committed_view = 0;
    private ArrayList<?> status_update = new ArrayList<Object>();
    
    private String replicated_workflow_id = "-";
    private long processID = 0L;	
    
    /**
     * A default constructor.
     */
    public SynchronizationMessage() {  	
    }
    
    
    /**
     * A copy constructor.
     * 
     * @param message
     */
    public SynchronizationMessage(SynchronizationMessage message) {
    	this.activity = message.get_activity();
    	this.all = message.get_all();
    	this.engine_number = message.get_engine_number();
    	this.last_committed_view = message.get_last_committed_view();
    	this.message = message.get_message();
    	this.next_master = message.get_next_master();
    	this.processID = message.getProcessID();
    	this.replicated_workflow_id = message.get_replicated_workflow_id();
    	this.state_list = message.get_state_list();
    	this.status_update = message.get_status_update();
    	this.view = message.get_view();
    }
    
    /**
     * A constructor that initializes the message with a message name and an engine number.
     * 
     * @param ip
     * @param message
     */
    public SynchronizationMessage(String message, boolean all, int engine_number, String replicated_workflow_id) { 
    	this.message = message;
    	this.all = all;
    	this.engine_number = engine_number;
    	this.replicated_workflow_id = replicated_workflow_id;
    }
    
    
    /**
     * A constructor that initializes the message with the message name, the engines engine number, 
     * and the engine number of the next activity master.
     * 
     * @param ip
     * @param message
     * @param next_master
     */
    public SynchronizationMessage(String message, 
    		boolean all, 
    		int engine_number, 
    		int next_master, 
    		String replicated_workflow_id) {
    	this.message = message;
    	this.all = all;
    	this.engine_number = engine_number;
    	this.next_master = next_master;
    	this.replicated_workflow_id = replicated_workflow_id;
    }
       
    public String toString() {
    	return "SynchronizationMessage (message: " + this.message 
    		+ ", all: "	+ this.all 
    		+ ", engine_number: " + this.engine_number 
    		+ ", next_master: "	+ this.next_master 
    		+ ", process: " + this.processID
    		+ ", replicated_workflow_id: "	+ this.replicated_workflow_id + ")";
    }
    
    /**
     * Getter method for the field this_message.
     * 
     * @return
     */
    public String get_message() {
    	return this.message;
    }
    
    /**
     * @return
     */
    public boolean get_all() {
    	return this.all;
    }
    
    /**
     * @return
     */
    public int get_engine_number() {
    	return this.engine_number;
    }
    
    /**
     * @return
     */
    public int get_next_master() {
    	return this.next_master;
    }
    
    /**
     * Getter method for the field this_activity.
     * 
     * @return
     */
    public Activity get_activity(){
    	Activity newActivity = null;
    	if(this.activity != null) {
    		newActivity = new Activity(this.activity);
    	}
    	return newActivity;
    }
    
    /**
     * Getter-method for the field this_view.
     * 
     * @return
     */
    public int get_view(){
    	return this.view;
    }
    
    
    /**
     * Getter method for the field this_state_list.
     * 
     * @return
     */
    public ArrayList<Write_Variable> get_state_list(){
    	ArrayList<Write_Variable> newStateList = new ArrayList<Write_Variable>();
    	if(this.state_list != null) {
    		for(int i = 0; i < this.state_list.size(); i++) {
    			Write_Variable write = new Write_Variable();
    			write.setChanges(this.state_list.get(i).getChanges());
    			write.setMsgID(this.state_list.get(i).getMsgID());
    			write.setProcessID(this.state_list.get(i).getProcessID());
    			write.setScopeID(this.state_list.get(i).getScopeID());
    			write.setVariableName(this.state_list.get(i).getVariableName());
    			newStateList.add(write);
    		}
    	}
    	return newStateList;
    }
    
    /**
     * Getter method for the field this_last_committed_view.
     * 
     * @return
     */
    public int get_last_committed_view(){
    	return this.last_committed_view;
    }
    
    /**
     * @return
     */
    public ArrayList<?> get_status_update(){
    	ArrayList<Object> newStatusUpdate = new ArrayList<Object>();
    	if(this.status_update != null) {
    		for(int i = 0; i < this.status_update.size(); i++) {
    			if(this.status_update.get(i) instanceof Activity) {
    				Activity activity = new Activity((Activity) this.status_update.get(i));
    				newStatusUpdate.add(activity);
    			}
    			else if(this.status_update.get(i) instanceof Write_Variable) {
    				Write_Variable write = new Write_Variable();
    				write.setChanges(((Write_Variable) this.status_update.get(i)).getChanges());
    				write.setMsgID(((Write_Variable) this.status_update.get(i)).getMsgID());
    				write.setProcessID(((Write_Variable) this.status_update.get(i)).getProcessID());
    				write.setScopeID(((Write_Variable) this.status_update.get(i)).getScopeID());
    				write.setVariableName(((Write_Variable) this.status_update.get(i)).getVariableName());
    				newStatusUpdate.add(write);
    			}
    			else if(this.status_update.get(i) instanceof Skip_Activity) {
    				Skip_Activity skip = new Skip_Activity();
    				skip.setMsgID(((Skip_Activity) this.status_update.get(i)).getMsgID());
    				skip.setReplyToMsgID(((Skip_Activity) this.status_update.get(i)).getReplyToMsgID());
    				newStatusUpdate.add(skip);
    			}
    		} 
    	}
    	return newStatusUpdate;
    }
    
       
    @Override
    public int hashCode() {
    	return engine_number + 17*message.hashCode() + 13*activity.hashCode() + view*31 + 52*last_committed_view + 79*replicated_workflow_id.hashCode();
    }
    
    
    @Override
    public boolean equals(Object obj) {
    	  if (obj == null) {
    	    return false;
    	  }
    	  if (obj == this) {
    	    return true;
    	  }
    	  if (obj.getClass() == this.getClass()) {
    	    SynchronizationMessage b = (SynchronizationMessage)obj;
    	    if (this.message.equals(b.get_message())
    	    		&& this.all == b.get_all()
    	    		&& this.engine_number == b.get_engine_number()
    	    		&& this.next_master == b.get_next_master()
    	    		&& this.activity.equals(b.get_activity())
    	    		&& this.view == b.get_view()
    	    		&& this.last_committed_view == b.get_last_committed_view()
    	    		&& this.replicated_workflow_id.equals(b.get_replicated_workflow_id())
    	    		&& this.processID == b.getProcessID()) {
    	      return true;
    	    }
    	  }
    	  return false;
    	}
    
    
    /**
     * @return
     */
    public String get_replicated_workflow_id() {
    	return this.replicated_workflow_id;
    }
    
    /**
     * Setter method for the field this_message.
     * 
     * @param message
     */
    public void set_message(String message) {
    	this.message = message;
    }
    
    /**
     * @param all
     */
    public void set_all(boolean all) {
    	this.all = all;
    }
    
    /**
     * @param engine_number
     */
    public void set_engine_number(int engine_number) {
    	this.engine_number = engine_number;
    }
    
    /**
     * Setter method for the field this_next_master.
     * 
     * @param master
     */
    public void set_next_master(int master) {
    	this.next_master = master;
    }
       
    /**
     * Setter method for the field this_activity.
     * 
     * @param activity
     */
    public void set_activity(Activity activity){
    	this.activity = activity;
    }
    
    /**
     * Setter method for the field this_view.
     * 
     * @param view
     */
    public void set_view(int view){
    	this.view = view;
    }
    
    /**
     * Setter method for the field this_state_list.
     * 
     * @param state_list
     */
    public void set_state_list(ArrayList<Write_Variable> state_list){
    	this.state_list = state_list;
    }
    
    /**
     * Setter method for the field this_last_committed_view.
     * 
     * @param last_committed_view
     */
    public void set_last_committed_view(int last_committed_view){
    	this.last_committed_view = last_committed_view;
    }
    
    /**
     * @param status_update
     */
    public void set_status_update(ArrayList<?> status_update) {
    	this.status_update = status_update;
    }
    
    /**
     * @param replicated_workflow_id
     */
    public void set_replicated_workflow_id(String replicated_workflow_id) {
    	this.replicated_workflow_id = replicated_workflow_id;
    }

	public void setProcessID(long processID) {
		this.processID = processID;
	}

	public long getProcessID() {
		return processID;
	}



    
}