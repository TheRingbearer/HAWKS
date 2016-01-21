package org.apache.ode.utils;

//@stmz: just to have a class that is accessible from both the runtime and jacob
//with this booleans we know, if we can execute another job (see BpelEngineImpl)
public class ZZBool {
	private static ZZBool instance;
	private Boolean running;
	private Boolean canRun;

	private ZZBool() {
		running = true;
		canRun = true;
	}

	public static ZZBool getInstance() {
		if (instance == null) {
			instance = new ZZBool();
		}
		return instance;
	}

	public Boolean getRunning() {
		return running;
	}

	public Boolean getCanRun() {
		return canRun;
	}

	public void setRunning(Boolean running) {
		this.running = running;
	}

	public void setCanRun(Boolean canRun) {
		this.canRun = canRun;
	}
}
