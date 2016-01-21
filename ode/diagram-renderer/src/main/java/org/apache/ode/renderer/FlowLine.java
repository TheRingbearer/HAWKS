package org.apache.ode.renderer;

import java.awt.Dimension;
import java.awt.Graphics;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OLink;

public class FlowLine implements DiagramElement {

	private int insetV = 20;
	private int insetH = 10;
	private OActivity lineStart;
	private Vector<DiagramElement> elements;
	private HashSet<OActivity> preparedActivities;
	private Set<OActivity> localActivities;
	private ActivityToRendererMapper mapper;
	
	public FlowLine(OActivity lineStart, Set<OActivity> localActivities,
			HashSet<OActivity> preparedActivities, ActivityToRendererMapper mapper) {
		this.lineStart = lineStart;
		this.mapper = mapper;
		this.preparedActivities = preparedActivities;
		this.localActivities = localActivities;
		elements = new Vector<DiagramElement>();

	}

	
	public void prepare(Graphics g) {
		OActivity currentActivity = lineStart;
		while (currentActivity != null
				&& localActivities.contains(currentActivity)
				&& !preparedActivities.contains(currentActivity)
				&& currentActivity.sourceLinks.size() >= 0) {
			
			Renderer renderer = ActivityRendererFactory
					.getRenderer(currentActivity, mapper);
			renderer.prepare(g);
			preparedActivities.add(currentActivity);
			elements.add(renderer);
			// one next element
			if (currentActivity.sourceLinks.size() < 2) {
				if (currentActivity.sourceLinks.size() == 1) {
					currentActivity = currentActivity.sourceLinks.iterator()
							.next().target;
				} else {
					currentActivity = null;
				}
			} else {
				// multiple next elements
				HashSet<OActivity> parallelActivities = new HashSet<OActivity>();
				for (OLink link : currentActivity.sourceLinks) {
					parallelActivities.add(link.target);
				}

				FlowLineContainer cont = new FlowLineContainer(
						parallelActivities, localActivities, preparedActivities, mapper);
				cont.prepare(g);
				elements.add(cont);
				currentActivity = null;
			}
		}
	}

	
	public Dimension getDimension() {
		int maxWidth = 0;
		int totalHeight = insetV * (elements.size());
		Iterator<DiagramElement> iterator = elements.iterator();
		while (iterator.hasNext()) {
			Dimension childDim = iterator.next().getDimension();
			if (childDim.width > maxWidth) {
				maxWidth = childDim.width;
			}
			totalHeight += childDim.height;
		}
		return new Dimension(maxWidth, totalHeight);
	}

	
	public void render(Graphics g) {
		for (DiagramElement element : elements) {
			element.render(g);
		}

	}

	
	public void setCoordinates(int leftTopX, int leftTopY) {
		Dimension dim = getDimension();

		int currentY = leftTopY;
		for (DiagramElement element : elements) {
			Dimension childDim = element.getDimension();
			int x = leftTopX + (dim.width - childDim.width) / 2;
			element.setCoordinates(x, currentY);
			currentY = currentY + element.getDimension().height + insetV;
		}

	}
}
