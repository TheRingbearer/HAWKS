package org.apache.ode.renderer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.HashMap;

import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OFragmentEntry;
import org.apache.ode.bpel.o.OFragmentExit;
import org.apache.ode.bpel.o.OFragmentRegion;
import org.apache.ode.bpel.o.OProcess;

public class ProcessRenderer implements ActivityToRendererMapper {
	private int insets = 10;
	private HashMap<OBase, Renderer> activityRendererMap;
	private Stroke dashedStroke;
	private Stroke normalStroke;
	private OProcess process;
	public ProcessRenderer(OProcess process) {
		activityRendererMap = new HashMap<OBase, Renderer>();

		dashedStroke = new BasicStroke(2.0f, // line width
				/* cap style */BasicStroke.CAP_BUTT,
				/* join style, miter limit */BasicStroke.JOIN_BEVEL, 1.0f,
				/* the dash pattern */new float[] { 8.0f, 3.0f },
				/* the dash phase */0.0f); 
		normalStroke = new BasicStroke(); 
		this.process = process;
	}

	public BufferedImage render() {

		Renderer renderer = ActivityRendererFactory.getRenderer(
				process.procesScope, this);
		BufferedImage tmpImage = new BufferedImage(1, 1,
				BufferedImage.TYPE_INT_RGB);

		renderer.prepare(tmpImage.getGraphics());
		renderer.setCoordinates(10, 10);
		Dimension dim = renderer.getDimension();

		int width = 2 * insets + dim.width;
		int height = 2 * insets + dim.height;

		BufferedImage image = new BufferedImage(width, height,
				BufferedImage.TYPE_INT_RGB);

		Graphics g = image.getGraphics();

		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);
		/*
		 * g.setColor(Color.DARK_GRAY); g.drawRect(1, 1, width - 1, height - 1);
		 * 
		 * g.setColor(Color.BLACK); FontMetrics fm = g.getFontMetrics();
		 * Rectangle2D rect = fm.getStringBounds("Process", g);
		 * 
		 * g.drawString("Process", width / 2 - (int)rect.getCenterX(), 1 +
		 * (int)rect.getCenterX());
		 */
		renderer.render(g);
		renderMappingLinks(g);
		return image;
	}

	
	public Renderer get(OBase activity) {
		return activityRendererMap.get(activity);
	}

	
	public void put(OBase activity, Renderer renderer) {
		activityRendererMap.put(activity, renderer);

	}

	private void renderMappingLinks(Graphics g) {
		for (OBase child: process.getChildren()){
			if (child instanceof OFragmentExit){
				OFragmentExit exit = (OFragmentExit) child;
				Renderer from = activityRendererMap.get(exit);
				if (!exit.danglingExit){
					OBase entry = process.getChild(exit.fragmentEntryId);
					if (entry instanceof OFragmentEntry){
						Renderer to = activityRendererMap.get(entry);
						drawLink(g, from.getExitPoint(), to.getEntryPoint());
					} else if (entry instanceof OFragmentRegion){
						Renderer to = activityRendererMap.get(entry);
						Point toPoint = to.getExitPoint();
						toPoint.y -= 3;
						drawLink(g, from.getExitPoint() , toPoint);
					}
				}
			} else if (child instanceof OFragmentRegion){
				OFragmentRegion region = (OFragmentRegion) child;
				Renderer from = activityRendererMap.get(region);
				Point fromPoint = from.getEntryPoint();
				
				fromPoint.y += from.getCaptionDimension().height; 
				if (!region.danglingExit){
					OBase entry = process.getChild(region.fragmentEntryId);
					if (entry instanceof OFragmentEntry){
						Renderer to = activityRendererMap.get(entry);
						drawLink(g, fromPoint, to.getEntryPoint());
					} 
				}
			}
		}
	}

	private void drawLink(Graphics g, Point from, Point to) {

		g.setColor(Color.GRAY);
		Graphics2D g2 = (Graphics2D) g;
		g2.setStroke(dashedStroke);
		g.drawLine(from.x, from.y, to.x, to.y);
		g2.setStroke(normalStroke);
		RendererUtil.drawFilledArrow(g, from, to);
		g.setColor(Color.BLACK);
	}

}
