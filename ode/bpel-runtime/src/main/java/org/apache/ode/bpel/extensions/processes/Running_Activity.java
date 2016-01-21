package org.apache.ode.bpel.extensions.processes;

import org.apache.ode.bpel.runtime.channels.TerminationChannel;

//@stmz: represetns a running activity
public class Running_Activity {
	private Long processID;
	private Long scopeID;
	private String XPath;
	private TerminationChannel termChannel;
	private String termChannelName;

	public Running_Activity(Long pr_id, Long scop_id, String path,
			TerminationChannel term, String termName) {
		processID = pr_id;
		scopeID = scop_id;
		XPath = path;
		termChannel = term;
		termChannelName = termName;
	}

	public Long getProcessID() {
		return processID;
	}

	public Long getScopeID() {
		return scopeID;
	}

	public String getXPath() {
		return XPath;
	}

	public TerminationChannel getTermChannel() {
		return termChannel;
	}

	public String getTermChannelName() {
		return termChannelName;
	}
}
