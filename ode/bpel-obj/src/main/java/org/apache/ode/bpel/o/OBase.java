/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ode.bpel.o;

import java.io.Serializable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.utils.fc.HashCodeGenerator;

/**
 * Base class for compiled BPEL objects.
 */
public class OBase implements Serializable {

	static final long serialVersionUID = -1L;

	// @hahnml: Only for debugging
	static final Log __log = LogFactory.getLog(OBase.class);

	/** Our identifier, in terms of our parent. */
	private final int _id;
	private final OProcess _owner;
	// AH: to prevent overwriting of the correlationSets in HashMaps and
	// HashSets
	// during prcess merging
	private int hashCode;
	// AH: end

	public DebugInfo debugInfo;

	// @stmz: is there a element in the bpel-file representing this object
	private Boolean art;

	// @stmz: Xpath Location of the Element this object is representing
	private String Xpath;

	protected OBase(OProcess owner) {
		_owner = owner;

		// AH:
		hashCode = HashCodeGenerator.generate();
		// AH: end

		art = false;
		Xpath = null;

		if (owner == null) {
			_id = 0;
		} else {

			// hahnml: If the model was migrated we have to block all currently
			// used ids
			if (!_owner._idBlackList.isEmpty()) {
				_id = calculateNextFreeId();
			} else {
				_id = ++_owner._childIdCounter;
			}

			_owner._children.add(this);
		}

		// @hahnml: Only for debugging
		if (__log.isDebugEnabled()) {
			StringBuffer buf = new StringBuffer(getClass().getSimpleName());
			buf.append('#');
			buf.append(_id);
			__log.debug(buf.toString());
		}

		assert _id == 0 || _owner != null;
	}

	// @hahnml
	protected OBase(OProcess owner, int id) {
		_owner = owner;

		// AH:
		hashCode = HashCodeGenerator.generate();
		// AH: end

		art = false;
		Xpath = null;

		_id = id;
		_owner._children.add(this);

		// @hahnml: Only for debugging
		if (__log.isDebugEnabled()) {
			StringBuffer buf = new StringBuffer(getClass().getSimpleName());
			buf.append('#');
			buf.append(_id);
			__log.debug(buf.toString());
		}

		assert _owner != null;
	}

	// AH: the methods were changed
	public OProcess getOldOwner() {
		OProcess result = null;
		if (_owner == null) {
			result = (OProcess) this;
		} else {
			result = _owner;
		}
		return result;
	}

	public OProcess getOwner() {
		OProcess result = null;
		if (_owner == null) {
			result = (OProcess) this;
		} else if (_owner.getNewRoot() != null) {
			result = _owner.getNewRoot();
		} else {
			result = _owner;
		}
		return result;
		// return (OProcess) (_owner == null ? this : _owner);
	}

	// AH: end
	// hash code must not change, due to several HasMaps and HashSets
	public int hashCode() {
		return hashCode;
	}

	// AH: end

	public boolean equals(Object obj) {
		if (!(obj instanceof OBase))
			return false;

		OBase other = (OBase) obj;
		// AH:
		int id = getId();
		return (id == 0 && other.getId() == 0) || id == other.getId()
				&& other.getOwner().equals(getOwner());
		// AH: end
	}

	// AH: id offsetted
	public int getId() {
		int offset = 0;
		if (_owner != null) {
			offset = _owner.getIdOffset();
		}
		return _id + offset;
	}

	// AH: end

	public String toString() {
		StringBuffer buf = new StringBuffer(getClass().getSimpleName());
		buf.append('#');
		// AH:
		buf.append(getId());
		// AH: end
		return buf.toString();
	}

	public void dehydrate() {
		if (debugInfo != null) {
			debugInfo.description = null;
			debugInfo.extensibilityElements = null;
			debugInfo = null;
		}
	}

	public String digest() {
		return "";
	}

	// @stmz
	// AH: temp
	public String getXpath() {
		// AH:
		// String result;
		//
		// if (Xpath != null && Xpath.charAt(0) != '/') {
		// result = Xpath;
		// } else {
		// result = _owner.processName + Xpath;
		// }
		// return result;
		// AH: end
		return Xpath;
	}

	public void setXpath(String xpath) {
		Xpath = xpath;
	}

	public Boolean getArt() {
		return art;
	}

	public void setArt(Boolean art) {
		this.art = art;
	}

	// @hahnml: Returns the next free not blacklisted id during the compilation
	// of a migrated process model
	private int calculateNextFreeId() {
		// Increment the current counter
		++_owner._childIdCounter;

		// Check if the current counter value is blacklisted
		if (_owner._idBlackList.contains(_owner._childIdCounter)) {
			// Execute this method recursive to get the next free id
			return calculateNextFreeId();
		} else {
			// Return the current counter
			return _owner._childIdCounter;
		}
	}
}
