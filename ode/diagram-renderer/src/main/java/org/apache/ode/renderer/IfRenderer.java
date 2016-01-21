package org.apache.ode.renderer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.util.List;
import java.util.Vector;

import org.apache.ode.bpel.elang.xpath10.o.OXPath10Expression;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OSwitch;
import org.apache.ode.bpel.o.OSwitch.OCase;

public class IfRenderer extends Renderer{
	private int insets = 10;
	private Vector<DiagramElement> elements;
	private OSwitch oSwitch;
	public IfRenderer(OBase element, ActivityToRendererMapper mapper) {
		super(element, mapper);
		elements = new Vector<DiagramElement>();
		defaultName = "If";
		oSwitch = (OSwitch) element;
		frameFillColor = new Color(167, 232, 239);
	}

	
	public void prepare(Graphics g) {
		calcCaptionDimension(g);
		
		
		createAndPrepareChildRenderers(g);
		calcDimension();
		
	}
	
	private void calcDimension(){
		int totalWidth = insets * (elements.size() + 1);
		int maxHeight = 0;
		for (DiagramElement element: elements){
			Dimension dim = element.getDimension();
			totalWidth += dim.width;
			if (dim.height > maxHeight){
				maxHeight = dim.height;
			}
		}
		dimension = new Dimension(totalWidth, maxHeight + 2 * insets + captionDimension.height);
	}
	private void createAndPrepareChildRenderers(Graphics g) {
		List<OCase> cases = oSwitch.getCases();
		for (OCase ocase: cases){
			String label = null;
			if (ocase.expression instanceof OXPath10Expression){
				OXPath10Expression exp = (OXPath10Expression)ocase.expression;
				label = exp.xpath;
			}
			
			CaseRenderer rend = new CaseRenderer(ocase.activity, label, mapper);
			rend.prepare(g);
			elements.add(rend);
		}
	}

	
	public void render(Graphics g) {
		drawActivityFrame(g);
		for (DiagramElement element: elements){
			element.render(g);
		}
	}

	
	public void setCoordinates(int leftTopX, int leftTopY) {
		leftUpper = new Point(leftTopX, leftTopY);
		int x = leftTopX + insets;
		for (DiagramElement element: elements){
			element.setCoordinates(x, leftTopY + insets + captionDimension.height);
			x = x + insets + element.getDimension().width;
		}
		
	}

}
