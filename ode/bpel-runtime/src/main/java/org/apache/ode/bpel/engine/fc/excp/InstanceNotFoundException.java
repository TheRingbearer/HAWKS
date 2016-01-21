package org.apache.ode.bpel.engine.fc.excp;

/**
 * 
 * @author Alex Hummel
 * 
 */

public class InstanceNotFoundException extends Exception {

	private static final long serialVersionUID = 8996246262629901797L;

	public InstanceNotFoundException() {
		super();
	}

	public InstanceNotFoundException(String message) {
		super(message);
	}
}
