package org.apache.ode.bpel.extensions.processes;

import java.io.File;
import java.util.ArrayList;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.iapi.ProcessState;

//@stmz: represents a deployed process
public class Deployed_Process {
	private File BPELfile;
	private ArrayList<File> wsdlFiles;
	private QName processName;
	private String state;
	private Long version;

	public Deployed_Process(File bpel, ArrayList<File> wsdls, QName name,
			Long ver) {
		BPELfile = bpel;
		wsdlFiles = wsdls;
		processName = name;
		version = ver;
	}

	public void setState(ProcessState stat) {
		state = stat.toString();
	}

	public File getBPELfile() {
		return BPELfile;
	}

	public ArrayList<File> getWsdlFiles() {
		return wsdlFiles;
	}

	public QName getProcessName() {
		return processName;
	}

	public String getState() {
		return state;
	}

	public Long getVersion() {
		return version;
	}
}
