package org.apache.ode.bpel.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OAssign;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OEventHandler;
import org.apache.ode.bpel.o.OInvoke;
import org.apache.ode.bpel.o.OPartnerLink;
import org.apache.ode.bpel.o.OPickReceive;
import org.apache.ode.bpel.o.OProcess;
import org.apache.ode.bpel.o.OReply;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OScope.CorrelationSet;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class ProcessMerger {
	private static final Log log = LogFactory.getLog(ProcessMerger.class);
	private HashMap<String, CorrelationSet> corrSetMap;
	private Set<String> correlationSetsToReplace;
	private Set<String> partnerLinksToReplace;

	public ProcessMerger() {
		corrSetMap = new HashMap<String, CorrelationSet>();
		correlationSetsToReplace = new HashSet<String>();
		partnerLinksToReplace = new HashSet<String>();
	}

	/*
	 * public void prepareForMerging(File ddFile, QName processName){
	 * 
	 * try { DeployDocument dd = DeployDocument.Factory.parse(ddFile);
	 * List<TDeployment.Process> processes = dd.getDeploy().getProcessList();
	 * TDeployment.Process proc = null; for (TDeployment.Process process:
	 * processes){ if (process.getName().equals(processName)){ proc = process; }
	 * }
	 * 
	 * } catch (XmlException e) {
	 * log.error("Error parsing the deployment descriptor!"); } catch
	 * (IOException e) { log.error("Could not read the deployment descriptor!");
	 * }
	 * 
	 * 
	 * }
	 */

	private void copyAllPartnerLinks(OProcess to, OProcess from) {
		for (OPartnerLink link : from.allPartnerLinks) {
			if (!partnerLinksToReplace.contains(link)) {
				to.allPartnerLinks.add(link);
			}

		}

	}

	public void merge(OProcess to, OProcess from, OActivity hostActivity) {
		copyAllPartnerLinks(to, from);
		to.elementTypes.putAll(from.elementTypes);
		to.expressionLanguages.addAll(from.expressionLanguages);
		to.messageTypes.putAll(from.messageTypes);
		to.properties.addAll(from.properties);
		to.xsdTypes.putAll(from.xsdTypes);
		to.xslSheets.putAll(from.xslSheets);
		// mergeCorrelationSets(from, hostActivity);
		// mergePartnerLinks(to.allPartnerLinks, from, hostActivity);
		// replacePartnerLinks(to, from);
	}

	private void replacePartnerLinks(OProcess to, OProcess from) {
		for (OBase element : from.getChildren()) {
			if (element instanceof OAssign.PartnerLinkRef) {
				OAssign.PartnerLinkRef ref = (OAssign.PartnerLinkRef) element;
				if (partnerLinksToReplace.contains(ref.partnerLink.name)) {
					ref.partnerLink = to.getPartnerLink(ref.partnerLink.name);
				}
			} else if (element instanceof OEventHandler.OEvent) {
				OEventHandler.OEvent handler = (OEventHandler.OEvent) element;
				if (partnerLinksToReplace.contains(handler.partnerLink.name)) {
					handler.partnerLink = to
							.getPartnerLink(handler.partnerLink.name);
				}

			} else if (element instanceof OInvoke) {
				OInvoke invoke = (OInvoke) element;
				if (partnerLinksToReplace.contains(invoke.partnerLink)) {
					invoke.partnerLink = to
							.getPartnerLink(invoke.partnerLink.name);
				}
			} else if (element instanceof OPickReceive.OnMessage) {
				OPickReceive.OnMessage onMessage = (OPickReceive.OnMessage) element;
				if (partnerLinksToReplace.contains(onMessage.partnerLink.name)) {
					onMessage.partnerLink = to
							.getPartnerLink(onMessage.partnerLink.name);
				}
			} else if (element instanceof OReply) {
				OReply reply = (OReply) element;
				if (partnerLinksToReplace.contains(reply.partnerLink.name)) {
					reply.partnerLink = to
							.getPartnerLink(reply.partnerLink.name);
				}

			} else if (element instanceof OScope) {
				OScope scope = (OScope) element;
				removeMergedLinks(scope);
			}
		}

	}

	private void removeMergedLinks(OScope scope) {
		for (String name : partnerLinksToReplace) {
			if (scope.partnerLinks.containsKey(name)) {
				scope.partnerLinks.remove(name);
			}
		}
	}

	private void mergePartnerLinks(Set<OPartnerLink> toPartnerLinks,
			OProcess from, OActivity hostActivity) {
		HashMap<String, OPartnerLink> nameLinkMap = new HashMap<String, OPartnerLink>();
		for (OPartnerLink link : toPartnerLinks) {
			nameLinkMap.put(link.name, link);
		}
		for (OPartnerLink link : from.allPartnerLinks) {
			if (partnerLinksToReplace.contains(link.name)) {
				OPartnerLink into = nameLinkMap.get(link.name);

				// copy join correlation sets
				Map<String, Set<OScope.CorrelationSet>> joinsMap = link
						.getJoiningCorrelationSets();
				for (String operation : joinsMap.keySet()) {
					Set<OScope.CorrelationSet> sets = joinsMap.get(operation);
					for (OScope.CorrelationSet set : sets) {
						into.addCorrelationSetForOperation(operation, set, true);
					}
				}

				// copy other correlation sets
				Map<String, Set<OScope.CorrelationSet>> nonInitMap = link
						.getNonIntitiatingCorrelationSets();
				for (String operation : nonInitMap.keySet()) {
					Set<OScope.CorrelationSet> sets = nonInitMap.get(operation);
					for (OScope.CorrelationSet set : sets) {
						into.addCorrelationSetForOperation(operation, set,
								false);
					}
				}
			}
		}
	}

	private void mergeCorrelationSets(OProcess from, OActivity hostActivity) {
		List<OBase> children = from.getChildren();
		for (OBase element : children) {

			if (element instanceof OScope) {
				OScope scope = (OScope) element;
				replaceCorrSetMap(hostActivity, scope.correlationSets);
			}

			if (element instanceof OEventHandler.OEvent) {
				OEventHandler.OEvent event = (OEventHandler.OEvent) element;
				replaceCorrSetCollection(hostActivity, event.initCorrelations);
				replaceCorrSetCollection(hostActivity, event.joinCorrelations);
				replaceCorrSetCollection(hostActivity, event.matchCorrelations);

			} else if (element instanceof OInvoke) {
				OInvoke invoke = (OInvoke) element;
				replaceCorrSetCollection(hostActivity,
						invoke.assertCorrelationsInput);
				replaceCorrSetCollection(hostActivity,
						invoke.assertCorrelationsOutput);
				replaceCorrSetCollection(hostActivity,
						invoke.initCorrelationsInput);
				replaceCorrSetCollection(hostActivity,
						invoke.initCorrelationsOutput);
				replaceCorrSetCollection(hostActivity,
						invoke.joinCorrelationsInput);
				replaceCorrSetCollection(hostActivity,
						invoke.joinCorrelationsOutput);

			} else if (element instanceof OPartnerLink) {
				OPartnerLink link = (OPartnerLink) element;
				List<Set<OScope.CorrelationSet>> list = link
						.getCorelationSetSets();
				for (Set<OScope.CorrelationSet> set : list) {
					replaceCorrSetCollection(hostActivity, set);
				}
			} else if (element instanceof OPickReceive.OnMessage) {
				OPickReceive.OnMessage onMessage = (OPickReceive.OnMessage) element;
				replaceCorrSetCollection(hostActivity,
						onMessage.initCorrelations);
				replaceCorrSetCollection(hostActivity,
						onMessage.joinCorrelations);
				replaceCorrSetCollection(hostActivity,
						onMessage.matchCorrelations);
			} else if (element instanceof OReply) {
				OReply reply = (OReply) element;
				replaceCorrSetCollection(hostActivity, reply.assertCorrelations);
				replaceCorrSetCollection(hostActivity, reply.initCorrelations);
				replaceCorrSetCollection(hostActivity, reply.joinCorrelations);
			}
		}
		/*
		 * for (String name: correlationSetsToReplace){ CorrelationSet set =
		 * findChildCorrelationSet(hostActivity, name);
		 * set.declaringScope.correlationSets.remove(name); }
		 */
	}

	private CorrelationSet findChildCorrelationSet(OActivity activity,
			String name) {
		CorrelationSet set = null;
		List<OBase> children = activity.getOwner().getChildren();
		for (OBase base : children) {
			if (base instanceof CorrelationSet) {
				CorrelationSet current = (CorrelationSet) base;
				if (isChildOf(current.declaringScope, activity)) {
					set = current;
					break;
				}
			}
		}

		return set;
	}

	private boolean isChildOf(OActivity child, OActivity parent) {
		boolean found = false;
		OActivity current = child;
		while (current != null && !found) {
			if (current.equals(parent)) {
				found = true;
			}
			current = current.getParent();
		}
		return found;

	}

	private void removeExistingCorrSetMap(OActivity hostActivity,
			Map<String, CorrelationSet> map) {

		ArrayList<CorrelationSet> toRemove = new ArrayList<CorrelationSet>();
		for (String name : map.keySet()) {
			if (correlationSetsToReplace.contains(name)) {
				CorrelationSet found = findCorrelationSet(hostActivity, name);
				if (found != null) {

					toRemove.add(map.get(name));
				} else {
					// do not replace
				}
			}

		}
		for (CorrelationSet set : toRemove) {
			map.remove(set.name);
		}

	}

	private void replaceCorrSetMap(OActivity hostActivity,
			Map<String, CorrelationSet> map) {
		ArrayList<CorrelationSet> toAdd = new ArrayList<CorrelationSet>();
		ArrayList<CorrelationSet> toRemove = new ArrayList<CorrelationSet>();
		for (String name : map.keySet()) {
			if (correlationSetsToReplace.contains(name)) {
				CorrelationSet found = findCorrelationSet(hostActivity, name);
				if (found != null) {
					toAdd.add(found);
					toRemove.add(map.get(name));
				} else {
					// do not replace
				}
			}

		}
		for (CorrelationSet set : toRemove) {
			map.remove(set.name);
		}
		for (CorrelationSet set : toAdd) {
			map.put(set.name, set);
		}
	}

	private void replaceCorrSetCollection(OActivity hostActivity,
			Collection<CorrelationSet> sets) {
		ArrayList<CorrelationSet> toAdd = new ArrayList<CorrelationSet>();
		ArrayList<CorrelationSet> toRemove = new ArrayList<CorrelationSet>();
		for (CorrelationSet set : sets) {
			CorrelationSet found = findCorrelationSet(hostActivity, set.name);
			if (found != null && found.name.equals(set.name)) {
				toAdd.add(found);
				toRemove.add(set);
			} else {
				// do not replace
			}
		}
		for (CorrelationSet set : toRemove) {
			sets.remove(set);
		}
		for (CorrelationSet set : toAdd) {
			sets.add(set);
		}
	}

	private CorrelationSet findCorrelationSet(OActivity activity, String name) {
		CorrelationSet result = null;
		// cache check for found correlatinSets
		if (corrSetMap.containsKey(name)) {
			result = corrSetMap.get(name);
		} else {
			// cache check end
			OActivity currentActivity = activity;

			while (currentActivity != null) {
				if (currentActivity instanceof OScope) {
					OScope scope = (OScope) currentActivity;
					if (scope.correlationSets.containsKey(name)) {
						result = scope.correlationSets.get(name);
						// caching of found correlationSets
						corrSetMap.put(name, result);
						// caching end
						break;
					}
				}
				currentActivity = currentActivity.getParent();
			}
		}
		return result;
	}

}
