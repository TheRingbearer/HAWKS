package org.apache.ode.bpel.dao;

import javax.xml.namespace.QName;

import org.w3c.dom.Element;

public interface SnapshotPartnerlinksDAO {

	public Long getSnapshotID();

	/**
	 * Get the model id of the partner link.
	 * 
	 * @return
	 */
	public int getPartnerLinkModelId();

	public String getMyRoleName();

	public String getPartnerRoleName();

	public String getPartnerLinkName();

	/**
	 * Get the service name associated with this partner link.
	 * 
	 * @return
	 */
	public QName getMyRoleServiceName();

	public void setMyRoleServiceName(QName svcName);

	public Element getMyEPR();

	public void setMyEPR(Element val);

	public Element getPartnerEPR();

	public void setPartnerEPR(Element val);

	public String getMySessionId();

	public String getPartnerSessionId();

	public void setPartnerSessionId(String session);

	public void setMySessionId(String sessionId);

	public void setScopeInstanceId(Long id);

	public Long getScopeInstanceId();

	public Element getPartnerEPR2();
}
