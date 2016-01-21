package org.apache.ode.bpel.extensions.sync;

import java.io.Serializable;

import java.util.ArrayList;

import org.apache.ode.bpel.extensions.comm.messages.engineIn.Write_Variable;


/**
 * A class that represents one of the message types send between execution engines.
 * 
 * @author krawczls
 *
 */
public class SynchronizationMessage implements Serializable {	
    		
	static final long serialVersionUID = 456;    		

    private String message;
    private boolean all;
    
    private int engine_number;
    
    private int next_master;
    private Activity activity;
    private int view;
    private ArrayList<Write_Variable> state_list;
    private int last_committed_view;
    private ArrayList<?> status_update;
    
    private String replicated_workflow_id;
    private long processID;	
    
    /**
     * A default constructor.
     */
    public SynchronizationMessage() {  	
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
    public SynchronizationMessage(String message, boolean all, int engine_number, int next_master, String replicated_workflow_id) {
    	this.message = message;
    	this.all = all;
    	this.engine_number = engine_number;
    	this.next_master = next_master;
    	this.replicated_workflow_id = replicated_workflow_id;
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
    	return this.activity;
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
    	return this.state_list;
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
    	return this.status_update;
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