package org.apache.ode.bpel.engine.iteration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.dao.CorrelatorDAO;
import org.apache.ode.bpel.dao.LinkDAO;
import org.apache.ode.bpel.dao.LinkStateEnum;
import org.apache.ode.bpel.dao.ProcessDAO;
import org.apache.ode.bpel.engine.BpelProcess;
import org.apache.ode.bpel.engine.BpelRuntimeContextImpl;
import org.apache.ode.bpel.extensions.handler.ActivityEventHandler;
import org.apache.ode.bpel.extensions.processes.Running_Activity;
import org.apache.ode.bpel.iapi.Scheduler.JobDetails;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OCatch;
import org.apache.ode.bpel.o.OEventHandler;
import org.apache.ode.bpel.o.OFlow;
import org.apache.ode.bpel.o.OForEach;
import org.apache.ode.bpel.o.OInvoke;
import org.apache.ode.bpel.o.OLink;
import org.apache.ode.bpel.o.OPickReceive;
import org.apache.ode.bpel.o.OProcess;
import org.apache.ode.bpel.o.ORepeatUntil;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OSequence;
import org.apache.ode.bpel.o.OSwitch;
import org.apache.ode.bpel.o.OWhile;
import org.apache.ode.bpel.runtime.ACTIVITY.Key;
import org.apache.ode.bpel.runtime.ActivityInfo;
import org.apache.ode.bpel.runtime.BpelJacobRunnable;
import org.apache.ode.bpel.runtime.ChildInfo;
import org.apache.ode.bpel.runtime.CompensationHandler;
import org.apache.ode.bpel.runtime.FLOW;
import org.apache.ode.bpel.runtime.LinkFrame;
import org.apache.ode.bpel.runtime.PICK;
import org.apache.ode.bpel.runtime.SCOPE;
import org.apache.ode.bpel.runtime.SEQUENCE;
import org.apache.ode.bpel.runtime.ScopeFrame;
import org.apache.ode.bpel.runtime.WAIT;
import org.apache.ode.bpel.runtime.XPathParser;
import org.apache.ode.bpel.runtime.channels.PickResponseChannel;
import org.apache.ode.jacob.IndexedObject;
import org.apache.ode.jacob.vpu.ExecutionQueueImpl;

/**
 * This class contains all methods to realize the common issues of the
 * repetition of activities and the jump to the future in a process instance.
 * 
 * @author hahnml
 * 
 */
public class RepetitionAndJumpHelper {

	private static RepetitionAndJumpHelper instance = null;

	public static RepetitionAndJumpHelper getInstance() {
		if (instance == null) {
			instance = new RepetitionAndJumpHelper();
		}

		return instance;
	}

	/**
	 * Creates a new runtime object to realize the jump to the future in a
	 * process instance
	 * 
	 * @param we
	 * @param soup
	 * @param process
	 * @return
	 * 
	 * @author sonntamo
	 */
	public static BpelJacobRunnable calculateJumpTargetActivity(JobDetails we,
			ExecutionQueueImpl soup, BpelProcess process) {
		return calculateIterationActivity(we, soup, process);
	}

	/**
	 * Creates a new runtime object to realize the iteration and re-execution of
	 * activities functionality.
	 */
	public static BpelJacobRunnable calculateIterationActivity(JobDetails we,
			ExecutionQueueImpl soup, BpelProcess process) {

		// Reset the IterationHelper
		RepetitionAndJumpHelper.getInstance().clear();

		BpelJacobRunnable resultActivity = null;

		SEQUENCE oldProcessSequence = null;
		SCOPE oldProcessScope = null;
		ScopeFrame scopeframe = null;
		LinkFrame linkframe = null;

		QName process_name = we.getProcessId();
		Long processID = we.getInstanceId();

		OBase element = new XPathParser()
				.handleXPath(
						(String) we.getDetailsExt().get(
								JobDetails.TARGET_ACTIVITY_XPATH), process);
		OActivity activity = (OActivity) element;

		OProcess oprocess = process.getOProcess();
		OActivity o_processActivity = oprocess.procesScope.activity;

		ExecutionQueueImpl eql = soup;

		Set<Entry<Object, LinkedList<IndexedObject>>> entry = eql.getIndex()
				.entrySet();
		Iterator<Entry<Object, LinkedList<IndexedObject>>> iterator = entry
				.iterator();

		// Remember all invalid entries which result from multiple iteration and
		// migration
		List<Object> nullKeys = new ArrayList<Object>();

		// Find the process SEQUENCE and SCOPE objects
		while (iterator.hasNext()) {
			Entry<Object, LinkedList<IndexedObject>> me = iterator.next();
			LinkedList<IndexedObject> objects = me.getValue();

			for (IndexedObject obj : objects) {
				if (obj.getKey() instanceof Key
						&& ((Key) obj.getKey()).getType() == null) {

					nullKeys.add(me.getKey());

				} else {
					if (obj instanceof SEQUENCE) {

						if (((SEQUENCE) obj)._self.o.getId() == oprocess.procesScope.activity
								.getId()) {
							oldProcessSequence = (SEQUENCE) obj;
						}

					} else if (obj instanceof SCOPE) {

						if (((SCOPE) obj)._self.o.getId() == oprocess.procesScope
								.getId()) {
							oldProcessScope = (SCOPE) obj;
						}

					} else if (obj instanceof WAIT) {

						process._timerToIgnoreForIteration
								.add(((WAIT) obj)._self.o.getXpath());

					}
				}
			}
		}

		// Remove the invalid entries from the index list
		for (Object obj : nullKeys) {
			eql.getIndex().remove(obj);
		}

		// Return a new runnable which schedules the correct set of links and
		// activities, if the process scope activity is a OFlow
		if (o_processActivity instanceof OFlow) {
			RepetitionAndJumpHelper.getInstance().setIsFlowIteration(true);

			return RepetitionAndJumpHelper.getInstance().createNewRunnable(we,
					process, (OFlow) o_processActivity);
		} else {
			RepetitionAndJumpHelper.getInstance().setIsFlowIteration(false);
		}

		// Check if the process SEQUENCE frames could be used
		if (oldProcessSequence != null) {
			// Use the frames of the SEQUENCE
			scopeframe = oldProcessSequence.get_scopeFrame();
			linkframe = oldProcessSequence.get_linkFrame();
		} else if (oldProcessScope != null) {
			// Use the frames of the Process SCOPE
			scopeframe = oldProcessScope.get_scopeFrame();
			linkframe = oldProcessScope.get_linkFrame();
		}

		List<OActivity> processActivityList = new ArrayList<OActivity>();
		List<OActivity> innerActivityList = new ArrayList<OActivity>();

		// Collect all activities which should be iterated
		OActivity currentActivity = activity;
		OActivity parent = currentActivity.getParent();

		if (parent.getXpath().equals("/process/sequence[1]")) {
			// Add all activities from the process sequence starting at the
			// given activity index to the list.
			List<OActivity> activities = ((OSequence) o_processActivity).sequence;
			int a = activities.indexOf(element);

			for (int i = a; i < activities.size(); i++) {
				processActivityList.add(activities.get(i));
			}
		} else {
			OActivity innerActivityContainer = null;

			// Loop through all inner sequences of the process model
			while (parent != o_processActivity) {

				if (parent instanceof OSequence) {

					List<OActivity> activities = ((OSequence) parent).sequence;
					int index = activities.indexOf(currentActivity);

					if (activities.contains(activity)) {
						// If the iteration starts within a sequence
						for (int i = index; i < activities.size(); i++) {
							innerActivityList.add(activities.get(i));
						}
					} else {
						// Add the created inner activity container
						innerActivityList.add(innerActivityContainer);

						// Add all successor activities
						for (int i = index + 1; i < activities.size(); i++) {
							innerActivityList.add(activities.get(i));
						}
					}

					// Create a new OSequence to hold the inner activities
					innerActivityContainer = new OSequence(oprocess,
							((OSequence) parent).getParent());

					((OSequence) innerActivityContainer).sequence
							.addAll(innerActivityList);

					// Copy all properties from the old to the new OSequence
					innerActivityContainer.name = parent.name;
					innerActivityContainer.setXpath(parent.getXpath());
					innerActivityContainer.setArt(parent.getArt());
					innerActivityContainer.debugInfo = parent.debugInfo;
					innerActivityContainer.setFailureHandling(parent
							.getFailureHandling());
					innerActivityContainer.variableRd.addAll(parent.variableRd);
					innerActivityContainer.variableWr.addAll(parent.variableWr);

					// Move one layer up the hierarchy
					currentActivity = parent;
					parent = currentActivity.getParent();

					innerActivityList.clear();

				} else if (parent instanceof OScope) {

					OActivity scopeActivity = ((OScope) parent).activity;

					if (scopeActivity == activity) {
						// There's nothing to do, just copying the old scope
						innerActivityContainer = parent;
					} else {
						// Create a new OScope with the previous created inner
						// activity container
						OScope scope = new OScope(oprocess,
								((OScope) parent).getParent());

						scope.activity = innerActivityContainer;

						// Copy all properties from the original compiled OScope
						scope.name = ((OScope) parent).name;
						scope.setXpath(((OScope) parent).getXpath());
						scope.compensatable
								.addAll(((OScope) parent).compensatable);
						scope.compensationHandler = ((OScope) parent).compensationHandler;
						scope.correlationSets
								.putAll(((OScope) parent).correlationSets);
						scope.debugInfo = ((OScope) parent).debugInfo;
						scope.eventHandler = ((OScope) parent).eventHandler;
						scope.setFailureHandling(((OScope) parent)
								.getFailureHandling());
						scope.faultHandler = ((OScope) parent).faultHandler;
						scope.joinCondition = ((OScope) parent).joinCondition;
						scope.partnerLinks
								.putAll(((OScope) parent).partnerLinks);
						scope.suppressJoinFailure = ((OScope) parent).suppressJoinFailure;
						scope.terminationHandler = ((OScope) parent).terminationHandler;
						scope.variables.putAll(((OScope) parent).variables);
						scope.variableRd.addAll(((OScope) parent).variableRd);
						scope.variableWr.addAll(((OScope) parent).variableWr);

						innerActivityContainer = scope;
					}

					// Move one layer up the hierarchy
					currentActivity = parent;
					parent = currentActivity.getParent();

				} else if (parent instanceof OFlow) {
					// TODO: Actually the whole element will be copied without
					// any adjustments

					Set<OActivity> flowActivities = ((OFlow) parent).parallelActivities;
					Set<OLink> flowLinks = ((OFlow) parent).localLinks;

					if (flowActivities.contains(activity)) {
						// TODO: Only testing, implementation should be improved
						innerActivityContainer = parent;
					} else {
						OFlow flow = new OFlow(oprocess,
								((OFlow) parent).getParent());

						// Adjust the link set
						Iterator<OLink> linkIter = flowLinks.iterator();

						Set<OLink> flowLinksNew = new HashSet<OLink>();
						HashMap<OLink, OLink> old2NewLink = new HashMap<OLink, OLink>();

						while (linkIter.hasNext()) {
							OLink link = linkIter.next();

							// Create a new link and copy all properties from
							// the original one
							OLink newLink = new OLink(oprocess);

							newLink.declaringFlow = flow;
							newLink.name = link.name;
							newLink.debugInfo = link.debugInfo;
							newLink.source = link.source;
							newLink.target = link.target;
							newLink.transitionCondition = link.transitionCondition;
							newLink.setArt(link.getArt());
							newLink.setXpath(link.getXpath());

							// Change the links which start or end at our
							// previous inner activity container
							// (currentActivity in the old model tree)
							if (link.source == currentActivity
									|| link.target == currentActivity) {
								if (link.source == currentActivity) {
									newLink.source = innerActivityContainer;
								} else {
									newLink.target = innerActivityContainer;
								}
							}

							// Remember which new link is created from the old
							// link to update the activity link references
							old2NewLink.put(link, newLink);

							flowLinksNew.add(newLink);
						}

						// Adjust the activity set
						Set<OActivity> flowActivitiesNew = new HashSet<OActivity>();

						for (OActivity act : flowActivities) {
							if (act == currentActivity) {
								// Update all links
								for (OLink link : act.incomingLinks) {
									if (old2NewLink.containsKey(link)) {
										innerActivityContainer.incomingLinks
												.add(old2NewLink.get(link));
									}
								}
								for (OLink link : act.outgoingLinks) {
									if (old2NewLink.containsKey(link)) {
										innerActivityContainer.outgoingLinks
												.add(old2NewLink.get(link));
									}
								}
								for (OLink link : act.sourceLinks) {
									if (old2NewLink.containsKey(link)) {
										innerActivityContainer.sourceLinks
												.add(old2NewLink.get(link));
									}
								}
								for (OLink link : act.targetLinks) {
									if (old2NewLink.containsKey(link)) {
										innerActivityContainer.targetLinks
												.add(old2NewLink.get(link));
									}
								}

								flowActivitiesNew.add(innerActivityContainer);
							} else {
								// Create local sets for all link sets to read
								// the original once and save the modified link
								// collections to the local set without a
								// ConcurrentModificationException
								Set<OLink> incomingLinks = new HashSet<OLink>();
								Set<OLink> outgoingLinks = new HashSet<OLink>();
								Set<OLink> sourceLinks = new HashSet<OLink>();
								Set<OLink> targetLinks = new HashSet<OLink>();

								for (OLink link : act.incomingLinks) {
									if (old2NewLink.containsKey(link)) {
										incomingLinks
												.add(old2NewLink.get(link));
									}
								}
								for (OLink link : act.outgoingLinks) {
									if (old2NewLink.containsKey(link)) {
										outgoingLinks
												.add(old2NewLink.get(link));
									}
								}
								for (OLink link : act.sourceLinks) {
									if (old2NewLink.containsKey(link)) {
										sourceLinks.add(old2NewLink.get(link));
									}
								}
								for (OLink link : act.targetLinks) {
									if (old2NewLink.containsKey(link)) {
										targetLinks.add(old2NewLink.get(link));
									}
								}

								// Clear the original sets and add all the links
								// of the local sets
								act.incomingLinks.clear();
								act.incomingLinks.addAll(incomingLinks);

								act.outgoingLinks.clear();
								act.outgoingLinks.addAll(outgoingLinks);

								act.sourceLinks.clear();
								act.sourceLinks.addAll(sourceLinks);

								act.targetLinks.clear();
								act.targetLinks.addAll(targetLinks);

								flowActivitiesNew.add(act);
							}
						}

						flow.debugInfo = ((OFlow) parent).debugInfo;
						flow.name = ((OFlow) parent).name;
						flow.setFailureHandling(((OFlow) parent)
								.getFailureHandling());
						flow.setXpath(((OFlow) parent).getXpath());

						flow.parallelActivities.addAll(flowActivitiesNew);
						flow.localLinks.addAll(flowLinksNew);

						innerActivityContainer = flow;
					}

					// Move one layer up the hierarchy
					currentActivity = parent;
					parent = currentActivity.getParent();

				} else if (parent instanceof OForEach) {
					// TODO:

					// Move one layer up the hierarchy
					currentActivity = parent;
					parent = currentActivity.getParent();

					innerActivityList.clear();

				} else if (parent instanceof OWhile) {
					// TODO:

					// Move one layer up the hierarchy
					currentActivity = parent;
					parent = currentActivity.getParent();

					innerActivityList.clear();

				} else if (parent instanceof ORepeatUntil) {
					// TODO:

					// Move one layer up the hierarchy
					currentActivity = parent;
					parent = currentActivity.getParent();

					innerActivityList.clear();

				} else if (parent instanceof OSwitch) {
					// TODO:

					// Move one layer up the hierarchy
					currentActivity = parent;
					parent = currentActivity.getParent();

					innerActivityList.clear();

				}
			}

			// Get the index of the new container in the parent sequence
			int innerContainerIndex = ((OSequence) o_processActivity).sequence
					.indexOf(currentActivity);

			// Add the new inner container and the old successor activities of
			// the root
			// process sequence to the process activity list
			List<OActivity> activities = ((OSequence) o_processActivity).sequence;

			for (int i = innerContainerIndex; i < activities.size(); i++) {
				if (i == innerContainerIndex) {
					processActivityList.add(innerActivityContainer);
				} else {
					processActivityList.add(activities.get(i));
				}
			}
		}

		// If the process sequence was not found, we have to create a
		// new process scope. This makes it possible to iterate/reexecute (a
		// part of) the process from the process fault handler.
		// Iteration/Re-execution/Jump from within the process fault handler
		// into
		// the normal process logic is also indicated by a process scope
		// that has no child anymore (child == null).
		if (oldProcessSequence == null
				|| (oldProcessScope != null && oldProcessScope.isChildNull())) {

			if (oldProcessScope != null) {

				// Create a new process OSequence from the original compiled
				// OSequence and if model changed use the original ID
				OSequence procSequence = new OSequence(oprocess,
						oprocess.procesScope);

				// Add all activities for iteration
				procSequence.sequence.addAll(processActivityList);

				// Copy the properties from the original sequence
				procSequence.name = o_processActivity.name;
				procSequence.setXpath(o_processActivity.getXpath());
				procSequence.setArt(o_processActivity.getArt());
				procSequence.debugInfo = o_processActivity.debugInfo;
				procSequence.setFailureHandling(o_processActivity
						.getFailureHandling());
				procSequence.variableRd.addAll(o_processActivity.variableRd);
				procSequence.variableWr.addAll(o_processActivity.variableWr);

				// Create a new process OScope (copied from BpelCompiler)
				OScope procesScope = new OScope(oprocess, null);
				procesScope.processScope = true;
				procesScope.setArt(true);

				// Copy all properties from the original compiled OScope
				procesScope.name = oprocess.procesScope.name;
				procesScope.setXpath(oprocess.procesScope.getXpath());
				procesScope.compensatable
						.addAll(oprocess.procesScope.compensatable);
				procesScope.compensationHandler = oprocess.procesScope.compensationHandler;
				procesScope.correlationSets
						.putAll(oprocess.procesScope.correlationSets);
				procesScope.debugInfo = oprocess.procesScope.debugInfo;
				procesScope.eventHandler = oprocess.procesScope.eventHandler;
				procesScope.setFailureHandling(oprocess.procesScope
						.getFailureHandling());
				procesScope.faultHandler = oprocess.procesScope.faultHandler;
				procesScope.joinCondition = oprocess.procesScope.joinCondition;
				procesScope.partnerLinks
						.putAll(oprocess.procesScope.partnerLinks);
				procesScope.suppressJoinFailure = oprocess.procesScope.suppressJoinFailure;
				procesScope.terminationHandler = oprocess.procesScope.terminationHandler;
				procesScope.variables.putAll(oprocess.procesScope.variables);
				procesScope.variableRd.addAll(oprocess.procesScope.variableRd);
				procesScope.variableWr.addAll(oprocess.procesScope.variableWr);

				// Add the process sequence to the process scope
				procesScope.activity = procSequence;

				// Create a new ActivityInfo object with the reduced OScope
				// object
				ActivityInfo child = new ActivityInfo(
						oldProcessScope._self.aId, procesScope,
						oldProcessScope._self.self,
						oldProcessScope._self.parent);

				// Create a new scope frame for the created scope
				ScopeFrame processFrame = new ScopeFrame(procesScope,
						oldProcessScope._scopeFrame.getScopeInstanceId(), null,
						null);

				// Create the new process scope
				resultActivity = new IterationContainerRunnable(new SCOPE(
						child, processFrame, new LinkFrame(null), process_name,
						processID));

			}

		} else {

			// Create the new "process" root sequence
			resultActivity = new IterationContainerRunnable(new SEQUENCE(
					oldProcessSequence._self, scopeframe, linkframe,
					processActivityList, CompensationHandler.emptySet(), false,
					process_name, processID));

		}

		return resultActivity;
	}

	// Instance
	private Set<OActivity> reachableActivities = null;
	private Set<OLink> visitedLinks = null;
	private Set<OLink> crossingLinks = null;
	private OFlow targetFlow = null;
	private boolean isFlowIteration = false;
	private HashMap<OActivity, Running_Activity> runningActivities = null;
	Set<OActivity> pickReceives = null;

	public void clear() {
		this.reachableActivities = null;
		this.visitedLinks = null;
		this.targetFlow = null;
		this.crossingLinks = null;
		this.isFlowIteration = false;
		this.runningActivities = null;
		this.pickReceives = null;
	}

	private BpelJacobRunnable createNewRunnable(final JobDetails we,
			BpelProcess process, final OFlow parentFlow) {
		// Initialize the instance
		init(we, process, parentFlow);

		// Create a new runnable which schedules all links and activities after
		// the instance is resumed
		BpelJacobRunnable runnable = new BpelJacobRunnable() {
			private static final long serialVersionUID = 987999999912L;

			public void run() {
				OFlow oflow = parentFlow;

				// Get the FLOW from the ActivityEventHandler
				// final FLOW flow = ActivityEventHandler.getInstance()
				// .getRunningFlow(we.getInstanceId(), oflow.getXpath());

				FLOW flow = null;

				ExecutionQueueImpl soup = (ExecutionQueueImpl) getBpelRuntimeContext()
						.getVPU()._executionQueue;
				FLOW bufferedFlow = ActivityEventHandler.getInstance()
						.getRunningFlow(we.getInstanceId(), oflow.getXpath());

				if (bufferedFlow != null && soup != null) {
					if (!soup.getIndex().keySet().isEmpty()) {
						LinkedList<IndexedObject> list = null;

						Iterator<Object> keys = soup.getIndex().keySet()
								.iterator();
						while (flow == null && keys.hasNext()) {
							Key key = (Key) keys.next();
							if (key.getType() == bufferedFlow._self.o) {
								list = soup.getIndex().get(key);
							}

							if (list != null) {
								Iterator<IndexedObject> iter = list.iterator();

								while (flow == null && iter.hasNext()) {
									IndexedObject obj = iter.next();
									if (obj instanceof FLOW) {
										flow = (FLOW) obj;
									}
								}
							}
						}
					}
				}

				if (flow != null) {

					Set<ChildInfo> childInfosToReuse = new HashSet<ChildInfo>();
					Set<OActivity> reach_Act = RepetitionAndJumpHelper
							.getInstance().getIterationActivities();
					for (ChildInfo info : flow._children) {
						// If this ChildInfo objects belongs to an activity
						// which will be executed again it has to be rescheduled
						if (reach_Act.contains(info.activity.o)) {
							info.resetFlags();
							childInfosToReuse.add(info);
						}
					}

					// Schedule all reachable activities to instantiate
					// them again
					for (ChildInfo childInfo : childInfosToReuse) {
						instance(createChild(childInfo.activity,
								flow._scopeFrame, flow._linkFrame));

					}

					/*
					 * Check the IMAManager for conflicting
					 * PickResponseChannels:
					 */
					Set<OActivity> pickReceives = RepetitionAndJumpHelper
							.getInstance().getRunningPickReceiveActivities();
					if (pickReceives != null && !pickReceives.isEmpty()) {
						// Query the PickResponseChannels of all PICK's over the
						// execution queue
						// index list
						Set<PickResponseChannel> pickResponseChannels = new HashSet<PickResponseChannel>();
						if (!soup.getIndex().keySet().isEmpty()) {
							LinkedList<IndexedObject> list = null;

							Iterator<Object> keys = soup.getIndex().keySet()
									.iterator();
							while (keys.hasNext()) {
								Key key = (Key) keys.next();
								if (key.getType().getClass() == OPickReceive.class) {
									if (pickReceives.contains(key.getType())) {
										list = soup.getIndex().get(key);
									} else {
										list = null;
									}
								}

								if (list != null) {
									Iterator<IndexedObject> iter = list
											.iterator();

									while (iter.hasNext()) {
										IndexedObject obj = iter.next();
										if (obj instanceof PICK) {
											pickResponseChannels
													.add(((PICK) obj)
															.getPickResponseChannel());
										}
									}
								}
							}
						}

						for (PickResponseChannel channel : pickResponseChannels) {
							String channelStr = channel.export();
							// Remove the PickResponseChannel from the
							// IMAManager, if it is registered
							if (getBpelRuntimeContext().isIMAChannelRegistered(
									channelStr)) {
								getBpelRuntimeContext()
										.cancelOutstandingRequests(channelStr);
								// Delete the corresponding outdated
								// MessageRoute
								String correlatorStr = ChannelRegistry
										.getRegistry()
										.getPickResponseChannelCorrelator(
												getBpelRuntimeContext()
														.getPid(), channelStr);
								ProcessDAO process = getBpelRuntimeContext()
										.getBpelProcess().getProcessDAO();
								CorrelatorDAO correlator = process
										.getCorrelator(correlatorStr);
								correlator.removeRoutes(channelStr, process
										.getInstance(getBpelRuntimeContext()
												.getPid()));
							}
						}
					}

					// Instance FLOW.ACTIVE again
					instance(flow._active);

					// Update the buffered running flow at ActivityEventHandler
					// to ensure the correct execution of following iterations
					// ActivityEventHandler.getInstance().updateRunningFLOW(flow);
				}
			}
		};

		// Mark the runnable to be executed immediately
		runnable.setExecuteImmediately(true);

		return runnable;
	}

	protected Set<OActivity> getRunningPickReceiveActivities() {
		return this.pickReceives;
	}

	public Set<OActivity> getIterationActivities() {
		Set<OActivity> iterationActivities = new HashSet<OActivity>();
		// Copy all reachable activities to the set
		iterationActivities.addAll(this.reachableActivities);

		// Loop through all crossing links and check if their source activities
		// (or predecessors) are running
		for (OLink link : this.crossingLinks) {
			if (containsPathRunningActivity(link.source)) {
				iterationActivities.remove(link.target);
			}
		}

		return iterationActivities;
	}

	private boolean containsPathRunningActivity(OActivity activity) {
		// Collect all reachable activities
		Set<OActivity> reachableActivities = new HashSet<OActivity>();
		collectReachableSourceActivities(reachableActivities, activity);

		// Check if one of them is running
		for (OActivity act : reachableActivities) {
			if (this.runningActivities.containsKey(act)) {
				return true;
			}
		}

		return false;
	}

	private void init(JobDetails we, BpelProcess process, OFlow flow) {
		this.targetFlow = flow;

		// Get the target activity of the reexecution job
		String targetXPath = (String) we.getDetailsExt().get(
				JobDetails.TARGET_ACTIVITY_XPATH);
		OActivity targetActivity = ReexecutionHandler.getActivity(targetXPath,
				process.getOProcess());

		// Collect all activities which are reachable from the target activity
		Set<OActivity> reachableActivities = new HashSet<OActivity>();
		Set<OLink> visitedLinks = new HashSet<OLink>();
		// Add the target activity to the reachable activities
		reachableActivities.add(targetActivity);
		collectReachableActivities(reachableActivities, targetActivity,
				visitedLinks);

		this.reachableActivities = reachableActivities;
		this.visitedLinks = visitedLinks;

		// Collect all links which connect an activity of the set of all flow
		// activities and an activity of the set of all reachable activities.
		this.crossingLinks = collectCrossingLinks(flow);
	}

	public Set<Integer> calculateContinuationsToDelete(JobDetails we,
			BpelRuntimeContextImpl context, ExecutionQueueImpl soup) {
		// TODO: Does not work for JumpTo...
		Set<Integer> objectsToDelete = new HashSet<Integer>();

		// Add the id of the OFlow to the set to delete the old FLOW.ACTIVE()
		// object from the queue
		objectsToDelete.add(this.targetFlow.getId());

		Long processID = we.getInstanceId();
		ActivityEventHandler evtHandler = ActivityEventHandler.getInstance();

		List<Running_Activity> activities = evtHandler.getRunningActivities();
		runningActivities = new HashMap<OActivity, Running_Activity>();
		pickReceives = new HashSet<OActivity>();

		// Loop through all running activities
		for (Running_Activity run_Act : activities) {
			// Check if the activity belongs to our process instance
			if (run_Act.getProcessID().equals(processID)) {
				// Get the activity over its xpath
				OActivity act = ReexecutionHandler.getActivity(run_Act
						.getXPath(), context.getBpelProcess().getOProcess());

				if (act != null) {
					runningActivities.put(act, run_Act);
				}
			}
		}

		// Loop through the list of reachable activities and check if they are
		// running
		for (OActivity activity : reachableActivities) {
			if (runningActivities.containsKey(activity)) {
				objectsToDelete.add(activity.getId());

				// Check if the running&reachable activity is of type
				// OPickReceive
				if (activity instanceof OPickReceive) {
					pickReceives.add(activity);
				}

				// Remove the running activities also from the
				// runnableActivities list in the ActivityEventHandler
				Running_Activity act = runningActivities.get(activity);
				ActivityEventHandler.getInstance().removeRunningActivity(
						act.getProcessID(), act.getScopeID(), act.getXPath());
			}
		}

		// Set the status of all visited links to NOT_EVALUATED
		for (OLink link : visitedLinks) {
			LinkDAO linkDao = context.getProcessInstanceDao().getLink(
					link.getId());
			if (linkDao != null) {
				linkDao.setState(LinkStateEnum.NOT_EVALUATED);
			}
		}

		return objectsToDelete;
	}

	/**
	 * Gets all correlation sets that are initialized by the given invoke
	 * activity. This holds both for inbound and outbound messages.
	 * 
	 * @param invoke
	 * @return
	 * 
	 * @author sonntamo
	 */
	private HashMap<String, OScope.CorrelationSet> getInitCorrSets(
			OInvoke invoke) {

		HashMap<String, OScope.CorrelationSet> corrSets = new HashMap<String, OScope.CorrelationSet>();

		// We take both correlation sets for inbound and outbound messages that
		// are initialized by this invoke activity
		if (!invoke.initCorrelationsInput.isEmpty()) {
			for (OScope.CorrelationSet cset : invoke.initCorrelationsInput) {
				corrSets.put(cset.getXpath(), cset);
			}
		}
		if (!invoke.initCorrelationsOutput.isEmpty()) {
			for (OScope.CorrelationSet cset : invoke.initCorrelationsOutput) {
				corrSets.put(cset.getXpath(), cset);
			}
		}
		return corrSets;
	}

	/**
	 * Gets all correlation sets that are initialized (initialize="yes") by
	 * invoke activities in the iteration body.
	 * 
	 * @param xpath
	 *            the target activity for the iteration
	 * @param bpelProcess
	 *            the BPEL process which is iterated
	 * @return all correlation sets that are initialized in the iteration body
	 * 
	 * @author sonntamo
	 */
	public HashMap<String, OScope.CorrelationSet> getReachableCorrelationSets(
			String xpath, BpelProcess bpelProcess) {

		// get the activity for the XPath
		OBase element = new XPathParser().handleXPath(xpath, bpelProcess);
		OActivity activity = (OActivity) element;

		// all correlation sets are collected in this map
		HashMap<String, OScope.CorrelationSet> corrSets = new HashMap<String, OScope.CorrelationSet>();

		// if the activity is an invoke, we consider its correlation sets and
		// also have a look on its compensation and fault handlers
		if (activity instanceof OInvoke) {
			corrSets.putAll(getInitCorrSets((OInvoke) activity));
			corrSets.putAll(considerInvokeHandlers((OInvoke) activity));
		}

		// A correlation set can also be initialized in a process fault or event
		// handler. That's why we must consider them, too.
		OProcess process = activity.getOwner();
		if (process.procesScope.faultHandler != null) {
			for (OCatch oCatch : process.procesScope.faultHandler.catchBlocks) {
				corrSets.putAll(considerChildren(oCatch.activity));
			}
		}
		if (process.procesScope.eventHandler != null) {
			for (OEventHandler.OEvent oEvent : process.procesScope.eventHandler.onMessages) {
				corrSets.putAll(considerChildren(oEvent.activity));
			}
			for (OEventHandler.OAlarm oAlarm : process.procesScope.eventHandler.onAlarms) {
				corrSets.putAll(considerChildren(oAlarm.activity));
			}
		}

		// Now we consider all successor activities
		corrSets.putAll(getCorrelationSetsOfSuccessorContainerAndContent(activity));
		return corrSets;
	}

	/**
	 * If the given activity is a structured activity, the algorithm goes on
	 * with all children. Then, all successors of the activity are considered.
	 * 
	 * @param act
	 * @return
	 * 
	 * @author sonntamo
	 */
	private HashMap<String, OScope.CorrelationSet> getCorrelationSetsOfSuccessorContainerAndContent(
			OActivity act) {

		// the result map
		HashMap<String, OScope.CorrelationSet> corrSets = new HashMap<String, OScope.CorrelationSet>();

		// Check if the selected activity is a structured one
		if (act instanceof OSequence || act instanceof OSwitch
				|| act instanceof OForEach || act instanceof ORepeatUntil
				|| act instanceof OWhile || act instanceof OScope
				|| act instanceof OFlow || act instanceof OPickReceive) {

			// go on with all children
			corrSets.putAll(considerChildren(act));

			// If it's a scope, consider its handlers
			if (act instanceof OScope)
				corrSets.putAll(considerScopeHandlers((OScope) act));
		}

		// Now we climb up the tree and have a look on the activity's parent
		// container, the parent's parent, etc.
		OActivity parent = act.getParent();
		while (parent != null) {

			// special handling for different kinds of structured activities
			if (parent instanceof OFlow) {

				// in a flow we have to consider all successors of the activity
				OFlow flow = (OFlow) parent;
				corrSets.putAll(considerFlowChildren(flow, act));
			} else if (parent instanceof OScope) {

				// in a scope we have to consider all handlers only because we
				// already had a look on the child activity
				corrSets.putAll(considerScopeHandlers((OScope) parent));
			} else if (parent instanceof OSequence) {

				// In a sequence we have to consider all successors of the
				// activity. We can get them via the index of the considered
				// activity.
				OSequence oSeq = (OSequence) parent;
				int elementIndex = oSeq.sequence.indexOf(act);

				OActivity current = null;
				for (int i = elementIndex + 1; i < oSeq.sequence.size(); i++) {

					current = oSeq.sequence.get(i);

					// In case of an invoke we take all initialized correlation
					// sets.
					if (current instanceof OInvoke) {
						corrSets.putAll(getInitCorrSets((OInvoke) current));
						corrSets.putAll(considerInvokeHandlers((OInvoke) current));
					}

					// Check if the current element is a container activity
					// so we have to handle the child elements
					if (current instanceof OSequence
							|| current instanceof OSwitch
							|| current instanceof OForEach
							|| current instanceof ORepeatUntil
							|| current instanceof OWhile
							|| current instanceof OScope
							|| current instanceof OFlow
							|| current instanceof OPickReceive) {
						corrSets.putAll(considerChildren(current));

						// If it's a scope, have a look on its handlers.
						if (current instanceof OScope)
							corrSets.putAll(considerScopeHandlers((OScope) current));
					}
				}
			} // TODO: other structured activities are missing here

			// Move one layer up the hierarchy
			act = parent;
			parent = parent.getParent();

			// If we started from inside of a flow, we have to
			// skip the flow itself so that we don't move downwards again.
			// if (parent instanceof OFlow) {
			//
			// // Move one layer up the hierarchy
			// act = parent;
			// parent = parent.getParent();
			// }
		}
		return corrSets;
	}

	/**
	 * Searches for correlation sets that are initialized by invoke activities
	 * in the children of the given structured activity.
	 * 
	 * @param act
	 *            a structured activity
	 * @return the correlation sets that are initialized in the structured
	 *         activity
	 * 
	 * @author sonntamo
	 */
	private HashMap<String, OScope.CorrelationSet> considerChildren(
			OActivity act) {
		HashMap<String, OScope.CorrelationSet> corrSets = new HashMap<String, OScope.CorrelationSet>();

		List<OActivity> children = new ArrayList<OActivity>();
		if (act != null) {

			// get all children of the activity
			if (act instanceof OSequence) {
				children.addAll(((OSequence) act).sequence);
			} else if (act instanceof OFlow) {
				children.addAll(((OFlow) act).parallelActivities);
			} else if (act instanceof OPickReceive) {
				OPickReceive pickrec = (OPickReceive) act;
				for (OPickReceive.OnMessage onMessage : pickrec.onMessages) {
					children.add(onMessage.activity);
				}
				for (OPickReceive.OnAlarm onAlarm : pickrec.onAlarms) {
					children.add(onAlarm.activity);
				}
			} else if (act instanceof OScope) {
				children.add(((OScope) act).activity);
			} else if (act instanceof OWhile) {
				children.add(((OWhile) act).activity);
			} else if (act instanceof ORepeatUntil) {
				children.add(((ORepeatUntil) act).activity);
			} else if (act instanceof OSwitch) {
				OSwitch oSwitch = (OSwitch) act;
				for (OSwitch.OCase oCase : oSwitch.getCases()) {
					children.add(oCase.activity);
				}
			} else if (act instanceof OForEach) {
				children.add(((OForEach) act).innerScope);
			}

			// iterate over the children
			for (OActivity oAct : children) {

				// If it's an invoke activity, look for initialized correlation
				// sets and consider its handlers.
				if (oAct instanceof OInvoke) {
					corrSets.putAll(getInitCorrSets((OInvoke) oAct));
					corrSets.putAll(considerInvokeHandlers((OInvoke) oAct));
				}

				// It it's a structured activity, consider its children
				if (oAct instanceof OSequence || oAct instanceof OSwitch
						|| oAct instanceof OForEach
						|| oAct instanceof ORepeatUntil
						|| oAct instanceof OWhile || oAct instanceof OScope
						|| oAct instanceof OFlow
						|| oAct instanceof OPickReceive) {
					corrSets.putAll(considerChildren(oAct));

					// If it's a scope, have a look on all handlers
					if (oAct instanceof OScope)
						corrSets.putAll(considerScopeHandlers((OScope) oAct));

				}
			}
		}
		return corrSets;
	}

	/**
	 * Gets all correlation sets initialized in the compensation or fault
	 * handler of the given invoke activity.
	 * 
	 * @param invoke
	 *            the invoke activity to check the handlers for
	 * @return correlation sets that are initialized in the handlers of the
	 *         invoke activity
	 * 
	 * @author sonntamo
	 */
	private HashMap<String, OScope.CorrelationSet> considerInvokeHandlers(
			OInvoke invoke) {
		HashMap<String, OScope.CorrelationSet> corrSets = new HashMap<String, OScope.CorrelationSet>();

		// get the invoke's implicit scope
		OActivity parent = invoke.getParent();
		if (parent instanceof OScope) {
			OScope implicitScope = (OScope) parent;

			// check the invoke's CH
			if (implicitScope.compensationHandler != null) {
				corrSets.putAll(considerChildren(implicitScope.compensationHandler));
			}

			// check the invoke's FH
			if (implicitScope.faultHandler != null) {
				for (OCatch oCatch : implicitScope.faultHandler.catchBlocks) {
					corrSets.putAll(considerChildren(oCatch.activity));
				}

			}
		}
		return corrSets;
	}

	/**
	 * Gets all correlation sets initialized in the handlers of the given scope
	 * activity.
	 * 
	 * @param scope
	 *            the scope to check for
	 * @return the correlation sets that are initialized in the scope
	 * 
	 * @author sonntamo
	 */
	private HashMap<String, OScope.CorrelationSet> considerScopeHandlers(
			OScope scope) {
		HashMap<String, OScope.CorrelationSet> corrSets = new HashMap<String, OScope.CorrelationSet>();

		// check the scope's CH
		if (scope.compensationHandler != null) {
			corrSets.putAll(considerChildren(scope.compensationHandler));
		}

		// check the scope's TH
		if (scope.terminationHandler != null) {
			corrSets.putAll(considerChildren(scope.terminationHandler.activity));
		}

		// check the scope's FH
		if (scope.faultHandler != null) {
			for (OCatch oCatch : scope.faultHandler.catchBlocks) {
				corrSets.putAll(considerChildren(oCatch.activity));
			}
		}

		// check the scope's EH
		if (scope.eventHandler != null) {
			for (OEventHandler.OEvent oEvent : scope.eventHandler.onMessages) {
				corrSets.putAll(considerChildren(oEvent.activity));
			}
			for (OEventHandler.OAlarm oAlarm : scope.eventHandler.onAlarms) {
				corrSets.putAll(considerChildren(oAlarm.activity));
			}
		}
		return corrSets;
	}

	/**
	 * Get all correlation sets that are initialized by children of the given
	 * flow activity.
	 * 
	 * @param flow
	 *            the flow activity to check
	 * @param activity
	 *            the start activity of the iteration/re-execution
	 * @return the correlation sets that are initialized by children of the
	 *         given flow
	 * 
	 * @author sonntamo
	 */
	private HashMap<String, OScope.CorrelationSet> considerFlowChildren(
			OFlow flow, OActivity activity) {
		HashMap<String, OScope.CorrelationSet> corrSets = new HashMap<String, OScope.CorrelationSet>();

		// If it's an invoke activity, get all initialized correlation sets and
		// have a look on the CH and FH.
		if (activity instanceof OInvoke) {
			corrSets.putAll(getInitCorrSets((OInvoke) activity));
			corrSets.putAll(considerInvokeHandlers((OInvoke) activity));
		}

		// Check if the current element is a structured activity
		// so we have to handle the child elements
		if (activity instanceof OSequence || activity instanceof OSwitch
				|| activity instanceof OForEach
				|| activity instanceof ORepeatUntil
				|| activity instanceof OWhile || activity instanceof OScope
				|| activity instanceof OFlow
				|| activity instanceof OPickReceive) {
			corrSets.putAll(considerChildren(activity));

			// If it's a scope, consider all handlers
			if (activity instanceof OScope)
				corrSets.putAll(considerScopeHandlers((OScope) activity));
		}

		// Now take all direct successors of the activity
		for (OLink link : flow.localLinks) {
			if (link.source.equals(activity)) {
				corrSets.putAll(considerFlowChildren(flow, link.target));
			}
		}
		return corrSets;
	}

	private Set<OLink> collectCrossingLinks(OFlow parent) {
		Set<OLink> crossingLinks = new HashSet<OLink>();

		// Get all links of the flow which are not visited during the iteration
		Set<OLink> remainingLinks = new HashSet<OLink>();
		remainingLinks.addAll(parent.localLinks);
		remainingLinks.removeAll(this.visitedLinks);

		// Loop through these links and collect all links where the target is an
		// activity of the reachable set.
		for (OLink link : remainingLinks) {
			if (this.reachableActivities.contains(link.target)) {
				crossingLinks.add(link);
			}
		}

		return crossingLinks;
	}

	private void collectReachableActivities(Set<OActivity> activities,
			OActivity activity, Set<OLink> visitedLinks) {
		for (OLink link : activity.sourceLinks) {
			activities.add(link.target);
			visitedLinks.add(link);

			// Invoke recursive to follow the links of the target activity
			collectReachableActivities(activities, link.target, visitedLinks);
		}
	}

	private void collectReachableSourceActivities(Set<OActivity> activities,
			OActivity activity) {
		for (OLink link : activity.targetLinks) {
			activities.add(link.source);

			// Invoke recursive to follow the links of the source activity
			collectReachableSourceActivities(activities, link.source);
		}
	}

	private void setIsFlowIteration(boolean isFlow) {
		this.isFlowIteration = isFlow;
	}

	public boolean isFlowIteration() {
		return this.isFlowIteration;
	}

	public Set<OActivity> getReachableActivities() {
		return reachableActivities;
	}

	public Set<OLink> getVisitedLinks() {
		return visitedLinks;
	}

	public Set<OLink> getCrossingLinks() {
		return crossingLinks;
	}

	public OFlow getTargetFlow() {
		return this.targetFlow;
	}
}
