package org.apache.ode.bpel.extensions.events;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.runtime.ACTIVITY;
import org.apache.ode.bpel.runtime.SCOPE;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannel;
import org.apache.ode.bpel.runtime.channels.TerminationChannel;

public class ScopeActivityReady extends BpelScopeEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private LinkStatusChannel chan;
	private TerminationChannel termChannel;
	private ParentScopeChannel faultChannel;
	private OScope oscope;
	private SCOPE scope;
	private Boolean ignore;
	private ACTIVITY activity;
	private String link_name;

	public ScopeActivityReady(String act_name, int oBase_id, String act_loc, Long id,
			String scope_loc, Long scope_id, QName proc_name, Long proc_id,
			Boolean art, Boolean scop, Long selfID, LinkStatusChannel signal,
			TerminationChannel trm, ParentScopeChannel flt, OScope o,
			SCOPE scope_impl, Boolean ign, ACTIVITY act) {
		super(act_name, oBase_id, act_loc, id, scope_loc, scope_id, proc_name, proc_id,
				art, scop, selfID);
		// TODO Auto-generated constructor stub
		chan = signal;
		termChannel = trm;
		faultChannel = flt;
		oscope = o;
		scope = scope_impl;
		ignore = ign;
		activity = act;
	}

	public LinkStatusChannel getChan() {
		return chan;
	}

	public TerminationChannel getTermChannel() {
		return termChannel;
	}

	public ParentScopeChannel getFaultChannel() {
		return faultChannel;
	}

	public OScope getOscope() {
		return oscope;
	}

	public SCOPE getScope() {
		return scope;
	}

	public Boolean getIgnore() {
		return ignore;
	}

	public ACTIVITY getActivity() {
		return activity;
	}

	public String getLink_name() {
		return link_name;
	}

	public void setLink_name(String link_name) {
		this.link_name = link_name;
	}

}
