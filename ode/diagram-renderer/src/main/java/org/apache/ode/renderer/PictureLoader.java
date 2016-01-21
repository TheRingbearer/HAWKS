package org.apache.ode.renderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class PictureLoader {
	private static final Log __log = LogFactory.getLog(PictureLoader.class);
	public static BufferedImage load(String fileName){
		BufferedImage image = null;
		InputStream is = PictureLoader.class.getResourceAsStream("/pictures/" + fileName);
		try {
			image = ImageIO.read(is);
		} catch (IOException e) {
			__log.error(e);
		}
		return image;
	}
}
