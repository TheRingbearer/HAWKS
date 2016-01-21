package org.apache.ode.bpel.dao;

/**
 * @author hahnml
 *
 */
public interface LinkDAO {

	/**
	 * Get the link model id from the object
	 * 
	 * @return link model id
	 */
	int getModelId();
	
	/**
	 * Get xpath of the link.
	 * 
	 * @return link xpath
	 */
	String getXPath();
	
	/**
	 * Set current state of the link.
	 * 
	 * @param state
	 *            new link state
	 */
	void setState(LinkStateEnum state);

	/**
	 * Get current state of the link.
	 * 
	 * @return current link state
	 */
	LinkStateEnum getState();
	
	/**
	 * Get the process instance to which this link belongs.
	 * 
	 * @return owner {@link ProcessInstanceDAO}
	 */
	ProcessInstanceDAO getProcessInstance();
}
