package org.apache.ode.bpel.extensions.processes;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.runtime.SCOPE;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannel;
import org.apache.ode.bpel.runtime.channels.TerminationChannel;

//@stmz: represents a running scope activity
public class Running_Scope {
	private Long scopeID;
	private TerminationChannel termChannel;
	private ParentScopeChannel faultChannel;
	private QName process_name;
	private Long process_ID;
	private OScope oscope;
	private SCOPE scope;
	private String faultChannelName;

	public Running_Scope(QName pr_name, Long pr_ID, Long scp_ID,
			TerminationChannel trm, ParentScopeChannel flt, OScope o,
			SCOPE scop, String faultChanName) {
		process_name = pr_name;
		process_ID = pr_ID;
		scopeID = scp_ID;
		termChannel = trm;
		faultChannel = flt;
		oscope = o;
		scope = scop;
		faultChannelName = faultChanName;
	}

	public Long getScopeID() {
		return scopeID;
	}

	public TerminationChannel getTermChannel() {
		return termChannel;
	}

	public ParentScopeChannel getFaultChannel() {
		return faultChannel;
	}

	public QName getProcess_name() {
		return process_name;
	}

	public Long getProcess_ID() {
		return process_ID;
	}

	public OScope getOscope() {
		return oscope;
	}

	public SCOPE getScope() {
		return scope;
	}

	public String getFaultChannelName() {
		return faultChannelName;
	}
}
