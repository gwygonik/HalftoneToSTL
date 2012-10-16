package imageTools;

import javax.media.opengl.GL;

import processing.core.*;
import processing.opengl.*;
import unlekker.modelbuilder.*;
import unlekker.util.*;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

@SuppressWarnings("serial")
public class HalftoneImageToSTL extends PApplet {

	String VERSION = "1.0.4";
	
	UGeometry primitives,shapes,primitives2;
	UNav3D nav;
	UGeometry struct;
	PImage img, img2;
	USimpleGUI gui;
	
	float ROT90 = 90f*DEG_TO_RAD;
	float SCALE2PRINTER = 1f;
	int POSTERIZE_LEVELS = 6;
	
	float PLATFORMXSIZE = 100f;
	float PLATFORMYSIZE = 100f;
	
	float HALFPLATFORMX = PLATFORMXSIZE / 2f;
	float HALFPLATFORMY = PLATFORMYSIZE / 2f;

	int NUM_STEPS = 15;
	float STEPXSIZE = (float) Math.floor(PLATFORMXSIZE / (NUM_STEPS-1));
	float STEPYSIZE = (float) Math.floor(PLATFORMYSIZE / (NUM_STEPS-1));

	float MINRADIUS = 0.5f;
	float MAXRADIUS = MINRADIUS + (PLATFORMXSIZE >= PLATFORMYSIZE ? PLATFORMXSIZE / (NUM_STEPS-1) / 2 : PLATFORMYSIZE / (NUM_STEPS-1) / 2);
	
	int FIXED_HEIGHT_MM = 2;
	int VARIABLE_MULT = 1;
	
	// for GUI
	int oldSteps = -1;
	int oldPosterize = POSTERIZE_LEVELS;
	int oldHeight = FIXED_HEIGHT_MM;
	int oldVariableMult = VARIABLE_MULT;
	
	controlP5.ControlGroup groupBasic;
	controlP5.ControlGroup groupColors;
	controlP5.ControlGroup groupHeight;
	controlP5.ControlGroup groupOutput;
	
	boolean useInvert = false;
	boolean usePosterize = true;
	boolean useVariableHeight = false;
	
	boolean isImageLoaded = false;
	
	// output vars
	String outputPath = "";
	String outputFilename = "";
	String scadFile = "";
	String NEWLINE = "\r\n";
	
	public void setup() {
		size(1280,650,OPENGL);

		// create ModelBuilder mouse nav area
		nav=new UNav3D(this);
		nav.setTranslation(630,height/2,400);
		
		createGUI();

		// create placeholder assets
		img = new PImage((int)PLATFORMXSIZE, (int)PLATFORMYSIZE);
		img2 = new PImage((int)PLATFORMXSIZE, (int)PLATFORMYSIZE);

	}
	
	// main draw method
	public void draw() {
		background(100);

		// draw the UI
		gui.draw();

		// if we have loaded an image, draw original image and gray/pixelated image and 3D model
		if (isImageLoaded) {
			fill(20);
			rect(1030,15,230,230);
			fill(60);
			rect(1044,29,202,202);
			image(img2,1045,30,200,200);
			fill(20);
			rect(1030,255,230,230);
			fill(60);
			rect(1044,269,202,202);
			image(img,1045,270,200,200);

			// draw 3D model
			fill(220);
			lights();
			nav.doTransforms();
			translate(0,0,0);
			primitives.draw(this);
		}
	}

	/*********************************************************************/
	// Main Worker Methods
	/*********************************************************************/
	
	public void buildPrimitives() {
		
		// PRIMITIVES
		primitives=new UGeometry();
		
		// start OpenSCAD file
		scadFile = "module " + outputFilename + "()" + NEWLINE + " { " + NEWLINE;
		scadFile += "\trotate([90,0,0]) {" + NEWLINE;
		scadFile += "\t\tunion() {" + NEWLINE;
		
		// bottom grid
		for (int x=0;x<NUM_STEPS;x++) {
			primitives.add(UPrimitive.box(HALFPLATFORMX+0.5f, 0.5f, 0.5f).translate(0,0.5f,HALFPLATFORMY-x*STEPYSIZE));
			scadFile += "\t\t\ttranslate([0,0.25," + (HALFPLATFORMY-x*STEPYSIZE) + "]) cube([" + (PLATFORMXSIZE+0.5f) + ", 0.5, 0.5],true);" + NEWLINE;
			primitives.add(UPrimitive.box(0.5f, 0.5f, HALFPLATFORMY).translate(HALFPLATFORMX-x*STEPXSIZE,0.5f,0));
			scadFile += "\t\t\ttranslate([" + (HALFPLATFORMX-x*STEPXSIZE) + ",0.25,0]) cube([0.5, 0.5, " + (PLATFORMYSIZE+0.5f) + "],true);" + NEWLINE;
		}
		
		scadFile += NEWLINE;
		
		// halftone cylinders
		float height;
		
		// Our source image will be the exact size as number of grid steps. So a 1:1 pixel to cylinder mapping is possible
		for (int x=0;x<NUM_STEPS;x++) {
			for (int y=0;y<NUM_STEPS;y++) {
				float pxVal = red(img.get(x, y));
				pxVal = map(pxVal,0,255,MINRADIUS,MAXRADIUS);
				height = useVariableHeight ? pxVal * VARIABLE_MULT : FIXED_HEIGHT_MM;
				if (!useVariableHeight || (useVariableHeight && height != 0)) {
					primitives.add(UPrimitive.cylinder(pxVal, height, (int)(pxVal+3), true).translate(-HALFPLATFORMX+(STEPXSIZE*x), height, HALFPLATFORMY-(STEPYSIZE*y)));
					scadFile += "\t\t\ttranslate([" + (HALFPLATFORMX-(STEPXSIZE*x)) + ","+ (height/2) +"," + (HALFPLATFORMY-(STEPYSIZE*y)) + "]) rotate([90,0,0]) cylinder(r = " + pxVal + ", h =" + height + ", center=true);" + NEWLINE;
				}
			}
		}
		
		// rotate the whole thing so it's flat on the build platform
		primitives.rotateX(ROT90);
		
		// finish off OpenSCAD file
		scadFile += "\t\t}" + NEWLINE + "\t};" + NEWLINE + "}" + NEWLINE;
		scadFile += outputFilename + "();";
		
	}

	public void buildPrimitivesForSTL() {
		
		// PRIMITIVES
		primitives2=new UGeometry();
		
		// bottom grid
		for (int x=0;x<NUM_STEPS;x++) {
			primitives2.add(UPrimitive.box(HALFPLATFORMX+0.5f, 0.5f, 0.5f).translate(0,0.5f,HALFPLATFORMY-x*STEPYSIZE));
			primitives2.add(UPrimitive.box(0.5f, 0.5f, HALFPLATFORMY).translate(HALFPLATFORMX-x*STEPXSIZE,0.5f,0));
		}
		
		// halftone cylinders
		float height;
		
		// Our source image will be the exact size as number of grid steps. So a 1:1 pixel to cylinder mapping is possible
		for (int x=0;x<NUM_STEPS;x++) {
			for (int y=0;y<NUM_STEPS;y++) {
				float pxVal = red(img.get(x, y));
				pxVal = map(pxVal,0,255,MINRADIUS,MAXRADIUS);
				height = useVariableHeight ? pxVal * VARIABLE_MULT : FIXED_HEIGHT_MM;
				if (!useVariableHeight || (useVariableHeight && height != 0)) {
					primitives2.add(UPrimitive.cylinder(pxVal, height, (int)(pxVal+3), true).translate(-HALFPLATFORMX+(STEPXSIZE*x), height, -HALFPLATFORMY+(STEPYSIZE*y)));
				}
			}
		}
		
		// rotate the whole thing so it's flat on the build platform
		primitives2.rotateX(ROT90);
		
		
	}

	// recreate image with new settings and redraw 3D model
	private void refreshImage() {
		img.delete();
		img = new PImage(NUM_STEPS, NUM_STEPS);
		img.copy(img2,0,0,img2.width,img2.height,0,0,NUM_STEPS, NUM_STEPS);
		img.filter(GRAY);
		if (useInvert) img.filter(INVERT);
		if (usePosterize) img.filter(POSTERIZE,POSTERIZE_LEVELS);
		img.loadPixels();
		
		// recreate 3D model
		buildPrimitives();
		buildPrimitivesForSTL();
		
		// scale image back up for display, not using Processing's built-in "smooth" method
		PImage tmpImage = scaleWithoutBlur(img,(int)PLATFORMXSIZE, (int)PLATFORMXSIZE);
		img = tmpImage.get();
		
	}
		
	private void createGUI() {
		// build UI
		// note - all groups are created off-screen and shown only after loading an image
		controlP5.Slider tmpS;
		controlP5.Toggle tmpT;
		controlP5.Button tmpB;
		controlP5.Textfield tmpTF;
		
		gui = new USimpleGUI(this);
		tmpB = gui.cp.addButton("LOAD_IMAGE", 1, 10, 10, 220, 30);
		tmpB.setLabel("  LOAD IMAGE");
		
		groupBasic = gui.cp.addGroup("BASIC SETTINGS", -310, 60,220);
		groupBasic.setBackgroundColor(30);
		groupBasic.setBackgroundHeight(35);
		tmpS = gui.cp.addSlider("GRID_STEPS", 5, 30, 15, 10, 10, 100, 15);
		tmpS.setLabel("NUMBER OF GRID STEPS");
		tmpS.moveTo(groupBasic);
		groupBasic.disableCollapse();
		
		groupColors = gui.cp.addGroup("COLOR SETTINGS",-310,115,220);
		groupColors.setBackgroundColor(30);
		groupColors.setBackgroundHeight(135);
		tmpT = gui.cp.addToggle("INVERT_IMAGE", useInvert, 10, 10, 100, 30);
		tmpT.setLabel("INVERT IMAGE");
		tmpT.moveTo(groupColors);
		tmpT = gui.cp.addToggle("REDUCE_COLORS", usePosterize, 10, 60, 100, 30);
		tmpT.setLabel("REDUCE COLORS");
		tmpT.moveTo(groupColors);
		tmpS = gui.cp.addSlider("NUMBER_OF_COLORS", 2,12,6,10,110,100,15);
		tmpS.setLabel("NUMBER OF COLORS");
		tmpS.moveTo(groupColors);
		groupColors.disableCollapse();
		
		groupHeight = gui.cp.addGroup("HEIGHT MODIFIERS", -310, 265,220);
		groupHeight.setBackgroundColor(30);
		groupHeight.setBackgroundHeight(110);
		tmpS = gui.cp.addSlider("HEIGHT_MM", 1,6,2,10,10,100,15);
		tmpS.setLabel("HEIGHT IN MM");
		tmpS.moveTo(groupHeight);
		tmpT = gui.cp.addToggle("VARIABLE_HEIGHT", useVariableHeight, 10, 35, 100, 30);
		tmpT.setLabel("USE VARIABLE HEIGHT");
		tmpT.moveTo(groupHeight);
		tmpS = gui.cp.addSlider("VARIABLE_MULTIPLIER",1,4,1,10,85,100,15);
		tmpS.setLabel("VARIABLE MULTIPLIER");
		tmpS.moveTo(groupHeight);
		groupHeight.disableCollapse();
		
		groupOutput = gui.cp.addGroup("FILE OUTPUT", -310, 380, 220);
		groupOutput.setBackgroundColor(30);
		groupOutput.setBackgroundHeight(130);
		tmpTF = gui.cp.addTextfield("OUTPUT_NAME", 10, 10, 100, 20);
		tmpTF.setLabel("OUTPUT FILE NAME");
		tmpTF.moveTo(groupOutput);
		tmpB = gui.cp.addButton("SAVE_STL",2,10,50,100,30);
		tmpB.setLabel("SAVE .STL");
		tmpB.moveTo(groupOutput);
		tmpB = gui.cp.addButton("SAVE_OPENSCAD",3,10,90,100,30);
		tmpB.setLabel("SAVE .SCAD");
		tmpB.moveTo(groupOutput);
		groupOutput.disableCollapse();
		
		gui.cp.addTextlabel("VERSION", "VERSION " + VERSION + "    ", 1200, 630);
		
		gui.cp.setMoveable(false);

		// attach UI to nav so it knows to allow mouse interaction
		nav.setGUI(gui);
	}


	/*********************************************************************/
	// GUI Handler Methods
	/*********************************************************************/
	
	// handler for load image button
	private void LOAD_IMAGE() {
		// disable ui while loading (mouse handler)
		nav.enabled = false;
		// create java file chooser dialog
		try {
		    SwingUtilities.invokeAndWait( new Runnable() {
		      @Override
		      public void run() {
		        final JFileChooser chooser = new JFileChooser();
		        chooser.setFileFilter(new ImageFilter());
		        final int returnVal = chooser.showOpenDialog( null );
		
		        if (returnVal == JFileChooser.APPROVE_OPTION) {
		          img2 = loadImage(chooser.getSelectedFile().toString());
		          outputPath = chooser.getSelectedFile().getParent() + java.io.File.separatorChar;
		          outputFilename = chooser.getSelectedFile().getName().substring(0, chooser.getSelectedFile().getName().lastIndexOf(".")).replaceAll(" ", "_");
		      gui.setText("OUTPUT_NAME", outputFilename);
		      isImageLoaded = true;
		      GRID_STEPS(NUM_STEPS);
		
		      // show rest of UI
		      groupBasic.setPosition(10f, 60f);
		      groupColors.setPosition(10f, 115f);
		      groupHeight.setPosition(10f, 270f);
		      groupOutput.setPosition(10f,400f);
		      
		      // re-enable model UI
		          nav.enabled = true;
		        }
		      }
		    } );
		}
		catch ( final Exception e ) {
		    UUtil.log( "Error whilst loading image: " + e.getMessage() );
		}
    }
	
	// handler for invert image toggle
	private void INVERT_IMAGE(boolean val) {
		useInvert = val;
		if (isImageLoaded) {
			refreshImage();
		}
	}
	
	// handler for reduce colors toggle
	private void REDUCE_COLORS(boolean val) {
		usePosterize = val;
		if (isImageLoaded) {
			refreshImage();
		}
	}
	
	// handler for height in mm slider
	private void HEIGHT_MM(int val) {
		if (val != oldHeight) {
			FIXED_HEIGHT_MM = oldHeight = val;
			if (isImageLoaded) {
				refreshImage();
			}
		}
	}
	
	// handler for variable height multiplier slider
	private void VARIABLE_MULTIPLIER(int val) {
		if (val != oldVariableMult) {
			VARIABLE_MULT = oldVariableMult = val;
			if (isImageLoaded) {
				refreshImage();
			}
		}
	}
	
	// handler for save as stl button
	private void SAVE_STL() {
		if (isImageLoaded) {
			UUtil.log("saving to " + outputPath + outputFilename + ".stl");
			primitives2.writeSTL(this, outputPath + outputFilename + ".stl");
		}
	}
	
	// handler for save as scad button
	private void SAVE_OPENSCAD() {
		if (isImageLoaded) {
			UUtil.log("saving to " + outputPath + outputFilename + ".scad");
			saveStrings(outputPath + outputFilename + ".scad",scadFile.split(NEWLINE));
		}
	}
	
	// handler for grid steps slider
	private void GRID_STEPS(int val) {
		if (val != oldSteps ) {
			NUM_STEPS = oldSteps = val;
			STEPXSIZE = (float) (PLATFORMXSIZE / (NUM_STEPS-1));
			STEPYSIZE = (float) (PLATFORMYSIZE / (NUM_STEPS-1));
			MAXRADIUS = MINRADIUS + (PLATFORMXSIZE >= PLATFORMYSIZE ? PLATFORMXSIZE / (NUM_STEPS-1) / 2 : PLATFORMYSIZE / (NUM_STEPS-1) / 2);
			if (isImageLoaded) {
				refreshImage();
			}
		}
	}
	
	// handler for number of colors slider
	private void NUMBER_OF_COLORS(int val) {
		if (val != oldPosterize) {
			POSTERIZE_LEVELS = oldPosterize = val;
			if (isImageLoaded) {
				refreshImage();
			}
		}
	}
	
	// handler for variable height toggle
	private void VARIABLE_HEIGHT(boolean val) {
		useVariableHeight = val;
		if (isImageLoaded) {
			refreshImage();
		}
	}
	

	/*********************************************************************/
	// Utility Methods
	/*********************************************************************/
	
	// This was from an old PDE, but I don't know the source. It was not me.
	PImage scaleWithoutBlur(PImage sourceImg,int w,int h) {
		  PImage newImg = new PImage(w,h,sourceImg.format);
		  sourceImg.loadPixels();
		  newImg.loadPixels();
		  for(int y = 0; y< newImg.height;y++) {
		    int y2 = floor(map(y,0,newImg.height,0,sourceImg.height));
		    for(int x = 0; x< newImg.width;x++) {
		      int x2 = floor(map(x,0,newImg.width,0,sourceImg.width));
		      newImg.pixels[y*newImg.width+x] = sourceImg.pixels[y2*sourceImg.width+x2];
		    }
		  }
		  newImg.updatePixels();
		  return newImg;
		}
	
	public static void main(String _args[]) {
		PApplet.main(new String[] { imageTools.HalftoneImageToSTL.class.getName() });
	}
}
