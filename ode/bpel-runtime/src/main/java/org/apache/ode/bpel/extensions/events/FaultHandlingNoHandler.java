package org.apache.ode.bpel.extensions.events;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;

public class FaultHandlingNoHandler extends BpelScopeEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private LinkStatusChannel chan;

	private Boolean ignore;
	private String link_name;

	public FaultHandlingNoHandler(String act_name, int oBase_id, String act_loc, Long id,
			String scope_loc, Long scope_id, QName proc_name, Long proc_id,
			Boolean art, Boolean scop, Long selfID, LinkStatusChannel signal,
			Boolean ign) {
		super(act_name, oBase_id, act_loc, id, scope_loc, scope_id, proc_name, proc_id,
				art, scop, selfID);
		// TODO Auto-generated constructor stub
		ignore = ign;
		chan = signal;
	}

	public LinkStatusChannel getChan() {
		return chan;
	}

	public Boolean getIgnore() {
		return ignore;
	}

	public String getLink_name() {
		return link_name;
	}

	public void setLink_name(String link_name) {
		this.link_name = link_name;
	}

}
