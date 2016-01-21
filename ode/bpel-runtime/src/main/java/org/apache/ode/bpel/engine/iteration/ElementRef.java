package org.apache.ode.bpel.engine.iteration;

import java.io.Serializable;

public class ElementRef implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private String name = "";
	private String scopeId = "";
	
	public ElementRef(String name, String scopeId) {
		this.name = name;
		this.scopeId = scopeId;
	}

	public String getName() {
		return name;
	}

	public String getScopeId() {
		return scopeId;
	}
}
