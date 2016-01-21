package org.apache.ode.renderer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OFragmentSequence;
import org.apache.ode.bpel.o.OSequence;

public class SequenceRenderer extends Renderer {
	private Vector<Renderer> elements;
	private Dimension sequenceDimension;

	public SequenceRenderer(OBase element, ActivityToRendererMapper mapper) {
		super(element, mapper);
		elements = new Vector<Renderer>();
		frameFillColor = new Color(181, 229, 229);
	}

	
	public void render(Graphics g) {
		drawActivityFrame(g);
		for (Renderer element : elements) {
			element.render(g);
		}
	}

	
	public void prepare(Graphics g) {
		calcCaptionDimension(g);
		List<OActivity> children = null;
		if (element instanceof OFragmentSequence) {
			children = ((OFragmentSequence) element).sequence;
		} else if (element instanceof OSequence) {
			children = ((OSequence) element).sequence;
		}

		createAndPrepareChildRenderers(children, g);
		calcSequenceDimension();

		int width = 2 * insets + sequenceDimension.width;
		int height = 2 * insets + sequenceDimension.height
				+ captionDimension.height;
		dimension = new Dimension(width, height);


	}

	private void createAndPrepareChildRenderers(List<OActivity> children,
			Graphics g) {
		if (children == null || children.size() == 0){
			Renderer renderer = new DummyRenderer();
			renderer.prepare(g);
			elements.add(renderer);
		} else  {
			for (OActivity activity : children) {
				Renderer renderer = ActivityRendererFactory.getRenderer(activity, mapper);
				renderer.prepare(g);
				elements.add(renderer);
			}	
		}
		
	}

	private void calcSequenceDimension() {
		int maxWidth = 0;
		int totalHeight = insets * (elements.size() - 1);
		Iterator<Renderer> iterator = elements.iterator();
		while (iterator.hasNext()) {
			Dimension childDim = iterator.next().getDimension();
			if (childDim.width > maxWidth) {
				maxWidth = childDim.width;
			}
			totalHeight += childDim.height;
		}
		sequenceDimension = new Dimension(maxWidth, totalHeight);
	}

	
	public void setCoordinates(int leftTopX, int leftTopY) {
		leftUpper = new Point(leftTopX, leftTopY);
		int relativeYOffset = insets + captionDimension.height;

		for (Renderer element : elements) {
			Dimension childDim = element.getDimension();
			int x = leftTopX + insets
					+ (sequenceDimension.width - childDim.width) / 2;
			element.setCoordinates(x, leftTopY + relativeYOffset);
			relativeYOffset = relativeYOffset + childDim.height + insets;
		}

	}

}
