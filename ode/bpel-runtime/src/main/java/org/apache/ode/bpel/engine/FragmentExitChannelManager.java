package org.apache.ode.bpel.engine;

import java.util.HashMap;

import org.apache.ode.bpel.o.OFragmentExit;
import org.apache.ode.bpel.runtime.channels.FragmentCompositionResponseChannel;

public class FragmentExitChannelManager {
	private HashMap<OFragmentExit, FragmentCompositionResponseChannel> fragmentExitChannelMap;

	public FragmentExitChannelManager() {
		fragmentExitChannelMap = new HashMap<OFragmentExit, FragmentCompositionResponseChannel>();
	}

	public FragmentCompositionResponseChannel getChannel(OFragmentExit exit) {
		return fragmentExitChannelMap.get(exit);
	}

	public void removeChannel(OFragmentExit exit) {
		fragmentExitChannelMap.remove(exit);
	}

	public void putChannel(OFragmentExit exit,
			FragmentCompositionResponseChannel channel) {
		fragmentExitChannelMap.put(exit, channel);
	}
}
