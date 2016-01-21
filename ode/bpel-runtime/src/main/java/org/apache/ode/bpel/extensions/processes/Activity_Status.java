package org.apache.ode.bpel.extensions.processes;


//@hahnml: Represents the status of an activity
public class Activity_Status {
	
	// @hahnml
	// @krawczls: added skipped, uncommitted, uncommittedskipped, executed, ready and committed to the enumeration type
	public enum ActivityStatus {
		running, completed, faulted, terminated, skipped, uncommitted, uncommittedskipped, executed, ready, committed
	}
	
	private Long processID;
	private String XPath;
	private ActivityStatus actStatus;

	public Activity_Status(Long pr_id, String xpath,
			ActivityStatus status) {
		processID = pr_id;
		XPath = xpath;
		actStatus = status;
	}

	public ActivityStatus getActStatus() {
		return actStatus;
	}

	public void setActStatus(ActivityStatus actStatus) {
		this.actStatus = actStatus;
	}

	public Long getProcessID() {
		return processID;
	}

	public String getXPath() {
		return XPath;
	}
}
