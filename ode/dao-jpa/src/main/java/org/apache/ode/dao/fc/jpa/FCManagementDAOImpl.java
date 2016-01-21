package org.apache.ode.dao.fc.jpa;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.fc.dao.FCManagementDAO;
import org.apache.ode.fc.dao.MappingInfo;

/**
 * 
 * @author Alex Hummel
 * 
 */

public class FCManagementDAOImpl implements FCManagementDAO {
	private static final Log __log = LogFactory
			.getLog(FCManagementDAOImpl.class);
	private static final int FRAGMENT_EXIT = 0;
	private static final int FRAGMENT_ENTRY = 1;
	private static final int FRAGMENT_CONTAINER = 2;

	private static final Log log = LogFactory.getLog(FCManagementDAOImpl.class);

	private EntityManager em;

	public FCManagementDAOImpl(EntityManager em) {
		this.em = em;
	}

	public void addFragmentExitChannel(Long instanceId, int fragmentExitId,
			String channel) {
		ChannelSelectorDAOImpl dao = new ChannelSelectorDAOImpl(instanceId,
				fragmentExitId, channel, FRAGMENT_EXIT);
		em.persist(dao);
	}

	public void addGlueResponseChannel(Long instanceId, int elementId,
			String channel) {
		ChannelSelectorDAOImpl glueSelectorDAO = new ChannelSelectorDAOImpl(
				instanceId, elementId, channel, FRAGMENT_CONTAINER);
		em.persist(glueSelectorDAO);
	}

	public void cleanUpChannels(Long instanceId,
			List<Integer> containerIdsNeeded) {
		HashSet<Integer> containerIds = new HashSet<Integer>(containerIdsNeeded);
		Query query = em
				.createNamedQuery(ChannelSelectorDAOImpl.GET_ACTIVITY_IDS);
		query.setParameter("instanceId", instanceId);
		query.setParameter("type", FRAGMENT_CONTAINER);
		List<Integer> ids = query.getResultList();
		for (Integer id : ids) {
			if (!containerIds.contains(id)) {
				removeChannel(instanceId, id);
			}
		}

	}

	public List<Integer> getActiveFragmentContainers(Long instanceId) {
		Query query = em
				.createNamedQuery(ChannelSelectorDAOImpl.GET_ACTIVITY_IDS);
		query.setParameter("instanceId", instanceId);
		query.setParameter("type", FRAGMENT_CONTAINER);
		List<Integer> ids = query.getResultList();
		return ids;
	}

	public List<Integer> getActiveFragmentExits(Long instanceId) {
		Query query = em
				.createNamedQuery(ChannelSelectorDAOImpl.GET_ACTIVITY_IDS);
		query.setParameter("instanceId", instanceId);
		query.setParameter("type", FRAGMENT_EXIT);
		List<Integer> ids = query.getResultList();
		return ids;
	}

	public String getChannel(Long instanceId, int fragmentExitId) {
		String channel = null;
		try {
			Query query = em
					.createNamedQuery(ChannelSelectorDAOImpl.GET_CHANNEL);
			query.setParameter("instanceId", instanceId);
			query.setParameter("elementId", fragmentExitId);
			channel = (String) query.getSingleResult();
		} catch (NoResultException e) {

		}
		return channel;
	}

	public List<MappingInfo> getElementMapping(Long instanceId, int activityId) {
		Query query = em.createNamedQuery(ElementMappingDAOImpl.GET_DATA);
		query.setParameter("instanceId", instanceId);
		query.setParameter("activityId", activityId);
		List<Object[]> result = query.getResultList();

		ArrayList<MappingInfo> mapping = new ArrayList<MappingInfo>();
		for (Object[] line : result) {
			mapping.add(new MappingInfo((Integer) line[0], (String) line[1]));
		}
		return mapping;
	}

	public void mapElements(Long instanceId, int activityId,
			List<MappingInfo> mapping) {
		__log.info("MapElements instanceId:" + instanceId + " elementId:"
				+ activityId);
		for (MappingInfo info : mapping) {
			ElementMappingDAOImpl dao = new ElementMappingDAOImpl(instanceId,
					activityId, info.getVariableId(), info.getMappingData());
			em.persist(dao);
		}
	}

	public void removeChannel(Long instanceId, int fragmentExitId) {
		Query query = em
				.createNamedQuery(ChannelSelectorDAOImpl.DELETE_CHANNEL);
		query.setParameter("instanceId", instanceId);
		query.setParameter("elementId", fragmentExitId);
		query.executeUpdate();
	}

	public void removeMappings(Long instanceId, int elementId) {
		__log.info("Remove Mappings instanceId:" + instanceId + " elementId:"
				+ elementId);
		Query query = em
				.createNamedQuery(ElementMappingDAOImpl.REMOVE_MAPPINGS);
		query.setParameter("instanceId", instanceId);
		query.setParameter("activityId", elementId);
		query.executeUpdate();
	}

	public void addFragmentEntryChannel(Long instanceId, int fragmentEntryId,
			String channel) {
		ChannelSelectorDAOImpl dao = new ChannelSelectorDAOImpl(instanceId,
				fragmentEntryId, channel, FRAGMENT_ENTRY);
		em.persist(dao);
	}

	public List<Integer> getActiveFragmentEntries(Long instanceId) {
		Query query = em
				.createNamedQuery(ChannelSelectorDAOImpl.GET_ACTIVITY_IDS);
		query.setParameter("instanceId", instanceId);
		query.setParameter("type", FRAGMENT_ENTRY);
		List<Integer> ids = query.getResultList();
		return ids;
	}
}
