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

package org.apache.ode.axis2.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.activation.DataHandler;
import javax.wsdl.Definition;
import javax.wsdl.WSDLException;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;

import org.apache.axiom.attachments.ByteArrayDataSource;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.OMText;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.engine.AxisEngine;
import org.apache.axis2.receivers.AbstractMessageReceiver;
import org.apache.axis2.util.Utils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.axis2.OdeFault;
import org.apache.ode.axis2.deploy.DeploymentPoller;
import org.apache.ode.axis2.hooks.ODEAxisService;
import org.apache.ode.axis2.parser.XMLReader;
import org.apache.ode.bpel.compiler.modelMigration.ProcessModelChangeRegistry;
import org.apache.ode.bpel.iapi.BpelServer;
import org.apache.ode.bpel.iapi.ContextException;
import org.apache.ode.bpel.iapi.ProcessConf;
import org.apache.ode.bpel.iapi.ProcessStore;
import org.apache.ode.il.OMUtils;
import org.apache.ode.utils.Namespaces;
import org.apache.ode.utils.fs.FileUtils;

/**
 * Axis wrapper for process deployment.
 */
public class DeploymentWebService {

	private static final Log __log = LogFactory
			.getLog(DeploymentWebService.class);

	private final OMNamespace _pmapi;
	private final OMNamespace _deployapi;

	private File _deployPath;
	private DeploymentPoller _poller;
	private ProcessStore _store;

	// @hahnml
	private BpelServer _server;

	public DeploymentWebService() {
		_pmapi = OMAbstractFactory.getOMFactory().createOMNamespace(
				"http://www.apache.org/ode/pmapi", "pmapi");
		_deployapi = OMAbstractFactory.getOMFactory().createOMNamespace(
				"http://www.apache.org/ode/deployapi", "deployapi");
	}

	// @hahnml: BPELServer parameter added for instance migration
	public void enableService(AxisConfiguration axisConfig, ProcessStore store,
			DeploymentPoller poller, BpelServer server, String rootpath,
			String workPath) throws AxisFault, WSDLException {
		_deployPath = new File(workPath, "processes");
		_store = store;
		_poller = poller;

		// @hahnml: Set the server
		_server = server;

		// @hahnml: Set the _server to the ProcessModelChangeRegistry
		ProcessModelChangeRegistry.getRegistry().setBpelServer(_server);

		Definition def;
		WSDLReader wsdlReader = WSDLFactory.newInstance().newWSDLReader();
		wsdlReader.setFeature("javax.wsdl.verbose", false);

		File wsdlFile = new File(rootpath + "/deploy.wsdl");
		def = wsdlReader.readWSDL(wsdlFile.toURI().toString());
		AxisService deployService = ODEAxisService.createService(axisConfig,
				new QName("http://www.apache.org/ode/deployapi",
						"DeploymentService"), "DeploymentPort",
				"DeploymentService", def, new DeploymentMessageReceiver());
		axisConfig.addService(deployService);
	}

	class DeploymentMessageReceiver extends AbstractMessageReceiver {

		public void invokeBusinessLogic(MessageContext messageContext)
				throws AxisFault {
			String operation = messageContext.getAxisOperation().getName()
					.getLocalPart();
			SOAPFactory factory = getSOAPFactory(messageContext);
			boolean unknown = false;

			try {
				if (operation.equals("deploy")) {
					// @hahnml: Reset the ProcessModelChangeRegistry
					ProcessModelChangeRegistry.getRegistry().clearAll();

					OMElement deployElement = messageContext.getEnvelope()
							.getBody().getFirstElement();
					OMElement namePart = deployElement
							.getFirstChildWithName(new QName(null, "name"));
					// "be liberal in what you accept from others"
					if (namePart == null) {
						namePart = OMUtils.getFirstChildWithName(deployElement,
								"name");
						if (namePart == null) {
							throw new OdeFault("The name part is missing");
						} else if (__log.isWarnEnabled()) {
							__log.warn("Invalid incoming request detected for operation "
									+ messageContext.getAxisOperation()
											.getName()
									+ ". Name part should have no namespace but has "
									+ namePart.getQName().getNamespaceURI());
						}
					}

					OMElement packagePart = deployElement
							.getFirstChildWithName(new QName(null, "package"));

					// "be liberal in what you accept from others"
					if (packagePart == null) {
						packagePart = OMUtils.getFirstChildWithName(
								deployElement, "package");
						if (packagePart != null && __log.isWarnEnabled()) {
							__log.warn("Invalid incoming request detected for operation "
									+ messageContext.getAxisOperation()
											.getName()
									+ ". Package part should have no namespace but has "
									+ packagePart.getQName().getNamespaceURI());
						}
					}

					OMElement zip = null;
					if (packagePart != null) {
						zip = packagePart.getFirstChildWithName(new QName(
								Namespaces.ODE_DEPLOYAPI_NS, "zip"));
						// "be liberal in what you accept from others"
						if (zip == null) {
							zip = OMUtils.getFirstChildWithName(packagePart,
									"zip");
							if (zip != null && __log.isWarnEnabled()) {
								String ns = zip.getQName().getNamespaceURI() == null
										|| zip.getQName().getNamespaceURI()
												.length() == 0 ? "empty" : zip
										.getQName().getNamespaceURI();
								__log.warn("Invalid incoming request detected for operation "
										+ messageContext.getAxisOperation()
												.getName()
										+ ". <zip/> element namespace should be "
										+ Namespaces.ODE_DEPLOYAPI_NS
										+ " but was " + ns);
							}
						}
					}

					if (zip == null || packagePart == null)
						throw new OdeFault(
								"Your message should contain an element named 'package' with a 'zip' element");

					String bundleName = namePart.getText().trim();
					if (!validBundleName(namePart.getText()))
						throw new OdeFault(
								"Invalid bundle name, only non empty alpha-numerics and _ strings are allowed.");

					OMText binaryNode = (OMText) zip.getFirstOMChild();
					if (binaryNode == null) {
						throw new OdeFault(
								"Empty binary node under <zip> element");
					}
					binaryNode.setOptimize(true);
					File dest = null;
					try {
						// We're going to create a directory under the
						// deployment root and put
						// files in there. The poller shouldn't pick them up so
						// we're asking
						// it to hold on for a while.
						_poller.hold();
						dest = new File(_deployPath, bundleName + "-"
								+ _store.getCurrentVersion());
						dest.mkdir();
						unzip(dest, (DataHandler) binaryNode.getDataHandler());

						// Check that we have a deploy.xml
						File deployXml = new File(dest, "deploy.xml");
						if (!deployXml.exists())
							throw new OdeFault(
									"The deployment doesn't appear to contain a deployment "
											+ "descriptor in its root directory named deploy.xml, aborting.");

						Collection<QName> deployed = _store.deploy(dest);

						// @hahnml: DeploymentPoller creates the deployed file,
						// so it should be invoked first to avoid an exception
						// Telling the poller what we deployed so that it
						// doesn't try to deploy it again
						_poller.markAsDeployed(dest);

						File deployedMarker = new File(_deployPath,
								dest.getName() + ".deployed");
						deployedMarker.createNewFile();

						__log.info("Deployment of artifact " + dest.getName()
								+ " successful.");

						OMElement response = factory.createOMElement(
								"response", null);

						if (__log.isDebugEnabled())
							__log.debug("Deployed package: " + dest.getName());
						OMElement d = factory.createOMElement("name",
								_deployapi);
						d.setText(dest.getName());
						response.addChild(d);

						for (QName pid : deployed) {
							if (__log.isDebugEnabled())
								__log.debug("Deployed PID: " + pid);
							d = factory.createOMElement("id", _deployapi);
							d.setText(pid);
							response.addChild(d);
						}
						sendResponse(factory, messageContext, "deployResponse",
								response);
					} catch (ContextException ce) {
						if (dest != null) {
							deleteDir(dest);
						}
						throw ce;
					} finally {
						_poller.release();
					}
				} else if (operation.equals("deployNewVersion")) {
					// @hahnml: Reset the ProcessModelChangeRegistry
					ProcessModelChangeRegistry.getRegistry().clear();
					ProcessModelChangeRegistry.getRegistry().setModelChanged(
							true);

					OMElement deployElement = messageContext.getEnvelope()
							.getBody().getFirstElement();
					OMElement namePart = deployElement
							.getFirstChildWithName(new QName(null, "name"));
					// "be liberal in what you accept from others"
					if (namePart == null) {
						namePart = OMUtils.getFirstChildWithName(deployElement,
								"name");
						if (namePart == null) {
							throw new OdeFault("The name part is missing");
						} else if (__log.isWarnEnabled()) {
							__log.warn("Invalid incoming request detected for operation "
									+ messageContext.getAxisOperation()
											.getName()
									+ ". Name part should have no namespace but has "
									+ namePart.getQName().getNamespaceURI());
						}
					}

					OMElement packagePart = deployElement
							.getFirstChildWithName(new QName(null, "package"));

					// "be liberal in what you accept from others"
					if (packagePart == null) {
						packagePart = OMUtils.getFirstChildWithName(
								deployElement, "package");
						if (packagePart != null && __log.isWarnEnabled()) {
							__log.warn("Invalid incoming request detected for operation "
									+ messageContext.getAxisOperation()
											.getName()
									+ ". Package part should have no namespace but has "
									+ packagePart.getQName().getNamespaceURI());
						}
					}

					OMElement zip = null;
					if (packagePart != null) {
						zip = packagePart.getFirstChildWithName(new QName(
								Namespaces.ODE_DEPLOYAPI_NS, "zip"));
						// "be liberal in what you accept from others"
						if (zip == null) {
							zip = OMUtils.getFirstChildWithName(packagePart,
									"zip");
							if (zip != null && __log.isWarnEnabled()) {
								String ns = zip.getQName().getNamespaceURI() == null
										|| zip.getQName().getNamespaceURI()
												.length() == 0 ? "empty" : zip
										.getQName().getNamespaceURI();
								__log.warn("Invalid incoming request detected for operation "
										+ messageContext.getAxisOperation()
												.getName()
										+ ". <zip/> element namespace should be "
										+ Namespaces.ODE_DEPLOYAPI_NS
										+ " but was " + ns);
							}
						}
					}

					if (zip == null || packagePart == null)
						throw new OdeFault(
								"Your message should contain an element named 'package' with a 'zip' element");

					String bundleName = namePart.getText().trim();
					if (!validBundleName(namePart.getText()))
						throw new OdeFault(
								"Invalid bundle name, only non empty alpha-numerics and _ strings are allowed.");

					OMText binaryNode = (OMText) zip.getFirstOMChild();
					if (binaryNode == null) {
						throw new OdeFault(
								"Empty binary node under <zip> element");
					}
					binaryNode.setOptimize(true);
					File dest = null;
					try {
						// We're going to create a directory under the
						// deployment root and put
						// files in there. The poller shouldn't pick them up so
						// we're asking
						// it to hold on for a while.
						_poller.hold();

						dest = new File(_deployPath, bundleName + "-"
								+ _store.getCurrentVersion());
						dest.mkdir();
						unzip(dest, (DataHandler) binaryNode.getDataHandler());

						// Check that we have a deploy.xml
						File deployXml = new File(dest, "deploy.xml");
						if (!deployXml.exists())
							throw new OdeFault(
									"The deployment doesn't appear to contain a deployment "
											+ "descriptor in its root directory named deploy.xml, aborting.");

						// schlieta
						// Parser
						// Version attribute is auto incremented and so an
						// unique identifier for a deployed process model
						long currentVersion = _store.getCurrentVersion();

						// @sonntamo: don't need to attach version number to
						// process name, address, etc. because concurrent
						// workflow evolution works with the same address. Meta
						// data is used to address the desired model version.
						// XMLReader.parse(dest, currentVersion);

						// InstanceID vom Client
						OMElement instanceIDPart = deployElement
								.getFirstChildWithName(new QName(null,
										"InstanceID"));

						// "be liberal in what you accept from others"
						if (instanceIDPart == null) {
							instanceIDPart = OMUtils.getFirstChildWithName(
									deployElement, "InstanceID");
							if (instanceIDPart != null && __log.isWarnEnabled()) {
								__log.warn("Invalid incoming request detected for operation "
										+ messageContext.getAxisOperation()
												.getName()
										+ ". InstanceID part should have no namespace but has "
										+ packagePart.getQName()
												.getNamespaceURI());
							}
						}

						String iid = instanceIDPart.getText();

						// end schlieta

						Collection<QName> deployed = _store.deploy(dest, true,
								null, true, true);

						// @hahnml: DeploymentPoller creates the deployed file,
						// so it should be invoked first to avoid an exception
						// Telling the poller what we deployed so that it
						// doesn't try to deploy it again
						_poller.markAsDeployed(dest);
						__log.info("Deployment of artifact " + dest.getName()
								+ " successful.");

						File deployedMarker = new File(_deployPath,
								dest.getName() + ".deployed");
						deployedMarker.createNewFile();

						OMElement response = factory.createOMElement(
								"response", null);

						if (__log.isDebugEnabled())
							__log.debug("Deployed package: " + dest.getName());
						OMElement d = factory.createOMElement("name",
								_deployapi);
						d.setText(dest.getName());
						response.addChild(d);

						for (QName pid : deployed) {
							if (__log.isDebugEnabled())
								__log.debug("Deployed PID: " + pid);
							d = factory.createOMElement("id", _deployapi);
							d.setText(pid);
							response.addChild(d);
						}

						// schlieta db change
						// @hahnml: Moved to BpelServerImpl
						_server.migrateProcessInstanceMetaData(deployed
								.iterator().next(), Long.parseLong(iid),
								currentVersion);

						sendResponse(factory, messageContext,
								"deployNewVersionResponse", response);
						// end schlieta
					} catch (ContextException ce) {
						if (dest != null) {
							deleteDir(dest);
						}
						throw ce;
					} finally {
						_poller.release();
					}
				}
				// end schlieta
				else if (operation.equals("undeploy")) {
					OMElement part = messageContext.getEnvelope().getBody()
							.getFirstElement().getFirstElement();
					if (part == null)
						throw new OdeFault(
								"Missing bundle name in undeploy message.");

					String pkg = part.getText().trim();
					if (!validBundleName(pkg)) {
						throw new OdeFault(
								"Invalid bundle name, only non empty alpha-numerics and _ strings are allowed.");
					}

					File deploymentDir = new File(_deployPath, pkg);
					if (!deploymentDir.exists())
						throw new OdeFault("Couldn't find deployment package "
								+ pkg + " in directory " + _deployPath);

					try {
						// We're going to delete files & directories under the
						// deployment root.
						// Put the poller on hold to avoid undesired side
						// effects
						_poller.hold();

						Collection<QName> undeployed = _store
								.undeploy(deploymentDir);

						// @hahnml: DeploymentPoller should invoked before the
						// directory is removed
						_poller.markAsUndeployed(deploymentDir);

						File deployedMarker = new File(deploymentDir
								+ ".deployed");
						deployedMarker.delete();
						FileUtils.deepDelete(deploymentDir);

						OMElement response = factory.createOMElement(
								"response", null);
						response.setText("" + (undeployed.size() > 0));
						sendResponse(factory, messageContext,
								"undeployResponse", response);

					} finally {
						_poller.release();
					}
				} else if (operation.equals("listDeployedPackages")) {
					Collection<String> packageNames = _store.getPackages();
					OMElement response = factory.createOMElement(
							"deployedPackages", null);
					for (String name : packageNames) {
						OMElement nameElmt = factory.createOMElement("name",
								_deployapi);
						nameElmt.setText(name);
						response.addChild(nameElmt);
					}
					sendResponse(factory, messageContext,
							"listDeployedPackagesResponse", response);
				} else if (operation.equals("listProcesses")) {
					OMElement namePart = messageContext.getEnvelope().getBody()
							.getFirstElement().getFirstElement();
					List<QName> processIds = _store.listProcesses(namePart
							.getText());
					if (processIds == null) {
						throw new OdeFault("Could not find process package: "
								+ namePart.getText());
					}
					OMElement response = factory.createOMElement("processIds",
							null);
					for (QName qname : processIds) {
						OMElement nameElmt = factory.createOMElement("id",
								_deployapi);
						nameElmt.setText(qname);
						response.addChild(nameElmt);
					}
					sendResponse(factory, messageContext,
							"listProcessesResponse", response);
				} else if (operation.equals("getProcessPackage")) {
					OMElement qnamePart = messageContext.getEnvelope()
							.getBody().getFirstElement().getFirstElement();
					ProcessConf process = _store
							.getProcessConfiguration(OMUtils
									.getTextAsQName(qnamePart));
					if (process == null) {
						throw new OdeFault("Could not find process: "
								+ qnamePart.getTextAsQName());
					}
					String packageName = _store.getProcessConfiguration(
							OMUtils.getTextAsQName(qnamePart)).getPackage();
					OMElement response = factory.createOMElement("packageName",
							null);
					response.setText(packageName);
					sendResponse(factory, messageContext,
							"getProcessPackageResponse", response);
				} else if (operation.equals("getDeploymentBundle")) {
					// @hahnml: Reply the deployment bundle (and its files) for
					// the given package name
					OMElement qnamePart = messageContext.getEnvelope()
							.getBody().getFirstElement().getFirstElement();
					if (qnamePart == null)
						throw new OdeFault(
								"Missing bundle name in getDeploymentBundle message.");

					// @hahnml: Get the package name
					String pkg = OMUtils.getTextAsQName(qnamePart)
							.getLocalPart();
					if (!validBundleName(pkg)) {
						throw new OdeFault(
								"Invalid bundle name, only non empty alpha-numerics and _ strings are allowed.");
					}

					// @hahnml: Check if the bundle exists
					File deploymentDir = new File(_deployPath, pkg);
					if (!deploymentDir.exists())
						throw new OdeFault("Couldn't find deployment package "
								+ pkg + " in directory " + _deployPath);

					OMElement zip = factory.createOMElement(new QName(
							Namespaces.ODE_DEPLOYAPI_NS, "zip"));

					// @hahnml: Get all the files of the directory and add them
					// to the binaryNode
					byte[] content = zipFolder(deploymentDir);

					OMText binaryNode = factory.createOMText(new DataHandler(
							new ByteArrayDataSource((byte[]) content)), true);
					binaryNode.setOptimize(true);

					OMElement response = factory.createOMElement("package",
							null);

					zip.addChild(binaryNode);
					response.addChild(zip);

					sendResponse(factory, messageContext,
							"getDeploymentBundleResponse", response);
				} else
					unknown = true;
			} catch (Throwable t) {
				// Trying to extract a meaningful message
				Throwable source = t;
				while (source.getCause() != null && source.getCause() != source)
					source = source.getCause();
				__log.warn("Invocation of operation " + operation + " failed",
						t);
				throw new OdeFault("Invocation of operation " + operation
						+ " failed: " + source.toString(), t);
			}
			if (unknown)
				throw new OdeFault("Unknown operation: '"
						+ messageContext.getAxisOperation().getName() + "'");
		}

		private File buildUnusedDir(File deployPath, String dirName) {
			int v = 1;
			while (new File(deployPath, dirName + "-" + v).exists())
				v++;
			return new File(deployPath, dirName + "-" + v);
		}

		private void unzip(File dest, DataHandler dataHandler) throws AxisFault {
			try {
				ZipInputStream zis = new ZipInputStream(dataHandler
						.getDataSource().getInputStream());
				ZipEntry entry;
				// Processing the package
				while ((entry = zis.getNextEntry()) != null) {
					if (entry.isDirectory()) {
						__log.debug("Extracting directory: " + entry.getName());
						new File(dest, entry.getName()).mkdir();
						continue;
					}
					__log.debug("Extracting file: " + entry.getName());
					File destFile = new File(dest, entry.getName());
					if (!destFile.getParentFile().exists())
						destFile.getParentFile().mkdirs();
					copyInputStream(zis, new BufferedOutputStream(
							new FileOutputStream(destFile)));
				}
				zis.close();
			} catch (IOException e) {
				throw new OdeFault("An error occured on deployment.", e);
			}
		}

		// @hahnml: Zip a deployment directory
		private byte[] zipFolder(File sourceFolder) {
			byte[] result = null;

			int BUFFER = 4096;
			ByteArrayOutputStream dataOut = new ByteArrayOutputStream(BUFFER);

			try {
				// compress outfile stream
				ZipOutputStream out = new ZipOutputStream(dataOut);

				// writing stream
				BufferedInputStream in = null;

				byte[] data = new byte[BUFFER];
				String files[] = sourceFolder.list(new FilenameFilter() {

					public boolean accept(File dir, String name) {
						boolean accept = true;
						// We don't want cbp, log, debug and bpelex files in the
						// zip
						if (name.endsWith(".cbp") || name.endsWith(".log")) {
							accept = false;
						}
						return accept;
					}
				});

				for (int i = 0; i < files.length; i++) {
					in = new BufferedInputStream(new FileInputStream(
							sourceFolder.getPath() + "/" + files[i]), BUFFER);

					out.putNextEntry(new ZipEntry(files[i])); // write data
																// header
					// (name, size, etc)
					int count;
					while ((count = in.read(data, 0, BUFFER)) != -1) {
						out.write(data, 0, count);
					}
					out.closeEntry(); // close each entry
				}

				out.flush();
				out.close();
				in.close();

				result = dataOut.toByteArray();
				dataOut.close();
			} catch (Exception e) {
				e.printStackTrace();
			}

			return result;
		}

		private void sendResponse(SOAPFactory factory,
				MessageContext messageContext, String op, OMElement response)
				throws AxisFault {
			MessageContext outMsgContext = Utils
					.createOutMessageContext(messageContext);
			outMsgContext.getOperationContext()
					.addMessageContext(outMsgContext);

			SOAPEnvelope envelope = factory.getDefaultEnvelope();
			outMsgContext.setEnvelope(envelope);

			OMElement responseOp = factory.createOMElement(op, _pmapi);
			responseOp.addChild(response);
			envelope.getBody().addChild(responseOp);
			AxisEngine.send(outMsgContext);
		}

		private boolean validBundleName(String bundle) {
			boolean valid;
			if (StringUtils.isBlank(bundle))
				valid = false;
			else
				valid = bundle.matches("[\\p{L}0-9_\\-]*");
			if (__log.isDebugEnabled()) {
				__log.debug("Validating bundle " + bundle + " valid: " + valid);
			}
			return valid;
		}
	}

	private static void copyInputStream(InputStream in, OutputStream out)
			throws IOException {
		byte[] buffer = new byte[1024];
		int len;
		while ((len = in.read(buffer)) >= 0)
			out.write(buffer, 0, len);
		out.close();
	}

	private static boolean deleteDir(File dir) {
		if (dir.isDirectory()) {
			String[] children = dir.list();
			for (int i = 0; i < children.length; i++) {
				boolean success = deleteDir(new File(dir, children[i]));
				if (!success) {
					return false;
				}
			}
		}
		return dir.delete();
	}

}
