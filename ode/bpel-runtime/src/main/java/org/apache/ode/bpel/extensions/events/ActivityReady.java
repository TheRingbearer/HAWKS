package org.apache.ode.bpel.extensions.events;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.runtime.ACTIVITY;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.apache.ode.bpel.runtime.channels.TerminationChannel;

public class ActivityReady extends BpelActivityEvent {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private LinkStatusChannel chan;
	private TerminationChannel termChan;
	private ACTIVITY activity;
	private String link_name;

	public ActivityReady(String act_name, int oBase_id, String act_loc, Long id,
			String scope_loc, Long scope_id, QName proc_name, Long proc_id,
			Boolean art, Boolean scop, LinkStatusChannel signal,
			TerminationChannel term, ACTIVITY act) {
		super(act_name, oBase_id, act_loc, id, scope_loc, scope_id, proc_name, proc_id,
				art, scop);
		chan = signal;
		termChan = term;
		activity = act;
		// TODO Auto-generated constructor stub
	}

	public LinkStatusChannel getChan() {
		return chan;
	}

	public TerminationChannel getTermChan() {
		return termChan;
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
