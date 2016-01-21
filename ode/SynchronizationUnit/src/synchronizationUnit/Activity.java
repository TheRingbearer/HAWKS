package synchronizationUnit;

import java.io.Serializable;

import org.apache.ode.bpel.extensions.comm.messages.engineOut.ActivityEventMessage;

public class Activity implements Serializable{
	
	static final long serialVersionUID = 455; 
	private String this_activity;
	private String this_xpath;
	private Long this_scope_id;
	private Long this_process_id;
	private Long this_message_id;
	private int this_view;
	private String this_status;
	private String this_ip;
	
	public Activity(){
		
	}
	
	public Activity(String activity, String xpath, Long scope_id, Long process_id, Long message_id, int view, String status, String ip){
		this_activity = activity;
		this_xpath = xpath;
		this_scope_id = scope_id;
		this_process_id = process_id;
		this_message_id = message_id;
		this_view = view;
		this_status = status;
		this_ip = ip;
	}
	
	public String get_activity(){
		return this_activity;
	}
	
	public String get_xpath(){
		return this_xpath;
	}
	
	public Long get_scope_id(){
		return this_scope_id;
	}
	
	public Long get_process_id(){
		return this_process_id;
	}
	
	public Long get_message_id(){
		return this_message_id;
	}
	
	public int get_view(){
		return this_view;
	}
	
	public String get_status(){
		return this_status;
	}
	
	public String get_ip(){
		return this_ip;
	}
	
	public String get_activity_type(){
		int from = this_xpath.lastIndexOf("/");
		int to = -1;
		to = this_xpath.lastIndexOf("[");
		if(to == -1){
			return (this_xpath.substring(from + 1, this_xpath.length()));
		}
		else{
			return (this_xpath.substring(from + 1, to));
			
		}
	}
	
	public void set_view(int view){
		this_view = view;
	}
	
	public void set_status(String status){
		this_status = status;
	}
}