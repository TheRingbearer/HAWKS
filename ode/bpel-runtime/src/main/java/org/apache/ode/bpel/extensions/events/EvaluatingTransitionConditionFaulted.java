package org.apache.ode.bpel.extensions.events;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;

public class EvaluatingTransitionConditionFaulted extends BpelActivityEvent {

	private static final long serialVersionUID = 14363L;
	private LinkStatusChannel chan;
	private FaultData fault;
	private String linkXPath;
	private String link_name;

	public EvaluatingTransitionConditionFaulted(String act_name, int oBase_id,
			String act_loc, Long id, String scope_loc, Long scope_id,
			QName proc_name, Long proc_id, Boolean art, Boolean scop,
			LinkStatusChannel signal, FaultData flt, String lnkPath) {
		super(act_name, oBase_id, act_loc, id, scope_loc, scope_id, proc_name, proc_id,
				art, scop);

		chan = signal;
		fault = flt;
		linkXPath = lnkPath;
	}

	public LinkStatusChannel getChan() {
		return chan;
	}

	public FaultData getFault() {
		return fault;
	}

	public String getLinkXPath() {
		return linkXPath;
	}

	public String getLink_name() {
		return link_name;
	}

	public void setLink_name(String link_name) {
		this.link_name = link_name;
	}

}
