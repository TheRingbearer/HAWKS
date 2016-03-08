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
	private String activity = "-";
	private String xpath = "-";
	private Long scope_id = 0L;
	private Long process_id = 0L;
	private Long message_id = 0L;
	private int view = 0;
	private String status = "-";
	private int engine_number = 0;
	
	/**
	 * A default constructor.
	 */
	public Activity() {		
	}
	
	/**
	 * A copy constructor.
	 * 
	 * @param activity
	 */
	public Activity(Activity activity) {
		this.activity = activity.get_activity();
		this.engine_number = activity.get_engine_number();
		this.message_id = activity.get_message_id();
		this.process_id = activity.get_process_id();
		this.scope_id = activity.get_scope_id();
		this.status = activity.get_status();
		this.view = activity.get_view();
		this.xpath = activity.get_xpath();
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
	

	@Override
	public int hashCode() {
		return activity.hashCode() + 13*xpath.hashCode() + 17*view + 31*status.hashCode() + 53*engine_number + process_id.hashCode()*79;
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
			Activity b = (Activity)obj;
			if (this.activity.equals(b.get_activity())
					&& this.xpath.equals(b.get_xpath())
					&& this.scope_id == b.get_scope_id()
					&& this.process_id == b.get_process_id()
					&& this.message_id == b.get_message_id()
					&& this.view == b.get_view()
					&& this.status.equals(b.get_status())
					&& this.engine_number == b.get_engine_number()) {
				return true;
			}
		}
		return false;
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