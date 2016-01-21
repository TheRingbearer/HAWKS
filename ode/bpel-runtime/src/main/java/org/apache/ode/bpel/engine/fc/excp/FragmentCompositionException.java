package org.apache.ode.bpel.engine.fc.excp;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class FragmentCompositionException extends Exception {

	private static final long serialVersionUID = 9052461952290680611L;

	public FragmentCompositionException() {
		super();
	}

	public FragmentCompositionException(String message) {
		super(message);
	}

	public FragmentCompositionException(Exception e) {
		super(e);
	}
}
