package org.apache.ode.bpel.engine;

import java.io.Serializable;

public class FragmentCompositionResponseDummy extends
		FragmentCompositionResponse implements Serializable {
	public FragmentCompositionResponseDummy() {
		super(null, null, null, null);
	}

	public void returnValue(Object value) {
		if (onSuccess != null) {
			onSuccess.run();
		}
	}

	public void throwException(Exception e) {

	}
}
