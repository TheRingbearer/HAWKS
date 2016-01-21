package org.apache.ode.bpel.iapi.fc;

import java.util.List;

import org.apache.ode.bpel.iapi.ProcessConf;

public interface ProcessConfLoader {
	public List<ProcessConf> reload(String deploymentUnitName);
}
