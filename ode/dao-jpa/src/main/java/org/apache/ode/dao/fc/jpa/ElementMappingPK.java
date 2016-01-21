package org.apache.ode.dao.fc.jpa;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class ElementMappingPK {

	@Column(name = "PROCESS_IID")
	private Long instanceId;

	@Column(name = "ACTIVITY_ID")
	private int activityId;

	@Column(name = "ELEMENT_ID")
	private int elementId;

	public ElementMappingPK() {

	}

	public ElementMappingPK(Long instanceId, int activityId, int elementId) {
		this.instanceId = instanceId;
		this.elementId = elementId;
		this.activityId = activityId;
	}

	public Long getInstanceId() {
		return instanceId;
	}

	public int getElementId() {
		return elementId;
	}

	public int getActivityId() {
		return activityId;
	}

	public boolean equals(Object o) {
		boolean result = false;
		if (o instanceof ElementMappingPK) {
			ElementMappingPK dao = (ElementMappingPK) o;
			result = dao.getElementId() == elementId
					&& dao.getInstanceId().equals(instanceId)
					&& dao.getActivityId() == activityId;
		}
		return result;
	}

	public int hashCode() {
		StringBuffer buffer = new StringBuffer();
		buffer.append(getActivityId());
		buffer.append("|");
		buffer.append(getInstanceId());
		buffer.append("|");
		buffer.append(getElementId());
		return buffer.toString().hashCode();
	}
}
