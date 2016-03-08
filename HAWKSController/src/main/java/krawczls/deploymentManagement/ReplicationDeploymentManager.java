package krawczls.deploymentManagement;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMText;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axiom.om.util.Base64;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.client.OperationClient;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.transport.TransportUtils;
import org.apache.ode.axis2.service.ServiceClientUtil;

@SuppressWarnings("deprecation")
public class ReplicationDeploymentManager {
    private OMFactory _factory;
    private ServiceClientUtil _client;

    public void setUp(String processName, String fileName, String ip) throws Exception {
        this._factory = OMAbstractFactory.getOMFactory();
        OMNamespace pmapi = this._factory.createOMNamespace("http://www.apache.org/ode/pmapi", "pmapi");
        OMNamespace api = this._factory.createOMNamespace("http://www.apache.org/ode/deployapi", "api");
        OMElement root = this._factory.createOMElement("deploy", pmapi);
        OMElement namePart = this._factory.createOMElement("name", null);
        namePart.setText(processName);
        OMElement zipPart = this._factory.createOMElement("package", null);
        OMElement zipElmt = this._factory.createOMElement("zip", api);
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(String.valueOf(fileName) + ".zip");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        int b = is.read();
        while (b >= 0) {
            outputStream.write((byte)b);
            b = is.read();
        }
        String base64Enc = Base64.encode((byte[])outputStream.toByteArray());
        OMText zipContent = this._factory.createOMText(base64Enc, "application/zip", true);
        root.addChild((OMNode)namePart);
        root.addChild((OMNode)zipPart);
        zipPart.addChild((OMNode)zipElmt);
        zipElmt.addChild((OMNode)zipContent);
        this.sendToDeployment(root, ip);
    }

    private OMElement sendToDeployment(OMElement msg, String ip) throws AxisFault {
        this._client = new ServiceClientUtil();
        return this._client.send(msg, "http://" + ip + ":8080/ode/services/DeploymentService");
    }

    /*public OMElement sendToService(String url, String ip) throws AxisFault, XMLStreamException {
        this._client = new ServiceClientUtil();
        OMFactory _factory = OMAbstractFactory.getOMFactory();
        OMNamespace sample = _factory.createOMNamespace("http://eclipse.org/bpel/sample", "sample");
        OMElement request = _factory.createOMElement("HelloBPELProcessRequest", sample);
        OMElement input = _factory.createOMElement("input", sample);
        input.setText("Hello");
        request.addChild((OMNode)input);
        System.out.println(request.toString());
        return this._client.send(request, "http://" + ip + ":8080/ode/processes/" + url);
    }*/

    public void sendSOAPToService(String message, String url, String ip) throws Exception {
        ServiceClient serviceClient = new ServiceClient();
        Options options = new Options();
        options.setTo(new EndpointReference("http://" + ip + ":8080/ode/processes/" + url));
        serviceClient.setOptions(options);
        MessageContext messageContext = new MessageContext();
        OMElement omelement = AXIOMUtil.stringToOM(message);
        Iterator<?> itr = omelement.getChildrenWithLocalName("Body");
        while (itr.hasNext()) {
            omelement = (OMElement)itr.next();
        }
        omelement = omelement.getFirstElement();
        SOAPEnvelope soapEnvelope = TransportUtils.createSOAPEnvelope(omelement);
        messageContext.setEnvelope(soapEnvelope);
        OperationClient operationClient = serviceClient.createClient(ServiceClient.ANON_OUT_IN_OP);
        operationClient.addMessageContext(messageContext);
        operationClient.execute(false);
    }
}