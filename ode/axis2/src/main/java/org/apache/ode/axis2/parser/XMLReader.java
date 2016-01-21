package org.apache.ode.axis2.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * XMLParser used to attach the version number of the process to the
 * <ul>
 * <li>process name in the BPEL file,</li>
 * <li>process name in the DD,</li>
 * <li>service name in the DD,</li>
 * <li>service name in the process WSDL,</li>
 * <li>port address in the process WSDL.</li>
 * </ul>
 * 
 * @author schlieta
 * @author hahnml
 * @author sonntamo
 * 
 */
public class XMLReader {

	public static void parse(File dest, long version) {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		dbf.setNamespaceAware(true);
		String processName = "";
		try {
			db = dbf.newDocumentBuilder();

			try {

				// parse BPEL files
				for (File f : dest.listFiles(new BPELFilter())) {
					Document doc = db.parse(f);
					doc.setStrictErrorChecking(false);
					doc.getDocumentElement().normalize();

					// Check if anything is found, if not query again
					// without the prefix
					NodeList nodeLst = doc.getElementsByTagName("bpel:process");
					if (nodeLst.getLength() < 1) {
						nodeLst = doc.getElementsByTagName("process");
					}

					// Attach the version number to the name of the BPEL process
					for (int i = 0; i < nodeLst.getLength(); i++) {

						Node attr = nodeLst.item(i).getAttributes()
								.getNamedItem("name");
						String sName = attr.getNodeValue();
						processName = sName.replaceAll("(.v\\d+)?$", "");
						String sNewName = processName + ".v" + version;
						attr.setNodeValue(sNewName);
					}
					SaveAsFile(doc, f);
				}

				// parse DD
				for (File f : dest.listFiles(new DDFilter())) {
					Document doc = db.parse(f);
					doc.setStrictErrorChecking(false);
					doc.getDocumentElement().normalize();

					// Check if anything is found, if not query again
					// without the prefix
					NodeList nodeLst = doc.getElementsByTagNameNS(
							"http://www.apache.org/ode/schemas/dd/2007/03",
							"process");
					if (nodeLst.getLength() < 1) {
						nodeLst = doc.getElementsByTagName("process");
					}

					// Attach the version number to the name of the BPEL
					// process
					for (int i = 0; i < nodeLst.getLength(); i++) {

						Node attr = nodeLst.item(i).getAttributes()
								.getNamedItem("name");
						String sName = attr.getNodeValue();
						String sNewName = sName.replaceAll("(.v\\d+)?$", "")
								+ ".v" + version;
						attr.setNodeValue(sNewName);
					}

					// Check if anything is found, if not query again
					// without the prefix
					nodeLst = doc.getElementsByTagNameNS(
							"http://www.apache.org/ode/schemas/dd/2007/03",
							"provide");
					if (nodeLst.getLength() < 1) {
						nodeLst = doc.getElementsByTagName("provide");
					}

					// Attach the version number to the service name of the
					// BPEL process
					for (int i = 0; i < nodeLst.getLength(); i++) {
						NodeList nodeList = nodeLst.item(i).getChildNodes();
						for (int j = 0; j < nodeList.getLength(); j++) {
							Node node = nodeList.item(j);
							if (node.getNodeType() == Node.ELEMENT_NODE) {
								Node attr = node.getAttributes().getNamedItem(
										"name");
								String sName = attr.getNodeValue();
								String sNewName = sName.replaceAll(
										"(.v\\d+)?$", "") + ".v" + version;
								attr.setNodeValue(sNewName);

							}
						}
					}
					SaveAsFile(doc, f);
				}

				// parse WSDL
				for (File f : dest.listFiles(new WSDLFilter())) {
					// only take the process WSDL. Process WSDLs equal
					// the process name + "Artifacts.wsdl"
					if (!f.getName().equals(processName + "Artifacts.wsdl"))
						continue;

					Document doc = db.parse(f);
					doc.setStrictErrorChecking(false);
					doc.getDocumentElement().normalize();

					// wsdl:service
					NodeList nodeLst = doc.getElementsByTagName("wsdl:service");

					// Check if anything is found, if not query again
					// without the prefix
					if (nodeLst.getLength() < 1) {
						nodeLst = doc.getElementsByTagName("service");
					}

					// Attach the version number to the service name of the
					// BPEL process in the corresponding WSDL file
					for (int i = 0; i < nodeLst.getLength(); i++) {
						Node attr = nodeLst.item(i).getAttributes()
								.getNamedItem("name");
						String sName = attr.getNodeValue();
						String sNewName = sName.replaceAll("(.v\\d+)?$", "")
								+ ".v" + version;
						attr.setNodeValue(sNewName);
					}

					// soap:adress
					nodeLst = doc.getElementsByTagName("soap:address");
					for (int i = 0; i < nodeLst.getLength(); i++) {

						// now we append the version number to the service
						// address
						Node attr = nodeLst.item(i).getAttributes()
								.getNamedItem("location");
						String sName = attr.getNodeValue();
						String sNewName = sName.replaceAll("(.v\\d+)?$", "")
								+ ".v" + version;
						attr.setNodeValue(sNewName);
					}
					SaveAsFile(doc, f);
				}
				db.reset();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (ParserConfigurationException e1) {
			e1.printStackTrace();
		}
	}

	public static void SaveAsFile(Document doc, File path) {
		try {
			DOMSource source = new DOMSource(doc);

			FileOutputStream fos = new FileOutputStream(path);

			Transformer xformer = TransformerFactory.newInstance()
					.newTransformer();
			xformer.transform(source, new StreamResult(fos));
			fos.close();
		} catch (TransformerConfigurationException e) {

		} catch (TransformerException e) {

		} catch (FileNotFoundException e) {

		} catch (IOException e) {

		}

	}
}

class BPELFilter implements FilenameFilter {
	public boolean accept(File dir, String name) {
		return (name.endsWith(".bpel"));
	}
}

class DDFilter implements FilenameFilter {
	public boolean accept(File dir, String name) {
		return (name.endsWith("deploy.xml"));
	}
}

class WSDLFilter implements FilenameFilter {
	public boolean accept(File dir, String name) {
		return (name.endsWith(".wsdl"));
	}
}