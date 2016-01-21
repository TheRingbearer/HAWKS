package org.apache.ode.renderer;

import org.apache.ode.bpel.o.OBase;

public interface ActivityToRendererMapper {
	public Renderer get(OBase activity);
	public void put(OBase activity, Renderer renderer);
}
