package org.apache.ode.renderer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;

import org.apache.ode.bpel.elang.xpath10.o.OXPath10Expression;
import org.apache.ode.bpel.o.OActivity;

public class CaseRenderer implements DiagramElement{
	private int insets = 5;
	private OActivity activity;
	private Point leftUpper;
	private Rectangle expRect;
	private Renderer childRenderer;
	private String label;
	private ActivityToRendererMapper mapper;
	private int expOffset = 0;
	private int childOffset = 0;
	public CaseRenderer(OActivity activity, String label, ActivityToRendererMapper mapper) {
		this.activity = activity;
		this.mapper = mapper;
		if (label == null){
			 this.label = "...";
		} else {
			this.label = label;
		}
	}

	
	public void prepare(Graphics g) {
		FontMetrics fm = g.getFontMetrics();
		
		expRect = fm.getStringBounds(label, g).getBounds();
		expRect.height += 2 * insets;
		expRect.width += 2 * insets;
		childRenderer = ActivityRendererFactory.getRenderer(activity, mapper);
		childRenderer.prepare(g);
		
		Dimension dim = childRenderer.getDimension();
		if(dim.width > expRect.width){
			expOffset = (dim.width - expRect.width) / 2;
		} else {
			childOffset = (expRect.width - dim.width) / 2;
		}
		
	}

	
	public void render(Graphics g) {
		g.setColor(Color.WHITE);
		g.fillRect(leftUpper.x + expOffset, leftUpper.y, expRect.width, expRect.height);
		g.setColor(Color.BLACK);
		g.drawRect(leftUpper.x + expOffset, leftUpper.y, expRect.width, expRect.height);
		g.drawString(label, leftUpper.x + insets + expOffset, leftUpper.y + expRect.height - insets);
		childRenderer.render(g);
	}

	
	public void setCoordinates(int leftTopX, int leftTopY) {
		leftUpper = new Point(leftTopX, leftTopY);
		childRenderer.setCoordinates(leftTopX + childOffset, leftTopY + expRect.height);
	}

	
	public Dimension getDimension() {
		Dimension dim = childRenderer.getDimension();
		int width = Math.max(dim.width, expRect.width);
		return new Dimension(width, dim.height + expRect.height);
		
	}

}
