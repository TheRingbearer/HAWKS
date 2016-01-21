package org.apache.ode.fc.dao;

import java.util.List;

public interface FCManagementDAO {
	public void addFragmentEntryChannel(Long instanceId, int fragmentEntryId,
			String channel);

	public void addFragmentExitChannel(Long instanceId, int fragmentExitId,
			String channel);

	public void addGlueResponseChannel(Long instanceId, int elementId,
			String channel);

	public void cleanUpChannels(Long instanceId,
			List<Integer> containerIdsNeeded);

	public List<Integer> getActiveFragmentContainers(Long instanceId);

	public List<Integer> getActiveFragmentExits(Long instanceId);

	public List<Integer> getActiveFragmentEntries(Long instanceId);

	public String getChannel(Long instanceId, int fragmentExitId);

	public List<MappingInfo> getElementMapping(Long instanceId, int activityId);

	public void mapElements(Long instanceId, int activityId,
			List<MappingInfo> mapping);

	public void removeChannel(Long instanceId, int elementId);

	public void removeMappings(Long instanceId, int activityId);
}
