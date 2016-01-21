package org.apache.ode.store;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.wsdl.Definition;
import javax.xml.namespace.QName;

import org.apache.ode.bpel.evt.BpelEvent.TYPE;
import org.apache.ode.bpel.iapi.Endpoint;
import org.apache.ode.bpel.iapi.EndpointReference;
import org.apache.ode.bpel.iapi.ProcessConf;
import org.apache.ode.bpel.iapi.ProcessState;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class FragmentConfImpl implements ProcessConf {
	private ProcessConf conf;
	private QName processId;
	private long version;

	public FragmentConfImpl(ProcessConf conf) {
		this.conf = conf;
		version = conf.getNextVersion();

		// assign new processId
		String name = conf.getProcessId().getLocalPart();
		int index = name.lastIndexOf('-');
		String newName = name.substring(0, index + 1);
		newName += String.valueOf(version);
		processId = new QName(conf.getProcessId().getNamespaceURI(), newName);
	}

	public URI getBaseURI() {
		return conf.getBaseURI();
	}

	public String getBpelDocument() {
		return conf.getBpelDocument();
	}

	public long getCBPFileSize() {
		return conf.getCBPFileSize();
	}

	public InputStream getCBPInputStream() {
		return conf.getCBPInputStream();
	}

	public Set<CLEANUP_CATEGORY> getCleanupCategories(boolean instanceSucceeded) {
		return conf.getCleanupCategories(instanceSucceeded);
	}

	public List<CronJob> getCronJobs() {
		return conf.getCronJobs();
	}

	public Definition getDefinitionForPortType(QName portTypeName) {
		return conf.getDefinitionForPortType(portTypeName);
	}

	public Definition getDefinitionForService(QName serviceName) {
		return conf.getDefinitionForService(serviceName);
	}

	public Date getDeployDate() {
		return conf.getDeployDate();
	}

	public String getDeployer() {
		return conf.getDeployer();
	}

	public Map<String, String> getEndpointProperties(EndpointReference epr) {
		return conf.getEndpointProperties(epr);
	}

	public List<Element> getExtensionElement(QName qname) {
		return conf.getExtensionElement(qname);
	}

	public List<File> getFiles() {
		return conf.getFiles();
	}

	public Map<String, Endpoint> getInvokeEndpoints() {
		return conf.getInvokeEndpoints();
	}

	public String getPackage() {
		return conf.getPackage();
	}

	public Map<String, PartnerRoleConfig> getPartnerRoleConfig() {
		return conf.getPartnerRoleConfig();
	}

	public QName getProcessId() {
		return processId;
	}

	public Map<QName, Node> getProcessProperties() {
		return conf.getProcessProperties();
	}

	public Map<String, Endpoint> getProvideEndpoints() {
		return conf.getProvideEndpoints();
	}

	public ProcessState getState() {
		return conf.getState();
	}

	public QName getType() {
		return conf.getType();
	}

	public long getVersion() {
		return version;
	}

	public boolean isCleanupCategoryEnabled(boolean instanceSucceeded,
			CLEANUP_CATEGORY category) {
		return conf.isCleanupCategoryEnabled(instanceSucceeded, category);
	}

	public boolean isEventEnabled(List<String> scopeNames, TYPE type) {
		return conf.isEventEnabled(scopeNames, type);
	}

	public boolean isSharedService(QName serviceName) {
		return conf.isSharedService(serviceName);
	}

	public boolean isTransient() {
		return conf.isTransient();
	}

	public long getNextVersion() {
		return conf.getNextVersion();
	}

	//@sonntamo
	public Map<String, String> getMetaData() {
		return conf.getMetaData();
	}
}
