package de.ustutt.simtech.extensions;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.wsdl.Operation;
import javax.xml.namespace.QName;
import javax.xml.transform.TransformerException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.common.CorrelationKey;
import org.apache.ode.bpel.common.CorrelationKeySet;
import org.apache.ode.bpel.common.FaultException;
import org.apache.ode.bpel.compiler.BpelCompiler;
import org.apache.ode.bpel.compiler.CommonCompilationMessages;
import org.apache.ode.bpel.compiler.api.CompilationException;
import org.apache.ode.bpel.compiler.bom.Correlation;
import org.apache.ode.bpel.compiler.bom.ReceiveActivity;
import org.apache.ode.bpel.compiler.modelMigration.ProcessModelChangeRegistry;
import org.apache.ode.bpel.evar.ExternalVariableModuleException;
import org.apache.ode.bpel.evt.PartnerLinkModificationEvent;
import org.apache.ode.bpel.explang.EvaluationContext;
import org.apache.ode.bpel.extension.ExtensibleElement;
import org.apache.ode.bpel.extension.ExtensionValidator;
import org.apache.ode.bpel.extensions.events.ActivityComplete;
import org.apache.ode.bpel.extensions.events.ActivityExecuted;
import org.apache.ode.bpel.extensions.events.ActivityTerminated;
import org.apache.ode.bpel.iapi.BpelEngineException;
import org.apache.ode.bpel.o.OElementVarType;
import org.apache.ode.bpel.o.OMessageVarType;
import org.apache.ode.bpel.o.OMessageVarType.Part;
import org.apache.ode.bpel.o.OPartnerLink;
import org.apache.ode.bpel.o.OPickReceive;
import org.apache.ode.bpel.o.OProcess;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OScope.Variable;
import org.apache.ode.bpel.runtime.ActivityInfo;
import org.apache.ode.bpel.runtime.BpelJacobRunnable;
import org.apache.ode.bpel.runtime.BpelRuntimeContext;
import org.apache.ode.bpel.runtime.CompensationHandler;
import org.apache.ode.bpel.runtime.ExprEvaluationContextImpl;
import org.apache.ode.bpel.runtime.ExtensionContextImpl;
import org.apache.ode.bpel.runtime.InvalidContextException;
import org.apache.ode.bpel.runtime.InvalidProcessException;
import org.apache.ode.bpel.runtime.LinkFrame;
import org.apache.ode.bpel.runtime.PartnerLinkInstance;
import org.apache.ode.bpel.runtime.ScopeFrame;
import org.apache.ode.bpel.runtime.Selector;
import org.apache.ode.bpel.runtime.VariableInstance;
import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannelListener;
import org.apache.ode.bpel.runtime.channels.PickResponseChannel;
import org.apache.ode.bpel.runtime.channels.PickResponseChannelListener;
import org.apache.ode.bpel.runtime.channels.TerminationChannelListener;
import org.apache.ode.bpel.runtime.common.extension.AbstractAsyncExtensionOperation;
import org.apache.ode.bpel.runtime.common.extension.ExtensionContext;
import org.apache.ode.jacob.Channel;
import org.apache.ode.jacob.JacobThread;
import org.apache.ode.jacob.vpu.JacobVPU;
import org.apache.ode.utils.DOMUtils;
import org.apache.ode.utils.msg.MessageBundle;
import org.apache.ode.utils.stl.CollectionsX;
import org.apache.ode.utils.stl.MemberOfFunction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class SimulationStartActivity extends AbstractAsyncExtensionOperation
		implements ExtensionValidator {

	private static final Log __log = LogFactory
			.getLog(SimulationStartActivity.class);

	private ExtensionContext _context = null;
	private BpelRuntimeContext _runtime = null;
	private JacobThread _jacob = null;
	private ScopeFrame _scopeFrame = null;
	private ActivityInfo _self = null;
	private LinkFrame _linkFrame = null;

	// @hahnml: Id to identify the JacobObject with it's OBase object id
	private Integer oId = 0;

	private OProcess _oprocess = null;

	public FaultData _fault;
	protected Boolean _terminatedActivity;

	private OPickReceive.OnMessage _onMessage;
	private boolean _createInstance = true;
	protected static final CommonCompilationMessages __cmsgsGeneral = MessageBundle
			.getMessages(CommonCompilationMessages.class);

	// if multiple alarms are set, this is the alarm the evaluates to
	// the shortest absolute time until firing.
	private OPickReceive.OnAlarm _alarm = null;

	public void validate(Object context, ExtensibleElement extensionAct) {
		Element activity = extensionAct.getNestedElement();

		if (context instanceof BpelCompiler) {
			BpelCompiler compiler = (BpelCompiler) context;

			// Read the createInstance attribute value
			String crtInst = activity.getAttribute("createInstance");
			boolean createInstance = false;
			if (crtInst != null && !crtInst.isEmpty()) {
				createInstance = crtInst.equals("yes");
			}

			// Get the partnerLink name and resolve it
			String pLink = activity.getAttribute("partnerLink");
			OPartnerLink partnerLink = null;
			if (pLink != null && !pLink.isEmpty()) {
				partnerLink = compiler.resolvePartnerLink(pLink);
			}

			// Get the operation name and resolve it
			String opr = activity.getAttribute("operation");
			Operation operation = null;
			if (opr != null && !opr.isEmpty()) {
				operation = compiler.resolveMyRoleOperation(partnerLink, opr);
			}

			// Mark the operation as createInstanceOperation in the
			// corresponding partnerLink
			if (createInstance && partnerLink != null && operation != null) {
				partnerLink.addCreateInstanceOperation(operation);
			}
		}
	}

	@Override
	public void run(Object context, Element element) throws FaultException {
		ExtensionContext eContext = (ExtensionContext) context;
		this._context = eContext;
		this._runtime = _context.getRuntimeInstance();
		this._jacob = JacobVPU.activeJacobThread();
		this._scopeFrame = _context.getScopeFrame();
		this._self = _context.getActivityInfo();
		this._oprocess = _self.o.getOwner();
		this._linkFrame = _context.getLinkFrame();

		// parse the receive elements from the simulationStart
		// activity element
		_onMessage = compileSimulationStart(element);

		// @hahnml: Set the id to the JacobObject
		this.oId = _self.o.getId();

		// Run the PICK stuff
		execute();
	}

	private void execute() {
		PickResponseChannel pickResponseChannel = newChannel(PickResponseChannel.class);
		Selector[] selectors;

		try {
			selectors = new Selector[1];

			OPickReceive.OnMessage onMessage = _onMessage;
			// collect all initiated correlations
			Set<OScope.CorrelationSet> matchCorrelations = new HashSet<OScope.CorrelationSet>();
			matchCorrelations.addAll(onMessage.matchCorrelations);
			for (OScope.CorrelationSet cset : onMessage.joinCorrelations) {
				if (_runtime
						.isCorrelationInitialized(_scopeFrame.resolve(cset))) {
					matchCorrelations.add(cset);
				}
			}

			PartnerLinkInstance pLinkInstance = _scopeFrame
					.resolve(onMessage.partnerLink);
			CorrelationKeySet keySet = resolveCorrelationKey(pLinkInstance,
					matchCorrelations);

			selectors[0] = new Selector(0, pLinkInstance,
					onMessage.operation.getName(),
					onMessage.operation.getOutput() == null,
					onMessage.messageExchangeId, keySet, onMessage.route);

			_runtime.select(pickResponseChannel, null, _createInstance,
					selectors);
		} catch (FaultException e) {
			__log.error(e);

			_context.completeWithFault(e);

			return;
		}

		_jacob.instance(new WAITING(pickResponseChannel));
	}

	// Copied from JacobObject
	@SuppressWarnings("unchecked")
	private <T extends Channel> T newChannel(Class<T> channelType)
			throws IllegalArgumentException {
		return (T) _jacob.newChannel(channelType, "SIMULATIONSTART", null);
	}

	// Copied from ACTIVITY
	protected EvaluationContext getEvaluationContext() {
		return new ExprEvaluationContextImpl(_scopeFrame, _runtime);
	}

	/**
	 * Resolves the correlation key from the given PartnerLinkInstance and a
	 * match type correlation(non-initiate or already initialized join
	 * correlation).
	 * 
	 * @param pLinkInstance
	 *            the partner link instance
	 * @param matchCorrelations
	 *            the match type correlation
	 * @return returns the resolved CorrelationKey
	 * @throws FaultException
	 *             thrown when the correlation is not initialized and
	 *             createInstance flag is not set
	 */
	private CorrelationKeySet resolveCorrelationKey(
			PartnerLinkInstance pLinkInstance,
			Set<OScope.CorrelationSet> matchCorrelations) throws FaultException {

		// CorrelationKeySet is empty for the case of the createInstance
		// activity
		CorrelationKeySet keySet = new CorrelationKeySet();

		if (matchCorrelations.isEmpty() && !_createInstance) {

			// Adding a route for opaque correlation. In this case,
			// correlation is on "out-of-band" session-id
			String sessionId = _runtime.fetchMySessionId(pLinkInstance);
			keySet.add(new CorrelationKey("-1", new String[] { sessionId }));
		} else if (!matchCorrelations.isEmpty()) {
			for (OScope.CorrelationSet cset : matchCorrelations) {
				CorrelationKey key = null;

				if (!_runtime.isCorrelationInitialized(_scopeFrame
						.resolve(cset))) {
					if (!_createInstance) {
						throw new FaultException(
								_self.o.getOwner().constants.qnCorrelationViolation,
								"Correlation not initialized.");
					}
				} else {
					key = _runtime.readCorrelation(_scopeFrame.resolve(cset));
					assert key != null;
				}

				if (key != null) {
					keySet.add(key);
				}
			}
		}

		return keySet;
	}

	@SuppressWarnings("unchecked")
	private void initVariable(String mexId, OPickReceive.OnMessage onMessage) {
		// This is allowed, if there is no parts in the message for example.
		if (onMessage.variable == null)
			return;

		Element msgEl;
		try {
			// At this point, not being able to get the request is most probably
			// a mex that hasn't properly replied to (process issue).
			msgEl = _runtime.getMyRequest(mexId);
		} catch (BpelEngineException e) {
			__log.error("The message exchange seems to be in an unconsistent state, you're "
					+ "probably missing a reply on a request/response interaction.");
			_self.parent.failure(e.toString(), null);
			return;
		}

		Collection<String> partNames = (Collection<String>) onMessage.operation
				.getInput().getMessage().getParts().keySet();

		// Let's do some sanity checks here so that we don't get weird errors in
		// assignment later.
		// The engine should have checked to make sure that the messages that
		// are delivered conform
		// to the correct format; but you know what they say, don't trust
		// anyone.
		if (!(onMessage.variable.type instanceof OMessageVarType)) {
			String errmsg = "Non-message variable for receive: should have been picked up by static analysis.";
			__log.fatal(errmsg);
			throw new InvalidProcessException(errmsg);
		}

		OMessageVarType vartype = (OMessageVarType) onMessage.variable.type;

		// Check that each part contains what we expect.
		for (String pName : partNames) {
			QName partName = new QName(null, pName);
			Element msgPart = DOMUtils.findChildByName(msgEl, partName);
			Part part = vartype.parts.get(pName);
			if (part == null) {
				String errmsg = "Inconsistent WSDL, part " + pName
						+ " not found in message type " + vartype.messageType;
				__log.fatal(errmsg);
				throw new InvalidProcessException(errmsg);
			}
			if (msgPart == null) {
				String errmsg = "Message missing part: " + pName;
				__log.fatal(errmsg);
				throw new InvalidContextException(errmsg);
			}

			if (part.type instanceof OElementVarType) {
				OElementVarType ptype = (OElementVarType) part.type;
				Element e = DOMUtils.getFirstChildElement(msgPart);
				if (e == null) {
					String errmsg = "Message (element) part " + pName
							+ " did not contain child element.";
					__log.fatal(errmsg);
					throw new InvalidContextException(errmsg);
				}

				QName qn = new QName(e.getNamespaceURI(), e.getLocalName());
				if (!qn.equals(ptype.elementType)) {
					String errmsg = "Message (element) part "
							+ pName
							+ " did not contain correct child element: expected "
							+ ptype.elementType + " but got " + qn;
					__log.fatal(errmsg);
					throw new InvalidContextException(errmsg);
				}
			}

		}

		try {
			_context.writeVariable(onMessage.variable, msgEl);
		} catch (FaultException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExternalVariableModuleException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private class WAITING extends BpelJacobRunnable {
		private static final long serialVersionUID = 1L;

		private PickResponseChannel _pickResponseChannel;

		private final ScopeFrame _scopeFrameW = _scopeFrame;
		private final ActivityInfo _selfW = _self;

		private WAITING(PickResponseChannel pickResponseChannel) {
			this._pickResponseChannel = pickResponseChannel;

			// @hahnml: Set the OBase id
			oId = SimulationStartActivity.this.oId;
		}

		public void run() {
			object(false,
					new PickResponseChannelListener(_pickResponseChannel) {
						private static final long serialVersionUID = -8237296827418738011L;

						public void onRequestRcvd(int selectorIdx, String mexId) {
							OPickReceive.OnMessage onMessage = _onMessage;

							// dead-path the alarm (if any)
							if (_alarm != null) {
								_context.doDPE(_alarm.activity);
							}

							_runtime.cancelOutstandingRequests(_pickResponseChannel
									.export());

							FaultData fault;
							initVariable(mexId, onMessage);
							try {
								// @tolevar
								fillParameters(mexId, onMessage);

								VariableInstance vinst = _scopeFrame
										.resolve(onMessage.variable);
								for (OScope.CorrelationSet cset : onMessage.initCorrelations) {
									initializeCorrelation(
											_scopeFrame.resolve(cset), vinst,
											_self.o.getXpath());
								}
								for (OScope.CorrelationSet cset : onMessage.joinCorrelations) {
									// will be ignored if already initialized
									initializeCorrelation(
											_scopeFrame.resolve(cset), vinst,
											_self.o.getXpath());
								}
								if (onMessage.partnerLink.hasPartnerRole()) {
									// Trying to initialize partner epr based on
									// a message-provided epr/session.

									if (!_runtime
											.isPartnerRoleEndpointInitialized(_scopeFrame
													.resolve(onMessage.partnerLink))
											|| !onMessage.partnerLink.initializePartnerRole) {

										Node fromEpr = _runtime
												.getSourceEPR(mexId);
										if (fromEpr != null) {
											if (__log.isDebugEnabled())
												__log.debug("Received callback EPR "
														+ DOMUtils
																.domToString(fromEpr)
														+ " saving it on partner link "
														+ onMessage.partnerLink
																.getName());
											_runtime.writeEndpointReference(
													_scopeFrame
															.resolve(onMessage.partnerLink),
													(Element) fromEpr);

											// @author sonntamo
											PartnerLinkModificationEvent plme = new PartnerLinkModificationEvent(
													onMessage.partnerLink.name,
													onMessage.partnerLink
															.getXpath(),
													fromEpr,
													_self.o.getXpath(),
													_scopeFrame.getOScope()
															.getXpath(),
													_scopeFrame
															.getScopeInstanceId());
											_scopeFrame.fillEventInfo(plme);
											getBpelRuntimeContext().sendEvent(
													plme);
										}
									}

									String partnersSessionId = _runtime
											.getSourceSessionId(mexId);
									if (partnersSessionId != null)
										_runtime.initializePartnersSessionId(
												_scopeFrame
														.resolve(onMessage.partnerLink),
												partnersSessionId);

								}
								// this request is now waiting for a reply
								_runtime.processOutstandingRequest(_scopeFrame
										.resolve(onMessage.partnerLink),
										onMessage.operation.getName(),
										onMessage.messageExchangeId, mexId);

							} catch (FaultException e) {
								__log.error(e);
								fault = createFault(e.getQName(), onMessage);
								_context.completeWithFault(fault,
										CompensationHandler.emptySet());
								return;
							} catch (TransformerException e) {
								__log.error(e);
								_context.completeWithFault(e);
							} catch (ExternalVariableModuleException e) {
								__log.error(e);
								_context.completeWithFault(e);
							}

							Activity_Complete();
						}

						public void onTimeout() {
							// cannot happen since "onAlarm" is not part of the
							// simulationStartActivity
						}

						public void onCancel() {
							_context.complete();
						}

					}.or(new TerminationChannelListener(_self.self) {
						private static final long serialVersionUID = 4399496341785922396L;

						public void terminate() {
							_runtime.cancel(_pickResponseChannel);
							// onCancel() will be executed next
							instance(WAITING.this);
						}
						//krwczk: TODO -implement skip
						public void skip() {
							
						}
					}));
		}

		/*
		 * @sonntamo: TODO should be moved some day to the ExtensionContextImpl
		 * to avoid duplicated code in the EXTENSIONACTIVITY and in every
		 * extension activity implementation
		 */
		public void Activity_Complete() {

			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 1022344588855L;

				public void linkStatus(boolean value) {

					ActivityComplete evt = new ActivityComplete(_selfW.o.name,
							_selfW.o.getId(), _selfW.o.getXpath(), _selfW.aId,
							_scopeFrameW.getOScope().getXpath(),
							_scopeFrameW.getScopeInstanceId(),
							getBpelRuntimeContext().getProcessQName(),
							getBpelRuntimeContext().getPid(),
							_selfW.o.getArt(), false);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);
					
					// need to re-create the context because it is sometimes null
					ExtensionContext context = new ExtensionContextImpl(_selfW,
							_scopeFrameW, getBpelRuntimeContext());
					context.complete();
				}

			};
			TerminationChannelListener termChan = new TerminationChannelListener(
					_self.self) {
				private static final long serialVersionUID = 15346579005562L;

				public void terminate() {

					// Event Activity_Terminated
					ActivityTerminated evt = new ActivityTerminated(
							_selfW.o.name, _selfW.o.getId(),
							_selfW.o.getXpath(), _selfW.aId, _scopeFrameW
									.getOScope().getXpath(),
							_scopeFrameW.getScopeInstanceId(),
							getBpelRuntimeContext().getProcessQName(),
							getBpelRuntimeContext().getPid(),
							_selfW.o.getArt(), false);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);
					_terminatedActivity = true;

					// need to re-create the context because it is sometimes null
					ExtensionContext context = new ExtensionContextImpl(_selfW,
							_scopeFrameW, getBpelRuntimeContext());
					context.complete();
				}
				//krwczk: TODO -implement skip
				public void skip() {
					
				}

			};

			object(false, (termChan).or(receiver));

			// Event Activity_Executed
			ActivityExecuted evt2 = new ActivityExecuted(_selfW.o.name,
					_selfW.o.getId(), _selfW.o.getXpath(), _selfW.aId,
					_scopeFrameW.getOScope().getXpath(),
					_scopeFrameW.getScopeInstanceId(), getBpelRuntimeContext()
							.getProcessQName(), getBpelRuntimeContext()
							.getPid(), _selfW.o.getArt(), false, signal);
			getBpelRuntimeContext().getBpelProcess().getEngine()
					.fireEvent(evt2);

			// Create a new snapshot
			_context.storeSnapshot();
		}

	}

	private OPickReceive.OnMessage compileSimulationStart(Element element) {
		// Parse the contained simulationStartActivity element like a
		// receiveActivity (same structure)
		ReceiveActivity receive = new ReceiveActivity(element);

		_createInstance = receive.isCreateInstance();

		// Create onMessage object
		OPickReceive.OnMessage onMessage = compileOnMessage(
				receive.getVariable(), receive.getPartnerLink(),
				receive.getOperation(), receive.getMessageExchangeId(),
				receive.getPortType(), receive.isCreateInstance(),
				receive.getCorrelations(), receive.getRoute(), BpelCompiler.getXPath(element)+"/onMessage");
		return onMessage;
	}

	private OPickReceive.OnMessage compileOnMessage(String varname,
			String plink, String operation, String messageExchangeId,
			QName portType, boolean createInstance,
			Collection<Correlation> correlations, String route, String xPath) {
		OPickReceive.OnMessage onMessage = null;
		
		// @hahnml
		if (ProcessModelChangeRegistry.getRegistry().isModelChanged()) {
			int id = ProcessModelChangeRegistry.getRegistry().getCorrectID(
					xPath);
			onMessage = new OPickReceive.OnMessage(
					_self.o.getOwner(), id);
		} else {
			onMessage = new OPickReceive.OnMessage(
					_self.o.getOwner());
		}

		// @hahnml: Initialize the xPath
		onMessage.setXpath(xPath);

		onMessage.partnerLink = _oprocess.getPartnerLink(plink);
		onMessage.operation = resolveMyRoleOperation(onMessage.partnerLink,
				operation);
		if (onMessage.operation.getInput() != null
				&& onMessage.operation.getInput().getMessage() != null) {
			Variable var = _oprocess.procesScope.getVisibleVariable(varname);
			QName messageType = onMessage.operation.getInput().getMessage()
					.getQName();

			if (!((OMessageVarType) var.type).messageType.equals(messageType)) {
				throw new CompilationException(
						__cmsgsGeneral.errVariableTypeMismatch(var.name,
								messageType,
								((OMessageVarType) var.type).messageType));
			}

			onMessage.variable = var;
		}

		onMessage.messageExchangeId = messageExchangeId;
		onMessage.route = route;

		if (portType != null
				&& !portType.equals(onMessage.partnerLink.myRolePortType
						.getQName()))
			throw new CompilationException(__cmsgsGeneral.errPortTypeMismatch(
					portType, onMessage.partnerLink.myRolePortType.getQName()));

		if (createInstance)
			onMessage.partnerLink
					.addCreateInstanceOperation(onMessage.operation);

		// prevents duplicate cset in on one set of correlations
		Set<String> csetNames = new HashSet<String>();
		for (Correlation correlation : correlations) {
			if (csetNames.contains(correlation.getCorrelationSet())) {
				throw new CompilationException(
						__cmsgsGeneral
								.errDuplicateUseCorrelationSet(correlation
										.getCorrelationSet()));
			}

			OScope.CorrelationSet cset = _oprocess.procesScope
					.getCorrelationSet(correlation.getCorrelationSet());

			switch (correlation.getInitiate()) {
			case UNSET:
			case NO:
				if (createInstance)
					throw new CompilationException(
							__cmsgsGeneral
									.errUseOfUninitializedCorrelationSet(correlation
											.getCorrelationSet()));
				onMessage.matchCorrelations.add(cset);
				onMessage.partnerLink.addCorrelationSetForOperation(
						onMessage.operation, cset, false);
				break;
			case YES:
				onMessage.initCorrelations.add(cset);
				break;
			case JOIN:
				cset.hasJoinUseCases = true;
				onMessage.joinCorrelations.add(cset);
				onMessage.partnerLink.addCorrelationSetForOperation(
						onMessage.operation, cset, true);
				break;

			default:
				throw new AssertionError(
						"Unexpected value for correlation set enumeration!");
			}

			for (OProcess.OProperty property : cset.properties) {
				// Force resolution of alias, to make sure that we have one for
				// this variable-property pair.
				resolvePropertyAlias(onMessage.variable, property.name);
			}

			csetNames.add(correlation.getCorrelationSet());
		}

		if (!onMessage.partnerLink.hasMyRole()) {
			throw new CompilationException(
					__cmsgsGeneral
							.errNoMyRoleOnReceivePartnerLink(onMessage.partnerLink
									.getName()));
		}

		return onMessage;
	}

	// copied from BpelCompiler
	@SuppressWarnings("unchecked")
	private Operation resolveMyRoleOperation(final OPartnerLink partnerLink,
			final String operationName) {
		if (partnerLink.myRolePortType == null) {
			throw new CompilationException(
					__cmsgsGeneral
							.errPartnerLinkDoesNotDeclareMyRole(partnerLink
									.getName()));
		}

		Operation found = CollectionsX.find_if(
				(List<Operation>) partnerLink.myRolePortType.getOperations(),
				new MemberOfFunction<Operation>() {
					public boolean isMember(Operation o) {
						// Again, guard against WSDL4J's "help"
						if ((o.getInput() == null || o.getInput().getMessage() == null)
								&& (o.getOutput() == null || o.getOutput()
										.getMessage() == null))
							return false;
						return o.getName().equals(operationName);
					}
				});
		if (found == null) {
			throw new CompilationException(
					__cmsgsGeneral.errUndeclaredOperation(
							partnerLink.myRolePortType.getQName(),
							operationName));
		}
		return found;
	}

	private OProcess.OPropertyAlias resolvePropertyAlias(
			OScope.Variable variable, QName propertyName) {
		if (!(variable.type instanceof OMessageVarType))
			throw new CompilationException(
					__cmsgsGeneral.errMessageVariableRequired(variable.name));

		OProcess.OProperty property = resolveProperty(propertyName);
		OProcess.OPropertyAlias alias = property.getAlias(variable.type);
		if (alias == null)
			throw new CompilationException(
					__cmsgsGeneral.errUndeclaredPropertyAlias(
							variable.type.toString(), propertyName));

		return alias;
	}

	private OProcess.OProperty resolveProperty(QName name) {

		for (OProcess.OProperty prop : _oprocess.properties) {
			if (prop.name.equals(name))
				return prop;
		}
		throw new CompilationException(
				__cmsgsGeneral.errUndeclaredProperty(name));
	}

	// @tolevar
	/**
	 * @param mexId
	 * @throws FaultException
	 */
	@SuppressWarnings("unchecked")
	private void fillParameters(String mexId, OPickReceive.OnMessage onMessage)
			throws TransformerException, ExternalVariableModuleException,
			FaultException {
		Element msgEl = _runtime.getMyRequest(mexId);

		Collection<String> partNames = (Collection<String>) onMessage.operation
				.getInput().getMessage().getParts().keySet();

		// @hahnml: Search the <parameters> element in all parts of the message
		for (String pName : partNames) {
			QName partName = new QName(null, pName);
			Element msgPart = DOMUtils.findChildByName(msgEl, partName);

			Element request = DOMUtils.getFirstChildElement(msgPart);

			if (request != null) {
				Element parameters = DOMUtils.findChildByName(msgPart,
						new QName(request.getNamespaceURI(), "parameters"),
						true);

				if (parameters != null) {
					NodeList parameterList = parameters.getElementsByTagNameNS(
							request.getNamespaceURI(), "parameter");
					for (int i = 0; i < parameterList.getLength(); i++) {
						Element parameterNode = (Element) parameterList.item(i);
						String variableName = parameterNode.getAttributeNS(
								request.getNamespaceURI(), "name");

						if (parameterNode != null && variableName != null) {
							extractNode(parameterNode, variableName);
						}
					}
				}
			}
		}
	}

	// @tolevar
	/**
	 * @param oScope
	 * @param paramterNode
	 * @param variableName
	 * @throws TransformerException
	 * @throws ExternalVariableModuleException
	 * @throws FaultException
	 */
	private void extractNode(Element parameterNode, String variableName)
			throws TransformerException, ExternalVariableModuleException,
			FaultException {

		// TODO: Handle internal child scopes to use local variables
		OScope oScope = _runtime.getBpelProcess().getOProcess().procesScope;

		for (String varName : oScope.variables.keySet()) {
			Variable variable = oScope.getLocalVariable(varName);
			if (!variableName.equals("input")
					&& variable.name.equals(variableName)) {
				Document valueDoc = buildDocument(variable, parameterNode);
				_context.writeVariable(variable, valueDoc.getDocumentElement());
			}
		}
	}

	// @tolevar
	/**
	 * @param variableNode
	 * @return
	 * @throws TransformerException
	 */
	private Document buildDocument(Variable variable, Element parameterNode)
			throws TransformerException {
		Document doc = DOMUtils.newDocument();
		Node val = variable.type.newInstance(doc);
		if (val.getNodeType() == Node.TEXT_NODE) {
			Element tempwrapper = doc.createElementNS(null,
					"temporary-simple-type-wrapper");
			doc.appendChild(tempwrapper);
			val.setNodeValue(parameterNode.getTextContent());
			tempwrapper.appendChild(val);
		} else {
			val.setNodeValue(parameterNode.getTextContent());
			doc.appendChild(val);
		}

		return doc;
	}

}
