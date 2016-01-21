package org.apache.ode.daohib.bpel.hobj;

/**
 * Hibernate table representing a BPEL link instance.
 * 
 * @hibernate.class table="BPEL_LINK"
 * @hibernate.query name="SELECT_LINK_IDS_BY_INSTANCES"
 *                  query="select id from HLink as l where l.instance in (:instances)"
 */
public class HLink extends HObject {
	public final static String SELECT_LINK_IDS_BY_INSTANCES = "SELECT_LINK_IDS_BY_INSTANCES";

	/** Process instance to which this link belongs. */
	private HProcessInstance _instance;
	
	/** State of the link. */
	private String _state;

	/** XPath of the link. */
	private String _xpath;

	/** Model id of the link. */
	private int _linkModelId;
	
	public HLink() {
		super();
	}
	
	/**
	 * Get the {@link HProcessInstance} to which this link object belongs.
	 * 
	 * @hibernate.many-to-one column="PIID" foreign-key="none"
	 */
	public HProcessInstance getInstance() {
		return _instance;
	}

	/** @see #getInstance() */
	public void setInstance(HProcessInstance instance) {
		_instance = instance;
	}
	
	/**
	 * @hibernate.property column="MODELID"
	 */
	public int getModelId() {
		return _linkModelId;
	}

	public void setModelId(int modelId) {
		_linkModelId = modelId;
	}
	
	/**
	 * @hibernate.property column="STATE" not-null="true"
	 */
	public String getState() {
		return _state;
	}

	/** @see #getState() */
	public void setState(String state) {
		_state = state;
	}
	
	/**
	 * Get the xpath of the link.
	 * 
	 * @hibernate.property column="XPATH" not-null="true"
	 */
	public String getXPath() {
		return _xpath;
	}

	/** @see #getXPath() */
	public void setXPath(String xpath) {
		_xpath = xpath;
	}
	
	public String toString() {
		return "HLink{id=" + getId() + ",xpath=" + _xpath + ",modelId=" + _linkModelId + ",state=" + _state + "}";
	}
}
