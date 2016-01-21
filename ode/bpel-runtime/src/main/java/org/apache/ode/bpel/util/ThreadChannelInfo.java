package org.apache.ode.bpel.util;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class ThreadChannelInfo {
	private Long instanceId;
	private int elementId;

	public ThreadChannelInfo(Long instanceId, int elementId) {
		this.instanceId = instanceId;
		this.elementId = elementId;
	}

	public Long getInstanceId() {
		return instanceId;
	}

	public int getElementId() {
		return elementId;
	}

	public boolean equals(Object o) {
		boolean result = false;
		if (o instanceof ThreadChannelInfo) {
			ThreadChannelInfo inf = (ThreadChannelInfo) o;
			result = inf.getInstanceId() == instanceId
					&& elementId == inf.getElementId();
		}

		return result;
	}

	public int hashCode() {
		return (int) (instanceId & Integer.MAX_VALUE) + elementId;
	}
}