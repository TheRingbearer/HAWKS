package org.apache.ode.bpel.engine.iteration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.ode.bpel.dao.PartnerLinkDAO;
import org.apache.ode.bpel.dao.ProcessInstanceDAO;
import org.apache.ode.bpel.dao.ScopeDAO;
import org.apache.ode.bpel.dao.ScopeStateEnum;
import org.apache.ode.bpel.dao.SnapshotDAO;
import org.apache.ode.bpel.dao.SnapshotPartnerlinksDAO;
import org.apache.ode.bpel.dao.SnapshotVariableDAO;
import org.apache.ode.bpel.dao.XmlDataDAO;
import org.apache.ode.bpel.engine.BpelProcess;
import org.apache.ode.bpel.engine.BpelRuntimeContextImpl;
import org.apache.ode.bpel.evt.PartnerLinkModificationEvent;
import org.apache.ode.bpel.evt.VariableModificationEvent;
import org.apache.ode.bpel.extensions.handler.ActivityEventHandler;
import org.apache.ode.bpel.extensions.processes.Compensation_Handler;
import org.apache.ode.bpel.extensions.processes.Running_Activity;
import org.apache.ode.bpel.iapi.Scheduler.JobDetails;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OAssign;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OFlow;
import org.apache.ode.bpel.o.OForEach;
import org.apache.ode.bpel.o.OInvoke;
import org.apache.ode.bpel.o.OLink;
import org.apache.ode.bpel.o.OPartnerLink;
import org.apache.ode.bpel.o.OProcess;
import org.apache.ode.bpel.o.ORepeatUntil;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OScope.Variable;
import org.apache.ode.bpel.o.OSequence;
import org.apache.ode.bpel.o.OSwitch;
import org.apache.ode.bpel.o.OSwitch.OCase;
import org.apache.ode.bpel.o.OWhile;
import org.apache.ode.bpel.runtime.CompensationHandler;
import org.w3c.dom.Node;

/**
 * This class contains all methods to realize the special parts of the
 * reexecution of process instance parts like loading snapshots or compensating
 * activities.
 * 
 * @author hahnml
 */
public class ReexecutionHandler {

	private static ReexecutionHandler instance;
	private List<CompensationHandler> _compensationChannels;

	private ReexecutionHandler() {
		_compensationChannels = Collections
				.synchronizedList(new ArrayList<CompensationHandler>());
	}

	public static ReexecutionHandler getInstance() {
		if (instance == null) {
			instance = new ReexecutionHandler();
		}
		return instance;
	}

	public void calculateCompensations(JobDetails we,
			BpelRuntimeContextImpl instance, BpelProcess process) {

		// Reset the list of compensation channels
		_compensationChannels.clear();
		
		Long processID = we.getInstanceId();
		ActivityEventHandler evtHandler = ActivityEventHandler.getInstance();

		// Get the target activity of the reexecution job
		String targetXPath = (String) we.getDetailsExt().get(
				JobDetails.TARGET_ACTIVITY_XPATH);
		OActivity targetActivity = getActivity(targetXPath,
				process.getOProcess());

		// Get the actual running activity for this process instance. This must
		// be the last entry in the runningActivities list of the
		// ActivityEventHandler.
		List<Running_Activity> activities = evtHandler.getRunningActivities();
		String runningActivityXPath = null;

		// Start at the end of the list
		int i = activities.size() - 1;
		while (i >= 0 && runningActivityXPath == null) {
			// Check if the activity belongs to our process instance
			if (activities.get(i).getProcessID().equals(processID)) {
				// Get the xpath of the entry
				runningActivityXPath = activities.get(i).getXPath();
			}

			i--;
		}

		// Get the real activity object for the xpath
		OActivity runningActivity = getActivity(runningActivityXPath,
				process.getOProcess());

		// Collect all compensateable scopes between the currently running and
		// the given target activity.
		List<OScope> scopes = new ArrayList<OScope>();
		findAllCompensateableScopes(targetActivity, runningActivity,
				process.getOProcess(), scopes);

		// Loop through all collected scopes and create compensation runnables
		// for them
		for (OScope scope : scopes) {
			Long scopeID = 0L;

			// Get the scope instance id of the given scope.
			// Loop through all the scopes and get the newest (highest id) of
			// them which belongs to the given OScope object
			for (Iterator<ScopeDAO> iter = instance.getProcessInstanceDao()
					.getScopes().iterator(); iter.hasNext();) {
				ScopeDAO current = iter.next();
				// Check if we found the correct scope, if it's completed and
				// the newest one available
				if (current.getModelId() == scope.getId()
						&& current.getState().equals(ScopeStateEnum.COMPLETED)
						&& current.getScopeInstanceId() > scopeID) {
					scopeID = current.getScopeInstanceId();
				}
			}

			if (scopeID != null) {
				Compensation_Handler tmp = null;

				// Get the correct Compensation_Handler data set from the
				// ActivityEventHandler instance
				synchronized (evtHandler.getCompensationHandlers()) {
					Iterator<Compensation_Handler> itr = evtHandler
							.getCompensationHandlers().iterator();
					while (itr.hasNext()) {
						Compensation_Handler tmp_comp = itr.next();
						if (tmp_comp.getProcess_ID().equals(processID)
								&& tmp_comp.getScopeID().equals(scopeID)) {
							tmp = tmp_comp;
						}
					}
				}

				final Compensation_Handler tmp2 = tmp;
				if (tmp2 != null) {

					// Add the compensation channel to the list
					this._compensationChannels.add(tmp2.getCompHandler()._self);

				}
			}
		}
	}

	public List<CompensationHandler> getCompensationChannels() {
		return _compensationChannels;
	}

	public static void reloadSnapshot(JobDetails we,
			BpelRuntimeContextImpl instance, BpelProcess process) {
		@SuppressWarnings("unchecked")
		List<ElementRef> variables = (List<ElementRef>) we.getDetailsExt().get(
				JobDetails.SNAPSHOT_VARIABLES);
		@SuppressWarnings("unchecked")
		List<ElementRef> partnerlinks = (List<ElementRef>) we.getDetailsExt()
				.get(JobDetails.SNAPSHOT_PARTNERLINKS);

		// Get the data from the JobDetails
		Long processInstanceId = we.getInstanceId();
		String snapshotXPath = (String) we.getDetailsExt().get(
				JobDetails.SNAPSHOT_XPATH);
		Long version = (Long) we.getDetailsExt().get(
				JobDetails.SNAPSHOT_VERSION_KEY);

		if (snapshotXPath != null && !snapshotXPath.isEmpty()
				&& version != null && version >= 0L) {
			// get the current ProcessInstanceDAO with the given
			// ProcessInstanceID
			ProcessInstanceDAO pi_dao = process.getProcessDAO().getInstance(
					processInstanceId);

			// @hahnml: Check if there exists a snapshot for the given target
			// xpath
			if (!isValidSnapshotXPath(pi_dao.getSnapshotDAO(), snapshotXPath)) {
				// Get the xpath of the next snapshot which is located
				// hierarchically before the given target xpath in the process
				// model.
				snapshotXPath = calculateNearestSnapshotXPath(
						pi_dao.getSnapshotDAO(), snapshotXPath, process);
			}

			// Check if specific variables and partnerLinks should be loaded
			if ((Boolean) we.getDetailsExt().get(JobDetails.EXT_MODE)) {
				if (variables != null && partnerlinks != null) {
					reloadSnapshotWithSpecificVarsPLs(pi_dao, snapshotXPath,
							version, variables, partnerlinks, instance, process);
				}
			} else {
				reloadSnapshotWithAllVarsPLs(pi_dao, snapshotXPath, version,
						instance, process);
			}
		}
	}

	// @hahnml: Extended the functionality to load only a set of variables and
	// improved the code
	private static void reloadSnapshotWithSpecificVarsPLs(
			ProcessInstanceDAO pi_dao, String targetXPath, Long version,
			List<ElementRef> variableRefs, List<ElementRef> partnerLinkRefs,
			BpelRuntimeContextImpl instance, BpelProcess process) {

		for (SnapshotDAO snapshot : pi_dao.getSnapshotDAO()) {
			// only get those snapshots, that has the given parameter xpath!

			// @hahnml: ... and the correct version
			if (snapshot.getXpath().equals(targetXPath)
					&& snapshot.getVersion().equals(version)) {
				Collection<SnapshotPartnerlinksDAO> spl = snapshot
						.getPartnerLinks();
				Collection<SnapshotVariableDAO> svar = snapshot.getVariables();

				ScopeDAO scope = null;

				// Handle all partner links
				for (SnapshotPartnerlinksDAO snapshotpl : spl) {
					Long scope_id_sp = snapshotpl.getScopeInstanceId();

					// Find the corresponding scope for the given
					// scopeInstanceId if the buffered scope is null or the id
					// changes
					if (scope == null
							|| (scope != null && scope.getScopeInstanceId() != scope_id_sp)) {
						scope = pi_dao.getScope(scope_id_sp);
						if (scope == null) {
							// Exit the loop if the scope was not found
							break;
						}
					}

					// Resolve the PartnerLinkDAO with the modelId of the
					// snapshot PartnerLinkDAO
					PartnerLinkDAO ps = scope.getPartnerLink(snapshotpl
							.getPartnerLinkModelId());

					// Check if the partnerLink is marked to set to the buffered
					// value
					boolean setPartnerLink = false;
					Iterator<ElementRef> plRefs = partnerLinkRefs.iterator();
					while (!setPartnerLink && plRefs.hasNext()) {
						ElementRef plRef = plRefs.next();

						if (plRef.getName().equals(ps.getPartnerLinkName())
								&& plRef.getScopeId().equals(
										scope.getScopeInstanceId().toString())) {
							setPartnerLink = true;
						}
					}

					if (ps != null && setPartnerLink) {
						ps.setMyEPR2(snapshotpl.getMyEPR());
						ps.setMyRoleServiceName2(snapshotpl
								.getMyRoleServiceName());
						ps.setMySessionId(snapshotpl.getMySessionId());
						ps.setPartnerEPR2(snapshotpl.getPartnerEPR());
						ps.setPartnerSessionId(snapshotpl.getPartnerSessionId());

						OPartnerLink partnerLink = (OPartnerLink) process
								.getOProcess().getChild(
										ps.getPartnerLinkModelId());
						OScope oscope = (OScope) process.getOProcess()
								.getChild(scope.getModelId());
						PartnerLinkModificationEvent plme = new PartnerLinkModificationEvent(
								partnerLink.name, partnerLink.getXpath(),
								snapshotpl.getPartnerEPR(),
								partnerLink.getXpath(), oscope.getXpath(),
								scope.getScopeInstanceId());
						instance.sendEvent(plme);
					}

				}

				// Handle all variables
				for (SnapshotVariableDAO snapshotvar : svar) {
					Long scope_id_sp = snapshotvar.getScopeInstanceId();

					// Find the corresponding scope for the given
					// scopeInstanceId if the buffered scope is null or the id
					// changes
					if (scope == null
							|| (scope != null && scope.getScopeInstanceId()
									.equals(scope_id_sp))) {
						scope = pi_dao.getScope(scope_id_sp);
						if (scope == null) {
							// Exit the loop if the scope was not found
							break;
						}
					}

					XmlDataDAO variable = scope.getVariable(snapshotvar
							.getName());

					// Check if the variable is marked to set to the buffered
					// value
					boolean setVariable = false;
					Iterator<ElementRef> varRefs = variableRefs.iterator();
					while (!setVariable && varRefs.hasNext()) {
						ElementRef varRef = varRefs.next();

						if (varRef.getName().equals(snapshotvar.getName())
								&& varRef.getScopeId().equals(
										scope.getScopeInstanceId().toString())) {
							setVariable = true;
						}
					}

					if (variable != null && setVariable) {
						// Get the buffered value from the snapshot variable
						Node snode = snapshotvar.get();
						// Set the variable to the buffered value
						variable.set(snode);

						OScope oscope = (OScope) process.getOProcess()
								.getChild(scope.getModelId());
						Variable ovariable = oscope.getLocalVariable(variable
								.getName());
						VariableModificationEvent vme = new VariableModificationEvent(
								ovariable.name, null, null,
								ovariable.declaringScope.getXpath(),
								ovariable.getXpath(),
								scope.getScopeInstanceId());
						vme.setNewValue(snode);
						instance.sendEvent(vme);
					}
				}

			}
		}
	}

	// @Bo Ning
	// @hahnml: Improved the code
	private static void reloadSnapshotWithAllVarsPLs(ProcessInstanceDAO pi_dao,
			String targetXPath, Long version, BpelRuntimeContextImpl instance,
			BpelProcess process) {

		for (SnapshotDAO snapshot : pi_dao.getSnapshotDAO()) {
			// only get those snapshots, that has the given parameter xpath!

			// @hahnml: ... and the correct version
			if (snapshot.getXpath().equals(targetXPath)
					&& snapshot.getVersion().equals(version)) {
				Collection<SnapshotPartnerlinksDAO> spl = snapshot
						.getPartnerLinks();
				Collection<SnapshotVariableDAO> svar = snapshot.getVariables();

				ScopeDAO scope = null;

				// Handle all partner links
				for (SnapshotPartnerlinksDAO snapshotpl : spl) {
					Long scope_id_sp = snapshotpl.getScopeInstanceId();

					// Find the corresponding scope for the given
					// scopeInstanceId if the buffered scope is null or the id
					// changes
					if (scope == null
							|| (scope != null && scope.getScopeInstanceId() != scope_id_sp)) {
						scope = pi_dao.getScope(scope_id_sp);
						if (scope == null) {
							// Exit the loop if the scope was not found
							break;
						}
					}

					// Resolve the PartnerLinkDAO with the modelId of the
					// snapshot PartnerLinkDAO
					PartnerLinkDAO ps = scope.getPartnerLink(snapshotpl
							.getPartnerLinkModelId());
					if (ps != null) {
						ps.setMyEPR2(snapshotpl.getMyEPR());
						ps.setMyRoleServiceName2(snapshotpl
								.getMyRoleServiceName());
						ps.setMySessionId(snapshotpl.getMySessionId());
						ps.setPartnerEPR2(snapshotpl.getPartnerEPR());
						ps.setPartnerSessionId(snapshotpl.getPartnerSessionId());

						OPartnerLink partnerLink = (OPartnerLink) process
								.getOProcess().getChild(
										ps.getPartnerLinkModelId());
						OScope oscope = (OScope) process.getOProcess()
								.getChild(scope.getModelId());
						PartnerLinkModificationEvent plme = new PartnerLinkModificationEvent(
								partnerLink.name, partnerLink.getXpath(),
								snapshotpl.getPartnerEPR(),
								partnerLink.getXpath(), oscope.getXpath(),
								scope.getScopeInstanceId());
						instance.sendEvent(plme);
					}

				}

				// Handle all variables
				for (SnapshotVariableDAO snapshotvar : svar) {
					Long scope_id_sp = snapshotvar.getScopeInstanceId();

					// Find the corresponding scope for the given
					// scopeInstanceId if the buffered scope is null or the id
					// changes
					if (scope == null
							|| (scope != null && scope.getScopeInstanceId() != scope_id_sp)) {
						scope = pi_dao.getScope(scope_id_sp);
						if (scope == null) {
							// Exit the loop if the scope was not found
							break;
						}
					}

					XmlDataDAO variable = scope.getVariable(snapshotvar
							.getName());

					if (variable != null) {
						// Get the buffered value from the snapshot variable
						Node snode = snapshotvar.get();
						// Set the variable to the buffered value
						variable.set(snode);

						OScope oscope = (OScope) process.getOProcess()
								.getChild(scope.getModelId());
						Variable ovariable = oscope.getLocalVariable(variable
								.getName());
						VariableModificationEvent vme = new VariableModificationEvent(
								ovariable.name, null, null,
								ovariable.declaringScope.getXpath(),
								ovariable.getXpath(),
								scope.getScopeInstanceId());
						vme.setNewValue(snode);
						instance.sendEvent(vme);
					}
				}

			}
		}
	}

	public static boolean isValidSnapshotXPath(
			Collection<SnapshotDAO> snaphots, String xpath) {
		boolean isValid = false;

		Iterator<SnapshotDAO> iter = snaphots.iterator();
		while (!isValid && iter.hasNext()) {
			SnapshotDAO current = iter.next();

			if (current.getXpath().equals(xpath)) {
				isValid = true;
			}
		}

		return isValid;
	}

	public static OActivity getActivity(String xpath, OProcess owner) {
		boolean found = false;
		OBase current = null;

		int i = 0;
		while (!found && i < owner.getChildren().size()) {
			current = owner.getChildren().get(i);

			if (current instanceof OActivity && current.getXpath() != null
					&& current.getXpath().equals(xpath)) {
				found = true;
			}

			i++;
		}

		return (OActivity) current;
	}

	private static boolean isADataManipulationActivity(OBase activity) {
		boolean _isManipulating = false;

		if (activity instanceof OAssign || activity instanceof OInvoke) {
			_isManipulating = true;
		}

		return _isManipulating;
	}

	private static void findAllCompensateableScopes(OActivity start,
			OActivity end, OProcess process, List<OScope> scopes) {
		int startIndex = process.getChildren().indexOf(start);
		int endIndex = process.getChildren().indexOf(end);

		// Loop through the children of the process from the currently running
		// activity to the reexecution target
		// activity and collect all
		// compensateable scopes.
		int counter = endIndex;
		while (counter >= startIndex) {
			OBase current = process.getChildren().get(counter);

			if (current instanceof OScope) {

				OScope scope = (OScope) current;
				// Check if this scope has a compensation handler
				if (scope.compensationHandler != null) {
					scopes.add(scope);
				}

			}
			// else if (current instanceof OInvoke) {
			//
			// OInvoke invoke = (OInvoke) current;
			// // Check if this scope has a compensation handler
			// if (invoke.compensationHandler != null) {
			// scopes.add(invoke);
			// }
			//
			// }

			counter--;
		}

	}

	public static String calculateNearestSnapshotXPath(
			Collection<SnapshotDAO> snapshots, String targetXPath,
			BpelProcess process) {

		OProcess oprocess = process.getOProcess();
		OActivity currentActivity = getActivity(targetXPath, oprocess);
		OActivity parent = currentActivity.getParent();

		OActivity result = currentActivity;

		// Loop through all inner containers of the process model from the
		// activity with the target xpath up to the process scope.
		while (parent != oprocess.procesScope) {

			if (parent instanceof OSequence) {

				List<OActivity> activities = ((OSequence) parent).sequence;
				int index = activities.indexOf(currentActivity);

				while (index > 0) {
					index--;

					OActivity current = activities.get(index);

					if (isADataManipulationActivity(current)) {
						if (isValidSnapshotXPath(snapshots, current.getXpath())) {
							return current.getXpath();
						}
					} else {
						if (isAContainer(current)) {
							result = findLastDataActivityInContainer(snapshots,
									current);

							if (result != null) {
								return result.getXpath();
							}
						}
					}
				}

				// Move one layer up the hierarchy
				currentActivity = parent;
				parent = currentActivity.getParent();

			} else if (parent instanceof OFlow) {

				// Search upwards in the flow for data manipulation activities
				result = findLastDataActivityInFlow(snapshots, currentActivity,
						((OFlow) parent));

				if (result != null) {
					return result.getXpath();
				}

				// Move one layer up the hierarchy
				currentActivity = parent;
				parent = currentActivity.getParent();

			} else if (parent instanceof OScope) {

				// Move one layer up the hierarchy
				currentActivity = parent;
				parent = currentActivity.getParent();

			} else if (parent instanceof OForEach) {

				// Move one layer up the hierarchy
				currentActivity = parent;
				parent = currentActivity.getParent();

			} else if (parent instanceof OWhile) {

				// Move one layer up the hierarchy
				currentActivity = parent;
				parent = currentActivity.getParent();

			} else if (parent instanceof ORepeatUntil) {

				// Move one layer up the hierarchy
				currentActivity = parent;
				parent = currentActivity.getParent();

			} else if (parent instanceof OSwitch) {

				// Move one layer up the hierarchy
				currentActivity = parent;
				parent = currentActivity.getParent();

			}

		}

		String resultXpath = "";

		if (result != null) {
			resultXpath = result.getXpath();
		}

		return resultXpath;
	}

	private static OActivity findLastDataActivityInContainer(
			Collection<SnapshotDAO> snapshots, OActivity container) {
		OActivity result = null;

		if (container instanceof OSequence) {

			List<OActivity> activities = ((OSequence) container).sequence;

			for (OActivity current : activities) {
				if (isADataManipulationActivity(current)) {
					if (isValidSnapshotXPath(snapshots, current.getXpath())) {
						result = current;
					}
				} else {
					if (isAContainer(current)) {
						result = findLastDataActivityInContainer(snapshots,
								current);
					}
				}
			}

		} else if (container instanceof OScope) {

			OActivity scopeActivity = ((OScope) container).activity;

			if (isADataManipulationActivity(scopeActivity)) {
				if (isValidSnapshotXPath(snapshots, scopeActivity.getXpath())) {
					result = scopeActivity;
				}
			} else {
				if (isAContainer(scopeActivity)) {
					result = findLastDataActivityInContainer(snapshots,
							scopeActivity);
				}
			}

		} else if (container instanceof OFlow) {
			// TODO:
			Set<OActivity> flowActivities = ((OFlow) container).parallelActivities;

			for (OActivity activity : flowActivities) {
				if (isADataManipulationActivity(activity)) {
					if (isValidSnapshotXPath(snapshots, activity.getXpath())) {
						result = activity;
					}
				} else {
					if (isAContainer(activity)) {
						result = findLastDataActivityInContainer(snapshots,
								activity);
					}
				}
			}

		} else if (container instanceof OForEach) {

			OActivity activity = ((OForEach) container).innerScope.activity;

			if (isADataManipulationActivity(activity)) {
				if (isValidSnapshotXPath(snapshots, activity.getXpath())) {
					result = activity;
				}
			} else {
				if (isAContainer(activity)) {
					result = findLastDataActivityInContainer(snapshots,
							activity);
				}
			}

		} else if (container instanceof OWhile) {

			OActivity activity = ((OWhile) container).activity;

			if (isADataManipulationActivity(activity)) {
				if (isValidSnapshotXPath(snapshots, activity.getXpath())) {
					result = activity;
				}
			} else {
				if (isAContainer(activity)) {
					result = findLastDataActivityInContainer(snapshots,
							activity);
				}
			}

		} else if (container instanceof ORepeatUntil) {

			OActivity activity = ((ORepeatUntil) container).activity;

			if (isADataManipulationActivity(activity)) {
				if (isValidSnapshotXPath(snapshots, activity.getXpath())) {
					result = activity;
				}
			} else {
				if (isAContainer(activity)) {
					result = findLastDataActivityInContainer(snapshots,
							activity);
				}
			}

		} else if (container instanceof OSwitch) {
			// TODO: Was ist wenn ein Switch zwei Cases hat, die beide ein
			// Assign enthalten? Welcher der Pfade bzw. welches Assign ist dann
			// das Richtige???

			// Get the cases
			List<OCase> cases = ((OSwitch) container).getCases();

			// Loop through all cases
			for (OCase current : cases) {

				if (isADataManipulationActivity(current.activity)) {
					if (isValidSnapshotXPath(snapshots,
							current.activity.getXpath())) {
						result = current.activity;
					}
				} else {
					if (isAContainer(current.activity)) {
						result = findLastDataActivityInContainer(snapshots,
								current.activity);
					}
				}

			}

		}

		return result;
	}

	private static boolean isAContainer(OActivity activity) {
		boolean _isContainer = false;

		if (activity instanceof OSequence || activity instanceof OScope
				|| activity instanceof OFlow || activity instanceof OForEach
				|| activity instanceof OWhile
				|| activity instanceof ORepeatUntil
				|| activity instanceof OSwitch) {
			_isContainer = true;
		}

		return _isContainer;
	}

	private static OActivity findLastDataActivityInFlow(
			Collection<SnapshotDAO> snapshots, OActivity activity, OFlow flow) {
		OActivity result = null;
		OActivity temp = null;

		// Loop through all links of the flow to get the predecessor activities
		for (OLink link : activity.targetLinks) {
			temp = link.source;
			if (isADataManipulationActivity(temp)) {
				if (isValidSnapshotXPath(snapshots, temp.getXpath())) {
					result = temp;
				}
			} else {
				if (isAContainer(temp)) {
					result = findLastDataActivityInContainer(snapshots, temp);
				}
			}

			if (result == null && !temp.targetLinks.isEmpty()) {
				findLastDataActivityInFlow(snapshots, temp, flow);
			} else {
				if (result != null) {
					return result;
				}
			}
		}

		return result;
	}

	public static List<String> calculateNearestSnapshotXPaths(
			Collection<SnapshotDAO> snapshots, String targetXPath,
			BpelProcess process) {

		OProcess oprocess = process.getOProcess();
		OActivity currentActivity = getActivity(targetXPath, oprocess);
		OActivity parent = currentActivity.getParent();

		List<String> snapshotXPaths = new ArrayList<String>();

		// Loop through all inner containers of the process model from the
		// activity with the target xpath up to the process scope.
		while (parent != oprocess.procesScope) {

			if (parent instanceof OSequence) {

				List<OActivity> activities = ((OSequence) parent).sequence;
				int index = activities.indexOf(currentActivity);

				while (index > 0) {
					index--;

					OActivity current = activities.get(index);

					if (isADataManipulationActivity(current)) {
						if (isValidSnapshotXPath(snapshots, current.getXpath())) {
							snapshotXPaths.add(current.getXpath());
							return snapshotXPaths;
						}
					} else {
						if (isAContainer(current)) {
							snapshotXPaths = findDataActivitiesInContainer(
									snapshots, current);

							if (!snapshotXPaths.isEmpty()) {
								return snapshotXPaths;
							}
						}
					}
				}

				// Move one layer up the hierarchy
				currentActivity = parent;
				parent = currentActivity.getParent();

			} else if (parent instanceof OFlow) {

				// Search upwards in the flow for data manipulation activities
				snapshotXPaths = findDataActivitiesInFlow(snapshots,
						currentActivity, ((OFlow) parent));

				if (!snapshotXPaths.isEmpty()) {
					return snapshotXPaths;
				}

				// Move one layer up the hierarchy
				currentActivity = parent;
				parent = currentActivity.getParent();

			} else if (parent instanceof OScope) {

				// Move one layer up the hierarchy
				currentActivity = parent;
				parent = currentActivity.getParent();

			} else if (parent instanceof OForEach) {

				// Move one layer up the hierarchy
				currentActivity = parent;
				parent = currentActivity.getParent();

			} else if (parent instanceof OWhile) {

				// Move one layer up the hierarchy
				currentActivity = parent;
				parent = currentActivity.getParent();

			} else if (parent instanceof ORepeatUntil) {

				// Move one layer up the hierarchy
				currentActivity = parent;
				parent = currentActivity.getParent();

			} else if (parent instanceof OSwitch) {

				// Move one layer up the hierarchy
				currentActivity = parent;
				parent = currentActivity.getParent();

			}

		}

		return snapshotXPaths;
	}

	private static List<String> findDataActivitiesInFlow(
			Collection<SnapshotDAO> snapshots, OActivity activity, OFlow flow) {

		List<String> snapshotXPaths = new ArrayList<String>();
		OActivity temp = null;

		// Loop through all links of the flow to get the predecessor activities
		for (OLink link : activity.targetLinks) {
			temp = link.source;
			if (isADataManipulationActivity(temp)) {
				if (isValidSnapshotXPath(snapshots, temp.getXpath())) {
					snapshotXPaths.add(temp.getXpath());
				}
			} else {
				if (isAContainer(temp)) {
					snapshotXPaths.addAll(findDataActivitiesInContainer(
							snapshots, temp));
				}
			}

			if (!temp.targetLinks.isEmpty()) {
				snapshotXPaths.addAll(findDataActivitiesInFlow(snapshots, temp,
						flow));
			}
		}

		return snapshotXPaths;
	}

	private static List<String> findDataActivitiesInContainer(
			Collection<SnapshotDAO> snapshots, OActivity container) {
		List<String> snapshotXPaths = new ArrayList<String>();

		if (container instanceof OSequence) {

			List<OActivity> activities = ((OSequence) container).sequence;

			int i = activities.size();
			while (i > 0 && snapshotXPaths.isEmpty()) {
				i--;

				OActivity current = activities.get(i);
				if (isADataManipulationActivity(current)) {
					if (isValidSnapshotXPath(snapshots, current.getXpath())) {
						snapshotXPaths.add(current.getXpath());
					}
				} else {
					if (isAContainer(current)) {
						snapshotXPaths = findDataActivitiesInContainer(
								snapshots, current);
					}
				}
			}

		} else if (container instanceof OScope) {

			OActivity scopeActivity = ((OScope) container).activity;

			if (isADataManipulationActivity(scopeActivity)) {
				if (isValidSnapshotXPath(snapshots, scopeActivity.getXpath())) {
					snapshotXPaths.add(scopeActivity.getXpath());
				}
			} else {
				if (isAContainer(scopeActivity)) {
					snapshotXPaths = findDataActivitiesInContainer(snapshots,
							scopeActivity);
				}
			}

		} else if (container instanceof OFlow) {
			Set<OActivity> flowActivities = ((OFlow) container).parallelActivities;

			for (OActivity activity : flowActivities) {
				if (isADataManipulationActivity(activity)) {
					if (isValidSnapshotXPath(snapshots, activity.getXpath())) {
						// Add all possible valid snapshots to the list
						snapshotXPaths.add(activity.getXpath());
					}
				} else {
					if (isAContainer(activity)) {
						snapshotXPaths = findDataActivitiesInContainer(
								snapshots, activity);
					}
				}
			}

		} else if (container instanceof OForEach) {

			OActivity activity = ((OForEach) container).innerScope.activity;

			if (isADataManipulationActivity(activity)) {
				if (isValidSnapshotXPath(snapshots, activity.getXpath())) {
					snapshotXPaths.add(activity.getXpath());
				}
			} else {
				if (isAContainer(activity)) {
					snapshotXPaths = findDataActivitiesInContainer(snapshots,
							activity);
				}
			}

		} else if (container instanceof OWhile) {

			OActivity activity = ((OWhile) container).activity;

			if (isADataManipulationActivity(activity)) {
				if (isValidSnapshotXPath(snapshots, activity.getXpath())) {
					snapshotXPaths.add(activity.getXpath());
				}
			} else {
				if (isAContainer(activity)) {
					snapshotXPaths = findDataActivitiesInContainer(snapshots,
							activity);
				}
			}

		} else if (container instanceof ORepeatUntil) {

			OActivity activity = ((ORepeatUntil) container).activity;

			if (isADataManipulationActivity(activity)) {
				if (isValidSnapshotXPath(snapshots, activity.getXpath())) {
					snapshotXPaths.add(activity.getXpath());
				}
			} else {
				if (isAContainer(activity)) {
					snapshotXPaths = findDataActivitiesInContainer(snapshots,
							activity);
				}
			}

		} else if (container instanceof OSwitch) {
			// TODO: Was ist wenn ein Switch zwei Cases hat, die beide ein
			// Assign enthalten? Welcher der Pfade bzw. welches Assign ist dann
			// das Richtige???

			// Get the cases
			List<OCase> cases = ((OSwitch) container).getCases();

			// Loop through all cases
			for (OCase current : cases) {

				if (isADataManipulationActivity(current.activity)) {
					if (isValidSnapshotXPath(snapshots,
							current.activity.getXpath())) {
						snapshotXPaths.add(current.activity.getXpath());
					}
				} else {
					if (isAContainer(current.activity)) {
						snapshotXPaths = findDataActivitiesInContainer(
								snapshots, current.activity);
					}
				}

			}

		}

		return snapshotXPaths;
	}
}
