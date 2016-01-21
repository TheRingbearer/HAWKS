package de.ustutt.simtech.extensions;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.runtime.common.extension.AbstractExtensionBundle;

public class SimTechExtensionBundle extends AbstractExtensionBundle {

	private static final Log __log = LogFactory.getLog(SimTechExtensionBundle.class);
	
	public static final String NAMESPACE = "http://de.ustutt.simtech/bpel/extensions";
	
	public SimTechExtensionBundle() {
		
	}
	
	@Override
	public String getNamespaceURI() {
		// TODO Auto-generated method stub
		return NAMESPACE;
	}

	@Override
	public void registerExtensionActivities() {
		
		// TODO Auto-generated method stub
		super.registerExtensionOperation("simulationStartActivity", SimulationStartActivity.class);
		
		if (__log.isDebugEnabled()) {
			__log.debug("SimTech Extension Bundle loaded successfully.");
		}
	}
	
}
