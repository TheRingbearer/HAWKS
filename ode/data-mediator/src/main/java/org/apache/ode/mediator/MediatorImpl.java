package org.apache.ode.mediator;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.utils.DOMUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class MediatorImpl implements Mediator {
	private static final Log __log = LogFactory.getLog(MediatorImpl.class);
	private Source xsltVarSource;
	private Source xsltCSetSource;
	
	private File xsltVarFile;
	private File xsltCSetFile;
	
	private long lastChangeXsltVar;
	private long lastChangeXsltCSet;
	public MediatorImpl(File xsltVarFile, File xsltCSetFile) {
		this.xsltVarFile = xsltVarFile;
		this.xsltCSetFile = xsltCSetFile;
		loadCSetXslt();
		loadVarXslt();
	}
	
	private void loadCSetXslt(){
		try {
			Node xsltDom = DOMUtils.parse(new FileInputStream(xsltCSetFile));
			xsltCSetSource = new DOMSource(xsltDom);
			lastChangeXsltCSet = xsltCSetFile.lastModified();
			__log.info("Mediation Rules loaded from File: " + xsltCSetFile.getAbsolutePath());
		} catch (FileNotFoundException e) {
			__log.error(e);
		} catch (SAXException e) {
			__log.error(e);
		} catch (IOException e) {
			__log.error(e);
		}
	}
	private void loadVarXslt(){
		try {
			Node xsltDom = DOMUtils.parse(new FileInputStream(xsltVarFile));
			xsltVarSource = new DOMSource(xsltDom);
			lastChangeXsltVar = xsltVarFile.lastModified();
			__log.info("Mediation Rules loaded from File: " + xsltVarFile.getAbsolutePath());
		} catch (FileNotFoundException e) {
			__log.error(e);
		} catch (SAXException e) {
			__log.error(e);
		} catch (IOException e) {
			__log.error(e);
		}
	}
	public Node mediateVariable(QName fromDataType, QName toDataType, Node data) throws MediationException{
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put("from", fromDataType);
		params.put("to", toDataType);
		
		if (lastChangeXsltVar < xsltVarFile.lastModified()){
			loadVarXslt();
		}
		
		Node result = mediate(xsltVarSource, params, data);
		if (result == null){
			StringBuffer buffer = new StringBuffer();
			buffer.append("No rule for variable mediation from type '");
			buffer.append(fromDataType.toString());
			buffer.append("' to '");
			buffer.append(toDataType.toString());
			buffer.append("' type found!");
			throw new MediationException(buffer.toString());
		}
		
		return result;
	}

	public Node mediateCorrelationSet(CSetMediationInfo from, CSetMediationInfo to, Node data) throws MediationException{
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put("fromProcess", from.getProcessName());
		params.put("toProcess", to.getProcessName());

		params.put("fromCSetName", from.getCorrelationSetName());
		params.put("toCSetName", to.getCorrelationSetName());
		
		params.put("fromScopeName", from.getScopeName());
		params.put("toScopeName", to.getScopeName());
		
		if (lastChangeXsltCSet < xsltCSetFile.lastModified()){
			loadCSetXslt();
		}
		
		Node result = mediate(xsltCSetSource, params, data);
		
		if (result.getOwnerDocument().getDocumentElement().getChildNodes().getLength() == 0) {
			StringBuffer buffer = new StringBuffer();
			buffer.append("No rule for CorrelationSet mediation from '");
			buffer.append(from.getCorrelationSetName());
			buffer.append("' to '");
			buffer.append(to.getCorrelationSetName());
			buffer.append("' found!");
			throw new MediationException(buffer.toString());
		}
		
		return result;
	}


	private Node mediate(Source xsltSource, Map<String, Object> params, Node data) throws MediationException{
		Node resultNode = null;
		System.setProperty("javax.xml.transform.TransformerFactory",
				"net.sf.saxon.TransformerFactoryImpl");

		// Source xmlSource = new DOMSource(data);
		TransformerFactory transFact = TransformerFactory.newInstance();

		try {
			
			Source xmlSource = new StreamSource(new ByteArrayInputStream(
					DOMUtils.domToString(data).getBytes()));
					
			//Source xmlSource = new DOMSource(data);
			Transformer transformator = transFact.newTransformer(xsltSource);
			for (String key: params.keySet()){
				transformator.setParameter(key, params.get(key));
			}
			String method = transformator.getOutputProperties().getProperty(
					"method");
			if (method != null && method.equals("xml")) {
				DOMResult result = new DOMResult();
				transformator.transform(xmlSource, result);
				resultNode = result.getNode();
				if (resultNode.getNodeType() == Node.DOCUMENT_NODE)
					resultNode = ((Document) resultNode).getDocumentElement();
			} else {
				StringWriter writerResult = new StringWriter();
				StreamResult result = new StreamResult(writerResult);
				transformator.transform(xmlSource, result);
				writerResult.flush();
				try {
					resultNode = DOMUtils.stringToDOM(writerResult.toString());
				} catch (SAXException e) {
					__log.error(e);
				} catch (IOException e) {
					__log.error(e);
				}
			}
		} catch (TransformerException e) {
			throw new MediationException(e);
		}
		
		return resultNode;
	}

}

