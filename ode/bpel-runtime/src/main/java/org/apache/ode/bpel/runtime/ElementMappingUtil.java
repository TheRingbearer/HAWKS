package org.apache.ode.bpel.runtime;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.axis2.util.XMLUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.common.CorrelationKey;
import org.apache.ode.bpel.common.FaultException;
import org.apache.ode.bpel.engine.fc.excp.FragmentCompositionException;
import org.apache.ode.bpel.evar.ExternalVariableModuleException;
import org.apache.ode.bpel.evt.PartnerLinkModificationEvent;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OFragmentEntry;
import org.apache.ode.bpel.o.OFragmentExit;
import org.apache.ode.bpel.o.OFragmentRegion;
import org.apache.ode.bpel.o.OPartnerLink;
import org.apache.ode.bpel.o.OProcess;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OScope.CorrelationSet;
import org.apache.ode.bpel.o.OScope.Variable;
import org.apache.ode.bpel.util.Pair;
import org.apache.ode.bpel.util.fc.FragmentCompositionUtil;
import org.apache.ode.bpel.util.fc.WsdlXsdMerger;
import org.apache.ode.fc.dao.FCManagementDAO;
import org.apache.ode.fc.dao.MappingInfo;
import org.apache.ode.fcanalyzer.ProcessElementFinder;
import org.apache.ode.mediator.CSetMediationInfo;
import org.apache.ode.mediator.MediationException;
import org.apache.ode.mediator.Mediator;
import org.apache.ode.mediator.MediatorImpl;
import org.apache.ode.utils.DOMUtils;
import org.apache.ode.utils.fc.Mapping;
import org.apache.ode.utils.fc.Mapping.ElementType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ElementMappingUtil {
	private static final Log __log = LogFactory
			.getLog(ElementMappingUtil.class);
	private static final String PROPERTY = "property";

	private static Pair<Node, OBase> mapVariable(ACTIVITY activity,
			OActivity fragmentExit, OActivity fragmentEntry, String fromVar,
			String toVar) throws FragmentCompositionException {
		Variable from = ProcessElementFinder
				.findVariable(fragmentExit, fromVar);
		Variable to = ProcessElementFinder.findVariable(fragmentEntry, toVar);

		if (from != null && to != null) {
			VariableInstance rval = activity._scopeFrame.resolve(from);
			Node value = null;
			try {
				value = activity.fetchVariableData(rval, false);
				__log.debug("Value of Variable: " + fromVar);
				__log.debug(DOMUtils.domToString(value));
				if (value == null) {
					StringBuffer buffer = new StringBuffer();
					buffer.append("Variable '");
					buffer.append(fromVar);
					buffer.append("' is not initialized!");
					throw new FragmentCompositionException(buffer.toString());
				} else if (!from.type.toQName().equals(to.type.toQName())) {
					Mediator mediator = initMediator(activity);
					try {
						__log.debug("Mediation of Variable: " + fromVar);
						__log.debug("Variable value: ");
						__log.debug(DOMUtils.domToString(value));
						value = mediator.mediateVariable(from.type.toQName(),
								to.type.toQName(), value);
						__log.debug("Mediated to: ");
						__log.debug(DOMUtils.domToString(value));
					} catch (MediationException e) {
						throw new FragmentCompositionException(e.getMessage());
					}
				}
			} catch (FaultException e) {
				StringBuffer buffer = new StringBuffer();
				buffer.append("Variable '");
				buffer.append(fromVar);
				buffer.append("' is not initialized!");
				throw new FragmentCompositionException(buffer.toString());
			}
			return new Pair<Node, OBase>(value, to);
		}
		return null;
	}

	private static Mediator initMediator(ACTIVITY activity) {
		List<File> files = activity.getBpelRuntimeContext().getBpelProcess()
				.getConf().getFiles();
		File ddFile = WsdlXsdMerger.findDeploymentDescriptorFile(files);
		ddFile = ddFile.getParentFile();
		ddFile = ddFile.getParentFile();
		ddFile = ddFile.getParentFile();
		ddFile = ddFile.getParentFile();
		File xsltVar = new File(ddFile.getAbsolutePath(), "var_mediator.xslt");
		File xsltCSet = new File(ddFile.getAbsolutePath(), "cset_mediator.xslt");
		return new MediatorImpl(xsltVar, xsltCSet);
	}

	private static Pair<Node, OBase> mapPartnerLink(ACTIVITY activity,
			OActivity fragmentExit, OActivity fragmentEntry,
			String fromPartnerLinkName, String toPartnerLinkName)
			throws FragmentCompositionException {
		OPartnerLink from = ProcessElementFinder.findPartnerLink(fragmentExit,
				fromPartnerLinkName);
		OPartnerLink to = ProcessElementFinder.findPartnerLink(fragmentEntry,
				toPartnerLinkName);

		if (from != null && to != null) {
			PartnerLinkInstance plval = activity._scopeFrame.resolve(from);
			Node partnerRoleVal = null;
			try {
				if (from.partnerRoleName != null) {
					partnerRoleVal = activity.getBpelRuntimeContext()
							.fetchPartnerRoleEndpointReferenceData(plval);
					__log.debug("Value of PartnerLink: " + fromPartnerLinkName);
					__log.debug(DOMUtils.domToString(partnerRoleVal));
				} else {
					StringBuffer buffer = new StringBuffer();
					buffer.append("PartnerRole of PartnerLink '");
					buffer.append(fromPartnerLinkName);
					buffer.append("' is not defined");
					throw new FragmentCompositionException(buffer.toString());
				}
			} catch (FaultException e) {
				StringBuffer buffer = new StringBuffer();
				buffer.append("Could not read PartnerLink value with name '");
				buffer.append(fromPartnerLinkName);
				buffer.append("'!");
				throw new FragmentCompositionException(buffer.toString());
			}
			return new Pair<Node, OBase>(partnerRoleVal, to);
		}
		return null;
	}

	private static Pair<Node, OBase> mapCorrelationSet(ACTIVITY activity,
			OActivity fragmentExit, OActivity fragmentEntry,
			String fromCorrelationSetName, String toCorrelationSetName)
			throws FragmentCompositionException {
		OScope.CorrelationSet from = ProcessElementFinder.findCorrelationSet(
				fragmentExit, fromCorrelationSetName);
		OScope.CorrelationSet to = ProcessElementFinder.findCorrelationSet(
				fragmentEntry, toCorrelationSetName);

		if (from != null && to != null) {
			CorrelationSetInstance plval = activity._scopeFrame.resolve(from);
			CorrelationKey key = null;
			Node result = null;
			Document doc;
			try {
				doc = XMLUtils.newDocument();
				key = activity.getBpelRuntimeContext().readCorrelation(plval);
				result = doc.createElement("CorrelationSet");
				doc.appendChild(result);
				if (key == null) {
					StringBuffer buffer = new StringBuffer();
					buffer.append("CorrelationSet '");
					buffer.append(fromCorrelationSetName);
					buffer.append("' is not initialized!");
					throw new FragmentCompositionException(buffer.toString());
				} else {
					String[] values = key.getValues();
					List<OProcess.OProperty> properties = from.properties;
					for (int i = 0; i < values.length; i++) {
						Element element = doc.createElement(PROPERTY);
						element.setTextContent(values[i]);

						QName propName = properties.get(i).name;
						element.setAttribute("name",
								"q0:" + propName.getLocalPart());
						element.setAttribute("xmlns:q0",
								propName.getNamespaceURI());
						result.appendChild(element);
					}
				}
			} catch (ParserConfigurationException e) {
				throw new FragmentCompositionException(
						"Could not serialize CorrelationSet data to XML!");
			}
			Mediator mediator = initMediator(activity);
			QName fromProcessType = fragmentExit.getOwner().getQName();
			QName toProcessType = fragmentEntry.getOldOwner().getQName();

			CSetMediationInfo fromInfo = new CSetMediationInfo(fromProcessType,
					from.declaringScope.name, from.name);
			CSetMediationInfo toInfo = new CSetMediationInfo(toProcessType,
					to.declaringScope.name, to.name);
			try {
				__log.debug("Mediation of CorrelationSet: "
						+ fromCorrelationSetName);
				__log.debug("CorrelationSet value: ");
				__log.debug(DOMUtils.domToString(result));
				result = mediator.mediateCorrelationSet(fromInfo, toInfo,
						result);
				__log.debug("Mediated to: ");
				__log.debug(DOMUtils.domToString(result));
			} catch (MediationException e) {
				throw new FragmentCompositionException(e.getMessage());
			}

			return new Pair<Node, OBase>(result, to);
		}
		return null;
	}

	public static String[] extractCorrelationSetValues(Element root) {
		NodeList list = root.getElementsByTagName(PROPERTY);
		String[] values = new String[list.getLength()];
		for (int i = 0; i < list.getLength(); i++) {
			values[i] = list.item(i).getTextContent();
		}

		return values;
	}

	private static void mappingOk(OActivity fragmentExit,
			OActivity fragmentEntry, Mapping[] mappings)
			throws FragmentCompositionException {
		List<Variable> varsToMap = new ArrayList<Variable>();
		List<OPartnerLink> plinksToMap = new ArrayList<OPartnerLink>();
		List<CorrelationSet> corrsetsToMap = new ArrayList<CorrelationSet>();
		if (fragmentEntry instanceof OFragmentEntry) {
			OFragmentEntry entry = (OFragmentEntry) fragmentEntry;
			varsToMap.addAll(entry.variablesToMap);
			plinksToMap.addAll(entry.partnerLinksToMap);
			corrsetsToMap.addAll(entry.correlationSetsToMap);
		} else if (fragmentEntry instanceof OFragmentRegion) {
			OFragmentRegion entry = (OFragmentRegion) fragmentEntry;
			varsToMap.addAll(entry.variablesToMap);
			plinksToMap.addAll(entry.partnerLinksToMap);
			corrsetsToMap.addAll(entry.correlationSetsToMap);
		}

		StringBuffer buffer = new StringBuffer();

		for (Mapping map : mappings) {
			if (map.getType().equals(Mapping.ElementType.VARIABLE)) {
				Variable from = ProcessElementFinder.findVariable(fragmentExit,
						map.getFromVar());
				Variable to = ProcessElementFinder.findVariable(fragmentEntry,
						map.getToVar());
				if (from != null && to != null) {
					varsToMap.remove(to);
				}
				buffer.append(getNotMappedString(from, to, map.getFromVar(),
						map.getToVar(), fragmentExit.name, fragmentEntry.name,
						"Variable"));
			} else if (map.getType().equals(Mapping.ElementType.PARTNER_LINK)) {
				OPartnerLink from = ProcessElementFinder.findPartnerLink(
						fragmentExit, map.getFromVar());
				OPartnerLink to = ProcessElementFinder.findPartnerLink(
						fragmentEntry, map.getToVar());
				if (from != null && to != null) {
					plinksToMap.remove(to);
				}
				buffer.append(getNotMappedString(from, to, map.getFromVar(),
						map.getToVar(), fragmentExit.name, fragmentEntry.name,
						"PartnerLink"));
			} else if (map.getType()
					.equals(Mapping.ElementType.CORRELATION_SET)) {
				CorrelationSet from = ProcessElementFinder.findCorrelationSet(
						fragmentExit, map.getFromVar());
				CorrelationSet to = ProcessElementFinder.findCorrelationSet(
						fragmentEntry, map.getToVar());
				if (from != null && to != null) {
					corrsetsToMap.remove(to);
				}
				buffer.append(getNotMappedString(from, to, map.getFromVar(),
						map.getToVar(), fragmentExit.name, fragmentEntry.name,
						"CorrelationSet"));
			}
		}

		if (varsToMap.size() > 0) {

			buffer.append("Following variables could not be mapped: ");
			Iterator<Variable> iterator = varsToMap.iterator();
			while (iterator.hasNext()) {
				buffer.append(iterator.next().name);
				if (iterator.hasNext()) {
					buffer.append(", ");
				}
			}
			buffer.append(". ");
		}
		if (plinksToMap.size() > 0) {

			buffer.append("Following PartnerLinks could not be mapped: ");
			Iterator<OPartnerLink> iterator = plinksToMap.iterator();
			while (iterator.hasNext()) {
				buffer.append(iterator.next().name);
				if (iterator.hasNext()) {
					buffer.append(", ");
				}
			}
			buffer.append(". ");
		}
		if (corrsetsToMap.size() > 0) {

			buffer.append("Following CorrelationSEts could not be mapped: ");
			Iterator<CorrelationSet> iterator = corrsetsToMap.iterator();
			while (iterator.hasNext()) {
				buffer.append(iterator.next().name);
				if (iterator.hasNext()) {
					buffer.append(", ");
				}
			}
			buffer.append(". ");
		}

		if (buffer.toString().length() > 0) {
			throw new FragmentCompositionException(buffer.toString());
		}

	}

	private static String getNotMappedString(Object from, Object to,
			String fromName, String toName, String exitName, String entryName,
			String elementType) {
		StringBuffer buffer = new StringBuffer();
		if (from == null) {
			buffer.append(elementType);
			buffer.append(fromName);
			buffer.append(" is not visible from FragmentExit with name: ");
			buffer.append(exitName);
			buffer.append(". ");
		}
		if (to == null) {
			buffer.append(elementType);
			buffer.append(toName);
			buffer.append(" is not visible from FragmentEntry with name: ");
			buffer.append(entryName);
			buffer.append(". ");
		}
		return buffer.toString();
	}

	private static void checkEntryExitInsideRegion(ACTIVITY activity,
			int fragmentExitId, int fragmentEntryId)
			throws FragmentCompositionException {
		OBase exit = activity._self.o.getOwner().getChild(fragmentExitId);
		OBase entry = activity._self.o.getOwner().getChild(fragmentEntryId);
		if (exit instanceof OFragmentExit && entry instanceof OFragmentRegion) {
			OActivity oExit = (OActivity) exit;
			OActivity oEntry = (OActivity) entry;
			if (!FragmentCompositionUtil.isInside(oEntry, oExit)) {
				throw new FragmentCompositionException(
						"frg:fragmentExit is not inside of frg:fragmentRegion!");
			}
		} else if (exit instanceof OFragmentRegion
				&& entry instanceof OFragmentEntry) {
			OActivity oExit = (OActivity) exit;
			OActivity oEntry = (OActivity) entry;
			if (!FragmentCompositionUtil.isInside(oExit, oEntry)) {
				throw new FragmentCompositionException(
						"frg:fragmentEntry is not inside of frg:fragmentRegion!");
			}
		}
	}

	public static void wireAndMap(ACTIVITY activity, int fragmentExitId,
			int fragmentEntryId, Mapping[] mappings)
			throws FragmentCompositionException {
		__log.debug("wireAndMap(Activity: " + activity.toString()
				+ ", exitId: " + fragmentExitId + ", entryId: "
				+ fragmentEntryId);
		OProcess process = activity._self.o.getOwner();

		ArrayList<Pair<Node, OBase>> pairs = new ArrayList<Pair<Node, OBase>>();

		checkEntryExitInsideRegion(activity, fragmentExitId, fragmentEntryId);

		OActivity fragmentEntry = null;
		OActivity fragmentExit = null;
		OBase found = process.getChild(fragmentEntryId);
		if (found != null
				&& (found instanceof OFragmentEntry || found instanceof OFragmentRegion)) {
			fragmentEntry = (OActivity) found;
		}

		found = process.getChild(fragmentExitId);
		if (found != null
				&& (found instanceof OFragmentExit || found instanceof OFragmentRegion)) {
			fragmentExit = (OActivity) found;
		}

		if (fragmentEntry != null && fragmentExit != null) {

			mappingOk(fragmentExit, fragmentEntry, mappings);

			for (Mapping map : mappings) {
				Pair<Node, OBase> pair = null;
				if (map.getType().equals(ElementType.VARIABLE)) {
					pair = ElementMappingUtil.mapVariable(activity,
							fragmentExit, fragmentEntry, map.getFromVar(),
							map.getToVar());
					__log.debug("Stored value of Variable: " + map.getFromVar());

				} else if (map.getType().equals(ElementType.PARTNER_LINK)) {
					pair = ElementMappingUtil.mapPartnerLink(activity,
							fragmentExit, fragmentEntry, map.getFromVar(),
							map.getToVar());
					__log.debug("Stored value of PartnerLink: "
							+ map.getFromVar());
				} else if (map.getType().equals(ElementType.CORRELATION_SET)) {
					pair = ElementMappingUtil.mapCorrelationSet(activity,
							fragmentExit, fragmentEntry, map.getFromVar(),
							map.getToVar());
					__log.debug("Stored value of CorrelationSet: "
							+ map.getFromVar());
				}
				if (pair != null) {
					pairs.add(pair);
				}
			}
			// mapping.map(fragmentEntryId, pairs);

			List<MappingInfo> infos = mappingToInfo(pairs);
			Long instanceId = activity.getBpelRuntimeContext().getPid();
			FCManagementDAO fcManagementDAO = activity.getBpelRuntimeContext()
					.getBpelProcess().getEngine().getFCManagementDAO();

			fcManagementDAO.mapElements(instanceId, fragmentEntryId, infos);

			if (fragmentExit instanceof OFragmentExit) {
				OFragmentExit exit = (OFragmentExit) fragmentExit;
				exit.danglingExit = false;
				exit.ignoredExit = false;
				exit.fragmentEntryId = fragmentEntryId;
				exit.mappings = mappings;
			} else if (fragmentExit instanceof OFragmentRegion) {
				OFragmentRegion region = (OFragmentRegion) fragmentExit;
				region.danglingExit = false;
				region.fragmentEntryId = fragmentEntryId;
				region.mappings = mappings;
			}

			if (fragmentEntry instanceof OFragmentEntry) {
				((OFragmentEntry) fragmentEntry).danglingEntry = false;
			} else if (fragmentEntry instanceof OFragmentRegion) {
				OFragmentRegion region = (OFragmentRegion) fragmentEntry;
				if (region.danglingEntry) {
					region.danglingEntry = false;
					process.incrementGluedFragmentsCount();
				}

			}
		} else {
			StringBuffer buffer = new StringBuffer();
			if (fragmentExit == null) {
				buffer.append("FragmentExit with id ");
				buffer.append(fragmentExitId);
				buffer.append(" not found. ");
			}
			if (fragmentEntry == null) {
				buffer.append("FragmentEntry with id ");
				buffer.append(fragmentEntryId);
				buffer.append(" not found.");
			}

			throw new FragmentCompositionException(buffer.toString());

		}

	}

	public static List<MappingInfo> mappingToInfo(
			List<Pair<Node, OBase>> mappings) {
		ArrayList<MappingInfo> infos = new ArrayList<MappingInfo>();
		for (Pair<Node, OBase> mapping : mappings) {
			String data = null;
			if (mapping.getKey() != null) {
				data = DOMUtils.domToString(mapping.getKey());
			}
			MappingInfo info = new MappingInfo(mapping.getValue().getId(), data);
			infos.add(info);
		}
		return infos;
	}

	public static List<Pair<Element, OBase>> infoToMapping(
			List<MappingInfo> infos, OProcess process) {
		ArrayList<Pair<Element, OBase>> result = new ArrayList<Pair<Element, OBase>>();
		for (MappingInfo info : infos) {
			try {
				Element element = null;
				if (info.getMappingData() != null) {
					element = DOMUtils.stringToDOM(info.getMappingData());
				}
				OBase base = process.getChild(info.getVariableId());
				Pair<Element, OBase> mapping = new Pair<Element, OBase>(
						element, base);
				result.add(mapping);
			} catch (SAXException e) {
				__log.error(e);
			} catch (IOException e) {
				__log.error(e);
			}
		}
		return result;
	}

	public static void executeMapping(ACTIVITY activity) {
		__log.debug("AssignMappingValues, Activity: " + activity.toString());
		Long instanceId = activity.getBpelRuntimeContext().getPid();
		FCManagementDAO fcManagementDAO = activity.getBpelRuntimeContext()
				.getBpelProcess().getEngine().getFCManagementDAO();
		List<MappingInfo> mappingInfos = fcManagementDAO.getElementMapping(
				instanceId, activity._self.o.getId());
		List<Pair<Element, OBase>> mappings = ElementMappingUtil.infoToMapping(
				mappingInfos, activity._self.o.getOwner());

		for (Pair<Element, ? extends OBase> pair : mappings) {
			if (pair.getKey() != null && pair.getValue() != null) {
				if (pair.getValue() instanceof OScope.Variable) {
					__log.debug("Set value for variable: "
							+ ((OScope.Variable) pair.getValue()).name);
					VariableInstance var = activity._scopeFrame
							.resolve((OScope.Variable) pair.getValue());
					try {
						activity.initializeVariable(var, pair.getKey());
					} catch (ExternalVariableModuleException e) {
						e.printStackTrace();
					}
				} else if (pair.getValue() instanceof OPartnerLink) {
					OPartnerLink pLink = (OPartnerLink)pair.getValue();
					__log.debug("Set EPR for PartnerLink: "
							+ ((OPartnerLink) pair.getValue()).name);
					PartnerLinkInstance pInstance = activity._scopeFrame
							.resolve((OPartnerLink) pair.getValue());

					try {
						activity.getBpelRuntimeContext()
								.writeEndpointReference(pInstance,
										pair.getKey());
						
						//@author sonntamo
						PartnerLinkModificationEvent plme = new PartnerLinkModificationEvent(
								pLink.name,
								pLink.getXpath(),
								pair.getKey(),
								activity._self.o.getXpath(),
								activity._scopeFrame.oscope.getXpath(),
								activity._scopeFrame.scopeInstanceId);
						activity._scopeFrame.fillEventInfo(plme);
						activity.getBpelRuntimeContext().sendEvent(plme);
					} catch (FaultException e) {
						__log.error(e);
					}
				} else if (pair.getValue() instanceof CorrelationSet) {
					__log.debug("Set value of CorrelationSet: "
							+ ((CorrelationSet) pair.getValue()).name);
					CorrelationSetInstance cset = activity._scopeFrame
							.resolve((CorrelationSet) pair.getValue());
					String[] propValues = ElementMappingUtil
							.extractCorrelationSetValues(pair.getKey());

					CorrelationKey ckeyVal = new CorrelationKey(
							cset.declaration.name, propValues);
					activity.getBpelRuntimeContext().writeCorrelation(cset,
							ckeyVal, false, activity._self.o.getXpath());
				}

			}
		}
		fcManagementDAO.removeMappings(instanceId, activity._self.o.getId());
		__log.debug("Mappings removed from DB.");
	}
}
