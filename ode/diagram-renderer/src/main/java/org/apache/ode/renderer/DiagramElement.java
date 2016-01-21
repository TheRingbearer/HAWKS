package org.apache.ode.renderer;

import java.awt.Dimension;
import java.awt.Graphics;

public interface DiagramElement {
	public Dimension getDimension();
	public void prepare(Graphics g);
	public void render(Graphics g);
	public void setCoordinates(int leftTopX, int leftTopY);
}
