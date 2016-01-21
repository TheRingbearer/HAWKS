package org.apache.ode.bpel.extensions.events;

import javax.xml.namespace.QName;

public class BpelScopeEvent extends BpelActivityEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Long selfScopeID;

	public BpelScopeEvent(String act_name, int oBase_id, String act_loc, Long id,
			String scope_loc, Long scope_id, QName proc_name, Long proc_id,
			Boolean art, Boolean scop, Long selfID) {
		super(act_name, oBase_id, act_loc, id, scope_loc, scope_id, proc_name, proc_id,
				art, scop);
		// TODO Auto-generated constructor stub
		selfScopeID = selfID;
	}

	public Long getSelfScopeID() {
		return selfScopeID;
	}

}
