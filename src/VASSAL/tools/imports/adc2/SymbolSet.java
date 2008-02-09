/*
 * Copyright (c) 2008 by Michael Kiefte
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */

package VASSAL.tools.imports.adc2;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BandCombineOp;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;

import javax.imageio.ImageIO;

import VASSAL.build.GameModule;
import VASSAL.tools.BitmapFileFilter;
import VASSAL.tools.imports.FileFormatException;
import VASSAL.tools.imports.Importer;

/**
 * ADC2 game piece and terrain symbols.
 * 
 * @author Michael Kiefte
 *
 */
public class SymbolSet extends Importer{

	/**
	 * Shape of the terrain elements.
	 */
	enum Shape {  // an enum is overkill here...
		SQUARE, HEX
	}

	/**
	 * Contains all of the information for a single game piece or terrain icon
	 */
	class SymbolData {
		
		/**
		 * Shared bitmap of all symbols in either the terrain or game piece set.
		 * Cannot be static as there are three different possible shared images
		 * corresponding to game pieces, terrain features, and bitmasks.
		 */ 
		private final BufferedImage bitmap;

		/** 
		 * Actual name of image file for this symbol in archive. May not be the same as the name 
		 * provided in the configuration file if duplicates exist.
		 */
		private String fileName;

		/** 
		 * Actual image which is lazily generated on request.
		 */
		private BufferedImage img;

		/**
		 * Prevents infinite loops when applying masks to symbol images.
		 */ 
		private final boolean isMask;

		/**
		 * Base 1 index into SymbolData array of masks. 0 means no mask.
		 */
		private int maskIndex;

		/**
		 * Actual name given in the configuration file--not the archive file name
		 * as duplicates are permitted in ADC2.
		 */ 
		private String name;

		/**
		 * Rectangle in shared bitmap <code>img</code>.
		 */
		private Rectangle rect;
		
		SymbolData(BufferedImage bitmap, boolean isMask) {
			this.bitmap = bitmap;
			this.isMask = isMask;
		}

		/**
		 * Get the mask associated with this symbol. Returns <code>null</code> it this
		 * is a mask, if it is from an older version file, or if there is no mask.
		 */
		protected SymbolData getMask() {
			if (ignoreMask || isMask)
				return null;
			// mask index is base 1
			else if (maskIndex > 0 && maskIndex <= maskData.length)
				return maskData[maskIndex - 1];
			else
				return null;
		}

		/**
		 * Read symbol data from configuration file.
		 * 
		 * @return   <code>this</code>, so that methods may be chained in series 
		 *           following <code>read</code>.
		 */
		protected SymbolData read(DataInputStream in) throws IOException {
			name = readNullTerminatedString(in);

			// Have to read in all the zoom level sizes for the first one
			// so that zooming in VASSAL can approximate the zoom behaviour of the
			// imported module.
			boolean readAllZoomLevels = false;
			if (mapBoardSymbolSize == null) {
				mapBoardSymbolSize = new int[3];
				readAllZoomLevels = true;
			}
			
			// Alternate indexing style (to be implemented):
			// if (header == -3)
			//    pict.maskIndex = in.readUnsignedByte();
			// else
			maskIndex = ADC2Utils.readBase250Word(in);
			for (int i = 0; i < 3; ++i) {
				int x1 = in.readInt();
				int y1 = in.readInt();
				int x2 = in.readInt();
				int y2 = in.readInt();
				int width = x2 - x1 + 1;
				int height = y2 - y1 + 1;
				if (readAllZoomLevels)
					mapBoardSymbolSize[i] = height; // or width--they should be square
				if (i == zoomLevel)
					rect = new Rectangle(x1, y1, width, height);
			}
			return this;
		}

		/**
		 * Returns the archive file name corresponding to the image for this
		 * symbol. If called, will actually write the image to the archive in
		 * order to get the file name itself.
		 * 
		 * @throws IOException if unable to read the image files for this symbol.
		 */
		String getFileName() throws IOException {
			if (fileName == null)
				writeToArchive();
			return fileName;
		}

		/**
		 * Get image corresponding to this symbol. Generates the image and applies
		 * optional mask if not already done so.
		 */
		BufferedImage getImage() {
			if (img == null) {
				img = bitmap.getSubimage(rect.x, rect.y, rect.width, rect.height);
				if (getMask() != null) {
					final BufferedImage bi = new BufferedImage(rect.width, rect.height, BufferedImage.TYPE_INT_ARGB);
					final Graphics2D g = bi.createGraphics();
					g.drawImage(img, null, 0, 0);
					g.setComposite(AlphaComposite.DstAtop);
					g.drawImage(getMask().getImage(), null, 0, 0);
					img = bi;
				}
			}
			return img;
		}

		/**
		 * Write symbol image to archive and return archive file name. Will only
		 * write to archive once.
		 */
		protected void writeToArchive() throws IOException {
			// only gets written once. if filename is not null, then we've
			// already been written
			if (fileName == null) { // this condition is really just a failsafe check
				fileName = getUniqueImageFileName(name);
				final ByteArrayOutputStream out = new ByteArrayOutputStream();
				ImageIO.write(getImage(), "png", out);
				GameModule.getGameModule().getArchiveWriter().addImage(fileName, out.toByteArray());
			}
		}
	}

	// Permute negative of red band (doesn't matter which colour) to alpha band.
	// masks are originally black where alpha should be 1.0 and white where
	// alpha should be 0.0.
	private final static float[][] THREE_BAND_MATRIX = { 
			{ 0.0f, 0.0f, 0.0f, 0.0f },
			{ 0.0f, 0.0f, 0.0f, 0.0f }, 
			{ 0.0f, 0.0f, 0.0f, 0.0f },
			{ -1.0f, 0.0f, 0.0f, 255.0f }};
	
	private final static float[][] ONE_BAND_MATRIX = { 
			{ 0.0f, 0.0f }, 
			{ 0.0f, 0.0f },
			{ 0.0f, 0.0f }, 
			{ -255.0f, 255.0f }};

	/**
	 * Convert a black-and-white bitmap to a mask image.
	 */
	private static BufferedImage generateAlphaMask(BufferedImage img) {
		final BandCombineOp op;
		if (img.getSampleModel().getNumBands() == 1)
			op = new BandCombineOp(ONE_BAND_MATRIX, null);
		else
			op = new BandCombineOp(THREE_BAND_MATRIX, null);
		final BufferedImage bi = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
		op.filter(img.getRaster(), bi.getRaster());
		return bi;
	}

	/**
	 * Unit symbols
	 */
	private SymbolData[] gamePieceData;

	/**
	 * Terrain symbols
	 */
	private SymbolData[] mapBoardData;

	/**
	 * Symbol size for all three zoom levels. Used to set zoom scale in VASSAL.
	 */
	private int[] mapBoardSymbolSize;

	/** 
	 * Mask symbols. Only used by SymbolData when requesting a mask index.
	 * Doesn't get used at all in Version I sets (where <code>ignoreMask</code> is true).
	 */
	private SymbolData[] maskData = null;

	/**
	 * Hex or square.
	 */
	private Shape symbolShape;

	/** 
	 * Zoom level to import
	 */
	private static final int zoomLevel = 2;

	/**
	 * Ignore mask despite specified mask indeces.  True for older configuration files.
	 */
	private boolean ignoreMask;

	/**
	 * Read symbol images based on basename and suffix. Bitmap filenames are of the form
	 * <tt>name + "-CN.bmp"</tt> where C is the specified suffix ('M' for masks; 'U' for
	 * game pieces; 'T' for terrain symbols) and N is the base-1 zoom level.
	 * 
	 * @param filename base file name. Should be the same as the base file name of the
	 *                 symbol set file.
	 * @param suffix   single character suffix
	 */
	BufferedImage loadSymbolImage(String filename, char suffix) throws IOException {
		final String fn = filename + '-' + suffix + (zoomLevel + 1) + ".bmp";
		File f = action.getCaseInsensitiveFile(new File(fn), null, true, new BitmapFileFilter());
		if (f == null)
			throw new FileFormatException("Missing bitmap file: " + fn);
		return ImageIO.read(f);
	}

	/**
	 * Read dimensions from input file. The dimensions must occur in triplets in the
	 * configuration file--each one corresponding to a zoom level. 
	 * Only the third dimension, corresponding to zoom level 3, is returned.
	 */
	Dimension readDimension(DataInputStream in) throws IOException {
		Dimension d = null;
		for (int i = 0; i < 3; ++i) {
			int width = in.readInt();
			int height = in.readInt();
			if (zoomLevel == i)
				d = new Dimension(width, height);
		}
		return d;
	}

	/**
	 * Returns the <code>SymbolData</code> corresponding to the game piece at
	 * the specified index.
	 * 
	 * @return      <code>null</code> if the index is out of bounds.
	 */
	SymbolData getGamePiece(int index) {
		if (index >= 0 && index < gamePieceData.length)
			return gamePieceData[index];
		else
			return null;
	}
	
	/**
	 * Returns the <code>SymbolData</code> corresponding to the terrain symbol at
	 * the specified index.
	 * 
	 * @return      <code>null</code> if the index is out of bounds.
	 */
	SymbolData getMapBoardSymbol(int index) {
		if (index >= 0 & index < mapBoardData.length)
			return mapBoardData[index];
		else
			return null;
	}

	/**
	 * @return The most frequently occuring dimension for game pieces in this module.
	 */
	Dimension getModalSize() {
		final HashMap<Dimension, Integer> histogram = new HashMap<Dimension, Integer>();
		for (SymbolData piece : gamePieceData) {
			final BufferedImage im = piece.getImage();
			final Dimension d = new Dimension(im.getWidth(), im.getHeight());
			final Integer i = histogram.get(d);
			if (i == null)
				histogram.put(d, 1);
			else
				histogram.put(d, i+1);
		}
		int max = 0;
		final Dimension maxDim = new Dimension(0,0);
		for (Dimension d : histogram.keySet()) {
			int n = histogram.get(d);
			if (n > max) {
				max = n;					
				maxDim.height = d.height;
				maxDim.width = d.width;
			}
		}
		return maxDim;
	}

	/**
	 * @return Map board symbol size corresponding to the current zoom level.
	 */
	int getMapBoardSymbolSize() {
		return mapBoardSymbolSize[zoomLevel];
	}

	/**
	 * @return Map board symbol size corresponding to the specified zoom level
	 */
	double getZoomFactor(int zoomLevel) {
		return (double) mapBoardSymbolSize[zoomLevel] / (double) mapBoardSymbolSize[SymbolSet.zoomLevel];
	}

	/**
	 * @return Hex or square?
	 */
	Shape getMapBoardSymbolShape() {
		return symbolShape;
	}

	/**
	 * Read a symbol set from the specified file.
	 */
	protected void load(File f) throws IOException {
		DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));

		// if header is -3, then mask indeces are one-byte long. Otherwise, if
		// the header is anything else between -6 and -1, then mask indeces are base-250
		// two-byte words.  However, we're only handling Version 2 files.
		int header = in.readByte();
		if (header < -6 || header > -1)
			throw new FileFormatException("Invalid Symbol Set Header");
		// Right now we could handle older set file formats, but I don't know what
		// the map file is supposed to look like. Easier to upgrade format before
		// importing.
		// TODO: take care of older version symbol set files.
		if (header == -3)
			throw new FileFormatException("Symbol set file version less than 2.12\nConvert before importing.");

		// comletely overridden by the map file
		/* int orientation = */ in.readByte(); // 1=vertical; 2=horizontal; 3=grid
		
		int mapStyle = in.readByte(); // 0=grid; all other values=hex		
		switch (mapStyle) {
		case 1:
			symbolShape = Shape.HEX;
			break;
		default:
			symbolShape = Shape.SQUARE;	
		}
		
		int symSetVersion = in.readByte(); // 1=version 1
		switch (symSetVersion) {
		case 0:
			ignoreMask = false;
			break;
		default:
			ignoreMask = true;
		}

		// bitmap dimensions are completely ignored
		int nMapBoardSymbols = ADC2Utils.readBase250Word(in);
		mapBoardData = new SymbolData[nMapBoardSymbols];
		/* terrainBitmapDims = */ readDimension(in);

		int nGamePieceSymbols = ADC2Utils.readBase250Word(in);
		gamePieceData = new SymbolData[nGamePieceSymbols];
		/* unitBitmapDims = */ readDimension(in);

		int nMasks = ADC2Utils.readBase250Word(in);
		/* maskBitmapDims = */ readDimension(in);
		if (!ignoreMask)
			maskData = new SymbolData[nMasks];

		String baseName = stripExtension(f.getPath());

		// load images
		BufferedImage mapBoardImages = loadSymbolImage(baseName, 't');
		for (int i = 0; i < nMapBoardSymbols; ++i) {
			mapBoardData[i] = new SymbolData(mapBoardImages, false).read(in);
			// check for size consistency. Not sure what to do if they're not
			// all the same size or not square
			if (mapBoardData[i].rect.height != mapBoardData[0].rect.height
					|| mapBoardData[i].rect.width != mapBoardData[0].rect.width)
				throw new FileFormatException("Map board image dimensions are inconsistent");
			if (mapBoardData[i].rect.width != mapBoardData[i].rect.height)
				throw new FileFormatException("Map board image dimensions are not square");
		}

		BufferedImage gamePieceImages = loadSymbolImage(baseName, 'u');
		for (int i = 0; i < nGamePieceSymbols; ++i)
			gamePieceData[i] = new SymbolData(gamePieceImages, false).read(in);

		if (!ignoreMask) {
			BufferedImage maskImages = loadSymbolImage(baseName, 'm');
			// convert binary bitmap to RGBA alpha mask
			maskImages = generateAlphaMask(maskImages);

			for (int i = 0; i < nMasks; ++i)
				maskData[i] = new SymbolData(maskImages, true).read(in);
		}
	}

	/**
	 * Write all of the game pieces to the archive.  Mainly for testing or if only
	 * the symbol set is imported.
	 */
	public void writeToArchive() throws IOException {
		for (SymbolData piece : gamePieceData)
			piece.writeToArchive();
		// for testing purposes only
//		for (SymbolData terrain : mapBoardData)
//			terrain.writeToArchive();
	}
}