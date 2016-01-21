package org.apache.ode.bpel.runtime.channels;

import org.apache.ode.jacob.ap.ChannelType;

/**
 * 
 * @author Alex Hummel
 * 
 */
@ChannelType
public interface FragmentCompositionResponse {
	public void fragmentCompositionCompleted();
}
