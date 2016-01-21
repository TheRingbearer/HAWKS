package org.apache.ode.bpel.extensions.processes;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.runtime.COMPENSATIONHANDLER_;

//@stmz: represents a compensation handler of a finished scope
public class Compensation_Handler {
	private Long ScopeID;
	private QName process_name;
	private Long process_ID;
	private COMPENSATIONHANDLER_ compHandler;
	private String compChannelName;

	public Compensation_Handler(Long id, QName name, Long pr_id,
			COMPENSATIONHANDLER_ c, String cName) {
		ScopeID = id;
		process_name = name;
		process_ID = pr_id;
		compHandler = c;
		compChannelName = cName;
	}

	public Long getScopeID() {
		return ScopeID;
	}

	public QName getProcess_name() {
		return process_name;
	}

	public Long getProcess_ID() {
		return process_ID;
	}

	public COMPENSATIONHANDLER_ getCompHandler() {
		return compHandler;
	}

	public String getCompChannelName() {
		return compChannelName;
	}

}
