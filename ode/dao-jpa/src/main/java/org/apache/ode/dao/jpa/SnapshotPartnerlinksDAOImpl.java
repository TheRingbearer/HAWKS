package org.apache.ode.dao.jpa;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.xml.namespace.QName;

import org.apache.ode.bpel.dao.ScopeDAO;
import org.apache.ode.bpel.dao.SnapshotDAO;
import org.apache.ode.bpel.dao.SnapshotPartnerlinksDAO;
import org.apache.ode.utils.DOMUtils;
import org.w3c.dom.Element;

@Entity
@Table(name = "ODE_SNAPSHOT_PARTNERLINKS")
public class SnapshotPartnerlinksDAOImpl extends OpenJPADAO implements
		SnapshotPartnerlinksDAO {

	@Id
	@Column(name = "ID")
	@GeneratedValue(strategy = GenerationType.AUTO)
	@SuppressWarnings("unused")
	private Long _id;

	@Lob
	@Column(name = "MY_EPR")
	private String _myEPR;
	@Transient
	private Element _myEPRElement;
	@Basic
	@Column(name = "MY_ROLE_NAME")
	private String _myRoleName;
	@Basic
	@Column(name = "MY_ROLE_SERVICE_NAME")
	private String _myRoleServiceName;
	@Basic
	@Column(name = "MY_SESSION_ID")
	private String _mySessionId;
	@Lob
	@Column(name = "PARTNER_EPR")
	private String _partnerEPR;
	@Transient
	private Element _partnerEPRElement;
	@Basic
	@Column(name = "PARTNER_LINK_MODEL_ID")
	private int _partnerLinkModelId;
	@Basic
	@Column(name = "PARTNERLINKS_NAME")
	private String _partnerlinksname;

	@Basic
	@Column(name = "PARTNER_ROLE_NAME")
	private String _partnerRoleName;
	@Basic
	@Column(name = "PARTNER_SESSION_ID")
	private String _partnerSessionId;

	@Basic
	@Column(name = "SNAPSHOT_ID", nullable = true, insertable = false, updatable = false)
	private Long _snapshotId;
	@ManyToOne(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST })
	@Column(name = "SNAPSHOT_ID")
	private SnapshotDAOImpl _snapshot;

	@Basic
	@Column(name = "SCOPE_ID", nullable = true, insertable = true, updatable = false)
	private Long _scopeId;

	// @ManyToOne(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST })
	// @Column(name = "SCOPE_ID")
	// @SuppressWarnings("unused")
	// private ScopeDAOImpl _scope;

	public SnapshotPartnerlinksDAOImpl() {

	}

	public SnapshotPartnerlinksDAOImpl(int modelId, String name, String myRole,
			String partnerRole) {
		_partnerLinkModelId = modelId;
		_partnerlinksname = name;
		_myRoleName = myRole;
		_partnerRoleName = partnerRole;
	}

	public Long getSnapshotID() {
		return _snapshotId;
	}

	public SnapshotDAO getSnapsotDAO() {
		return _snapshot;
	}

	public Element getMyEPR() {
		if (_myEPRElement != null && _myEPR != null && !"".equals(_myEPR)) {
			try {
				_myEPRElement = DOMUtils.stringToDOM(_myEPR);

			} catch (Exception e) {
				throw new RuntimeException(e);
			}

		}

		return _myEPRElement;

	}

	public String getMyRoleName() {
		return _myRoleName;
	}

	public QName getMyRoleServiceName() {
		return _myRoleServiceName == null ? null : QName
				.valueOf(_myRoleServiceName);
	}

	public String getMySessionId() {
		return _mySessionId;
	}

	public Element getPartnerEPR2() {
		return _partnerEPRElement;
	}

	public Element getPartnerEPR() {
		if (_partnerEPRElement != null && _partnerEPR != null
				&& !"".equals(_partnerEPR)) {
			try {
				_partnerEPRElement = DOMUtils.stringToDOM(_partnerEPR);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return _partnerEPRElement;

	}

	public int getPartnerLinkModelId() {
		return _partnerLinkModelId;
	}

	public String getPartnerLinkName() {
		return _partnerlinksname;
	}

	public String getPartnerRoleName() {
		return _partnerRoleName;
	}

	public String getPartnerSessionId() {
		return _partnerSessionId;
	}

	public void setMyEPR(Element val) {
		_myEPRElement = val;
		if (val != null) {
			_myEPR = DOMUtils.domToString(val);
		} else {
			_myEPR = null;
		}

	}

	public void setMyRoleServiceName(QName svcName) {
		if (svcName != null) {
			_myRoleServiceName = svcName.toString();
		} else {
			_myRoleServiceName = null;
		}
	}

	public void setMySessionId(String sessionId) {
		_mySessionId = sessionId;

	}

	public void setPartnerEPR(Element val) {
		_partnerEPRElement = val;
		if (val != null) {
			_partnerEPR = DOMUtils.domToString(val);
		} else {
			_partnerEPR = null;
		}
	}

	public void setPartnerSessionId(String session) {
		_partnerSessionId = session;

	}

	public void setSnapshotDAO(SnapshotDAOImpl sp) {
		_snapshot = sp;
	}

	public void setPartnerlinksName(String partnerlinksname) {
		this._partnerlinksname = partnerlinksname;
	}

	public void setScopeInstanceId(Long id) {
		_scopeId = id;
	}

	public Long getScopeInstanceId() {
		return _scopeId;
	}
}
