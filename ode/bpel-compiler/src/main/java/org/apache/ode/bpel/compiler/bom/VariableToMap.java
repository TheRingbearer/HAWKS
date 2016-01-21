package org.apache.ode.bpel.compiler.bom;

import org.w3c.dom.Element;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class VariableToMap extends BpelObject {

	public VariableToMap(Element el) {
		super(el);
	}

	public String getName() {
		return getElement().getAttribute("name");
	}
}
