package org.apache.ode.bpel.extensions.events;

import javax.xml.namespace.QName;

public class LinkReady extends BpelLinkEvent {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public LinkReady(String xpath, int oBase_id, String name, String xpath_scope,
			Long scopeID, QName pr_name, Long pr_id, String src_xpath,
			String tr_xpath) {
		super(xpath, oBase_id, name, xpath_scope, scopeID, pr_name, pr_id, src_xpath,
				tr_xpath);
	}

}
