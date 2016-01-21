package org.apache.ode.renderer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.util.List;
import java.util.Vector;

import org.apache.ode.bpel.elang.xpath10.o.OXPath10Expression;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OPickReceive;
import org.apache.ode.bpel.o.OPickReceive.OnAlarm;
import org.apache.ode.bpel.o.OPickReceive.OnMessage;

public class PickRenderer extends Renderer {
	private int insets = 10;
	private Vector<DiagramElement> elements;
	private OPickReceive pick;

	public PickRenderer(OBase element, ActivityToRendererMapper mapper) {
		super(element, mapper);
		elements = new Vector<DiagramElement>();
		defaultName = "Pick";
		pick = (OPickReceive) element;
		
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
		List<OnAlarm> onAlarms = pick.onAlarms;
		List<OnMessage> onMessages = pick.onMessages;
		
		for (OnMessage onMessage: onMessages){
			String label = onMessage.getOperationName();
			CaseRenderer rend = new CaseRenderer(onMessage.activity, label, mapper);
			rend.prepare(g);
			elements.add(rend);
		}
		
		for (OnAlarm onAlarm: onAlarms){
			String label = null;
			if (onAlarm.forExpr != null && onAlarm.forExpr instanceof OXPath10Expression){
				OXPath10Expression exp = (OXPath10Expression)onAlarm.forExpr;
				label = exp.xpath;
			} else if (onAlarm.untilExpr != null && onAlarm.untilExpr instanceof OXPath10Expression) {
				OXPath10Expression exp = (OXPath10Expression)onAlarm.untilExpr;
				label = exp.xpath;				
			}
			
			CaseRenderer rend = new CaseRenderer(onAlarm.activity, label, mapper);
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
