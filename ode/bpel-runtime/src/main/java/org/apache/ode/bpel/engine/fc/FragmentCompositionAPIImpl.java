package org.apache.ode.bpel.engine.fc;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.imageio.ImageIO;
import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.geronimo.mail.util.Base64Encoder;
import org.apache.ode.bpel.common.ProcessState;
import org.apache.ode.bpel.dao.ProcessInstanceDAO;
import org.apache.ode.bpel.engine.BpelProcess;
import org.apache.ode.bpel.engine.FragmentCompositionEventBroker;
import org.apache.ode.bpel.engine.FragmentCompositionResponse;
import org.apache.ode.bpel.engine.fc.excp.FragmentCompositionException;
import org.apache.ode.bpel.engine.fc.excp.InstanceNotFoundException;
import org.apache.ode.bpel.fcapi.ActivityListDocument;
import org.apache.ode.bpel.fcapi.MappingListDocument;
import org.apache.ode.bpel.fcapi.StringListDocument;
import org.apache.ode.bpel.fcapi.TActivityInfo;
import org.apache.ode.bpel.fcapi.TActivityInfoList;
import org.apache.ode.bpel.fcapi.TMappingInfo;
import org.apache.ode.bpel.fcapi.TMappingInfoList;
import org.apache.ode.bpel.fcapi.TStringList;
import org.apache.ode.bpel.fcapi.TVariableInfo;
import org.apache.ode.bpel.fcapi.TVariableInfoList;
import org.apache.ode.bpel.fcapi.VariableInfoListDocument;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OConstantVarType;
import org.apache.ode.bpel.o.OElementVarType;
import org.apache.ode.bpel.o.OFragmentEntry;
import org.apache.ode.bpel.o.OFragmentExit;
import org.apache.ode.bpel.o.OFragmentRegion;
import org.apache.ode.bpel.o.OMessageVarType;
import org.apache.ode.bpel.o.OPartnerLink;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OXsdTypeVarType;
import org.apache.ode.bpel.o.OScope.CorrelationSet;
import org.apache.ode.bpel.o.OScope.Variable;
import org.apache.ode.bpel.runtime.BpelRuntimeContext;
import org.apache.ode.bpel.runtime.channels.FragmentCompositionChannel;
import org.apache.ode.bpel.util.fc.FragmentCompositionUtil;
import org.apache.ode.fc.dao.FCManagementDAO;
import org.apache.ode.fcanalyzer.CorrelationSetNameComparator;
import org.apache.ode.fcanalyzer.FragmentEntryExitFinder;
import org.apache.ode.fcanalyzer.PartnerLinkNameComparator;
import org.apache.ode.fcanalyzer.ProcessElementFinder;
import org.apache.ode.fcanalyzer.VariableNameComparator;
import org.apache.ode.jacob.JacobRunnable;
import org.apache.ode.jacob.vpu.JacobVPU;
import org.apache.ode.renderer.ProcessRenderer;
import org.apache.ode.utils.fc.Mapping;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class FragmentCompositionAPIImpl implements FragmentCompositionAPI {
	private static final Log __log = LogFactory
			.getLog(FragmentCompositionAPIImpl.class);
	private BpelProcess process;
	private final ProcessInstanceDAO procInstance;
	private FragmentCompositionEventBroker broker;
	private FragmentCompositionResponse response;

	public FragmentCompositionAPIImpl(BpelProcess process,
			final ProcessInstanceDAO procInstance) {
		broker = process.getEngine().getFragmentCompositionEventBroker();
		this.process = process;
		this.procInstance = procInstance;
	}

	private VariableInfoListDocument variablesToDocument(List<Variable> vars) {
		VariableInfoListDocument doc = VariableInfoListDocument.Factory
				.newInstance();
		TVariableInfoList list = doc.addNewVariableInfoList();
		for (Variable var : vars) {
			QName type = null;
			if (var.type instanceof OElementVarType) {
				type = ((OElementVarType) var.type).elementType;
			} else if (var.type instanceof OMessageVarType) {
				type = ((OMessageVarType) var.type).messageType;
			} else if (var.type instanceof OXsdTypeVarType) {
				type = ((OXsdTypeVarType) var.type).xsdType;
			} else if (var.type instanceof OConstantVarType) {
				type = new QName("http://www.w3.org/2001/XMLSchema", "string");
			}

			if (type != null) {
				TVariableInfo info = list.addNewVariableInfo();
				info.setName(var.name);
				info.setType(type);
			}
		}
		return doc;
	}

	public VariableInfoListDocument getAvailableVariables(Long instanceId,
			int elementId) throws FragmentCompositionException,
			InstanceNotFoundException {
		__log.debug("getAvailableVariables(instanceId: " + instanceId
				+ ", elementId: " + elementId + ")");
		OBase temp = process.getOProcess().getChild(elementId);
		if (temp != null
				&& (temp instanceof OFragmentEntry
						|| temp instanceof OFragmentExit || temp instanceof OFragmentRegion)) {
			OActivity act = (OActivity) temp;

			if (act != null) {
				List<Variable> vars = ProcessElementFinder
						.getVisibleVariables(act);
				Collections.sort(vars, new VariableNameComparator());
				VariableInfoListDocument doc = variablesToDocument(vars);

				response.returnValue(doc);
			} else {
				StringBuffer buffer = new StringBuffer();
				buffer.append("No FragmentExit no FragmentEntry and no FragmentRegion with the id \"");
				buffer.append(elementId);
				buffer.append("\" found!");
				response.throwException(new FragmentCompositionException(buffer
						.toString()));
			}
		}

		return null;
	}

	private void fillActivityList(TActivityInfoList list, List<Integer> ids) {
		for (Integer id : ids) {
			OBase element = process.getOProcess().getChild(id);
			if (element != null && element instanceof OActivity) {
				TActivityInfo info = list.addNewActivityInfo();
				info.setId(id);
				info.setName(((OActivity) element).name);
			}
		}
	}

	public ActivityListDocument getDanglingEntries(Long instanceId)
			throws InstanceNotFoundException {
		__log.debug("getDanglingEntries(instanceId: " + instanceId + ")");
		List<Integer> entries = FragmentEntryExitFinder
				.findDanglingEntries(process.getOProcess());
		List<Integer> regions = FragmentEntryExitFinder
				.findRegionsToMapBack(process.getOProcess());
		entries.addAll(regions);

		ActivityListDocument ret = ActivityListDocument.Factory.newInstance();
		TActivityInfoList list = ret.addNewActivityList();
		fillActivityList(list, entries);

		response.returnValue(ret);
		return null;
	}

	public ActivityListDocument getDanglingExits(Long instanceId)
			throws InstanceNotFoundException {
		__log.debug("getDanglingExits(instanceId: " + instanceId + ")");
		// List<Integer> exits =
		// FragmentEntryExitFinder.findDanglingExits(process.getOProcess());
		ArrayList<Integer> result = new ArrayList<Integer>();
		List<Integer> regions = FragmentEntryExitFinder
				.findRegionsToMapForward(process.getOProcess());
		List<Integer> exits = process.getEngine().getFCManagementDAO()
				.getActiveFragmentExits(instanceId);
		result.addAll(exits);
		result.addAll(regions);

		ActivityListDocument ret = ActivityListDocument.Factory.newInstance();
		TActivityInfoList list = ret.addNewActivityList();
		fillActivityList(list, result);

		response.returnValue(ret);
		return null;
	}

	public ActivityListDocument getIgnorableExits(Long instanceId)
			throws InstanceNotFoundException {
		__log.debug("getIgnorableExits(instanceId: " + instanceId + ")");
		List<Integer> exits = process.getEngine().getFCManagementDAO()
				.getActiveFragmentExits(instanceId);

		ActivityListDocument ret = ActivityListDocument.Factory.newInstance();
		TActivityInfoList list = ret.addNewActivityList();
		fillActivityList(list, exits);

		response.returnValue(ret);
		return null;

	}

	public ActivityListDocument getFragmentContainers(Long instanceId)
			throws InstanceNotFoundException {
		__log.debug("getFragmentContainers(instanceId: " + instanceId + ")");
		ActivityListDocument ret = ActivityListDocument.Factory.newInstance();
		TActivityInfoList list = ret.addNewActivityList();

		if (FragmentCompositionUtil.isGlueAllowed(process.getOProcess())) {
			List<Integer> activeFlows = process.getEngine()
					.getFCManagementDAO()
					.getActiveFragmentContainers(instanceId);
			List<Integer> flows = FragmentEntryExitFinder
					.findElementsToGlue(process.getOProcess());

			// intersect the activeFlows and flows
			HashSet<Integer> activeSet = new HashSet<Integer>(activeFlows);
			ArrayList<Integer> result = new ArrayList<Integer>();
			for (Integer current : flows) {
				if (activeSet.contains(current)) {
					result.add(current);
				}
			}

			fillActivityList(list, result);
		}
		response.returnValue(ret);
		return null;
	}

	public byte[] getProcessImage(Long instanceId)
			throws InstanceNotFoundException {
		__log.debug("getProcessImage(instanceId: " + instanceId + ")");
		ProcessRenderer renderer = new ProcessRenderer(process.getOProcess());
		BufferedImage image = renderer.render();
		try {

			ByteArrayOutputStream out = new ByteArrayOutputStream();

			ImageIO.write(image, "png", out);

			byte[] notEncoded = out.toByteArray();

			ByteArrayOutputStream encoded = new ByteArrayOutputStream();
			Base64Encoder encoder = new Base64Encoder();
			encoder.encode(notEncoded, 0, notEncoded.length, encoded);
			response.returnValue(encoded.toByteArray());
		} catch (IOException e) {
			__log.error(e);
		}
		return null;
	}

	public boolean glue(Long instanceId, int containerId, String newFragmentName)
			throws FragmentCompositionException, InstanceNotFoundException {
		__log.debug("glue(instanceId: " + instanceId + ", containerId: "
				+ containerId + ", fragmentName" + newFragmentName + ")");
		if (procInstance.getState() == ProcessState.STATE_ACTIVE) {
			final QName newFragment = QName.valueOf(newFragmentName);
			final int tmpContainerId = containerId;
			final Long tempInstanceId = instanceId;
			JacobRunnable runnable = new JacobRunnable() {
				private BpelRuntimeContext getRuntimeContext() {
					return (BpelRuntimeContext) JacobVPU.activeJacobThread()
							.getExtension(BpelRuntimeContext.class);
				}

				@Override
				public void run() {

					BpelProcess process = getRuntimeContext().getBpelProcess();
					short instanceState = process.getProcessDAO()
							.getInstance(tempInstanceId).getState();
					if (instanceState == ProcessState.STATE_ACTIVE) {
						FCManagementDAO dao = process.getEngine()
								.getFCManagementDAO();
						String channel = dao.getChannel(tempInstanceId,
								tmpContainerId);
						if (channel != null) {
							FragmentCompositionChannel imported = importChannel(
									channel, FragmentCompositionChannel.class);
							if (imported != null) {
								imported.glue(newFragment, response);
							} else {
								response.throwException(new FragmentCompositionException(
										"Could not import FragmentCompositionChannel"));
							}
						} else {
							response.throwException(new FragmentCompositionException(
									"Could not find FragmentCompositionChannel"));
						}
					}
				}

			};

			broker.continueExecution(process, procInstance, runnable);
		} else {
			response.throwException(new FragmentCompositionException(
					"Process instance is not active!"));
		}
		return true;
	}

	private Mapping[] extractMappings(MappingListDocument doc) {
		TMappingInfoList list = doc.getMappingList();
		Mapping mappings[] = new Mapping[list.sizeOfMappingInfoArray()];
		for (int i = 0; i < list.sizeOfMappingInfoArray(); i++) {
			TMappingInfo info = list.getMappingInfoArray(i);
			TMappingInfo.ElementType.Enum type = info.getElementType();
			Mapping.ElementType eType;
			switch (type.intValue()) {
			case TMappingInfo.ElementType.INT_PARTNERLINK:
				eType = Mapping.ElementType.PARTNER_LINK;
				break;
			case TMappingInfo.ElementType.INT_CORRELATIONSET:
				eType = Mapping.ElementType.CORRELATION_SET;
				break;
			default:
				eType = Mapping.ElementType.VARIABLE;
			}

			mappings[i] = new Mapping(eType, info.getFromElementName(),
					info.getToElementName());
		}
		return mappings;
	}

	public boolean wireAndMap(Long instanceId, int fragmentExitId,
			int fragmentEntryId, MappingListDocument mappings)
			throws FragmentCompositionException, InstanceNotFoundException {
		__log.debug("wireAndMap(instanceId: " + instanceId
				+ ", fragmentExitId: " + fragmentExitId + ", fragmentEntryId: "
				+ fragmentEntryId + ")");
		if (procInstance.getState() == ProcessState.STATE_ACTIVE) {
			final int containerId = FragmentCompositionUtil
					.findEnclosingFragmentContainer(process.getOProcess(),
							fragmentExitId, fragmentEntryId);
			if (containerId != -1) {

				final int tempExit = fragmentExitId;
				final int tempEntry = fragmentEntryId;
				final Mapping[] tempMappings = extractMappings(mappings);
				final Long tempInstanceId = instanceId;

				// Runnable onSuccess = new
				// CleanUpChannelsTask(process.getOProcess(),
				// process.getEngine(), procInstance.getInstanceId());
				// response.setOnSuccessAction(onSuccess);

				JacobRunnable runnable = new JacobRunnable() {

					private BpelRuntimeContext getRuntimeContext() {
						return (BpelRuntimeContext) JacobVPU
								.activeJacobThread().getExtension(
										BpelRuntimeContext.class);
					}

					@Override
					public void run() {

						BpelProcess process = getRuntimeContext()
								.getBpelProcess();
						short instanceState = process.getProcessDAO()
								.getInstance(tempInstanceId).getState();
						if (instanceState == ProcessState.STATE_ACTIVE) {
							FCManagementDAO dao = process.getEngine()
									.getFCManagementDAO();
							String channel = dao.getChannel(tempInstanceId,
									containerId);
							if (channel != null) {
								FragmentCompositionChannel imported = importChannel(
										channel,
										FragmentCompositionChannel.class);
								if (imported != null) {
									imported.wireAndMap(tempExit, tempEntry,
											tempMappings, response);
								} else {
									response.throwException(new FragmentCompositionException(
											"Could not import FragmentCompositionChannel"));
								}
							} else {
								response.throwException(new FragmentCompositionException(
										"Could not find FragmentCompositionChannel"));
							}
						}
					}
				};

				broker.continueExecution(process, procInstance, runnable);
			} else {
				throw new FragmentCompositionException(
						"Could not find the FragmentFlowCompositionChannel");
			}
		} else {
			response.throwException(new FragmentCompositionException(
					"Process instance is not active!"));
		}
		return true;
	}

	public void setFCResponseObject(FragmentCompositionResponse response) {
		this.response = response;
	}

	public VariableInfoListDocument getVariablesToMap(Long instanceId,
			int elementId) throws InstanceNotFoundException {
		__log.debug("getVariablesToMap(instanceId: " + instanceId
				+ ", elementId: " + elementId + ")");
		List<OScope.Variable> vars = ProcessElementFinder.getVariablesToMap(
				process.getOProcess(), elementId);

		VariableInfoListDocument doc = variablesToDocument(vars);

		response.returnValue(doc);
		return null;
	}

	public boolean ignoreFragmentExit(Long instanceId, int fragmentExitId)
			throws InstanceNotFoundException, FragmentCompositionException {
		__log.debug("ignoreFragmentExit(instanceId: " + instanceId
				+ ", fragmentExitId: " + fragmentExitId + ")");
		if (procInstance.getState() == ProcessState.STATE_ACTIVE) {
			final int containerId = FragmentCompositionUtil
					.findEnclosingFragmentContainer(process.getOProcess(),
							fragmentExitId);
			if (containerId != -1) {
				final int tempExit = fragmentExitId;
				final Long tempInstanceId = instanceId;
				// Runnable onSuccess = new
				// CleanUpChannelsTask(process.getOProcess(),
				// process.getEngine(), procInstance.getInstanceId());
				// response.setOnSuccessAction(onSuccess);

				JacobRunnable runnable = new JacobRunnable() {
					private BpelRuntimeContext getRuntimeContext() {
						return (BpelRuntimeContext) JacobVPU
								.activeJacobThread().getExtension(
										BpelRuntimeContext.class);
					}

					@Override
					public void run() {

						BpelProcess process = getRuntimeContext()
								.getBpelProcess();
						short instanceState = process.getProcessDAO()
								.getInstance(tempInstanceId).getState();
						if (instanceState == ProcessState.STATE_ACTIVE) {
							FCManagementDAO dao = process.getEngine()
									.getFCManagementDAO();
							String channel = dao.getChannel(tempInstanceId,
									containerId);
							if (channel != null) {
								FragmentCompositionChannel imported = importChannel(
										channel,
										FragmentCompositionChannel.class);
								if (imported != null) {
									imported.ignoreFragmentExit(tempExit,
											response);
								} else {
									response.throwException(new FragmentCompositionException(
											"Could not import FragmentCompositionChannel"));
								}
							} else {
								response.throwException(new FragmentCompositionException(
										"Could not find FragmentCompositionChannel"));
							}
						}
					}

				};

				broker.continueExecution(process, procInstance, runnable);
			} else {
				throw new FragmentCompositionException(
						"Could not find the FragmentFlowCompositionChannel");
			}
		} else {
			response.throwException(new FragmentCompositionException(
					"Process instance is not active!"));
		}
		return true;

	}

	public StringListDocument getAvailableCorrelationSets(Long instanceId,
			int elementId) throws FragmentCompositionException,
			InstanceNotFoundException {
		__log.debug("getAvailableCorrelationSets(instanceId: " + instanceId
				+ ", elementId: " + elementId + ")");
		OBase temp = process.getOProcess().getChild(elementId);
		if (temp != null
				&& (temp instanceof OFragmentEntry
						|| temp instanceof OFragmentExit || temp instanceof OFragmentRegion)) {
			OActivity act = (OActivity) temp;

			if (act != null) {
				List<CorrelationSet> sets = ProcessElementFinder
						.getVisibleCorrelationSets(act);
				Collections.sort(sets, new CorrelationSetNameComparator());
				StringListDocument ret = StringListDocument.Factory
						.newInstance();
				TStringList list = ret.addNewStringList();
				for (CorrelationSet set : sets) {
					list.addElement(set.name);
				}
				response.returnValue(ret);
			} else {
				StringBuffer buffer = new StringBuffer();
				buffer.append("No FragmentExit no FragmentEntry and no FragmentRegion with the id \"");
				buffer.append(elementId);
				buffer.append("\" found!");
				response.throwException(new FragmentCompositionException(buffer
						.toString()));
			}
		}
		return null;
	}

	public StringListDocument getAvailablePartnerLinks(Long instanceId,
			int elementId) throws FragmentCompositionException,
			InstanceNotFoundException {
		__log.debug("getAvailablePartnerLinks(instanceId: " + instanceId
				+ ", elementId: " + elementId + ")");
		OBase temp = process.getOProcess().getChild(elementId);
		if (temp != null
				&& (temp instanceof OFragmentEntry
						|| temp instanceof OFragmentExit || temp instanceof OFragmentRegion)) {
			OActivity act = (OActivity) temp;

			if (act != null) {
				List<OPartnerLink> links = ProcessElementFinder
						.getVisiblePartnerLinks(act);
				Collections.sort(links, new PartnerLinkNameComparator());
				StringListDocument ret = StringListDocument.Factory
						.newInstance();
				TStringList list = ret.addNewStringList();
				for (OPartnerLink link : links) {
					list.addElement(link.name);
				}
				response.returnValue(ret);
			} else {
				StringBuffer buffer = new StringBuffer();
				buffer.append("No FragmentExit no FragmentEntry and no FragmentRegion with the id \"");
				buffer.append(elementId);
				buffer.append("\" found!");
				response.throwException(new FragmentCompositionException(buffer
						.toString()));
			}
		}

		return null;
	}

	public StringListDocument getCorrelationSetsToMap(Long instanceId,
			int elementId) throws InstanceNotFoundException {
		__log.debug("getCorrelationSetsToMap(instanceId: " + instanceId
				+ ", elementId: " + elementId + ")");
		List<CorrelationSet> sets = ProcessElementFinder
				.getCorrelationSetsToMap(process.getOProcess(), elementId);

		StringListDocument ret = StringListDocument.Factory.newInstance();
		TStringList list = ret.addNewStringList();
		for (CorrelationSet set : sets) {
			list.addElement(set.name);
		}

		response.returnValue(list);
		return null;
	}

	public StringListDocument getPartnerLinksToMap(Long instanceId,
			int elementId) throws InstanceNotFoundException {
		__log.debug("getPartnerLinksToMap(instanceId: " + instanceId
				+ ", elementId: " + elementId + ")");
		List<OPartnerLink> links = ProcessElementFinder.getPartnerLinksToMap(
				process.getOProcess(), elementId);

		StringListDocument ret = StringListDocument.Factory.newInstance();
		TStringList list = ret.addNewStringList();
		for (OPartnerLink link : links) {
			list.addElement(link.name);
		}

		response.returnValue(list);
		return null;
	}

	public ActivityListDocument getIgnorableEntries(Long instanceId)
			throws InstanceNotFoundException {
		__log.debug("getIgnorableEntries(instanceId: " + instanceId + ")");
		List<Integer> entries = process.getEngine().getFCManagementDAO()
				.getActiveFragmentEntries(instanceId);

		ActivityListDocument ret = ActivityListDocument.Factory.newInstance();
		TActivityInfoList list = ret.addNewActivityList();
		fillActivityList(list, entries);

		response.returnValue(ret);
		return null;
	}

	public boolean ignoreFragmentEntry(Long instanceId, int fragmentEntryId)
			throws FragmentCompositionException, InstanceNotFoundException {
		__log.debug("ignoreFragmentEntry(instanceId: " + instanceId
				+ ", fragmentEntryId: " + fragmentEntryId + ")");
		if (procInstance.getState() == ProcessState.STATE_ACTIVE) {
			final int containerId = FragmentCompositionUtil
					.findEnclosingFragmentContainer(process.getOProcess(),
							fragmentEntryId);
			if (containerId != -1) {
				final int tempEntry = fragmentEntryId;
				final Long tempInstanceId = instanceId;
				// Runnable onSuccess = new
				// CleanUpChannelsTask(process.getOProcess(),
				// process.getEngine(), procInstance.getInstanceId());
				// response.setOnSuccessAction(onSuccess);

				JacobRunnable runnable = new JacobRunnable() {
					private BpelRuntimeContext getRuntimeContext() {
						return (BpelRuntimeContext) JacobVPU
								.activeJacobThread().getExtension(
										BpelRuntimeContext.class);
					}

					@Override
					public void run() {

						BpelProcess process = getRuntimeContext()
								.getBpelProcess();
						short instanceState = process.getProcessDAO()
								.getInstance(tempInstanceId).getState();
						if (instanceState == ProcessState.STATE_ACTIVE) {
							FCManagementDAO dao = process.getEngine()
									.getFCManagementDAO();
							String channel = dao.getChannel(tempInstanceId,
									containerId);
							if (channel != null) {
								FragmentCompositionChannel imported = importChannel(
										channel,
										FragmentCompositionChannel.class);
								if (imported != null) {
									imported.ignoreFragmentEntry(tempEntry,
											response);
								} else {
									response.throwException(new FragmentCompositionException(
											"Could not import FragmentCompositionChannel"));
								}
							} else {
								response.throwException(new FragmentCompositionException(
										"Could not find FragmentCompositionChannel"));
							}
						}
					}

				};

				broker.continueExecution(process, procInstance, runnable);
			} else {
				throw new FragmentCompositionException(
						"Could not find the FragmentFlowCompositionChannel");
			}
		} else {
			response.throwException(new FragmentCompositionException(
					"Process instance is not active!"));
		}
		return true;
	}

}
