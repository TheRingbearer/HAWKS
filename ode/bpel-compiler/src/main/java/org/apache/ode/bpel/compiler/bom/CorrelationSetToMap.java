package org.apache.ode.bpel.compiler.bom;

import org.w3c.dom.Element;

public class CorrelationSetToMap extends BpelObject {
	public CorrelationSetToMap(Element el) {
		super(el);
	}

	public String getName() {
		return getElement().getAttribute("name");
	}
}
