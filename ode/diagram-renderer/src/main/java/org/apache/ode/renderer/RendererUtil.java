package org.apache.ode.renderer;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.Polygon;

public class RendererUtil {
	public static void drawArrow(Graphics g, Point from, Point to){
		int arrowLength = 10;

		int deltaX = to.x - from.x;
		int deltaY = to.y - from.y;
		
		float length = (float)Math.sqrt(deltaX * deltaX + deltaY * deltaY); 
		
		Point toRotate = new Point();
		toRotate.x = Math.round(0 - (arrowLength * deltaX) / length);
		toRotate.y = Math.round(0 - (arrowLength * deltaY) / length);
		
		Point one = rotate(toRotate, (float)Math.toRadians(20));
		Point two = rotate(toRotate, (float)Math.toRadians(-20));
		one.x += to.x;
		one.y += to.y;
		
		two.x += to.x;
		two.y += to.y;
		// draw arrow
		g.drawLine(one.x, one.y, to.x, to.y);
		g.drawLine(two.x, two.y, to.x, to.y);
	}
	
	public static void drawFilledArrow(Graphics g, Point from, Point to){
		int arrowLength = 12;

		int deltaX = to.x - from.x;
		int deltaY = to.y - from.y;
		
		float length = (float)Math.sqrt(deltaX * deltaX + deltaY * deltaY); 
		
		Point toRotate = new Point();
		toRotate.x = Math.round(0 - (arrowLength * deltaX) / length);
		toRotate.y = Math.round(0 - (arrowLength * deltaY) / length);
		
		Point one = rotate(toRotate, (float)Math.toRadians(20));
		Point two = rotate(toRotate, (float)Math.toRadians(-20));
		one.x += to.x;
		one.y += to.y;
		
		two.x += to.x;
		two.y += to.y;
		// draw arrow
		
		Polygon p = new Polygon();
		p.addPoint(one.x, one.y);
		p.addPoint(two.x, two.y);
		p.addPoint(to.x, to.y);
		g.fillPolygon(p);
	}
	
	public static Point rotate(Point toRotate, float tetta) {
		float sin = (float) Math.sin(tetta);
		float cos = (float) Math.cos(tetta);

		int x = (int) (toRotate.x * cos - toRotate.y * sin);
		int y = (int) (toRotate.x * sin + toRotate.y * cos);

		return new Point(x, y);
	}
}
