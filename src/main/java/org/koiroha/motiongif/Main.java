package org.koiroha.motiongif;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * @see <a href="http://lab.moyo.biz/recipes/java/imageio/gifimage.xsp">my site</a>.
 */
public class Main {
	private static void help() {
		System.out.println("USAGE: java " + Main.class.getName() + " {-o [outfile]} {-d [indir]} {-f [width] [height]} [file]...");
		System.out.println("OPTIONS:");
		System.out.println("  --out [outfilie], -o [outfile]");
		System.out.println("    specify output gif filename.");
		System.out.println("  --dir [indir], -d [indir]");
		System.out.println("    scan image files on specified directory, sort by last modified and add to join to gif image.");
		System.out.println("  --fit [width] [height], -f [width] [height]");
		System.out.println("    change size to fit withxheight.");
		System.out.println("  --interval [msec], -i [msec]");
		System.out.println("    delay interval between each frame. default 500 msec.");
		System.exit(0);
	}
	public static void main(String[] args) throws Exception {

		// parse commandline parameters
		List<URI> sources = new ArrayList<>();        // image files to join to animation gif
		int interval = 500;                           // frame interval [msec]
		String output = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".gif";
		int width = -1;
		int height = -1;
		for(int i=0; i<args.length; i++){
			switch (args[i]) {
				case "-i":
				case "--interval":
					interval = Integer.parseInt(args[i+1]);
					i++;
					break;
				case "-o":
				case "--output":
					output = args[i + 1];
					i++;
					break;
				case "-d":
				case "--dir":
					File dir = new File(args[i + 1]);
					File[] files = dir.listFiles();
					files = (files == null)? new File[0]: files;
					Arrays.sort(files, new Comparator<File>(){
						@Override public int compare(File o1, File o2) {
							return Long.valueOf(o1.lastModified()).compareTo(o2.lastModified());
						}
					});
					for (File file : files) {
						String suffix = "";
						int sep = file.getName().lastIndexOf('.');
						if (sep >= 0) {
							suffix = file.getName().substring(sep + 1);
						}
						boolean supported = ImageIO.getImageReadersBySuffix(suffix).hasNext();
						if (supported) {
							sources.add(file.toURI());
						}
					}
					i++;
					break;
				case "-f":
				case "--fit":
					width = Integer.parseInt(args[i+1]);
					height = Integer.parseInt(args[i+2]);
					i+=2;
					break;
				case "-h":
				case "--help":
					help();
					break;
				default:
					URI uri = null;
					try {
						uri = new URI(args[i]);
					} catch (URISyntaxException ex) { /* */ }
					if (uri == null || !uri.isAbsolute()) {
						uri = new File(args[i]).toURI();
					}
					sources.add(uri);
					break;
			}
		}

		// check parameters
		if(sources.size() == 0){
			error("image files are not specified");
		}

		Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("GIF");
		if(! it.hasNext()){
			error("unable to write gif image in this environment.");
		}
		ImageWriter writer = it.next();
		ImageOutputStream out = ImageIO.createImageOutputStream(new File(output));
		writer.setOutput(out);

		// write gif header as default
		writer.prepareWriteSequence(null);

		boolean first = true;
		for(URI uri: sources){

			// read frame image
			BufferedImage image = ImageIO.read(uri.toURL());

			// change frame size to specified one if need
			if(width > 0 && height > 0){
				double m = Math.min((double)width / image.getWidth(), (double)height / image.getHeight());
				if(m < 1.0){
					int w = (int)(image.getWidth() * m);
					int h = (int)(image.getHeight() * m);
					Image temp = image.getScaledInstance(w, h, BufferedImage.SCALE_SMOOTH);
					BufferedImage btemp = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
					btemp.getGraphics().drawImage(temp, 0, 0, null);
					image = btemp;
				}
			}

			IIOMetadata meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), null);
			String format = meta.getNativeMetadataFormatName();
			IIOMetadataNode root = (IIOMetadataNode)meta.getAsTree(format);
			if(first){
				first = false;
				// write animation control
				int count = 0;
				byte[] data = { 0x01, (byte)(count & 0xFF), (byte)((count >> 8) & 0xFF) };
				IIOMetadataNode list = new IIOMetadataNode("ApplicationExtensions");
				IIOMetadataNode node = new IIOMetadataNode("ApplicationExtension");
				node.setAttribute("applicationID", "NETSCAPE");
				node.setAttribute("authenticationCode", "2.0");
				node.setUserObject(data);
				list.appendChild(node);
				root.appendChild(list);
			}

			// writer graphic control
			IIOMetadataNode node = new IIOMetadataNode("GraphicControlExtension");
			node.setAttribute("disposalMethod", "none");
			node.setAttribute("userInputFlag", "FALSE");
			node.setAttribute("transparentColorFlag", "FALSE");
			node.setAttribute("delayTime", Integer.toString(interval / 10));
			node.setAttribute("transparentColorIndex", "0");
			root.appendChild(node);
			meta.setFromTree(format, root);

			// write frame image
			writer.writeToSequence(new IIOImage(image, null, meta), null);
		}

		// write terminate block
		writer.endWriteSequence();
		out.close();
	}

	private static void error(String msg){
		System.err.println("ERROR: " + msg);
		System.exit(1);
	}
}
