package org.apache.ode.bpel.extensions.events;

import javax.xml.namespace.QName;

public class ActivityComplete extends BpelActivityEvent {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public ActivityComplete(String act_name, int oBase_id, String act_loc, Long id,
			String scope_loc, Long scope_id, QName proc_name, Long proc_id,
			Boolean art, Boolean scop) {
		super(act_name, oBase_id, act_loc, id, scope_loc, scope_id, proc_name, proc_id,
				art, scop);
		// TODO Auto-generated constructor stub
	}

}
