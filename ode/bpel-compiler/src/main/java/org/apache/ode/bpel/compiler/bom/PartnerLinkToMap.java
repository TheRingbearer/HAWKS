package org.apache.ode.bpel.compiler.bom;

import org.w3c.dom.Element;

public class PartnerLinkToMap extends BpelObject {
	public PartnerLinkToMap(Element el) {
		super(el);
	}

	public String getName() {
		return getElement().getAttribute("name");
	}
}
