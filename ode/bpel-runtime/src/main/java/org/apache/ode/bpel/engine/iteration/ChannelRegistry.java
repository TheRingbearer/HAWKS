package org.apache.ode.bpel.engine.iteration;

import java.util.HashMap;

/**
 * This class provides some channels of all currently running process instances.
 * The registered channels are used to solve some issues during the
 * iteration/reexecution of parts of the corresponding instances.
 * 
 * @author hahnml
 */
public class ChannelRegistry {

	private static ChannelRegistry _registry = null;
	
	private HashMap<Long, HashMap<String, String>> _pickResponseChannels = new HashMap<Long, HashMap<String, String>>();
	
	public static ChannelRegistry getRegistry() {
		if (_registry == null) {
			_registry = new ChannelRegistry();
		}
		
		return _registry;
	}
	
	public void registerPickResponseChannel(Long instanceID, String pickResponseChannelID, String correlatorID) {
		if (!_pickResponseChannels.containsKey(instanceID)) {
			_pickResponseChannels.put(instanceID, new HashMap<String, String>());
		}
		
		HashMap<String, String> pickRespCorMap = _pickResponseChannels.get(instanceID);
		pickRespCorMap.put(pickResponseChannelID, correlatorID);
	}

	public String getPickResponseChannelCorrelator(Long instanceID, String channelID) {
		String correlatorID = null;
		
		if (_pickResponseChannels.containsKey(instanceID)) {
			correlatorID = _pickResponseChannels.get(instanceID).get(channelID);
		}
		
		return correlatorID;
	}
	
	public void removeInstanceFromRegistry(Long instanceID) {
		_pickResponseChannels.remove(instanceID);
	}
}
