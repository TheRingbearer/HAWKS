package org.apache.ode.mediator;

import javax.xml.namespace.QName;

public class CSetMediationInfo {
	private QName processName;
	private String scopeName;
	private String correlationSetName;

	public CSetMediationInfo(QName processName, String scopeName,
			String correlationSetName) {
		this.processName = processName;
		this.scopeName = scopeName;
		this.correlationSetName = correlationSetName;
	}

	public QName getProcessName() {
		return processName;
	}

	public String getScopeName() {
		return scopeName;
	}

	public String getCorrelationSetName() {
		return correlationSetName;
	}
	
}
