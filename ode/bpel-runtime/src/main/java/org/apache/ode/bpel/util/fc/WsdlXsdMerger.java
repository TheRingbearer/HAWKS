package org.apache.ode.bpel.util.fc;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.dd.DeployDocument;
import org.apache.ode.bpel.dd.TDeployment;
import org.apache.ode.bpel.dd.TInvoke;
import org.apache.ode.bpel.dd.TProvide;
import org.apache.ode.bpel.iapi.ProcessConf;
import org.apache.xmlbeans.XmlException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class WsdlXsdMerger {
	private static final Log __log = LogFactory.getLog(WsdlXsdMerger.class);
	private HashMap<File, File> fileRenamingMap;
	private HashMap<File, File> reverseFileCopyMap;

	private HashMap<File, Document> newParsedDocuments;

	public WsdlXsdMerger() {
		reverseFileCopyMap = new HashMap<File, File>();
		fileRenamingMap = new HashMap<File, File>();
		newParsedDocuments = new HashMap<File, Document>();
	}

	public void merge(ProcessConf into, ProcessConf from) {
		List<File> intoFiles = into.getFiles();

		List<File> fromFiles = from.getFiles();
		List<File> xsdFiles = findXsdFiles(fromFiles);
		List<File> wsdlFiles = findWsdlFiles(fromFiles);
		File fromDd = findDeploymentDescriptorFile(fromFiles);
		File toDd = findDeploymentDescriptorFile(intoFiles);

		parseXMLs(xsdFiles, newParsedDocuments);
		parseXMLs(wsdlFiles, newParsedDocuments);

		mergeFiles(xsdFiles, fromDd.getParentFile(), toDd.getParentFile());
		mergeFiles(wsdlFiles, fromDd.getParentFile(), toDd.getParentFile());
		updateImportReferences();

		filterOutExistingServices(findWsdlFiles(intoFiles), wsdlFiles);
		writeFiles();

		mergeDeploymentDescriptors(toDd, fromDd, into.getType(), from.getType());
	}

	private Node findProcess(Document doc, QName processName) {
		Node found = null;
		NodeList processList = doc.getDocumentElement().getElementsByTagName(
				"process");
		for (int i = 0; i < processList.getLength(); i++) {
			Node process = processList.item(i);
			NamedNodeMap map = process.getAttributes();
			Node nameNode = map.getNamedItem("name");
			if (nameNode != null) {
				String foundProcessName = nameNode.getNodeValue();
				QName foundQName = extractQName(process, foundProcessName);
				if (processName.equals(foundQName)) {
					found = process;
					break;
				}
			}

		}

		return found;
	}

	private String extractPrefix(String name) {
		String prefix = null;
		int index = name.indexOf(":");
		if (index > 0) {
			prefix = name.substring(0, index);
		}
		return prefix;
	}

	private String extractLocalName(String name) {
		String localPart = null;
		int index = name.indexOf(":");
		if (index > 0) {
			localPart = name.substring(index + 1);
		}
		return localPart;
	}

	private QName extractQName(Node node, String foundProcessName) {
		QName result = null;
		Node currentNode = node;

		while (result == null && currentNode.getParentNode() != null) {

			String prefix = extractPrefix(foundProcessName);
			if (prefix != null) {
				String localPart = extractLocalName(foundProcessName);
				String toFind = "xmlns:" + prefix;
				NamedNodeMap attributes = currentNode.getAttributes();
				for (int i = 0; i < attributes.getLength(); i++) {
					Node attribute = attributes.item(i);
					String attrName = attribute.getNodeName();
					if (attrName.equals(toFind)) {
						result = new QName(attribute.getNodeValue(), localPart);
						break;
					}
				}

			}
			currentNode = currentNode.getParentNode();
		}
		if (result == null) {
			result = new QName("", extractLocalName(foundProcessName));
		}
		return result;
	}

	private void mergeDeploymentDescriptors(File toDd, File fromDd,
			QName toName, QName fromName) {

		try {
			DeployDocument fromDdDoc = DeployDocument.Factory.parse(fromDd);
			List<TDeployment.Process> fromProcesses = fromDdDoc.getDeploy()
					.getProcessList();

			DeployDocument toDdDoc = DeployDocument.Factory.parse(toDd);
			List<TDeployment.Process> toProcesses = toDdDoc.getDeploy()
					.getProcessList();

			// find from process
			TDeployment.Process fromProcess = null;
			for (TDeployment.Process process : fromProcesses) {
				if (process.getName().equals(fromName)) {
					fromProcess = process;
					break;
				}
			}
			// find to process
			TDeployment.Process toProcess = null;
			for (TDeployment.Process process : toProcesses) {
				if (process.getName().equals(toName)) {
					toProcess = process;
					break;
				}
			}

			List<TProvide> fromProvides = fromProcess.getProvideList();
			List<TInvoke> fromInvokes = fromProcess.getInvokeList();

			for (TProvide fromProvide : fromProvides) {
				mergeProvide(toProcess, fromProvide);
			}

			for (TInvoke fromInvoke : fromInvokes) {
				mergeInvoke(toProcess, fromInvoke);
			}

			toDdDoc.save(toDd);
		} catch (XmlException e) {
			__log.error(e);
		} catch (IOException e) {
			__log.error(e);
		}

	}

	private void mergeProvide(TDeployment.Process toProcess,
			TProvide fromProvide) {
		int index = findProvidePartnerLink(toProcess.getProvideList(),
				fromProvide.getPartnerLink());
		if (index == -1) {
			// provide partnerlink not found, so we add it
			TProvide provide = toProcess.addNewProvide();
			if (fromProvide.isNil()) {
				provide.setNil();
			} else {
				provide.setService(fromProvide.getService());
				if (fromProvide.isSetEnableSharing()) {
					provide.setEnableSharing(fromProvide.getEnableSharing());
				}
				provide.setPartnerLink(fromProvide.getPartnerLink());
			}
		} else {
			// there is one already ignore it, there can be only one service per
			// partnerlink
		}
	}

	private int findInvokePartnerLink(List<TInvoke> invokes,
			String partnerLinkName) {
		int index = -1;
		for (int i = 0; i < invokes.size(); i++) {
			TInvoke current = invokes.get(i);
			if (current.getPartnerLink().equals(partnerLinkName)) {
				index = i;
				break;
			}
		}
		return index;
	}

	private void mergeInvoke(TDeployment.Process toProcess, TInvoke fromInvoke) {
		int index = findInvokePartnerLink(toProcess.getInvokeList(),
				fromInvoke.getPartnerLink());
		if (index == -1) {
			// invoke partnerlink not found, so we add it
			TInvoke invoke = toProcess.addNewInvoke();
			if (fromInvoke.isNil()) {
				invoke.setNil();
			} else {
				invoke.setService(fromInvoke.getService());
				if (fromInvoke.isSetBinding()) {
					invoke.setBinding(fromInvoke.getBinding());
				}
				if (fromInvoke.isSetFailureHandling()) {
					invoke.setFailureHandling(fromInvoke.getFailureHandling());
				}
				if (fromInvoke.isSetUsePeer2Peer()) {
					invoke.setUsePeer2Peer(fromInvoke.getUsePeer2Peer());
				}
				invoke.setPartnerLink(fromInvoke.getPartnerLink());
			}
		} else {
			// there is one already ignore it, there can be only one service per
			// partnerlink
		}
	}

	private int findProvidePartnerLink(List<TProvide> provides,
			String partnerLinkName) {
		int index = -1;
		for (int i = 0; i < provides.size(); i++) {
			TProvide current = provides.get(i);
			if (current.getPartnerLink().equals(partnerLinkName)) {
				index = i;
				break;
			}
		}
		return index;
	}

	private Map<String, String> buildPrefixToNamespaceMap(Node node) {
		HashMap<String, String> prefixMap = new HashMap<String, String>();
		Node currentNode = node;
		while (currentNode != null) {
			if (currentNode.hasAttributes()) {
				NamedNodeMap map = currentNode.getAttributes();
				for (int i = 0; i < map.getLength(); i++) {
					Node attribute = map.item(i);
					String attrName = attribute.getNodeName();
					String prefix = extractPrefix(attrName);
					if (prefix != null && prefix.equals("xmlns")) {
						prefixMap.put(extractLocalName(attrName),
								attribute.getNodeValue());
					}
				}
			}

			currentNode = currentNode.getParentNode();
		}
		return prefixMap;
	}

	private Set<String> getRegistredPrefixes(Node node) {
		HashSet<String> registred = new HashSet<String>();
		Node current = node;
		while (current != null) {
			if (current.hasAttributes()) {
				NamedNodeMap map = current.getAttributes();
				for (int i = 0; i < map.getLength(); i++) {
					Node attr = map.item(i);
					String name = attr.getNodeName();
					String attrPrefix = extractPrefix(name);
					if (attrPrefix != null && attrPrefix.equals("xmlns")) {
						registred.add(extractLocalName(name));
					}
				}
			}
			current = current.getParentNode();
		}

		return registred;
	}

	private void registerPrefix(Node attribute, Set<String> registred,
			Map<String, String> prefixToNamespaceMap) {
		Document doc = attribute.getOwnerDocument();

		String value = attribute.getNodeValue();

		String prefix = extractPrefix(value);
		String nameSpace = prefixToNamespaceMap.get(prefix);

		if (registred.contains(prefix)) {
			prefix = generateNewPrefix(registred, prefix);
			StringBuffer buffer = new StringBuffer();
			buffer.append(prefix);
			buffer.append(":");
			buffer.append(extractLocalName(value));
			attribute.setNodeValue(buffer.toString());
		}

		Element rootElement = doc.getDocumentElement();
		rootElement.setAttribute("xmlns:" + prefix, nameSpace);

	}

	private String generateNewPrefix(Set<String> registred, String prefix) {
		int counter = 1;
		String currentPrefix = prefix;
		while (registred.contains(currentPrefix)) {
			currentPrefix = prefix + String.valueOf(counter);
			counter++;
		}
		return currentPrefix;
	}

	private void addNamespacePrefices(Node node,
			Map<String, String> prefixToNamespaceMap) {

		if (node instanceof Element) {
			Element element = (Element) node;
			NodeList serviceList = element.getElementsByTagName("service");
			for (int i = 0; i < serviceList.getLength(); i++) {
				Node service = serviceList.item(i);
				Set<String> registredPrefixes = getRegistredPrefixes(service);
				NamedNodeMap map = service.getAttributes();
				Node nameNode = map.getNamedItem("name");
				Node portNode = map.getNamedItem("port");

				registerPrefix(nameNode, registredPrefixes,
						prefixToNamespaceMap);
				registerPrefix(portNode, registredPrefixes,
						prefixToNamespaceMap);
			}
		}

	}

	private void updateImportReferences() {
		for (File file : fileRenamingMap.values()) {
			Document doc = newParsedDocuments.get(reverseFileCopyMap.get(file));

			replaseLocationInTag(file, doc, "import");
			replaseLocationInTag(file, doc, "include");
		}
	}

	private void replaseLocationInTag(File xmlFile, Document doc, String tagName) {
		String attributeName = "schemaLocation";
		NodeList list = doc.getDocumentElement().getElementsByTagNameNS("*",
				tagName);

		for (int i = 0; i < list.getLength(); i++) {
			Node node = list.item(i);
			NamedNodeMap map = node.getAttributes();
			Node attribute = map.getNamedItem(attributeName);
			if (attribute != null) {
				String fileName = attribute.getNodeValue();
				File tmp = new File(fileName);
				if (!tmp.exists()) {
					// it should exit, because we rename it only if there is
					// already one.
					// it means fileName is relative absolute path to the file
					tmp = new File(xmlFile.getParentFile(), fileName);
				}
				File mapping = fileRenamingMap.get(tmp);
				int indexS = fileName.lastIndexOf('/');
				int indexB = fileName.lastIndexOf('\\');
				int index = Math.max(indexB, indexS);
				StringBuffer newRelativePath = new StringBuffer();
				if (index != -1) {
					newRelativePath.append(fileName.substring(0, index + 1));
				}
				newRelativePath.append(mapping.getName());
				attribute.setNodeValue(newRelativePath.toString());
			}
		}
	}

	private void writeXml(Document doc, File file)
			throws FileNotFoundException, TransformerException {
		TransformerFactory transformerFactory = TransformerFactory
				.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		transformer.setOutputProperty("indent", "yes");
		OutputStream out = new FileOutputStream(file);
		transformer.transform(new DOMSource(doc), new StreamResult(out));

	}

	public static File findDeploymentDescriptorFile(List<File> files) {
		File result = null;
		for (File file : files) {
			if (file.getName().equals("deploy.xml")) {
				result = file;
				break;
			}
		}
		return result;
	}

	private List<File> findXsdFiles(List<File> files) {
		ArrayList<File> result = new ArrayList<File>();
		for (File file : files) {
			if (file.getName().endsWith(".xsd")) {
				result.add(file);
			}
		}
		return result;
	}

	private List<File> findWsdlFiles(List<File> files) {
		ArrayList<File> result = new ArrayList<File>();
		for (File file : files) {
			if (file.getName().endsWith(".wsdl")) {
				result.add(file);
			}
		}
		return result;
	}

	private void copyFile(File from, File to) {

		try {
			InputStream in = new BufferedInputStream(new FileInputStream(from));
			OutputStream out = new FileOutputStream(to);

			byte[] buffer = new byte[1024];
			int len;

			while ((len = in.read(buffer)) > 0) {
				out.write(buffer, 0, len);
			}
			in.close();
			out.close();

		} catch (FileNotFoundException e) {
			__log.error(e);
		} catch (IOException e) {
			__log.error(e);
		}

	}

	private File generateNotExistingFile(File file) {
		File current = file;
		String parent = file.getParent();
		String fullFileName = current.getName();
		String fileName = fullFileName;
		String fileExtension = "";
		int index = fullFileName.lastIndexOf('.');
		if (index > 0) {
			fileName = fullFileName.substring(0, index);
			fileExtension = fullFileName.substring(index + 1);
		}
		int counter = 1;
		while (current.exists()) {
			StringBuffer buffer = new StringBuffer();
			buffer.append(fileName);
			buffer.append("_");
			buffer.append(counter);
			buffer.append(".");
			buffer.append(fileExtension);
			current = new File(parent, buffer.toString());
			counter++;
		}
		return current;
	}

	private void mergeFiles(List<File> files, File baseFolder, File into) {
		int start = baseFolder.getAbsolutePath().length();
		for (File file : files) {
			String path = file.getParent();
			String toCreate = "";
			if (path.length() > start) {
				toCreate = path.substring(start + 1);
			}
			String newPath = into.getAbsolutePath() + File.separator + toCreate;
			File newDir = new File(newPath);
			if (toCreate.length() > 0 && !newDir.exists()) {
				newDir.mkdirs();
			}

			File tmpTarget = new File(newPath, file.getName());
			File targetFile = generateNotExistingFile(tmpTarget);
			fileRenamingMap.put(tmpTarget, targetFile);
			// copyFile(file, targetFile);
			reverseFileCopyMap.put(targetFile, file);
		}
	}

	private void writeFiles() {
		for (File to : reverseFileCopyMap.keySet()) {
			File from = reverseFileCopyMap.get(to);
			try {
				writeXml(newParsedDocuments.get(from), to);
			} catch (FileNotFoundException e) {
				__log.error(e);
			} catch (TransformerException e) {
				__log.error(e);
			}
		}
	}

	private void parseXMLs(List<File> files, Map<File, Document> map) {
		try {
			for (File file : files) {
				parseXML(file, map);
			}

		} catch (ParserConfigurationException e) {
			__log.error(e);
		} catch (SAXException e) {
			__log.error(e);
		} catch (IOException e) {
			__log.error(e);
		}
	}

	private void filterOutExistingServices(List<File> existingWsdls,
			List<File> newWsdls) {
		HashSet<QName> existingServices = new HashSet<QName>();

		try {
			for (File wsdl : existingWsdls) {
				Document doc = parseXML(wsdl);
				existingServices.addAll(getServices(doc));
			}
			for (File wsdl : newWsdls) {
				Document doc = newParsedDocuments.get(wsdl);
				List<QName> services = getServices(doc);
				for (QName service : services) {
					if (existingServices.contains(service)) {
						removeService(doc, service);
					}
				}
			}

		} catch (ParserConfigurationException e) {
			__log.error(e);
		} catch (SAXException e) {
			__log.error(e);
		} catch (IOException e) {
			__log.error(e);
		}

	}

	private String getTargetNamespace(Document doc) {
		String namespace = "";
		Node def = doc.getDocumentElement();
		if (def.hasAttributes()) {
			NamedNodeMap map = def.getAttributes();
			Node tns = map.getNamedItem("targetNamespace");
			if (tns != null) {
				namespace = tns.getNodeValue();
			}
		}
		return namespace;
	}

	private void removeService(Document doc, QName service) {
		String tns = getTargetNamespace(doc);
		NodeList services = doc.getDocumentElement().getElementsByTagNameNS(
				"*", "service");
		for (int i = 0; i < services.getLength(); i++) {
			Node current = services.item(i);
			if (current.hasAttributes()) {
				NamedNodeMap map = current.getAttributes();
				Node nameAttr = map.getNamedItem("name");
				if (nameAttr != null) {
					QName serviceName = new QName(tns, nameAttr.getNodeValue());
					if (serviceName.equals(service)) {
						current.getParentNode().removeChild(current);
					}
				}
			}
		}

	}

	private void parseXML(File file, Map<File, Document> map)
			throws ParserConfigurationException, SAXException, IOException {
		Document doc = parseXML(file);
		map.put(file, doc);
	}

	private Document parseXML(File file) throws ParserConfigurationException,
			SAXException, IOException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder docBuilder;
		docBuilder = factory.newDocumentBuilder();
		Document doc = docBuilder.parse(file);
		return doc;
	}

	private List<QName> getServices(Document doc) {
		ArrayList<QName> result = new ArrayList<QName>();

		String namespace = getTargetNamespace(doc);

		NodeList list = doc.getDocumentElement().getElementsByTagNameNS("*",
				"service");
		for (int i = 0; i < list.getLength(); i++) {
			Node node = list.item(i);
			if (node.hasAttributes()) {
				NamedNodeMap map = node.getAttributes();
				Node attribute = map.getNamedItem("name");
				if (attribute != null) {
					result.add(new QName(namespace, attribute.getNodeValue()));
				}
			}
		}

		return result;
	}
}
