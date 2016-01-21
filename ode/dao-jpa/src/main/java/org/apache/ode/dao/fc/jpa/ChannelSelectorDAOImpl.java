package org.apache.ode.dao.fc.jpa;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

@Entity
@Table(name = "ODE_CHANNEL_SELECTOR")
@NamedQueries({
		@NamedQuery(name = ChannelSelectorDAOImpl.GET_CHANNEL, query = "select g.channel from ChannelSelectorDAOImpl as g where g.pk.instanceId = :instanceId and g.pk.elementId = :elementId"),
		@NamedQuery(name = ChannelSelectorDAOImpl.DELETE_CHANNEL, query = "delete from ChannelSelectorDAOImpl as g where g.pk.instanceId = :instanceId and g.pk.elementId = :elementId"),
		@NamedQuery(name = ChannelSelectorDAOImpl.GET_ACTIVITY_IDS, query = "select g.pk.elementId from ChannelSelectorDAOImpl as g where g.pk.instanceId = :instanceId and g.type = :type") })
public class ChannelSelectorDAOImpl {
	public final static String GET_CHANNEL = "GET_CHANNEL";
	public final static String DELETE_CHANNEL = "DELETE_CHANNEL";
	public final static String GET_ACTIVITY_IDS = "GET_ACTIVITY_IDS";
	@EmbeddedId
	private ChannelSelectorPK pk;
	@Basic
	@Column(name = "CHANNEL")
	private String channel;

	@Basic
	@Column(name = "CHANNEL_TYPE")
	private int type;

	public ChannelSelectorDAOImpl(Long instanceId, int elementId,
			String channel, int type) {
		pk = new ChannelSelectorPK();
		pk.setElementId(elementId);
		pk.setInstanceId(instanceId);
		this.channel = channel;
		this.type = type;
	}

	public ChannelSelectorDAOImpl() {

	}

	public Long getInstanceId() {
		return pk.getInstanceId();
	}

	public void setInstanceId(Long instanceId) {
		pk.setInstanceId(instanceId);
	}

	public int getElementId() {
		return pk.getElementId();
	}

	public void setElementId(int elementId) {
		pk.setElementId(elementId);
	}

	public String getChannel() {
		return channel;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	/*
	 * public boolean equals(Object o){ boolean result = false; if (o instanceof
	 * GlueChannelSelectorDAOImpl){ GlueChannelSelectorDAOImpl dao =
	 * (GlueChannelSelectorDAOImpl)o; result = this.equals(dao); } return
	 * result; }
	 */
}
