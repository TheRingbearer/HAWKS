package org.apache.ode.bpel.compiler.modelMigration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.compiler.BpelCompiler;
import org.apache.ode.bpel.iapi.BpelServer;
import org.apache.ode.bpel.modelChange.ChangeDocument;
import org.apache.ode.bpel.modelChange.ChangeType;
import org.apache.ode.bpel.modelChange.ReasonType;
import org.apache.ode.bpel.modelChange.UpdateChangeType;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OProcess;

/**
 * @author hahnml
 * 
 */
public class ProcessModelChangeRegistry {

	private static ProcessModelChangeRegistry _instance;

	private boolean _isInitialized = false;

	private ChangeDocument _modelChange = null;
	private boolean _isModelChanged = false;

	// A HashMap with (newXPath, oldXPath) mappings
	private HashMap<String, String> _xPathChanges = new HashMap<String, String>();

	// A list with the XPath of all added elements of the changed process model
	private List<String> _addedElements = new ArrayList<String>();

	// A map with the XPath of all added elements of the changed process model
	// which are handled already and a boolean if they are updated.
	private HashMap<String, Boolean> _handledElements = new HashMap<String, Boolean>();

	// A list with the XPath of all deleted elements of the original process
	// model
	private List<String> _removedElements = new ArrayList<String>();

	private BpelServer _server = null;

	private BpelCompiler _compiler = null;

	private OProcess _originalProcessModel = null;

	private long _instanceID = 0;

	private int _lastOriginalID = 0;

	private int _internalElementCounter = 0;

	private HashMap<String, Integer> _unidentifiedElementCount = new HashMap<String, Integer>();

	// A list to hold all used id's to avoid two activities with the same id
	private List<Integer> _idBlackList = new ArrayList<Integer>();

	// A list to hold the id's of all deleted activities over all instance
	// migrations. This list will only be cleared if a new process model was
	// deployed or the instance ID changed from last to current migration.
	private List<Integer> _deletedIDList = new ArrayList<Integer>();

	// @hahnml: Only for debugging
	static final Log __log = LogFactory
			.getLog(ProcessModelChangeRegistry.class);

	public static ProcessModelChangeRegistry getRegistry() {
		if (_instance == null) {
			_instance = new ProcessModelChangeRegistry();
		}

		return _instance;
	}

	public void setBpelServer(BpelServer server) {
		_server = server;
	}

	public void setChangeDocument(ChangeDocument change) {
		this._modelChange = change;

		if (_isModelChanged) {
			loadChanges();
			_isInitialized = true;
		} else {
			_isInitialized = false;
		}
	}

	public int getNewUnusedID() {
		int id = 0;

		if (_isInitialized) {
			// Use a new ID which is not assigned in the original
			// process model
			++_internalElementCounter;

			id = _lastOriginalID + _internalElementCounter;

			// Check if this id is not in use or get the next free one
			id = calculateNextFreeId(id);

			// Mark the id as used over the blacklist
			_idBlackList.add(id);

			// Update the blacklist at the process
			_compiler.getOProcess().setIdBlackList(_idBlackList);
		}

		return id;
	}

	public int getCorrectID(String xPath) {
		int id = 0;

		if (_isInitialized) {
			// Check if the xpath references an added element or a child element
			// of it, e.g. a <copy> element of an added <assign> activity
			if (_addedElements.contains(xPath)
					|| isChildOf(_addedElements, xPath)) {

				// Use a new ID which is not assigned in the original
				// process model
				++_internalElementCounter;

				id = _lastOriginalID + _internalElementCounter;

				// Check if this id is not in use or get the next free one
				id = calculateNextFreeId(id);

				// Mark the id as used over the blacklist
				_idBlackList.add(id);

				// Update the blacklist at the process
				_compiler.getOProcess().setIdBlackList(_idBlackList);

			} else if (_xPathChanges.containsKey(xPath)) {

				// Use the original xPath to get the id from the original
				// process model
				id = findElementIdByXPath(_xPathChanges.get(xPath));

				// If the correct id was not found we use a new one
				if (id < 0) {
					++_internalElementCounter;

					id = _lastOriginalID + _internalElementCounter;

					// Check if this id is not in use or get the next free one
					id = calculateNextFreeId(id);

					// Mark the id as used over the blacklist
					_idBlackList.add(id);

					// Update the blacklist at the process
					_compiler.getOProcess().setIdBlackList(_idBlackList);
				} else {
					// Check if this id is not in use or get the next free one
					id = calculateNextFreeId(id);

					// Mark the id as used over the blacklist
					_idBlackList.add(id);

					// Update the blacklist at the process
					_compiler.getOProcess().setIdBlackList(_idBlackList);
				}

			} else {

				// Try to find the id from the original
				// process model with the xpath
				id = findElementIdByXPath(xPath);

				if (id < 0) {
					// The xpath is not used in the original model, so the element must be new...
					++_internalElementCounter;

					id = _lastOriginalID + _internalElementCounter;

					// Check if this id is not in use or get the next free one
					id = calculateNextFreeId(id);

					// Mark the id as used over the blacklist
					_idBlackList.add(id);

					// Update the blacklist at the process
					_compiler.getOProcess().setIdBlackList(_idBlackList);
				} else {
					// Nothing was changed, so we can use the next free id of
					// the process
					// Check if the id is already used.
					id = calculateNextFreeId(id);

					// Mark the id as used over the blacklist
					_idBlackList.add(id);

					// Update the blacklist at the process
					_compiler.getOProcess().setIdBlackList(_idBlackList);
				}

			}
		}
		
		// @hahnml: Only for debugging
		if (__log.isDebugEnabled()) {
			StringBuffer buf = new StringBuffer(getClass().getSimpleName());
			buf.append("#getCorrectId, ");
			buf.append(xPath);
			__log.debug(buf.toString());
		}

		return id;
	}

	private boolean isChildOf(List<String> elementList, String xPath) {
		boolean found = false;
		Iterator<String> iter = elementList.iterator();

		while (iter.hasNext() && !found) {
			String elementXPath = iter.next();

			if (xPath.startsWith(elementXPath)) {
				found = true;
			}
		}

		return found;
	}

	public void setModelChanged(boolean changed) {
		_isModelChanged = changed;
	}

	public boolean isModelChanged() {
		return _isModelChanged;
	}

	private int calculateNextFreeId(int startId) {
		if (_idBlackList.contains(startId)
				|| _compiler.getOProcess().getChildIdCounter() >= startId) {
			int id = startId + 1;
			return calculateNextFreeId(id);
		} else {
			return startId;
		}
	}

	private void loadChanges() {

		for (ChangeType entry : _modelChange.getChange().getChangeList()) {

			if (entry.getChangeReason() == ReasonType.ADDED) {
				_addedElements.add(entry.getElementXPath());
			} else if (entry.getChangeReason() == ReasonType.DELETED) {
				_removedElements.add(entry.getElementXPath());
			} else if (entry.getChangeReason() == ReasonType.XPATH_CHANGED) {
				UpdateChangeType change = (UpdateChangeType) entry;

				_xPathChanges.put(change.getUpdatedElementXPath(),
						change.getElementXPath());
			}

		}

		_originalProcessModel = _server.getProcessModel(_modelChange
				.getChange().getInstanceID());

		_lastOriginalID = getHighestOriginalProcessID();

		// Check if the migration belongs to the same instance as the last one.
		// If not the deleted ID list has to be cleared.
		if (_instanceID != _modelChange.getChange().getInstanceID()) {
			_deletedIDList.clear();
		}

		loadDeletedIDList();

		_idBlackList.addAll(_deletedIDList);

		_instanceID = _modelChange.getChange().getInstanceID();
	}

	private void loadDeletedIDList() {
		for (String xPath : _removedElements) {
			int id = findElementIdByXPath(xPath);
			_deletedIDList.add(id);
		}
	}

	private int findElementIdByXPath(String xPath) {
		int id = -1;

		List<OBase> children = _originalProcessModel.getChildren();
		boolean found = false;
		int i = 0;

		while (!found && i < children.size()) {
			OBase current = children.get(i);

			if (current.getXpath() != null && current.getXpath().equals(xPath)) {
				id = current.getId();
				found = true;
			}

			i++;
		}

		return id;
	}

	public List<String> getAddedElementXPaths() {
		return _addedElements;
	}

	public void clear() {
		_isInitialized = false;
		_modelChange = null;
		_isModelChanged = false;

		_xPathChanges.clear();
		_addedElements.clear();
		_removedElements.clear();
		_handledElements.clear();
		_unidentifiedElementCount.clear();

		_idBlackList.clear();

		_originalProcessModel = null;
		_lastOriginalID = 0;
		_internalElementCounter = 0;
		_compiler = null;
	}

	public void clearAll(Long instanceID) {
		// Check if another instance ID is given then the buffered one
		if (_instanceID != 0 && !instanceID.equals(Long.valueOf(_instanceID))) {
			this.clear();

			_instanceID = 0;
			_deletedIDList.clear();
		}
	}

	public void clearAll() {
		this.clear();

		_instanceID = 0;
		_deletedIDList.clear();
	}

	public void setBpelCompiler(BpelCompiler bpelCompiler) {
		_compiler = bpelCompiler;

		_compiler.getOProcess().setIdBlackList(_idBlackList);
	}

	public boolean isAddedAndNotHandled(OBase object) {
		boolean handled = false;

		if (this._addedElements.contains(object.getXpath())
				&& !this._handledElements.keySet().contains(object.getXpath())) {
			handled = true;
		}

		return handled;
	}

	public boolean isHandled(OBase object) {
		return this._handledElements.keySet().contains(object.getXpath());
	}

	public boolean isUpdated(OBase object) {
		return this._handledElements.get(object.getXpath());
	}

	public void setUpdated(OBase object, boolean isUpdated) {
		this._handledElements.put(object.getXpath(), new Boolean(isUpdated));
	}

	public void setHandled(OBase object) {
		this._handledElements.put(object.getXpath(), false);
	}

	private int getHighestOriginalProcessID() {
		int highestId = 0;

		for (OBase element : _originalProcessModel.getChildren()) {
			if (element.getId() > highestId) {
				highestId = element.getId();
			}
		}

		return highestId;
	}

	public int getUnidentifiedElementCount(String elementName) {
		int count = 1;
		
		if (_unidentifiedElementCount.containsKey(elementName)) {
			Integer current = _unidentifiedElementCount.get(elementName);
			count = ++current;
		}

		_unidentifiedElementCount.put(elementName, count);
		
		return count;
	}
}
