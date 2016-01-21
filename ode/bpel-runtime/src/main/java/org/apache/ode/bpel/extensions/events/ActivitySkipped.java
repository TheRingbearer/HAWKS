package org.apache.ode.bpel.extensions.events;

import javax.xml.namespace.QName;


/**
 * A class representing an ActivitySkipped event. 
 * Is fired when an activity is skipped and the workflow continues with the next activity.
 * 
 * @author krawczls
 *
 */
public class ActivitySkipped extends BpelActivityEvent {
	
	private static final long serialVersionUID = 1L;
	
	
	/**
	 * A contructor for the ActivitySkipped class.
	 * 
	 * @param act_name
	 * @param oBase_id
	 * @param act_loc
	 * @param id
	 * @param scope_loc
	 * @param scope_id
	 * @param proc_name
	 * @param proc_id
	 * @param art
	 * @param scop
	 */
	public ActivitySkipped(String act_name, int oBase_id, String act_loc, Long id,
			String scope_loc, Long scope_id, QName proc_name, Long proc_id,
			Boolean art, Boolean scop) {
		super(act_name, oBase_id, act_loc, id, scope_loc, scope_id, proc_name, proc_id,
				art, scop);
	}
	
	
	
}