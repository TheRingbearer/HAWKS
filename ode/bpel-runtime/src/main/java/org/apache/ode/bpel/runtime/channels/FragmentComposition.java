package org.apache.ode.bpel.runtime.channels;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.engine.FragmentCompositionResponse;
import org.apache.ode.jacob.ap.ChannelType;
import org.apache.ode.utils.fc.Mapping;

/**
 * 
 * @author Alex Hummel
 * 
 */
@ChannelType
public interface FragmentComposition {
	public void glue(QName newFragmentName, FragmentCompositionResponse response);

	public void wireAndMap(int fragmentExitId, int fragmentEntryId,
			Mapping[] mappings, FragmentCompositionResponse response);

	public void ignoreFragmentExit(int fragmentExitId,
			FragmentCompositionResponse response);

	public void ignoreFragmentEntry(int fragmentEntryId,
			FragmentCompositionResponse response);
}
