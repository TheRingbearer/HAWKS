package org.apache.ode.bpel.extensions.sync;

import java.io.Serializable;

/**
 * A class that represents an activity. Stores all necessary information for handling activities.
 * 
 * @author krawczls
 *
 */
public class Activity implements Serializable{
	
	static final long serialVersionUID = 455; 
	private String activity;
	private String xpath;
	private Long scope_id;
	private Long process_id;
	private Long message_id;
	private int view;
	private String status;
	private int engine_number;
	
	/**
	 * A default constructor.
	 */
	public Activity(){		
	}
	
	/**
	 * A constructor that initializes all fields.
	 * 
	 * @param activity
	 * @param xpath
	 * @param scope_id
	 * @param process_id
	 * @param message_id
	 * @param view
	 * @param status
	 * @param ip
	 */
	public Activity(String activity, String xpath, Long scope_id, Long process_id, Long message_id, int view, String status, int engine_number){
		this.activity = activity;
		this.xpath = xpath;
		this.scope_id = scope_id;
		this.process_id = process_id;
		this.message_id = message_id;
		this.view = view;
		this.status = status;
		this.engine_number = engine_number;
	}
	
	
	/**
	 * Getter method for the field this_activity.
	 * 
	 * @return
	 */
	public String get_activity(){
		return this.activity;
	}
	
	/**
	 * Getter method for the field this_xpath.
	 * 
	 * @return
	 */
	public String get_xpath(){
		return this.xpath;
	}
	
	/**
	 * Getter method for the field this_scope_id.
	 * 
	 * @return
	 */
	public Long get_scope_id(){
		return this.scope_id;
	}
	
	/**
	 * Getter method for the field this_process_id.
	 * 
	 * @return
	 */
	public Long get_process_id(){
		return this.process_id;
	}
	
	/**
	 * Getter method for the field this_message_id.
	 * 
	 * @return
	 */
	public Long get_message_id(){
		return this.message_id;
	}
	
	/**
	 * Getter method for the field this_view.
	 * 
	 * @return
	 */
	public int get_view(){
		return this.view;
	}
		

	/**
	 * @return
	 */
	public int get_engine_number(){
		return this.engine_number;
	}
	
	
	/**
	 * A function that returns the type (invoke, reply, wait etc.) of an activity.
	 * 
	 * @return
	 */
	public String get_activity_type(){
		int from = this.xpath.lastIndexOf("/");
		int to = -1;
		to = this.xpath.lastIndexOf("[");
		if(to == -1){
			return (this.xpath.substring(from + 1, this.xpath.length()));
		}
		else{
			return (this.xpath.substring(from + 1, to));
			
		}
	}
	
	/**
	 * Setter method for the field this_view.
	 * 
	 * @param view
	 */
	public void set_view(int view){
		this.view = view;
	}

	public String get_status() {
		return this.status;
	}	
	
	public void set_status(String status) {
		this.status = status;
	}
}