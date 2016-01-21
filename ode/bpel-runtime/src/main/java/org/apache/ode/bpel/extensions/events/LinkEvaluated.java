package org.apache.ode.bpel.extensions.events;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;

public class LinkEvaluated extends BpelLinkEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Boolean value;
	private LinkStatusChannel chan;
	private String link_name2;

	public LinkEvaluated(String xpath, int oBase_id, String name, String xpath_scope,
			Long scopeID, QName pr_name, Long pr_id, String src_xpath,
			String tr_xpath, Boolean val, LinkStatusChannel channel) {
		super(xpath, oBase_id, name, xpath_scope, scopeID, pr_name, pr_id, src_xpath,
				tr_xpath);

		value = val;
		chan = channel;
	}

	public Boolean getValue() {
		return value;
	}

	public LinkStatusChannel getChan() {
		return chan;
	}

	public String getLink_name2() {
		return link_name2;
	}

	public void setLink_name2(String link_name2) {
		this.link_name2 = link_name2;
	}

}
