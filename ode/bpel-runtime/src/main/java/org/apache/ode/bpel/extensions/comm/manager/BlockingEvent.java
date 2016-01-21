package org.apache.ode.bpel.extensions.comm.manager;

import org.apache.ode.bpel.evt.BpelEvent;

//@stmz: represents a blocking event, waiting for an incoming event to unblock it
//contains the BpelEvent, so that we can fetch the channel we need to unblock this blocking event
public class BlockingEvent {
	private Long MsgID;
	private String xpath;
	private Long scopeID;
	private Long processID;
	private Boolean isActivityEvent;
	private BpelEvent bpelEvent;
	private Long processVersion;

	// @stmz: for Scopes: scopeID is ID of the scope. in case of other
	// activities, its the ID of the surrounding scope.
	public BlockingEvent(Long msg_id, String path, Long scopID, Long procID,
			Boolean isAct, BpelEvent evt, Long version) {
		MsgID = msg_id;
		xpath = path;
		scopeID = scopID;
		processID = procID;
		isActivityEvent = isAct;
		bpelEvent = evt;
		processVersion = version;
	}

	public Long getMsgID() {
		return MsgID;
	}

	public String getXpath() {
		return xpath;
	}

	public Long getScopeID() {
		return scopeID;
	}

	public Long getProcessID() {
		return processID;
	}

	public Boolean getIsActivityEvent() {
		return isActivityEvent;
	}

	public BpelEvent getBpelEvent() {
		return bpelEvent;
	}

	public Long getProcessVersion() {
		return processVersion;
	}
}
