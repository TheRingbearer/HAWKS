package synchronizationUnit;

import java.io.Serializable;

import java.util.ArrayList;

public class SynchronizationMessage implements Serializable{	
    		
	static final long serialVersionUID = 456;    		
    private String this_ip;
    private String this_message;
    private String this_next_master;
    private Activity this_activity;
    private int this_view;
    private ArrayList this_state_list;
    private int this_last_committed_view;
    		
    public SynchronizationMessage(){
    	
    }
    
    public SynchronizationMessage(String ip, String message){
    	this_ip = ip;
    	this_message = message;
    }
    
    public SynchronizationMessage(String ip, String message, String next_master){
    	this_ip = ip;
    	this_message = message;
    	this_next_master = next_master;
    }
    
    public String get_ip(){
    	return this_ip;
    }
    		
    public String get_message(){
    	return this_message;
    }
    
    public String get_next_master(){
    	return this_next_master;
    }
    
    public Activity get_activity(){
    	return this_activity;
    }
    
    public int get_view(){
    	return this_view;
    }
    
    public ArrayList get_state_list(){
    	return this_state_list;
    }
    
    public int get_last_committed_view(){
    	return this_last_committed_view;
    }
    
    public void set_message(String message){
    	this_message = message;
    }
    
    public void set_activity(Activity activity){
    	this_activity = activity;
    }
    
    public void set_view(int view){
    	this_view = view;
    }
    
    public void set_state_list(ArrayList state_list){
    	this_state_list = state_list;
    }
    
    public void set_last_committed_view(int last_committed_view){
    	this_last_committed_view = last_committed_view;
    }
}