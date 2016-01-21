package org.apache.ode.bpel.extensions.events;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;

public class ScopeHandlingTermination extends BpelScopeEvent {

	private static final long serialVersionUID = 454361L;

	private Boolean ignore;

	private LinkStatusChannel chan;
	private String link_name;

	public ScopeHandlingTermination(String act_name, int oBase_id, String act_loc, Long id,
			String scope_loc, Long scope_id, QName proc_name, Long proc_id,
			Boolean art, Boolean scop, Long selfID, Boolean ign,
			LinkStatusChannel signal) {
		super(act_name, oBase_id, act_loc, id, scope_loc, scope_id, proc_name, proc_id,
				art, scop, selfID);

		ignore = ign;
		chan = signal;
	}

	public Boolean getIgnore() {
		return ignore;
	}

	public LinkStatusChannel getChan() {
		return chan;
	}

	public String getLink_name() {
		return link_name;
	}

	public void setLink_name(String link_name) {
		this.link_name = link_name;
	}
}
