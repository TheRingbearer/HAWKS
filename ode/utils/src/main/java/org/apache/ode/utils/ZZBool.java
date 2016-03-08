package org.apache.ode.utils;

import java.util.concurrent.atomic.AtomicBoolean;

//@stmz: just to have a class that is accessible from both the runtime and jacob
//with this booleans we know, if we can execute another job (see BpelEngineImpl)
public class ZZBool {
	private static ZZBool instance;
	private AtomicBoolean running;
	private AtomicBoolean canRun;

	private ZZBool() {
		running = new AtomicBoolean(true);
		canRun = new AtomicBoolean(true);
	}

	public static ZZBool getInstance() {
		if (instance == null) {
			instance = new ZZBool();
		}
		return instance;
	}

	public Boolean getRunning() {
		return running.get();
	}

	public Boolean getCanRun() {
		return canRun.get();
	}

	public void setRunning(Boolean running) {
		this.running.set(running);
	}

	public void setCanRun(Boolean canRun) {
		this.canRun.set(canRun);
	}
}
