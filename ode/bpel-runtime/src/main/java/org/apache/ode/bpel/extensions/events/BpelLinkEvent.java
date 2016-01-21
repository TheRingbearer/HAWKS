package org.apache.ode.bpel.extensions.events;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.evt.BpelEvent;

public class BpelLinkEvent extends BpelEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private String XPath_Link;
	private String link_name;
	private String xpath_surrounding_scope;
	private Long ID_scope;
	private QName process_name;
	private Long process_ID;
	private String source_xpath;
	private String target_xpath;
	
	// @hahnml: OActivity id
	private int oBaseId;

	public BpelLinkEvent(String xpath, int oBase_id, String name, String xpath_scope,
			Long scopeID, QName pr_name, Long pr_id, String src_xpath,
			String tr_xpath) {
		XPath_Link = xpath;
		link_name = name;
		xpath_surrounding_scope = xpath_scope;
		ID_scope = scopeID;
		process_name = pr_name;
		process_ID = pr_id;
		source_xpath = src_xpath;
		target_xpath = tr_xpath;
		// @hahnml
		oBaseId = oBase_id;
	}

	@Override
	public TYPE getType() {
		// TODO Auto-generated method stub
		return TYPE.activityLifecycle;
	}

	public String getXPath_Link() {
		return XPath_Link;
	}

	public String getLink_name() {
		return link_name;
	}

	public String getXpath_surrounding_scope() {
		return xpath_surrounding_scope;
	}

	public Long getID_scope() {
		return ID_scope;
	}

	public QName getProcess_name() {
		return process_name;
	}

	public Long getProcess_ID() {
		return process_ID;
	}

	public String getSource_xpath() {
		return source_xpath;
	}

	public String getTarget_xpath() {
		return target_xpath;
	}

	//@hahnml
	public int getOBaseId() {
		return oBaseId;
	}
}
