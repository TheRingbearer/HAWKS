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

package org.apache.ode.bpel.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.wsdl.Definition;
import javax.wsdl.Operation;
import javax.wsdl.PortType;
import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.dao.MessageExchangeDAO;
import org.apache.ode.bpel.dao.ProcessDAO;
import org.apache.ode.bpel.dao.ProcessInstanceDAO;
import org.apache.ode.bpel.engine.fc.DeploymentUnitNameGenerator;
import org.apache.ode.bpel.evt.BpelEvent;
import org.apache.ode.bpel.extensions.sync.Constants;
import org.apache.ode.bpel.iapi.BpelEngine;
import org.apache.ode.bpel.iapi.BpelEngineException;
import org.apache.ode.bpel.iapi.ContextException;
import org.apache.ode.bpel.iapi.Endpoint;
import org.apache.ode.bpel.iapi.Message;
import org.apache.ode.bpel.iapi.MessageExchange;
import org.apache.ode.bpel.iapi.MessageExchange.FailureType;
import org.apache.ode.bpel.iapi.MessageExchange.MessageExchangePattern;
import org.apache.ode.bpel.iapi.MessageExchange.Status;
import org.apache.ode.bpel.iapi.MyRoleMessageExchange;
import org.apache.ode.bpel.iapi.MyRoleMessageExchange.CorrelationStatus;
import org.apache.ode.bpel.iapi.PartnerRoleMessageExchange;
import org.apache.ode.bpel.iapi.ProcessState;
import org.apache.ode.bpel.iapi.Scheduler;
import org.apache.ode.bpel.iapi.Scheduler.JobDetails;
import org.apache.ode.bpel.iapi.Scheduler.JobType;
import org.apache.ode.bpel.iapi.fc.ProcessConfLoader;
import org.apache.ode.bpel.intercept.InterceptorInvoker;
import org.apache.ode.bpel.intercept.MessageExchangeInterceptor;
import org.apache.ode.bpel.intercept.ProcessCountThrottler;
import org.apache.ode.bpel.intercept.ProcessSizeThrottler;
import org.apache.ode.bpel.o.OConstants;
import org.apache.ode.bpel.o.OPartnerLink;
import org.apache.ode.bpel.o.OProcess;
import org.apache.ode.bpel.runtime.InvalidProcessException;
import org.apache.ode.bpel.util.fc.ProcessRegistry;
import org.apache.ode.fc.dao.FCManagementDAO;
import org.apache.ode.scheduler.simple.ZJobList;
import org.apache.ode.utils.DOMUtils;
import org.apache.ode.utils.Namespaces;
import org.apache.ode.utils.ZZBool;
import org.apache.ode.utils.fc.FCConstants;
import org.apache.ode.utils.msg.MessageBundle;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Implementation of the {@link BpelEngine} interface: provides the server
 * methods that should be invoked in the context of a transaction.
 * 
 * @author mszefler
 * @author Matthieu Riou <mriou at apache dot org>
 */
public class BpelEngineImpl implements BpelEngine {
	private static final Log __log = LogFactory.getLog(BpelEngineImpl.class);

	/** RNG, for delays */
	private Random _random = new Random(System.currentTimeMillis());

	private static double _delayMean = 0;

	// @stmz
	Timer timer;
	ZZBool zzbool;
	ZJobList jobList;
	public static Logger logger = Logger.getLogger("Log-XML");

	static {
		try {
			String delay = System.getenv("ODE_DEBUG_TX_DELAY");
			if (delay != null && delay.length() > 0) {
				_delayMean = Double.valueOf(delay);
				__log.info("Stochastic debugging delay activated. Delay (Mean)="
						+ _delayMean + "ms.");
			}
		} catch (Throwable t) {
			if (__log.isDebugEnabled()) {
				__log.debug(
						"Could not read ODE_DEBUG_TX_DELAY environment variable; assuming 0 (mean) delay",
						t);
			} else {
				__log.info("Could not read ODE_DEBUG_TX_DELAY environment variable; assuming 0 (mean) delay");
			}
		}
	}

	private static final Messages __msgs = MessageBundle
			.getMessages(Messages.class);

	private static final double PROCESS_OVERHEAD_MEMORY_FACTOR = 1.2;

	/** Active processes, keyed by process id. */
	public final HashMap<QName, BpelProcess> _activeProcesses = new HashMap<QName, BpelProcess>();

	/** Mapping from myrole service name to active process. */
	private final HashMap<QName, List<BpelProcess>> _serviceMap = new HashMap<QName, List<BpelProcess>>();

	/** Mapping from a potentially shared endpoint to its EPR */
	private SharedEndpoints _sharedEps;

	/** Manage instance-level locks. */
	private final InstanceLockManager _instanceLockManager = new InstanceLockManager();

	public final Contexts _contexts;

	private final Map<QName, Long> _hydratedSizes = new HashMap<QName, Long>();
	private final Map<QName, Long> _unhydratedSizes = new HashMap<QName, Long>();
	
	// AH:
	private Definition fcServiceDefinition;
	private FragmentCompositionEventBroker fcEventBroker;
	private BpelDatabase db;
	private DeploymentUnitNameGenerator deploymentUnitNameGenerator;

	// AH: end

	public BpelEngineImpl(Contexts contexts) {
		_contexts = contexts;
		_sharedEps = new SharedEndpoints();
		_sharedEps.init();

		// @stmz
		jobList = ZJobList.getInstance();
		zzbool = ZZBool.getInstance();
		timer = new Timer();
		timer.schedule(new Task(), 1000, 100);
		
		// AH:

		deploymentUnitNameGenerator = new DeploymentUnitNameGenerator();
		// AH: end
	}

	// @stmz: if we can execute a job, do so
	// by putting a job of the list in ZJobList
	// and forward it to the SimpleScheduler for
	// immediate execution
	public class Task extends TimerTask {

		public Task() {
		}

		public void run() {
			if (zzbool.getCanRun()) {
				zzbool.setCanRun(false);
				//trying to test if this gets triggered after the deadlock
				/*Thread thread = new Thread(){
					public void run(){
						Object o = new Object();
						int i = 0;
						while (!zzbool.getCanRun() && i++ < 2000) {
							synchronized(o) {
								try {
									o.wait(10);
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
						}
						if (i >= 2000) {
							zzbool.setCanRun(true);
							System.out.println("Forced Release of the lock");
						}
					}
				};

				thread.start();*/
				
				final Scheduler.JobInfo info = jobList.getJobInfo();
				final Scheduler scheduler = _contexts.scheduler;
				if (info != null) {
					zzbool.setRunning(true);
					try {
						scheduler.execTransaction(new Callable<Void>() {
							public Void call() throws Exception {

								scheduler.scheduleVolatileJob(true,
										info.jobDetail);
								//TODO did break
								zzbool.setCanRun(true);
								return null;
							}
						});
					} catch (ContextException e) {
//						no impact
						zzbool.setCanRun(true);
						System.out.println(e);
					} catch (Exception e) {
//						no impact
						zzbool.setCanRun(true);
						System.out.println(e);
					}

				} else {
					zzbool.setCanRun(true);
				}
			}
		}

	}
	
	// AH:
	public void addRoutingsToFragment(QName serviceName, BpelProcess process) {
		List<BpelProcess> processes = _serviceMap.get(serviceName);
		if (!processes.contains(process)) {
			processes.add(process);
		}
	}

	// AH: end

	public SharedEndpoints getSharedEndpoints() {
		return _sharedEps;
	}

	public MyRoleMessageExchange createMessageExchange(String clientKey,
			QName targetService, String operation, String pipedMexId)
			throws BpelEngineException {

		List<BpelProcess> targets = route(targetService, null);
		List<BpelProcess> activeTargets = new ArrayList<BpelProcess>();
		for (BpelProcess target : targets) {
			if (target.getConf().getState() == ProcessState.ACTIVE) {
				activeTargets.add(target);
			}
		}

		if (targets == null || targets.size() == 0)
			throw new BpelEngineException("NoSuchService: " + targetService);

		if (targets.size() == 1 || activeTargets.size() == 1) {
			// If the number of targets is one, create and return a simple MEX
			BpelProcess target;
			if (activeTargets.size() == 1) {
				target = activeTargets.get(0);
			} else {
				target = targets.get(0);
			}
			return createNewMyRoleMex(target, clientKey, targetService,
					operation, pipedMexId);
		} else {
			// If the number of targets is greater than one, create and return
			// a brokered MEX that embeds the simple MEXs for each of the
			// targets
			BpelProcess template = activeTargets.get(0);
			ArrayList<MyRoleMessageExchange> meps = new ArrayList<MyRoleMessageExchange>();
			for (BpelProcess target : activeTargets) {
				meps.add(createNewMyRoleMex(target, clientKey, targetService,
						operation, pipedMexId));
			}
			return createNewMyRoleMex(template, meps);
		}
	}
	
	public MyRoleMessageExchange createFragmentMessageExchange(
			String clientKey, QName targetService, String operation,
			Long instanceId) throws BpelEngineException {

		String pipedMexId = null;
		BpelProcess target = null;
		ProcessInstanceDAO instance = null;
		if (instanceId != null) {
			instance = _contexts.dao.getConnection().getInstance(instanceId);
		}
		if (instance != null) {
			BpelProcess process = null;
			ProcessDAO processDao = instance.getProcess();
			process = _activeProcesses.get(processDao.getProcessId());
			target = process;
		}

		if (target == null)
			throw new BpelEngineException("NoSuchInstance: " + instanceId);

		return createNewMyRoleMex(target, clientKey, targetService, operation,
				pipedMexId);

	}

	private MyRoleMessageExchange createNewMyRoleMex(BpelProcess target,
			String clientKey, QName targetService, String operation,
			String pipedMexId) {
		MessageExchangeDAO dao;
		if (target == null || target.isInMemory()) {
			dao = _contexts.inMemDao.getConnection().createMessageExchange(
					MessageExchangeDAO.DIR_PARTNER_INVOKES_MYROLE);
		} else {
			dao = _contexts.dao.getConnection().createMessageExchange(
					MessageExchangeDAO.DIR_PARTNER_INVOKES_MYROLE);
		}
		dao.setCorrelationId(clientKey);
		dao.setCorrelationStatus(CorrelationStatus.UKNOWN_ENDPOINT.toString());
		dao.setPattern(MessageExchangePattern.UNKNOWN.toString());
		dao.setCallee(targetService);
		dao.setStatus(Status.NEW.toString());
		dao.setOperation(operation);
		dao.setPipedMessageExchangeId(pipedMexId);
		MyRoleMessageExchangeImpl mex = new MyRoleMessageExchangeImpl(target,
				this, dao);

		// AH: ignore fragment composition
		if (target != null
				&& !targetService.equals(FCConstants.FC_SERVICE_NAME)) {
			// AH: end
			target.initMyRoleMex(mex);
		}
		return mex;
	}

	/**
	 * Return a brokered MEX that delegates invocations to each of the embedded
	 * MEXs contained in the <code>meps</code> list, using the appropriate
	 * style.
	 * 
	 * @param target
	 * @param meps
	 * @return
	 * @throws BpelEngineException
	 */
	private MyRoleMessageExchange createNewMyRoleMex(BpelProcess target,
			List<MyRoleMessageExchange> meps) throws BpelEngineException {
		MyRoleMessageExchangeImpl templateMex = (MyRoleMessageExchangeImpl) meps
				.get(0);
		MessageExchangeDAO templateMexDao = templateMex.getDAO();
		return new BrokeredMyRoleMessageExchangeImpl(target, this, meps,
				templateMexDao, templateMex);
	}

	public MyRoleMessageExchange createMessageExchange(String clientKey,
			QName targetService, String operation) {
		return createMessageExchange(clientKey, targetService, operation, null);
	}
	
	// AH:
	public void setFcServiceDefinition(Definition def) {
		fcServiceDefinition = def;
	}

	// AH: end

	private void setMessageExchangeProcess(String mexId, ProcessDAO processDao) {
		MessageExchangeDAO mexdao = _contexts.inMemDao.getConnection()
				.getMessageExchange(mexId);
		if (mexdao == null)
			mexdao = _contexts.dao.getConnection().getMessageExchange(mexId);
		if (mexdao != null)
			mexdao.setProcess(processDao);
	}

	public MessageExchange getMessageExchange(String mexId) {
		MessageExchangeDAO mexdao = _contexts.inMemDao.getConnection()
				.getMessageExchange(mexId);
		if (mexdao == null)
			mexdao = _contexts.dao.getConnection().getMessageExchange(mexId);
		if (mexdao == null)
			return null;

		ProcessDAO pdao = mexdao.getProcess();
		BpelProcess process = pdao == null ? null : _activeProcesses.get(pdao
				.getProcessId());

		MessageExchangeImpl mex;
		switch (mexdao.getDirection()) {
		case MessageExchangeDAO.DIR_BPEL_INVOKES_PARTNERROLE:
			if (process == null) {
				String errmsg = __msgs.msgProcessNotActive(pdao.getProcessId());
				__log.error(errmsg);
				// TODO: Perhaps we should define a checked exception for this
				// condition.
				throw new BpelEngineException(errmsg);
			}
			{
				OPartnerLink plink = (OPartnerLink) process.getOProcess()
						.getChild(mexdao.getPartnerLinkModelId());
				PortType ptype = plink.partnerRolePortType;
				Operation op = plink.getPartnerRoleOperation(mexdao
						.getOperation());
				// TODO: recover Partner's EPR
				mex = createPartnerRoleMessageExchangeImpl(mexdao, ptype, op,
						plink, process);
			}
			break;
		case MessageExchangeDAO.DIR_PARTNER_INVOKES_MYROLE:
			mex = new MyRoleMessageExchangeImpl(process, this, mexdao);
			// AH:
			if (process == null) {

				if (mexdao.getCallee().equals(FCConstants.FC_SERVICE_NAME)) {
					PortType port = fcServiceDefinition
							.getPortType(FCConstants.FC_PORT_TYPE_NAME);
					Operation wsdlOperation = port.getOperation(
							mex.getOperationName(), null, null);
					mex.setPortOp(port, wsdlOperation);
				}
			} else {

				// AH: end
				OPartnerLink plink = (OPartnerLink) process.getOProcess()
						.getChild(mexdao.getPartnerLinkModelId());
				// the partner link might not be hydrated
				if (plink != null) {
					PortType ptype = plink.myRolePortType;
					Operation op = plink.getMyRoleOperation(mexdao
							.getOperation());
					mex.setPortOp(ptype, op);
				}
			}
			break;
		default:
			String errmsg = "BpelEngineImpl: internal error, invalid MexDAO direction: "
					+ mexId;
			__log.fatal(errmsg);
			throw new BpelEngineException(errmsg);
		}

		return mex;
	}

	// enable extensibility
	protected PartnerRoleMessageExchangeImpl createPartnerRoleMessageExchangeImpl(
			MessageExchangeDAO mexdao, PortType ptype, Operation op,
			OPartnerLink plink, BpelProcess process) {
		return new PartnerRoleMessageExchangeImpl(this, mexdao, ptype, op,
				null, plink.hasMyRole() ? process.getInitialMyRoleEPR(plink)
						: null, process.getPartnerRoleChannel(plink));
	}

	BpelProcess unregisterProcess(QName process) {
		BpelProcess p = _activeProcesses.remove(process);
		__log.debug("Unregister process: serviceId=" + process + ", process="
				+ p);
		if (p != null) {
			if (__log.isDebugEnabled())
				__log.debug("Deactivating process " + p.getPID());

			Iterator<List<BpelProcess>> serviceIter = _serviceMap.values()
					.iterator();
			while (serviceIter.hasNext()) {
				Iterator<BpelProcess> entryProcesses = serviceIter.next()
						.iterator();
				while (entryProcesses.hasNext()) {
					BpelProcess entryProcess = entryProcesses.next();
					if (entryProcess.getPID().equals(process)) {
						entryProcesses.remove();
					}
				}
			}

			// unregister the services provided by the process
			p.deactivate();
			// release the resources held by this process
			p.dehydrate();
			// update the process footprints list
			_hydratedSizes.remove(p.getPID());
		}
		return p;
	}

	boolean isProcessRegistered(QName pid) {
		return _activeProcesses.containsKey(pid);
	}

	public BpelProcess getProcess(QName pid) {
		return _activeProcesses.get(pid);
	}

	/**
	 * Register a process with the engine.
	 * 
	 * @param process
	 *            the process to register
	 */
	void registerProcess(BpelProcess process) {
		_activeProcesses.put(process.getPID(), process);
		// AH:
		deploymentUnitNameGenerator.registerProcess(process);
		// AH: end
		
		for (Endpoint e : process.getServiceNames()) {
			__log.debug("Register process: serviceId=" + e + ", process="
					+ process);
			List<BpelProcess> processes = _serviceMap.get(e.serviceName);
			if (processes == null) {
				processes = new ArrayList<BpelProcess>();
				_serviceMap.put(e.serviceName, processes);
			}
			// Remove any older version of the process from the list
			Iterator<BpelProcess> processesIter = processes.iterator();
			while (processesIter.hasNext()) {
				BpelProcess cachedVersion = processesIter.next();
				__log.debug("cached version " + cachedVersion.getPID()
						+ " vs registering version " + process.getPID());
				if (cachedVersion.getProcessType().equals(
						process.getProcessType())) {
//					// Check for versions to retain newer one
//					if (cachedVersion.getVersion() > process.getVersion()) {
//						__log.debug("removing current version");
//						process.activate(this);
//						process.deactivate();
//						return;
//					} else {
//						__log.debug("removing cached older version");
//						processesIter.remove();
//						cachedVersion.deactivate();
//					}
					
					//@hahnml: Remove the cached process
					__log.debug("removing cached older version");
					//@sonntamo: don't do anything due to concurrent workflow evolution
//					processesIter.remove();
//					cachedVersion.deactivate();
				}
			}
			processes.add(process);
		}
		process.activate(this);
	}

	/**
	 * Route to a process using the service id. Note, that we do not need the
	 * endpoint name here, we are assuming that two processes would not be
	 * registered under the same service qname but different endpoint.
	 * 
	 * @param service
	 *            target service id
	 * @param request
	 *            request message
	 * @return process corresponding to the targetted service, or
	 *         <code>null</code> if service identifier is not recognized.
	 */
	List<BpelProcess> route(QName service, Message request) {
		// TODO: use the message to route to the correct service if more than
		// one service is listening on the same endpoint.
		List<BpelProcess> routed = _serviceMap.get(service);
		if (__log.isDebugEnabled())
			__log.debug("Routed: svcQname " + service + " --> " + routed);
		return routed;

	}

	OProcess getOProcess(QName processId) {
		BpelProcess process = _activeProcesses.get(processId);

		if (process == null)
			return null;
		return process.getOProcess();
	}

	public void acquireInstanceLock(final Long iid) {
		//TODO could break
		//@krawczls: Testing if this avoids the deadlock problem
		//return;
		// We lock the instance to prevent concurrent transactions and prevent
		// unnecessary rollbacks,
		// Note that we don't want to wait too long here to get our lock, since
		// we
		// are likely holding
		// on to scheduler's locks of various sorts.
		if (Constants.DEBUG_LEVEL > 1) {
			System.out.println("BpelEngineImpl - AquireInstanceLock" + iid);
		}
		try {
			System.out.println("BpelEngineImpl - AquireInstanceLock1");
			_instanceLockManager.lock(iid, 1, TimeUnit.MICROSECONDS);
			_contexts.scheduler
					.registerSynchronizer(new Scheduler.Synchronizer() {
						public void afterCompletion(boolean success) {
							if (Constants.DEBUG_LEVEL > 1) {
								System.out.println("BpelEngineImpl - unlock1" + iid);
							}
							_instanceLockManager.unlock(iid);
							if (Constants.DEBUG_LEVEL > 1) {
								System.out.println("BpelEngineImpl - unlock2" + iid);
							}
						}

						public void beforeCompletion() {
						}
					});
		} catch (InterruptedException e) {
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("BpelEngineImpl - AquireInstanceLock2:Catched Exception1");
			}
			e.printStackTrace();
			// Retry later.
			__log.debug("Thread interrupted, job will be rescheduled");
			zzbool.setRunning(false);
			throw new Scheduler.JobProcessorException(true);
		} catch (org.apache.ode.bpel.engine.InstanceLockManager.TimeoutException e) {
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("BpelEngineImpl - AquireInstanceLock2:Catched Exception2");
			}
			e.printStackTrace();
			__log.debug("Instance " + iid + " is busy, rescheduling job.");
			zzbool.setRunning(false);
			throw new Scheduler.JobProcessorException(true);
		}
	}

	public void onScheduledJob(Scheduler.JobInfo jobInfo)
			throws Scheduler.JobProcessorException {
		if (Constants.DEBUG_LEVEL > 1) {
			System.out.println("BpelEngineImpl - " + jobInfo.jobDetail.instanceId);
		}
		final JobDetails we = jobInfo.jobDetail;
		if (Constants.DEBUG_LEVEL > 1) {
			System.out.println("BpelEngineImpl - 1");
		}

		/*
		 * if (!we.getBool()) { we.setBool(true); addJobInfo(jobInfo); return; }
		 */

		// @stmz: mark this job as executed, so in case of rescheduling, its put
		// to the list in ZJobList
		// again, to prevent concurrent execution of jobs
		we.setBool(false);

		if (__log.isTraceEnabled())
			__log.trace("[JOB] onScheduledJob " + jobInfo + ""
					+ we.getInstanceId());
		
		if (Constants.DEBUG_LEVEL > 1) {
			System.out.println("BpelEngineImpl - 2");
		}
		acquireInstanceLock(we.getInstanceId());
		if (Constants.DEBUG_LEVEL > 1) {
			System.out.println("BpelEngineImpl - 2.5");
		}
		// DONT PUT CODE HERE-need this method real tight in a try/catch block,
		// we
		// need to handle
		// all types of failure here, the scheduler is not going to know how to
		// handle our errors,
		// ALSO we have to release the lock obtained above (IMPORTANT), lest the
		// whole system come
		// to a grinding halt.
		BpelProcess process = null;
		try {
			if (we.getProcessId() != null) {
				if (Constants.DEBUG_LEVEL > 1) {
					System.out.println("BpelEngineImpl - 3");
				}
				process = _activeProcesses.get(we.getProcessId());
			} else {
				if (Constants.DEBUG_LEVEL > 1) {
					System.out.println("BpelEngineImpl - 4");
				}
				ProcessInstanceDAO instance;
				if (we.getInMem())
					instance = _contexts.inMemDao.getConnection().getInstance(
							we.getInstanceId());
				else
					instance = _contexts.dao.getConnection().getInstance(
							we.getInstanceId());
				if (Constants.DEBUG_LEVEL > 1) {
					System.out.println("BpelEngineImpl - 5");
				}
				if (instance == null) {
					if (Constants.DEBUG_LEVEL > 1) {
						System.out.println("BpelEngineImpl - 6");
					}
					__log.debug(__msgs
							.msgScheduledJobReferencesUnknownInstance(we
									.getInstanceId()));
					// nothing we can do, this instance is not in the database,
					// it will
					// always fail, not
					// exactly an error since can occur in normal course of
					// events.
					zzbool.setRunning(false);
					return;
				}
				if (Constants.DEBUG_LEVEL > 1) {
					System.out.println("BpelEngineImpl - 7");
				}
				ProcessDAO processDao = instance.getProcess();
				process = _activeProcesses.get(processDao.getProcessId());
				if (Constants.DEBUG_LEVEL > 1) {
					System.out.println("BpelEngineImpl - 8");
				}
			}
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("BpelEngineImpl - 8.5");
			}
			if (process == null) {
				// The process is not active, there's nothing we can do with
				// this job
				if (Constants.DEBUG_LEVEL > 1) {
					System.out.println("BpelEngineImpl - 9");
				}
				__log.debug("Process " + we.getProcessId()
						+ " can't be found, job abandoned.");
				if (Constants.DEBUG_LEVEL > 1) {
					System.out.println("Process " + we.getProcessId()
						+ " can't be found, job abandoned.");
				}
				zzbool.setRunning(false);
				return;
			}
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("BpelEngineImpl - 10");
			}
			ClassLoader cl = Thread.currentThread().getContextClassLoader();
			try {
				if (Constants.DEBUG_LEVEL > 1) {
					System.out.println("BpelEngineImpl - 11");
				}
				Thread.currentThread().setContextClassLoader(
						process._classLoader);
				if (we.getType().equals(JobType.INVOKE_CHECK)) {
					if (Constants.DEBUG_LEVEL > 1) {
						System.out.println("BpelEngineImpl - 12");
					}
					if (__log.isDebugEnabled())
						__log.debug("handleJobDetails: InvokeCheck event for mexid "
								+ we.getMexId());

					sendPartnerRoleFailure(we,
							MessageExchange.FailureType.COMMUNICATION_ERROR);
					// @hahnml: CHECK if this is the right position for this
					// statement
					zzbool.setRunning(false);
					return;
				} else if (we.getType().equals(JobType.INVOKE_INTERNAL)) {
					if (Constants.DEBUG_LEVEL > 1) {
						System.out.println("BpelEngineImpl - 13");
					}
					if (__log.isDebugEnabled())
						__log.debug("handleJobDetails: InvokeInternal event for mexid "
								+ we.getMexId());

					setMessageExchangeProcess(we.getMexId(),
							process.getProcessDAO());
					MyRoleMessageExchangeImpl mex = (MyRoleMessageExchangeImpl) getMessageExchange(we
							.getMexId());
					if (!process.processInterceptors(mex,
							InterceptorInvoker.__onJobScheduled)) {
						boolean isTwoWay = Boolean.valueOf(mex
								.getProperty("isTwoWay"));
						if (isTwoWay) {
							String causeCodeValue = mex
									.getProperty("causeCode");
							mex.getDAO().setProcess(process.getProcessDAO());
							sendMyRoleFault(
									process,
									we,
									causeCodeValue != null ? Integer
											.valueOf(causeCodeValue)
											: InvalidProcessException.DEFAULT_CAUSE_CODE);
							// @hahnml: CHECK if this is the right position for
							// this statement
							zzbool.setRunning(false);
							return;
						} else {
							throw new Scheduler.JobProcessorException(
									checkRetry(we));
						}
					}
				}
				if (Constants.DEBUG_LEVEL > 1) {
					System.out.println("BpelEngineImpl - 14");
				}
				process.handleJobDetails(jobInfo.jobDetail);
				if (Constants.DEBUG_LEVEL > 1) {
					System.out.println("BpelEngineImpl - 15");
				}
				debuggingDelay();
				if (Constants.DEBUG_LEVEL > 1) {
					System.out.println("BpelEngineImpl - 16");
				}
			} finally {
				if (Constants.DEBUG_LEVEL > 1) {
					System.out.println("BpelEngineImpl - 17");
				}
				// @hahnml: CHECK if this is the right position for this
				// statement
				zzbool.setRunning(false);
				Thread.currentThread().setContextClassLoader(cl);
			}
		} catch (Scheduler.JobProcessorException e) {
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("BpelEngineImpl - 16");
			}
			zzbool.setRunning(false);
			throw e;
		} catch (BpelEngineException bee) {
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("BpelEngineImpl - 17");
			}
			zzbool.setRunning(false);
			__log.error(__msgs.msgScheduledJobFailed(we), bee);
			throw new Scheduler.JobProcessorException(bee, checkRetry(we));
		} catch (ContextException ce) {
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("BpelEngineImpl - 18");
			}
			zzbool.setRunning(false);
			__log.error(__msgs.msgScheduledJobFailed(we), ce);
			throw new Scheduler.JobProcessorException(ce, checkRetry(we));
		} catch (InvalidProcessException ipe) {
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("BpelEngineImpl - 19");
			}
			zzbool.setRunning(false);
			__log.error(__msgs.msgScheduledJobFailed(we), ipe);
			sendMyRoleFault(process, we, ipe.getCauseCode());
		} catch (RuntimeException rte) {
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("BpelEngineImpl - 20");
			}
			zzbool.setRunning(false);
			__log.error(__msgs.msgScheduledJobFailed(we), rte);
			throw new Scheduler.JobProcessorException(rte, checkRetry(we));
		} catch (Throwable t) {
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("BpelEngineImpl - 21");
			}
			zzbool.setRunning(false);
			__log.error(__msgs.msgScheduledJobFailed(we), t);
			throw new Scheduler.JobProcessorException(t, checkRetry(we));
		}
	}

	private boolean checkRetry(JobDetails we) {
		// Only retry if the job is NOT in memory. Not that this does not
		// guaranty
		// that a retry will be scheduled.
		// Actually events are not retried if not persisted and the scheduler
		// might
		// choose to discard the event if it has been retried too many times.
		return !we.getInMem();
	}

	/**
	 * Block the thread for random amount of time. Used for testing for races
	 * and the like. The delay generated is exponentially distributed with the
	 * mean obtained from the <code>ODE_DEBUG_TX_DELAY</code> environment
	 * variable.
	 */
	private void debuggingDelay() {
		// Do a delay for debugging purposes.
		if (_delayMean != 0)
			try {
				long delay = randomExp(_delayMean);
				// distribution
				// with mean
				// _delayMean
				__log.warn("Debugging delay has been activated; delaying transaction for "
						+ delay + "ms.");
				Thread.sleep(delay);
			} catch (InterruptedException e) {
				; // ignore
			}
	}

	private long randomExp(double mean) {
		double u = _random.nextDouble(); // Uniform
		long delay = (long) (-Math.log(u) * mean); // Exponential
		return delay;
	}

	public void fireEvent(BpelEvent event) {
		// Note that the eventListeners list is a copy-on-write array, so need
		// to mess with synchronization.
		for (org.apache.ode.bpel.iapi.BpelEventListener l : _contexts.eventListeners) {
			l.onEvent(event);
		}
	}

	/**
	 * Get the list of globally-registered message-exchange interceptors.
	 * 
	 * @return list
	 */
	List<MessageExchangeInterceptor> getGlobalInterceptors() {
		return _contexts.globalInterceptors;
	}

	public void registerMessageExchangeInterceptor(
			MessageExchangeInterceptor interceptor) {
		_contexts.globalInterceptors.add(interceptor);
	}

	public void unregisterMessageExchangeInterceptor(
			MessageExchangeInterceptor interceptor) {
		_contexts.globalInterceptors.remove(interceptor);
	}

	public void unregisterMessageExchangeInterceptor(Class interceptorClass) {
		MessageExchangeInterceptor candidate = null;
		for (MessageExchangeInterceptor interceptor : _contexts.globalInterceptors) {
			if (interceptor.getClass().isAssignableFrom(interceptorClass)) {
				candidate = interceptor;
				break;
			}
		}
		if (candidate != null) {
			_contexts.globalInterceptors.remove(candidate);
		}
	}

	public long getTotalBpelFootprint() {
		long bpelFootprint = 0;
		for (BpelProcess process : _activeProcesses.values()) {
			Long size = _hydratedSizes.get(process.getPID());
			if (size == null) {
				size = _unhydratedSizes.get(process.getPID());
			}
			if (size != null && size.longValue() > 0) {
				bpelFootprint += size;
			}
		}
		return bpelFootprint;
	}

	public long getHydratedFootprint() {
		long hydratedFootprint = 0;
		for (BpelProcess process : _activeProcesses.values()) {
			if (!process.hintIsHydrated()) {
				continue;
			}
			Long size = _hydratedSizes.get(process.getPID());
			if (size == null) {
				size = _unhydratedSizes.get(process.getPID());
			}
			if (size != null && size.longValue() > 0) {
				hydratedFootprint += size;
			}
		}
		return hydratedFootprint;
	}

	public long getHydratedProcessSize(QName processName) {
		return getHydratedProcessSize(_activeProcesses.get(processName));
	}

	private long getHydratedProcessSize(BpelProcess process) {
		long potentialGrowth = 0;
		if (!process.hintIsHydrated()) {
			Long mySize = _hydratedSizes.get(process.getPID());
			if (mySize == null) {
				mySize = _unhydratedSizes.get(process.getPID());
			}
			if (mySize != null && mySize.longValue() > 0) {
				potentialGrowth = mySize.longValue();
			}
		}
		return getHydratedProcessSize(potentialGrowth);
	}

	private long getHydratedProcessSize(long potentialGrowth) {
		long processMemory = (long) ((getHydratedFootprint() + potentialGrowth) * PROCESS_OVERHEAD_MEMORY_FACTOR);
		return processMemory;
	}

	public int getHydratedProcessCount(QName processName) {
		int processCount = 0;
		for (BpelProcess process : _activeProcesses.values()) {
			if (process.hintIsHydrated()
					|| process.getPID().equals(processName)) {
				processCount++;
			}
		}
		return processCount;
	}

	private long _processThrottledMaximumSize = Long.MAX_VALUE;
	private int _processThrottledMaximumCount = Integer.MAX_VALUE;
	private int _instanceThrottledMaximumCount = Integer.MAX_VALUE;
	private boolean _hydrationThrottled = false;
	
	// AH:
	private ProcessConfLoader processConfLoader;

	private ProcessRegistry processRegistry;

	// AH: end

	public void setInstanceThrottledMaximumCount(
			int instanceThrottledMaximumCount) {
		this._instanceThrottledMaximumCount = instanceThrottledMaximumCount;
	}

	public int getInstanceThrottledMaximumCount() {
		return _instanceThrottledMaximumCount;
	}

	public void setProcessThrottledMaximumCount(
			int hydrationThrottledMaximumCount) {
		this._processThrottledMaximumCount = hydrationThrottledMaximumCount;
		if (hydrationThrottledMaximumCount < Integer.MAX_VALUE) {
			registerMessageExchangeInterceptor(new ProcessCountThrottler());
		} else {
			unregisterMessageExchangeInterceptor(ProcessCountThrottler.class);
		}
	}

	public int getProcessThrottledMaximumCount() {
		return _processThrottledMaximumCount;
	}

	public void setProcessThrottledMaximumSize(
			long hydrationThrottledMaximumSize) {
		this._processThrottledMaximumSize = hydrationThrottledMaximumSize;
		if (hydrationThrottledMaximumSize < Long.MAX_VALUE) {
			registerMessageExchangeInterceptor(new ProcessSizeThrottler());
		} else {
			unregisterMessageExchangeInterceptor(ProcessSizeThrottler.class);
		}
	}

	public long getProcessThrottledMaximumSize() {
		return _processThrottledMaximumSize;
	}

	public void setProcessSize(QName processId, boolean hydratedOnce) {
		BpelProcess process = _activeProcesses.get(processId);
		long processSize = process.sizeOf();
		if (hydratedOnce) {
			_hydratedSizes.put(process.getPID(), new Long(processSize));
			_unhydratedSizes.remove(process.getPID());
		} else {
			_hydratedSizes.remove(process.getPID());
			_unhydratedSizes.put(process.getPID(), new Long(processSize));
		}
	}

	/**
	 * Returns true if the last used process was dehydrated because it was not
	 * in-use.
	 */
	public boolean dehydrateLastUnusedProcess() {
		BpelProcess lastUnusedProcess = null;
		long lastUsedMinimum = Long.MAX_VALUE;
		for (BpelProcess process : _activeProcesses.values()) {
			if (process.hintIsHydrated()
					&& process.getLastUsed() < lastUsedMinimum
					&& process.getInstanceInUseCount() == 0) {
				lastUsedMinimum = process.getLastUsed();
				lastUnusedProcess = process;
			}
		}
		if (lastUnusedProcess != null) {
			lastUnusedProcess.dehydrate();
			return true;
		}
		return false;
	}

	public void sendMyRoleFault(BpelProcess process, JobDetails we,
			int causeCode) {
		MessageExchange mex = (MessageExchange) getMessageExchange(we
				.getMexId());
		if (!(mex instanceof MyRoleMessageExchange)) {
			return;
		}
		QName faultQName = null;
		OConstants constants = process.getOProcess().constants;
		if (constants != null) {
			Document document = DOMUtils.newDocument();
			Element faultElement = document.createElementNS(
					Namespaces.SOAP_ENV_NS, "Fault");
			Element faultDetail = document.createElementNS(
					Namespaces.ODE_EXTENSION_NS, "fault");
			faultElement.appendChild(faultDetail);
			switch (causeCode) {
			case InvalidProcessException.TOO_MANY_PROCESSES_CAUSE_CODE:
				faultQName = constants.qnTooManyProcesses;
				faultDetail
						.setTextContent("The total number of processes in use is over the limit.");
				break;
			case InvalidProcessException.TOO_HUGE_PROCESSES_CAUSE_CODE:
				faultQName = constants.qnTooHugeProcesses;
				faultDetail
						.setTextContent("The total size of processes in use is over the limit");
				break;
			case InvalidProcessException.TOO_MANY_INSTANCES_CAUSE_CODE:
				faultQName = constants.qnTooManyInstances;
				faultDetail
						.setTextContent("No more instances of the process allowed at start at this time.");
				break;
			case InvalidProcessException.RETIRED_CAUSE_CODE:
				// we're invoking a target process, trying to see if we can
				// retarget the
				// message
				// to the current version (only applies when it's a new process
				// creation)
				for (BpelProcess activeProcess : _activeProcesses.values()) {
					if (activeProcess
							.getConf()
							.getState()
							.equals(org.apache.ode.bpel.iapi.ProcessState.ACTIVE)
							&& activeProcess.getConf().getType()
									.equals(process.getConf().getType())) {
						we.setProcessId(activeProcess._pid);
						((MyRoleMessageExchangeImpl) mex)._process = activeProcess;
						process.handleJobDetails(we);
						return;
					}
				}
				faultQName = constants.qnRetiredProcess;
				faultDetail
						.setTextContent("The process you're trying to instantiate has been retired.");
				break;
			case InvalidProcessException.DEFAULT_CAUSE_CODE:
			default:
				faultQName = constants.qnUnknownFault;
				break;
			}
			MexDaoUtil.setFaulted((MessageExchangeImpl) mex, faultQName,
					faultElement);
		}
	}

	private void sendPartnerRoleFailure(JobDetails we, FailureType failureType) {
		MessageExchange mex = (MessageExchange) getMessageExchange(we
				.getMexId());
		if (mex instanceof PartnerRoleMessageExchange) {
			if (mex.getStatus() == MessageExchange.Status.ASYNC
					|| mex.getStatus() == MessageExchange.Status.REQUEST) {
				String msg = "No response received for invoke (mexId="
						+ we.getMexId() + "), forcing it into a failed state.";
				if (__log.isDebugEnabled())
					__log.debug(msg);
				MexDaoUtil.setFailure((PartnerRoleMessageExchangeImpl) mex,
						failureType, msg, null);
			}
		}
	}

	public BpelProcess getNewestProcessByType(QName processType) {
		int v = -1;
		BpelProcess q = null;
		for (BpelProcess p : _activeProcesses.values()) {
			if (p.getProcessType().equals(processType) && v < p.getVersion()) {
				v = p.getVersion();
				q = p;
			}
		}
		return q;
	}
	
	// AH:
	public void setProcessConfLoader(ProcessConfLoader loader) {
		processConfLoader = loader;
	}

	public ProcessConfLoader getProcessConfLoader() {
		return processConfLoader;
	}

	public void setProcessRegistry(ProcessRegistry registry) {
		this.processRegistry = registry;

	}

	public ProcessRegistry getProcessRegistry() {
		return processRegistry;

	}

	public void setFragmentCompositionEventBroker(
			FragmentCompositionEventBroker fcEventBroker) {
		this.fcEventBroker = fcEventBroker;
	}

	public FragmentCompositionEventBroker getFragmentCompositionEventBroker() {
		return fcEventBroker;
	}

	public void setBpelDatabase(BpelDatabase db) {
		this.db = db;
	}

	public FCManagementDAO getFCManagementDAO() {
		return db.getConnection().getFCManagement();
	}

	public DeploymentUnitNameGenerator getDeploymentUnitNameGenerator() {
		return deploymentUnitNameGenerator;
	}
	// AH: end
}
