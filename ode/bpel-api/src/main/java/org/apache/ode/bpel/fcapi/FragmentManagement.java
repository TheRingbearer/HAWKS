package org.apache.ode.bpel.fcapi;

/**
 * 
 * @author Alex Hummel
 * 
 */
public interface FragmentManagement {
	public StringListDocument getAvailableFragments() throws ManagementFault;

	public StringListDocument getAvailableStartFragments()
			throws ManagementFault;

	public StringListDocument getAvailableNonStartFragments()
			throws ManagementFault;
}
