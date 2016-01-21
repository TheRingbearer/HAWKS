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

import org.apache.ode.bpel.dao.SnapshotDAO;
import org.apache.ode.bpel.dao.SnapshotVariableDAO;
import org.apache.ode.utils.DOMUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

@Entity
@Table(name = "ODE_SNAPSHOT_VARIABLE")
public class SnapshotVariableDAOImpl extends OpenJPADAO implements
		SnapshotVariableDAO {

	@Id
	@Column(name = "ID")
	@GeneratedValue(strategy = GenerationType.AUTO)
	@SuppressWarnings("unused")
	private Long _id;

	@Basic
	@Column(name = "SNAPSHOT_ID", nullable = true, insertable = false, updatable = false)
	private Long _snapshotId;
	@ManyToOne(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST })
	@Column(name = "SNAPSHOT_ID")
	private SnapshotDAOImpl _snapshot;

	@Lob
	@Column(name = "DATA")
	private String _data;
	@Transient
	private Node _node;
	@Basic
	@Column(name = "IS_SIMPLE_TYPE")
	private boolean _isSimpleType;
	@Basic
	@Column(name = "NAME")
	private String _name;

	@Basic
	@Column(name = "SCOPE_ID", nullable = true, insertable = true, updatable = false)
	private Long _scopeId;

	/*
	 * @ManyToOne(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST })
	 * 
	 * @Column(name = "SCOPE_ID")
	 * 
	 * @SuppressWarnings("unused") private ScopeDAOImpl _scope;
	 */
	public SnapshotVariableDAOImpl() {

	}

	public SnapshotVariableDAOImpl(SnapshotDAOImpl sp, String name) {
		_snapshot = sp;
		_name = name;
	}

	public Long getSnapshotID() {
		return _snapshotId;
	}

	public Node get() {
		if (_node == null && _data != null) {
			if (_isSimpleType) {
				Document d = DOMUtils.newDocument();
				// we create a dummy wrapper element
				// prevents some apps from complaining
				// when text node is not actual child of document
				Element e = d.createElement("text-node-wrapper");
				Text tnode = d.createTextNode(_data);
				d.appendChild(e);
				e.appendChild(tnode);
				_node = tnode;
			} else {
				try {
					_node = DOMUtils.stringToDOM(_data);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}

		return _node;
	}

	public String getName() {
		return _name;
	}

	public void set(Node val) {
		_node = val;
		if (val instanceof Element) {
			_isSimpleType = false;
			_data = DOMUtils.domToString(val);
		} else if (_node != null) {
			_isSimpleType = true;
			_data = _node.getNodeValue();
		}
	}

	public SnapshotDAO getSnapsotDAO() {
		return _snapshot;
	}

	public boolean isNull() {
		return _data == null;
	}

	public void setSnapshotDAO(SnapshotDAOImpl sp) {
		_snapshot = sp;
	}

	public void setScopeInstanceId(Long id) {
		_scopeId = id;
	}

	public Long getScopeInstanceId() {
		return _scopeId;
	}
}
