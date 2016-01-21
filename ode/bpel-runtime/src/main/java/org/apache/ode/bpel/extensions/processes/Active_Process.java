package org.apache.ode.bpel.extensions.processes;

import javax.xml.namespace.QName;

//@stmz: represents a running process instance
public class Active_Process {
	private QName name;
	private Long ID;
	private long version;

	public Active_Process(QName qname, Long processID, Long vers) {
		name = qname;
		ID = processID;
		version = vers;
	}

	public QName getName() {
		return name;
	}

	public Long getID() {
		return ID;
	}

	public long getVersion() {
		return version;
	}

}
