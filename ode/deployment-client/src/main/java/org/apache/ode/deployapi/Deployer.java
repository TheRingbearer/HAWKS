package org.apache.ode.deployapi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.xmlmime.Base64Binary;

public class Deployer {
	private static final Log __log = LogFactory.getLog(Deployer.class);
	public Deployer() {

	}

	public QName[] deployNewProcessVersion(List<File> files, String name) {
		QName[] id = null;
		ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
		ZipOutputStream out = new ZipOutputStream(bytesOut);
		Base64Binary zip = null;
		try {
			byte[] buffer = new byte[1024];
			for (File file : files) {

				FileInputStream in = new FileInputStream(file);
				out.putNextEntry(new ZipEntry(file.getName()));
				int read;
				while ((read = in.read(buffer)) > 0) {
					out.write(buffer, 0, read);
				}
				out.closeEntry();
				in.close();

			}
			out.close();
			byte[] bytes = bytesOut.toByteArray();
			zip = new Base64Binary(bytes);
			bytesOut.close();
		} catch (FileNotFoundException e) {
			__log.error(e);
		} catch (IOException e) {
			__log.error(e);
		}

		if (zip != null) {
			_package pack = new _package(zip);

			try {
				System.out.println("Send deploy message");
				DeploymentPortTypeProxy deploymentApi = new DeploymentPortTypeProxy();
				DeployUnit unit = deploymentApi.deploy(name, pack);
				id = unit.getId();
			} catch (RemoteException e) {
				__log.error(e);
			}
		}
		return id;
	}

}
