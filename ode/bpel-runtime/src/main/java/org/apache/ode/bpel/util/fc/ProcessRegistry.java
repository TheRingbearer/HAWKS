package org.apache.ode.bpel.util.fc;

import org.apache.ode.bpel.iapi.ProcessConf;

/**
 * 
 * @author Alex Hummel
 * 
 */
public interface ProcessRegistry {
	public void reregister(ProcessConf conf);

	public void releaseManagementReadLock();

	public void getManagementReadLock();
}
