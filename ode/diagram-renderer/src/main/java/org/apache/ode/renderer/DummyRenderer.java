package org.apache.ode.renderer;

import java.awt.Dimension;
import java.awt.Graphics;

public class DummyRenderer extends Renderer{

	public DummyRenderer() {
		super(null, null);
	}

	
	public void prepare(Graphics g) {
		captionDimension = new Dimension(0 , 0);
		dimension = new Dimension(50, 50);
	}

	
	public void render(Graphics g) {
	
	}

	
	public void setCoordinates(int leftTopX, int leftTopY) {
		
	}

}
