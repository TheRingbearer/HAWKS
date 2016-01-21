package org.apache.ode.renderer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.image.BufferedImage;

import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OBase;

public abstract class Renderer implements DiagramElement {
	private int captionInsets = 5;
	private int offset = 5;
	private BufferedImage icon;
	private int iconWidth = 10;
	private int iconHeight = 10;
	protected Color frameFillColor;
	protected boolean thickFrame;
	private Color captionFillColor;
	
	protected String defaultName = "";
	protected Dimension captionDimension;
	protected int insets = 10;
	protected Point leftUpper;
	protected Dimension dimension;
	protected ActivityToRendererMapper mapper;
	
	protected OBase element;
	public Renderer(OBase element, ActivityToRendererMapper mapper){
		this.element = element;
		this.mapper = mapper;
		icon = null;
		frameFillColor = Color.WHITE;
		captionFillColor = new Color(244, 244, 244);
		thickFrame = false;
	}
	
	public Point getEntryPoint(){
		return new Point(leftUpper.x + dimension.width / 2, leftUpper.y);
	}
	public Point getExitPoint(){
		return new Point(leftUpper.x + dimension.width / 2, leftUpper.y + dimension.height);
	}
	
	public Dimension getDimension(){
		if (dimension.width < captionDimension.width){
			dimension.width = captionDimension.width + 2 * offset;
		}
		return dimension;
	}
	
	
	public void setIcon(BufferedImage image){
		icon = image;
		iconWidth = image.getWidth();
		iconHeight = image.getHeight();
	}

	
	protected void drawActivityFrame(Graphics g){
		g.setColor(frameFillColor);
		g.fillRect(leftUpper.x, leftUpper.y + captionDimension.height / 2, dimension.width, dimension.height - captionDimension.height / 2);
		g.setColor(Color.DARK_GRAY);
		int leftX = leftUpper.x;
		int leftY = leftUpper.y + captionDimension.height / 2;
		int width = dimension.width;
		int height = dimension.height - captionDimension.height / 2;
		int iterations = 1;
		if (thickFrame){
			iterations = 3;
		}
		for (int i = 0; i < iterations; i++){
			g.drawRect(leftX + i, leftY + i, width - 2 * i, height - 2 * i);	
		}
		
		drawCaption(g);
	}
	protected void drawCaption(Graphics g){
		FontMetrics fm = g.getFontMetrics();
		int x = (dimension.width - captionDimension.width) / 2 + leftUpper.x;
		int y = leftUpper.y;
		g.setColor(captionFillColor);
		g.fillRect(x, y, captionDimension.width, captionDimension.height);
		g.setColor(Color.BLACK);
		g.drawRect(x, y, captionDimension.width, captionDimension.height);
		
		if (icon != null){
			g.drawImage(icon, x + captionInsets, y + (captionDimension.height - iconHeight) / 2, null);
		}
		
		int strX = 2 * captionInsets + iconWidth + x;
		
		g.drawString(getElementName(), strX, y + (captionDimension.height + fm.getHeight()) / 2);
	}
	protected void calcCaptionDimension(Graphics g){
		FontMetrics fm = g.getFontMetrics();

		int width = captionInsets * 3 + iconWidth + (int)fm.getStringBounds(getElementName(), g).getWidth();
		int height = 2 * captionInsets + Math.max(fm.getHeight(), iconHeight);
		captionDimension = new Dimension(width, height);
	}
	
	public Dimension getCaptionDimension(){
		return captionDimension;
	}
	
	private String getElementName(){
		OActivity activity = (OActivity)element;
		StringBuffer buffer = new StringBuffer();
		
		if (activity.name == null){
			buffer.append(defaultName);
		} else {
			buffer.append(activity.name);
		}
		buffer.append(" (id:");
		buffer.append(activity.getId());
		buffer.append(")");
		return buffer.toString();
	}
}
