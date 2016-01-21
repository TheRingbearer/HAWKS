package org.apache.ode.renderer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;

import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OFragmentScope;
import org.apache.ode.bpel.o.OScope;

public class ScopeRenderer extends Renderer{
	private Renderer renderer;
	protected OActivity child;
	private boolean implicitScope;
	public ScopeRenderer(OBase element, ActivityToRendererMapper mapper) {
		super(element, mapper);
		
		if (element instanceof OFragmentScope){
			defaultName = "fragmentScope";
			child = ((OFragmentScope) element).activity;
			thickFrame = true;
			implicitScope = false;
		} else if (element instanceof OScope){
			OScope scope = (OScope)element;
			child = ((OScope) element).activity;
			if (scope.processScope){
				defaultName = "Process";
				implicitScope = false;
			} else {
				defaultName = "Scope";
				implicitScope = ((OScope) element).implicitScope;
			}
		} 
		frameFillColor = new Color(208, 229, 224);
		
	}

	
	public void render(Graphics g) {
		if (!implicitScope){
			drawActivityFrame(g);	
		}
		
		if (renderer != null){
			renderer.render(g);
		}

	}

	
	public void prepare(Graphics g) {
		calcCaptionDimension(g);
		
 		if (child != null){
 			renderer = ActivityRendererFactory.getRenderer(child, mapper);
 		} else {
 			renderer = new DummyRenderer();
 		}
 		renderer.prepare(g);
 		Dimension dim = renderer.getDimension();
		if (dim == null){
			dim = captionDimension;
		}
		if (implicitScope){
			dimension = dim;
		} else {
			int width = 2 * insets + dim.width;
			int height = 2 * insets + dim.height + captionDimension.height;
			dimension = new Dimension(width, height);
		}
	}

	
	public void setCoordinates(int leftTopX, int leftTopY) {
		leftUpper = new Point(leftTopX, leftTopY);
		if (renderer != null){
			if (implicitScope){
				renderer.setCoordinates(leftTopX, leftTopY);
			} else {
				renderer.setCoordinates(leftTopX + insets, leftTopY + insets + captionDimension.height);	
			}
			
		}
	}

}
