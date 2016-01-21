package org.apache.ode.renderer;

import java.awt.Graphics;
import java.awt.Point;

import org.apache.ode.bpel.o.OBase;

public class SimpleActivityRenderer extends Renderer{

	public SimpleActivityRenderer(OBase element, ActivityToRendererMapper mapper) {
		super(element, mapper);
		defaultName = "Dummy";
	}

	
	public void render(Graphics g) {
		drawCaption(g);

	}

	
	public void prepare(Graphics g) {
		calcCaptionDimension(g);
		dimension = captionDimension;
		
	}

	
	public void setCoordinates(int leftTopX, int leftTopY) {
		leftUpper = new Point(leftTopX, leftTopY);
		
	}

}
