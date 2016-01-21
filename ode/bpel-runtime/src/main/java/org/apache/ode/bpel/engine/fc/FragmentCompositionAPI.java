package org.apache.ode.bpel.engine.fc;

import org.apache.ode.bpel.engine.fc.excp.FragmentCompositionException;
import org.apache.ode.bpel.engine.fc.excp.InstanceNotFoundException;
import org.apache.ode.bpel.fcapi.ActivityListDocument;
import org.apache.ode.bpel.fcapi.MappingListDocument;
import org.apache.ode.bpel.fcapi.StringListDocument;
import org.apache.ode.bpel.fcapi.VariableInfoListDocument;

/**
 * 
 * @author Alex Hummel
 * 
 */
public interface FragmentCompositionAPI {
	public boolean glue(Long instanceId, int containerId, String newFragmentName)
			throws FragmentCompositionException, InstanceNotFoundException;

	public ActivityListDocument getFragmentContainers(Long instanceId)
			throws InstanceNotFoundException;

	public ActivityListDocument getDanglingExits(Long instanceId)
			throws InstanceNotFoundException;

	public ActivityListDocument getDanglingEntries(Long instanceId)
			throws InstanceNotFoundException;

	public VariableInfoListDocument getVariablesToMap(Long instanceId,
			int elementId) throws InstanceNotFoundException;

	public StringListDocument getPartnerLinksToMap(Long instanceId,
			int elementId) throws InstanceNotFoundException;

	public StringListDocument getCorrelationSetsToMap(Long instanceId,
			int elementId) throws InstanceNotFoundException;

	public ActivityListDocument getIgnorableExits(Long instanceId)
			throws InstanceNotFoundException;

	public ActivityListDocument getIgnorableEntries(Long instanceId)
			throws InstanceNotFoundException;

	public VariableInfoListDocument getAvailableVariables(Long instanceId,
			int elementId) throws FragmentCompositionException,
			InstanceNotFoundException;

	public StringListDocument getAvailablePartnerLinks(Long instanceId,
			int elementId) throws FragmentCompositionException,
			InstanceNotFoundException;

	public StringListDocument getAvailableCorrelationSets(Long instanceId,
			int elementId) throws FragmentCompositionException,
			InstanceNotFoundException;

	public boolean wireAndMap(Long instanceId, int fragmentExitId,
			int fragmentEntryId, MappingListDocument mappings)
			throws FragmentCompositionException, InstanceNotFoundException;

	public boolean ignoreFragmentExit(Long instanceId, int fragmentExitId)
			throws FragmentCompositionException, InstanceNotFoundException;

	public boolean ignoreFragmentEntry(Long instanceId, int fragmentEntryId)
			throws FragmentCompositionException, InstanceNotFoundException;

	public byte[] getProcessImage(Long instanceId)
			throws InstanceNotFoundException;
}
