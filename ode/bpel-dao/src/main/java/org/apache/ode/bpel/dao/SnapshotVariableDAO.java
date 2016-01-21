package org.apache.ode.bpel.dao;

import org.w3c.dom.Node;

public interface SnapshotVariableDAO {

	public Long getSnapshotID();

	/**
	 * Get the name of the variable.
	 * 
	 * @return variable name
	 */
	public String getName();

	/**
	 * Checks if the dao has been assigned any data.
	 * 
	 * @return <code>true</code> is assignment has NOT occured.
	 */
	public boolean isNull();

	/**
	 * Retreive the variable data.
	 * 
	 * @return the variable data
	 */
	public Node get();

	/**
	 * Set the data value of a variable.
	 * 
	 * @param val
	 *            value
	 */
	public void set(Node val);

	public void setScopeInstanceId(Long id);

	public Long getScopeInstanceId();

}
