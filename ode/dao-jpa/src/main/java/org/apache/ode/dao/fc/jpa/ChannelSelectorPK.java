package org.apache.ode.dao.fc.jpa;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class ChannelSelectorPK {

	@Column(name = "PROCESS_IID")
	private Long instanceId;

	@Column(name = "ELEMENT_ID")
	private int elementId;

	public Long getInstanceId() {
		return instanceId;
	}

	public void setInstanceId(Long instanceId) {
		this.instanceId = instanceId;
	}

	public int getElementId() {
		return elementId;
	}

	public void setElementId(int elementId) {
		this.elementId = elementId;
	}

	public boolean equals(Object o) {
		boolean result = false;
		if (o instanceof ChannelSelectorPK) {
			ChannelSelectorPK dao = (ChannelSelectorPK) o;
			result = dao.getElementId() == elementId
					&& dao.getInstanceId().equals(instanceId);
		}
		return result;
	}

	public int hashCode() {
		StringBuffer buffer = new StringBuffer();
		buffer.append(getElementId());
		buffer.append("|");
		buffer.append(getInstanceId());
		return buffer.toString().hashCode();
	}
}
