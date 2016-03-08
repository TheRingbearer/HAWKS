package krawczls.executionEngineRegistry;

import java.util.Comparator;

public class WorkflowEngineCompareByActiveProcesses implements Comparator<WorkflowEngine> {
	public int compare(WorkflowEngine engine1, WorkflowEngine engine2) {
		return ((Integer) engine1.numberOfActiveProcesses()).compareTo( ((Integer) engine2.numberOfActiveProcesses()) );
	}
}
