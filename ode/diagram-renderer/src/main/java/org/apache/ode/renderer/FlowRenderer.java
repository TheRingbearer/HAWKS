package org.apache.ode.renderer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OFlow;
import org.apache.ode.bpel.o.OFragmentFlow;
import org.apache.ode.bpel.o.OLink;

public class FlowRenderer extends Renderer {

	private Set<OLink> localLinks;
	private Set<OActivity> localActivities;

	private HashSet<OActivity> preparedActivities;
	private BufferedImage arrow;
	private DiagramElement container;

	public FlowRenderer(OBase element, ActivityToRendererMapper mapper) {
		super(element, mapper);
		arrow = PictureLoader.load("arrow.png");
		preparedActivities = new HashSet<OActivity>();

		if (element instanceof OFlow) {
			OFlow flow = (OFlow) element;
			localActivities = flow.parallelActivities;
			localLinks = flow.localLinks;
		} else if (element instanceof OFragmentFlow) {
			OFragmentFlow flow = (OFragmentFlow) element;
			localActivities = flow.parallelActivities;
			localLinks = flow.localLinks;
		}
		frameFillColor = new Color(242, 228, 169);
	}

	private OActivity getNotPreparedActivity() {
		OActivity activity = null;
		for (OActivity current : localActivities) {
			if (!preparedActivities.contains(current)) {
				activity = current;
				break;
			}
		}

		return activity;
	}

	
	public void prepare(Graphics g) {
		calcCaptionDimension(g);

		if (localActivities != null) {
			HashSet<OActivity> initials = new HashSet<OActivity>();
			for (OActivity activity : localActivities) {
				if (activity.targetLinks.size() == 0) {
					initials.add(activity);
				} else if (activity.targetLinks.size() == 1){
					OActivity source = activity.targetLinks.iterator().next().source;
					if (source == null || !localActivities.contains(source)){
						initials.add(activity);	
					}
				}
			}
			container = new FlowLineContainer(initials, localActivities,
					preparedActivities, mapper);
			container.prepare(g);

			OActivity activity;
			while ((activity = getNotPreparedActivity()) != null) {
				FlowLine line = new FlowLine(activity, localActivities,
						preparedActivities, mapper);
				line.prepare(g);
				((FlowLineContainer)container).addLine(line);

			}

		} else {
			container = new DummyRenderer();
			container.prepare(g);
		}
		Dimension dim = container.getDimension();

		int width = insets + dim.width;
		int height = dim.height + captionDimension.height;
		dimension = new Dimension(width, height);

	}

	
	public void render(Graphics g) {
		drawActivityFrame(g);
		container.render(g);
		renderLinks(g);
	}

	private void drawLinkWithoutStart(Graphics g, Point to){
		int arcLength = 70;
		g.drawArc(to.x - arcLength + 1, to.y - arcLength / 2, arcLength, arcLength, 0, 80);
		RendererUtil.drawArrow(g, new Point(to.x, to.y - arcLength), to);
	}
	
	
	private void drawLink(Graphics g, Point from, Point to){
		
		g.setColor(Color.BLACK);
		g.drawLine(from.x, from.y, to.x, to.y);
		RendererUtil.drawArrow(g, from, to);

		
		/*
		float angle;
		if (deltaX == 0){
			if (deltaY > 0){
				angle = (float)Math.toRadians(90);	
			} else {
				angle = (float)Math.toRadians(270);
			}
			 
		} else {
			angle = (float)Math.atan(deltaY / deltaX);
		}
		if (deltaX < 0){
			angle += (float)Math.toRadians(180);
		}
		
		BufferedImage rotated = rotateArrow(angle);
		g.drawImage(rotated, to.x - rotated.getWidth() / 2 + 1, to.y - rotated.getHeight() / 2, null);
		*/
	}

	public BufferedImage rotateArrow(float angle) {
		int width = arrow.getWidth();
		int height = arrow.getHeight();
		BufferedImage rotated = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = rotated.createGraphics();
		g.rotate(angle, width / 2, height / 2);
		g.drawImage(arrow, null, 0, 0);
		return rotated;
	}



	private void renderLinks(Graphics g) {
		for (OLink link : localLinks) {
			Point from = null;
			Point to = null;

			OActivity fromAct = link.source;
			if (fromAct != null) {
				from = mapper.get(fromAct).getExitPoint();
			}
			OActivity toAct = link.target;
			if (toAct != null) {
				to = mapper.get(toAct).getEntryPoint();
			}
			if (from == null && to != null) {
				drawLinkWithoutStart(g, to);
			}
			if (from != null && to != null) {
				drawLink(g, from, to);
			}
		}
	}

	
	public void setCoordinates(int leftTopX, int leftTopY) {
		leftUpper = new Point(leftTopX, leftTopY);
		container.setCoordinates(leftTopX, leftTopY + captionDimension.height);

	}

}
