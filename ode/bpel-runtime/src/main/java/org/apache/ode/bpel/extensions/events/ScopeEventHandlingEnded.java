package org.apache.ode.bpel.extensions.events;

import javax.xml.namespace.QName;

public class ScopeEventHandlingEnded extends BpelScopeEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Boolean ignore;

	public ScopeEventHandlingEnded(String act_name, int oBase_id, String act_loc, Long id,
			String scope_loc, Long scope_id, QName proc_name, Long proc_id,
			Boolean art, Boolean scop, Long selfID, Boolean ign) {
		super(act_name, oBase_id, act_loc, id, scope_loc, scope_id, proc_name, proc_id,
				art, scop, selfID);
		// TODO Auto-generated constructor stub
		ignore = ign;
	}

	public Boolean getIgnore() {
		return ignore;
	}

}
