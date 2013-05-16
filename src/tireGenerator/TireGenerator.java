package tireGenerator;

//===============================================================
//Imports
import controlP5.*;
import processing.core.*;
import java.io.File;
import java.util.*;
import java.lang.String;

import peasy.*;
import geomerative.*;
import toxi.geom.*;
import toxi.geom.mesh.*;
import toxi.geom.mesh2d.*;
import toxi.processing.*;

/***
 * 
 * Tire Generator, generate messages on stuff!
 * 
 * @author Jordan Parsons
 * @author Golan Levin
 * 
 */
public class TireGenerator extends PApplet {
	private static final long serialVersionUID = 693924492789036865L;

	// ===============================================================
	// Public Classes
	PeasyCam nav; // cam
	ToxiclibsSupport gfx; // toxiclibs draw

	// Threaded tire builder
	ReBuilderv2 build;

	// ===============================================================
	// Drawing Instances of the meshes
	TriangleMesh hubDraw;
	WETriangleMesh tireDraw;
	WETriangleMesh lettersDraw;
	Voronoi voronoiDraw;

	// Other classes
	RShape svg;
	RFont font;

	// GUI vars
	ControlP5 controlP5;
	DropdownList list1, list2;
	Textfield textInput;
	Textarea outputMSG;
	Textlabel appStatus;
	Group advCtrlsGrp;
	String messages = "";

	// ===============================================================
	// Flags
	// Display
	boolean showHub = false;
	boolean showTire = true;
	boolean showNormals = true;
	// GUI
	boolean verbose = false; // Print detailed debugging information.
	boolean advCtrls = false; // Show detailed sliders, may break code.
	boolean showDebug = true; // Hide the message window.
	// Mesh options
	boolean reverseLetters = false;
	boolean repeat = false;
	boolean vert = false; // Rotate the message 90* CCW.
	// Mesh blocks of various shapes in the gap between the messages.
	boolean tireBlocking = true;
	// If making a stamp, size it to the message instead of a preset size.
	boolean autoStamp = false;
	boolean revSTL = false; // Export a mirrored STL.
	// Place bars above and below the message, shrinking it, but protecting its
	// edges.
	boolean topBars = true;
	// Status
	boolean shapeLoaded = false; // Did someone pick a STL?
	boolean building = false; // Is the build thread running?
	boolean textLoaded = false; // Did someone set text?
	boolean prog = false; // Is an operation in progress?

	// ===============================================================
	// Voronoi Constants
	// Voronoi Rounding Amount X X
	int cVoronoiRound = 2;
	// Voronoi Blur Top Threshold X X
	float cVoronoiTThresh = 0.52f;
	// Voronoi Blur Number X X
	int cVoronoiBNum = 4;
	// Voronoi Blur Size X X
	int cVoronoiBSize = 5;
	// Voronoi Valley Search Space X X
	int cVoronoiValSearch = 3;
	// Voronoi Valley Epsilon X X
	int cVoronoiValEps = 11;
	// Voronoi Letter Space Constant X X
	float cVoronoiLettter = 1.75f;

	// ===============================================================
	// Mesh Constants
	// Mesh Smooth Passes X X
	int cMeshSmoothNum = 3;
	// Mesh Chamfer Amount X X
	float cMeshChamf = 0.04f;

	// ===============================================================
	// Message Constants
	// Message Border X X
	float cMessageBorder = 5;
	float cStripThickness = 10;

	// ===============================================================
	// Shape & Path Constants
	// Path Clip Angle X X
	float cPathClipAngle = 0.75f;
	// Path Min Segment Distance X X
	float cPathMinSeg = 0.25f;
	// Path Resample Passes X X
	int cResamplePassNum = 3;

	// ===============================================================
	// Base Stamp Width
	float stampWidth = 3;
	// Base Stamp Thickness
	float stampThickness = .5f;

	// ===============================================================
	// public info
	long time = 0; // Run time
	final int D_SF = 400; // Scale Factor
	final int D_SF_2 = 200;// Scale Factor
	final int DSIZE = 10000000; // Huge number to setup the DT.
	int file = 99999; // Bogus number for the file picker.
	int blockType = 0; // Selects the type of blocking to use
	// float autoStampH = .25f; //Unused?
	float treadDepth = .0625f; // Default tread depth
	float patternSpace = 10; // Amount to space looped messages (I think)
	String fileName = "";// Empty file picker name.
	String textValue = null;// Empty text (tire message).
	File hub; // The hub file
	// Where the tires are stored
	String path = sketchPath + "/data/tire_directory";
	// List out all of the tire files right away.
	ArrayList<File> allFiles = listFilesRecursive(path);

	/**
	 * Setup the app, start a blank rebuilder
	 */
	public void setup() {
		// app, nav, and gui setup
		size(1280, 768, OPENGL);
		nav = new PeasyCam(this, 500);
		nav.setMinimumDistance(0);
		nav.setMaximumDistance(2000);
		strokeJoin(MITER);
		newGuiSetup(); // start the gui

		// Setup geomerative
		RG.init(this);
		font = new RFont("ariblk.ttf", 256, RFont.LEFT); // seems to handle text
															// best at 80

		// RCommand.setSegmentLength(3); //segmenting for text, may need to be
		// even smaller
		RCommand.setSegmentAngle(PI / 10);
		// RCommand.setSegmentator(RCommand.UNIFORMLENGTH);
		RCommand.setSegmentator(RCommand.ADAPTATIVE);

		// Setup Blanks For Drawing & output
		gfx = new ToxiclibsSupport(this);
		hubDraw = new TriangleMesh();
		tireDraw = new WETriangleMesh();
		voronoiDraw = new Voronoi(DSIZE);
		lettersDraw = new WETriangleMesh();

		// Blank rebuilder
		hub = new File(path + "/Lego/Lego_T10.txt");
		build = new ReBuilderv2(this, hub);
		build.start();
		building = true;
	}

	/**
	 * Start drawing stuff.
	 */
	public void draw() {
		background(100);
		// Draw the light before drawing the 3d
		nav.beginHUD();
		lights();
		nav.endHUD();
		stroke(0);
		fill(150);

		// draws the tires & hubs
		if (showNormals) { // w/ normals
			if (showHub)
				gfx.meshNormalMapped(hubDraw, false, 5);
			if (showTire)
				gfx.meshNormalMapped(tireDraw, false, 5);
		} else { // w/o normals
			if (showHub)
				gfx.mesh(hubDraw);
			if (showTire)
				gfx.mesh(tireDraw);
			fill(255, 0, 0);
			if (showTire)
				gfx.mesh(lettersDraw);
		}

		nav.beginHUD(); // start ignoring the camera
		controlP5.draw(); // draw the gui

		// if an operation is happening, show its progress
		if (prog)
			drawProgressBar(build.getProg(), build.getGoal());
		nav.endHUD(); // stop ignoring the camera

		// Check the thread, and input
		statusCheck();
	}

	/**
	 * Setup the ControlP5 GUI
	 */
	@SuppressWarnings("deprecation")
	public void newGuiSetup() {
		controlP5 = new ControlP5(this);
		controlP5.setAutoDraw(false);

		PFont p = createFont("Arial", 11, false);
		ControlFont cf = new ControlFont(p);

		p = createFont("Arial", 14, false);
		ControlFont cfLabel = new ControlFont(p);

		// Lables
		Textlabel appName = controlP5.addTextlabel("appName", "F.A.T. Wheels", 10, 10);// new
																						// Textlabel(controlP5,
																						// "FAT Wheels",
																						// 100,
																						// 100);
		appName.setControlFont(cfLabel);
		appName.setVisible(true);

		Textlabel appTag = controlP5.addTextlabel("appTag", "Tire Generator\nWIP", 125, 10);// new
																							// Textlabel(controlP5,
																							// "FAT Wheels",
																							// 100,
																							// 100);
		appTag.setControlFont(cf);
		appTag.setVisible(true);

		// Display Group
		Group displayCtrls = controlP5.addGroup("Display Controls").setPosition(10, 305);
		Toggle tHub = controlP5.addToggle("showHub", false, 5, 5, 30, 10).setMode(ControlP5.SWITCH);
		tHub.setGroup(displayCtrls);
		tHub.captionLabel().setControlFont(cf);

		Toggle tTire = controlP5.addToggle("showTire", true, 70, 5, 30, 10).setMode(
				ControlP5.SWITCH);
		tTire.setGroup(displayCtrls);
		tTire.captionLabel().setControlFont(cf);

		Toggle tNormals = controlP5.addToggle("showNormals", true, 130, 5, 30, 10).setMode(
				ControlP5.SWITCH);
		tNormals.setGroup(displayCtrls);
		tNormals.captionLabel().setControlFont(cf);

		Toggle tVerb = controlP5.addToggle("verbose", false, 5, 35, 30, 10).setMode(
				ControlP5.SWITCH);
		tVerb.setGroup(displayCtrls);
		tVerb.captionLabel().setControlFont(cf);

		Toggle tAdv = controlP5.addToggle("advCtrls", false, 70, 35, 30, 10).setMode(
				ControlP5.SWITCH);
		tAdv.setGroup(displayCtrls);
		tAdv.captionLabel().setControlFont(cf);

		Toggle tShowDebug = controlP5.addToggle("showDebug", true, 130, 35, 30, 10).setMode(
				ControlP5.SWITCH);
		tShowDebug.setGroup(displayCtrls);
		tShowDebug.captionLabel().setControlFont(cf);

		// Output Group
		Group outCtrls = controlP5.addGroup("Output Controls").setPosition(10, 250);
		Button bTireSave = controlP5.addButton("saveSTL", 1, 5, 5, 55, 15);
		bTireSave.setGroup(outCtrls);
		bTireSave.captionLabel().setControlFont(cf);

		Button bHubSave = controlP5.addButton("saveHub", 1, 65, 5, 55, 15);
		bHubSave.setGroup(outCtrls);
		bHubSave.captionLabel().setControlFont(cf);

		Button bPDFSave = controlP5.addButton("savePDF", 1, 130, 5, 55, 15);
		bPDFSave.setGroup(outCtrls);
		bPDFSave.captionLabel().setControlFont(cf);

		Button bScreenSave = controlP5.addButton("screenShot", 1, 5, 25, 75, 15);
		bScreenSave.setGroup(outCtrls);
		bScreenSave.captionLabel().setControlFont(cf); // saveImagePts

		Toggle tRevSTL = controlP5.addToggle("revSTL", false, 180, 25, 30, 10).setMode(
				ControlP5.SWITCH);
		tRevSTL.setGroup(outCtrls);
		tRevSTL.captionLabel().setControlFont(cf);

		// Advanced Controls Group
		advCtrlsGrp = controlP5.addGroup("Advanced Controls").setPosition(width - 500 - 5, 20);
		advCtrlsGrp.setOpen(true);
		advCtrlsGrp.setVisible(false);

		Slider cMessageBorderSlider = controlP5
				.addSlider("cMessageBorder", 1, 35, 5, 5, 5, 150, 10).setTriggerEvent(
						Slider.RELEASE);
		cMessageBorderSlider.setGroup(advCtrlsGrp);
		cMessageBorderSlider.captionLabel().setControlFont(cf);

		Slider cMeshSmoothSlider = controlP5.addSlider("cMeshSmoothNum", 0, 7, 3, 5, 20, 150, 10)
				.setTriggerEvent(Slider.RELEASE);
		cMeshSmoothSlider.setGroup(advCtrlsGrp);
		cMeshSmoothSlider.snapToTickMarks(true);
		cMeshSmoothSlider.setNumberOfTickMarks(8);
		cMeshSmoothSlider.showTickMarks(false);
		cMeshSmoothSlider.captionLabel().setControlFont(cf);

		Slider cMeshChamfSlider = controlP5.addSlider("cMeshChamf", 0, 0.125f, 0.04f, 5, 35, 150,
				10).setTriggerEvent(Slider.RELEASE);
		cMeshChamfSlider.setGroup(advCtrlsGrp);
		cMeshChamfSlider.captionLabel().setControlFont(cf);

		Slider cStripThicknessSlider = controlP5.addSlider("cStripThickness", 1, 35, 10, 5, 50,
				150, 10).setTriggerEvent(Slider.RELEASE);
		cStripThicknessSlider.setGroup(advCtrlsGrp);
		cStripThicknessSlider.captionLabel().setControlFont(cf);

		Slider cPathClipSlider = controlP5.addSlider("cPathClipAngle", .25f, 1, .75f, 5, 65, 150,
				10).setTriggerEvent(Slider.RELEASE);
		cPathClipSlider.setGroup(advCtrlsGrp);
		cPathClipSlider.captionLabel().setControlFont(cf);

		Slider cPathMinSegSlider = controlP5.addSlider("cPathMinSeg", .1f, 1, .25f, 5, 80, 150, 10)
				.setTriggerEvent(Slider.RELEASE);
		cPathMinSegSlider.setGroup(advCtrlsGrp);
		cPathMinSegSlider.captionLabel().setControlFont(cf);

		Slider cPathResampleSlider = controlP5.addSlider("cResamplePassNum", 0, 7, 3, 5, 95, 150,
				10).setTriggerEvent(Slider.RELEASE);
		cPathResampleSlider.setGroup(advCtrlsGrp);
		cPathResampleSlider.snapToTickMarks(true);
		cPathResampleSlider.setNumberOfTickMarks(8);
		cPathResampleSlider.showTickMarks(false);
		cPathResampleSlider.captionLabel().setControlFont(cf);

		Slider cVorValleyEpSlider = controlP5.addSlider("cVoronoiValEps", 1, 15, 11, 5, 110, 150,
				10).setTriggerEvent(Slider.RELEASE);
		cVorValleyEpSlider.setGroup(advCtrlsGrp);
		cVorValleyEpSlider.setNumberOfTickMarks(15);
		cVorValleyEpSlider.showTickMarks(false);
		cVorValleyEpSlider.captionLabel().setControlFont(cf);

		Slider cVorRoundSlider = controlP5.addSlider("cVoronoiRound", 1, 5, 2, 5, 125, 150, 10)
				.setTriggerEvent(Slider.RELEASE);
		cVorRoundSlider.setGroup(advCtrlsGrp);
		cVorRoundSlider.setNumberOfTickMarks(5);
		cVorRoundSlider.showTickMarks(false);
		cVorRoundSlider.captionLabel().setControlFont(cf);

		Slider cVoronoiTThreshSlider = controlP5.addSlider("cVoronoiTThresh", .1f, .75f, .52f, 5,
				140, 150, 10).setTriggerEvent(Slider.RELEASE);
		cVoronoiTThreshSlider.setGroup(advCtrlsGrp);
		cVoronoiTThreshSlider.captionLabel().setControlFont(cf);

		Slider cVorLetterSpaceSlider = controlP5.addSlider("cVoronoiLettter", .1f, 3, 1.75f, 5,
				155, 150, 10).setTriggerEvent(Slider.RELEASE);
		cVorLetterSpaceSlider.setGroup(advCtrlsGrp);
		cVorLetterSpaceSlider.captionLabel().setControlFont(cf);

		Slider cVorSearchSpaceSlider = controlP5.addSlider("cVoronoiValSearch", .1f, 10, 3, 5, 170,
				150, 10).setTriggerEvent(Slider.RELEASE);
		cVorSearchSpaceSlider.setGroup(advCtrlsGrp);
		cVorSearchSpaceSlider.captionLabel().setControlFont(cf);

		Slider cVorBlurNSlider = controlP5.addSlider("cVoronoiBNum", 1, 10, 4, 5, 185, 150, 10)
				.setTriggerEvent(Slider.RELEASE);
		cVorBlurNSlider.setGroup(advCtrlsGrp);
		cVorBlurNSlider.setNumberOfTickMarks(15);
		cVorBlurNSlider.showTickMarks(false);
		cVorBlurNSlider.captionLabel().setControlFont(cf);

		Slider cVorBlurSizeSlider = controlP5.addSlider("cVoronoiBSize", 1, 10, 5, 5, 200, 150, 10)
				.setTriggerEvent(Slider.RELEASE);
		cVorBlurSizeSlider.setGroup(advCtrlsGrp);
		cVorBlurSizeSlider.setNumberOfTickMarks(15);
		cVorBlurSizeSlider.showTickMarks(false);
		cVorBlurSizeSlider.captionLabel().setControlFont(cf);

		String advDesText = "These are advanced controls.\nRebuild the tire to see the effect.\nChanging these may make or fix holes in the mesh.\nLook in the comments to see what they do.";
		Textlabel advDesc = controlP5.addTextlabel("advDesText", advDesText, 0, 230);// new
																						// Textlabel(controlP5,
																						// "FAT Wheels",
																						// 100,
																						// 100);
		advDesc.setGroup(advCtrlsGrp);
		advDesc.setControlFont(cf);
		advDesc.setVisible(true);

		// Tire Controls
		Group messageCtrls = controlP5.addGroup("Tire Message").setPosition(10, 40);
		textInput = controlP5.addTextfield("Message", 5, 25, 200, 15).setId(2);
		// textInput.setColor(color(255, 255, 255));
		textInput.setFocus(true);
		textInput.setGroup(messageCtrls);
		textInput.setAutoClear(false);
		textInput.captionLabel().setControlFont(cf);

		Bang openSVG = controlP5.addBang("openSVG", 5, 60, 30, 10).setTriggerEvent(Bang.RELEASE);
		openSVG.setGroup(messageCtrls);
		openSVG.captionLabel().setControlFont(cf);

		Toggle tRep = controlP5.addToggle("repeat", false, 70, 60, 30, 10)
				.setMode(ControlP5.SWITCH);
		tRep.setGroup(messageCtrls);
		tRep.captionLabel().setControlFont(cf);

		Toggle tVert = controlP5.addToggle("vert", false, 120, 60, 33, 10)
				.setMode(ControlP5.SWITCH);
		tVert.setGroup(messageCtrls);
		tVert.captionLabel().setControlFont(cf);

		Slider tdSlider = controlP5.addSlider("TDepth", -.125f, .125f, .0625f, 5, 90, 150, 10)
				.setId(1).setTriggerEvent(Slider.RELEASE);
		tdSlider.setGroup(messageCtrls);
		tdSlider.captionLabel().setControlFont(cf);

		Slider tSpace = controlP5.addSlider("Spacing", 5, 50, 10, 5, 110, 150, 10).setId(5)
				.setTriggerEvent(Slider.RELEASE);
		tSpace.setGroup(messageCtrls);
		tSpace.captionLabel().setControlFont(cf);

		Slider tStampW = controlP5.addSlider("stampWidth", 1, 10, 3, 5, 130, 150, 10).setId(6)
				.setTriggerEvent(Slider.RELEASE);
		tStampW.setGroup(messageCtrls);
		tStampW.captionLabel().setControlFont(cf);

		Slider tStampH = controlP5.addSlider("stampThickness", .125f, 1, .25f, 5, 150, 150, 10)
				.setId(7).setTriggerEvent(Slider.RELEASE);
		tStampH.setGroup(messageCtrls);
		tStampH.captionLabel().setControlFont(cf);

		Toggle tTireBlocking = controlP5.addToggle("tireBlocking", true, 5, 170, 30, 10).setMode(
				ControlP5.SWITCH);
		tTireBlocking.setGroup(messageCtrls);
		tTireBlocking.captionLabel().setControlFont(cf);

		Toggle tTopBars = controlP5.addToggle("topBars", true, 180, 170, 30, 10).setMode(
				ControlP5.SWITCH);
		tTopBars.setGroup(messageCtrls);
		tTopBars.captionLabel().setControlFont(cf);

		Toggle tStampAuto = controlP5.addToggle("autoStamp", false, 170, 60, 30, 10).setMode(
				ControlP5.SWITCH);
		tStampAuto.setGroup(messageCtrls);
		tStampAuto.captionLabel().setControlFont(cf);

		// File selector
		list2 = controlP5.addDropdownList("blockingType", 85, 180, 75, 140);
		list2.captionLabel().set("Blocking Type");
		list2.setGroup(messageCtrls);
		list2.addItem("Rectangle", 0);
		list2.addItem("Heart", 1);
		list2.addItem("Circle", 2);
		list2.addItem("Arrow", 3);

		// Rebuild Button
		Button reBuild = controlP5.addButton("Build", 1, 225, 5, 80, 20);
		reBuild.setGroup(messageCtrls);

		// Message output
		outputMSG = controlP5.addTextarea("Messages", messages, width - 200 - 5, 5, 200, height
				- 10 - 12 - 5 - 10);
		// outputMSG.setColorBackground(0xffffff);
		outputMSG.captionLabel().setControlFont(cf);

		String appStatusString = "Step 1 of 89:";
		appStatus = controlP5.addTextlabel("appStatus", appStatusString, width - 10 - 265,
				height - 5 - 10);// new Textlabel(controlP5, "FAT Wheels", 100,
									// 100);
		appStatus.setControlFont(cf);
		appStatus.setVisible(true);

		path = sketchPath + "/data/tire_directory";
		allFiles = listFilesRecursive(path);

		// File selector
		list1 = controlP5.addDropdownList("tireFile", 5, 15, 200, 200);
		list1.captionLabel().set("Select Tire");
		list1.setGroup(messageCtrls);
		for (int i = 1; i < allFiles.size(); i++) {
			File f = (File) allFiles.get(i);
			if (f.isDirectory() == false)
				list1.addItem(f.getName(), i);
			if (f.isDirectory() == true)
				list1.addItem(f.getName() + "---------", 99999);
		}
	}

	/***
	 * List all the files in a directory.
	 * 
	 * @author Daniel Shiffman
	 * @param dir
	 *            Directory to search.
	 * @return Array of files as File.
	 */
	File[] listFiles(String dir) {
		File file = new File(dir);
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			return files;
		} else {
			return null;
		}
	}

	/**
	 * List of all files in a directory and all subdirectories.
	 * 
	 * @author Daniel Shiffman
	 * @param dir
	 *            Directory to search.
	 * @return ArrayList of files as File.
	 */
	ArrayList<File> listFilesRecursive(String dir) {
		ArrayList<File> fileList = new ArrayList<File>();
		recurseDir(fileList, dir);
		return fileList;
	}

	/***
	 * Recursive directory traversal for listFilesRecursive()
	 * 
	 * @author Daniel Shiffman
	 * @param a
	 *            File list, passing back and forth.
	 * @param dir
	 *            Directory to search/
	 */
	void recurseDir(ArrayList<File> a, String dir) {
		File file = new File(dir);
		if (file.isDirectory()) {
			a.add(file);
			File[] subfiles = file.listFiles();
			for (int i = 0; i < subfiles.length; i++) {
				recurseDir(a, subfiles[i].getAbsolutePath());
			}
		} else {
			a.add(file);
		}
	}

	/**
	 * Mouse Drag, setup to not spin the camera when moving a slider
	 */
	public void mouseDragged() {
		if (controlP5.getWindow().isMouseOver())
			return;
	}

	/**
	 * ControlP5 gui event handling.
	 * 
	 * @param _theEvent
	 *            Event to parse.
	 */
	public void controlEvent(ControlEvent _theEvent) {
		// This receives events from the drop down list
		if (_theEvent.isGroup()) {
			if (_theEvent.getName() == "tireFile")
				file = (int) _theEvent.getGroup().getValue();
			if (_theEvent.getName() == "blockingType")
				blockType = (int) _theEvent.getGroup().getValue();
		} else if (_theEvent.isController()) {
			// this receives the rest of the events
			Controller<?> c = _theEvent.getController();
			int cid = c.getId();
			switch (cid) {
			// Handles a slider value change, sets a new depth & flags for a
			// rebuild
			case 1:
				treadDepth = c.getValue();
				break;

			// triggered if new text is set
			case 2:
				textValue = c.getStringValue();
				textLoaded = true;
				sendMessage("Text:\"" + textValue + "\" input");
				// if its empty, draw a blank tire & hide the dt
				if (trim(textValue).toCharArray().length == 0) {
					textLoaded = false;
				}
				shapeLoaded = false;
				buildMesh();
				break;
			}
		}
	}

	/**
	 * Open the File browser for the SVG selector
	 */
	void openSVG() {
		sendMessage("Waiting for SVG");
		// Sets the window title, and calls "fileSelected(path)
		selectInput("Select and SVG to Load:", "fileSelected");
	}

	/**
	 * Parse the selected file, check to see if its an svg.
	 * 
	 * @param loadPath
	 *            Path to parse.
	 */
	void fileSelected(File loadPath) {
		if (loadPath == null) {
			sendMessage("No file was selected...");
		} else {
			if (loadPath.getAbsolutePath().endsWith("svg")) {
				svg = RG.loadShape(loadPath.getAbsolutePath());
				textLoaded = false;
				shapeLoaded = true;
				sendMessage("File: " + loadPath + " loaded.");
				buildMesh();
			} else {
				svg = new RShape();
				shapeLoaded = false;
				sendMessage("Please select an SVG");
			}
		}
	}

	/**
	 * Draw method for a given Delaunay triangulation.
	 * 
	 * @param _dt
	 *            Triangulation (Voronoi) to draw.
	 */
	void drawDT(Voronoi _dt) {
		noFill();
		stroke(0);
		beginShape(TRIANGLES);
		// get the delaunay triangles
		for (Triangle2D t : _dt.getTriangles()) {
			// ignore any triangles which share a vertex with the initial
			// triangle
			if ((abs(t.a.x) != DSIZE && abs(t.a.y) != DSIZE)) {
				beginShape(TRIANGLES);
				vertex(map(t.a.x, 0, build.getTreadX(), 0, width - 40),
						map(t.a.y, 0, build.getTreadY(), 0,
								map(build.getTreadY(), 0, build.getTreadX(), 0, width - 40)));
				vertex(map(t.b.x, 0, build.getTreadX(), 0, width - 40),
						map(t.b.y, 0, build.getTreadY(), 0,
								map(build.getTreadY(), 0, build.getTreadX(), 0, width - 40)));
				vertex(map(t.c.x, 0, build.getTreadX(), 0, width - 40),
						map(t.c.y, 0, build.getTreadY(), 0,
								map(build.getTreadY(), 0, build.getTreadX(), 0, width - 40)));
				// vertex(map(t.c.x, 0, build.getTreadX(), 0, width-40),
				// map(t.c.y, 0, build.getTreadY(), 0, 200));
				endShape();
			}
		}
		endShape();
	}

	/**
	 * Draw a progress bar for a given operation, mapping _p from 0 to _g.
	 * 
	 * @param _p
	 *            Progress
	 * @param _g
	 *            Goal value.
	 */
	void drawProgressBar(int _p, int _g) {
		pushMatrix();
		translate(width - 5 - 200, height - 12 - 5);
		noStroke();
		fill(225);
		rect(0, 0, 200, 12);
		fill(50);
		rect(1, 1, map(_p, 0, _g * 1.1f, 0, 198), 10);
		popMatrix();
	}

	/**
	 * Main status checking function, looks for information from the thread and
	 * processes it.
	 */
	void statusCheck() {
		outputMSG.setVisible(showDebug);

		advCtrlsGrp.setVisible(advCtrls);

		appStatus.setVisible(prog);

		// check the thread
		switch (build.getStatus()) {
		case 0:
			prog = true;
			break;
		case 1:
			// the hub is done
			hubDraw = build.getHub();
			prog = true;
			break;
		case 2:
			// the dt is done option 1
			voronoiDraw = build.getV();
			prog = true;
			break;
		case 3:
			// the dt is done option 2
			voronoiDraw = build.getV();
			prog = true;
			break;
		case 4:
			// the tire is done
			tireDraw = build.getTire();
			lettersDraw = build.getLetters();
			prog = false;
			build.done();
			break;
		}
	}

	/**
	 * Handles the calls & flags to build a new mesh. It checks the thread,
	 * sends it the right info and starts it running.
	 */
	void buildMesh() {
		if (build.isBuilding() == true) {
			// a thread is running, we need to kill it
			build.quit("Interrupted to start a new build.");
		}

		// ok we need to rebuild
		// use the current hub
		if (file == 99999) {
			hub = new File(path + "/Lego/Lego_T10.txt");
		} else {
			allFiles = listFilesRecursive(path);
			hub = (File) allFiles.get(file);
		}

		textValue = textInput.getText();

		// if its empty, draw a blank tire & hide the dt
		if (trim(textValue).toCharArray().length == 0) {
			textLoaded = false;
		} else {
			sendMessage("Text:\"" + textValue + "\" input");
			textLoaded = true;
		}

		if (shapeLoaded) {
			// Rebuild w/ SVG
			build.quit();
			build = null;
			build = new ReBuilderv2(this, svg, hub);
			build.start();
			building = true;
		} else if (textLoaded) {
			// Rebuild w/ text
			build.quit();
			build = null;
			// PApplet, text, hub, reverse the text
			build = new ReBuilderv2(this, textValue, hub, reverseLetters);
			build.start();
			building = true;
		} else {
			// Rebuild Empty
			build.quit();
			build = null;
			build = new ReBuilderv2(this, hub);
			build.start();
			building = true;
		}
	}

	/**
	 * Send a timed message to the GUI and the console.
	 * 
	 * @param _msg
	 *            String to write.
	 */
	void sendMessageT(String _msg) {
		time = millis() - time;
		messages = messages + _msg + " - " + time + " MS\n";
		outputMSG.setText(messages);
		println(_msg + " - " + time + " MS");
	}

	/**
	 * Send a message to the GUI and the console.
	 * 
	 * @param _msg
	 *            String to write.
	 */
	void sendMessage(String _msg) {
		messages = messages + _msg + "\n";
		outputMSG.setText(messages);
		println(_msg);
	}

	/**
	 * Send a message to the GUI and the console, boolean to reset message
	 * stream.
	 * 
	 * @param _msg
	 *            String to write.
	 * @param _reset
	 *            Reset the message list in the GUI.
	 */
	void sendMessage(String _msg, boolean _reset) {
		if (_reset)
			messages = "";
		messages = messages + _msg + "\n";
		outputMSG.setText(messages);
		println(_msg);
	}

	/**
	 * Returns an array of ved3d's used to generate the base point // circles
	 * for the tire and hub, assumes the circles are on the xy plane
	 * 
	 * @param _r
	 *            radius.
	 * @param _res
	 *            number of points around the circle
	 * @param _cp
	 *            center of the circle
	 * @return Vec3D[] of points.
	 */
	Vec3D[] makeCirc(float _r, int _res, Vec3D _cp) {

		float xT, yT;
		float theta;
		float dt = TWO_PI / _res;

		Vec3D[] temp = new Vec3D[_res];
		theta = 0;

		// makes a circle of res points around _cp at _r radius
		// also the base circle is scaled by a factor of D_SF_2, for viewing
		for (int j = 0; j < _res; j++) {
			xT = _r * cos(theta);
			yT = _r * sin(theta);
			theta += dt;
			temp[j] = new Vec3D(xT * D_SF_2, yT * D_SF_2, _cp.z * D_SF_2);
		}

		return temp;
	}

	/***
	 * Save the tire to a stl.
	 */
	public void saveSTL() {
		sendMessage("Saving STL.");
		tireDraw.getScaled(1.0f / D_SF_2).saveAsSTL(
				sketchPath("output/stl/" + year() + "_" + month() + "_" + day() + "_" + hour()
						+ "_" + minute() + "_" + frameCount + "_" + fileName + ".stl"), !revSTL);
	}

	/**
	 * Save the hub to a stl.
	 */
	public void saveHub() {
		hubDraw.getScaled(1.0f / D_SF_2)
				.saveAsSTL(
						sketchPath("output/stl/" + year() + "_" + month() + "_" + day() + "_"
								+ hour() + "_" + minute() + "_" + frameCount + "_" + fileName
								+ "_hub.stl"), !revSTL);
	}

	/***
	 * Save a screenshot
	 */
	public void screenShot() {
		sendMessage("Screen Captured");
		save("output/screenShots/" + year() + "_" + month() + "_" + day() + "_" + hour() + "_"
				+ minute() + "_" + frameCount + "_" + fileName + ".png");
	}

	/**
	 * Save a PDF of the DT w/ raised areas in grey.
	 */
	public void savePDF() {
		PGraphics pdf = createGraphics(floor(build.getTreadX()), floor(build.getTreadY()), PDF,
				sketchPath("output/pdf/" + year() + "_" + month() + "_" + day() + "_" + hour()
						+ "_" + minute() + "_" + frameCount + "_" + fileName + ".pdf"));
		pdf.beginDraw();

		pdf.noStroke();
		pdf.fill(200);

		for (Triangle2D t : build.gettextTri()) {
			// ignore any triangles which share a vertex with the root triangle
			if ((abs(t.a.x) != DSIZE && abs(t.a.y) != DSIZE)) {
				pdf.beginShape(TRIANGLES);
				pdf.vertex(t.a.x, t.a.y);
				pdf.vertex(t.b.x, t.b.y);
				pdf.vertex(t.c.x, t.c.y);
				pdf.endShape();
			}
		}

		pdf.stroke(0);
		pdf.strokeWeight(.01f);
		pdf.noFill();
		// get the delaunay triangles
		for (Triangle2D t : voronoiDraw.getTriangles()) {
			// ignore any triangles which share a vertex with the root triangle
			if ((abs(t.a.x) != DSIZE && abs(t.a.y) != DSIZE)) {
				pdf.beginShape(TRIANGLES);
				pdf.vertex(t.a.x, t.a.y);
				pdf.vertex(t.b.x, t.b.y);
				pdf.vertex(t.c.x, t.c.y);
				pdf.endShape();
			}
		}
		pdf.dispose();
		pdf.endDraw();
	}

	/**
	 * Code to get the angle between three points
	 * 
	 * @author Golan Levin
	 * 
	 * @param x0
	 *            Point 0 x value.
	 * @param y0
	 *            Point 0 y value.
	 * @param x1
	 *            Point 1 x value.
	 * @param y1
	 *            Point 1 y value.
	 * @param x2
	 *            Point 1 x value.
	 * @param y2
	 *            Point 1 y value.
	 * @return
	 */
	public float getJointAngle(float x0, float y0, float x1, float y1, float x2, float y2) {
		float anglei = 0;
		float dxBA = x1 - x0 + 0.00001f;
		float dyBA = y1 - y0;
		float dhBA = (dxBA * dxBA + dyBA * dyBA);
		float dxCB = x2 - x1 + 0.00001f;
		float dyCB = y2 - y1;
		float dhCB = (dxCB * dxCB + dyCB * dyCB);

		if ((dhBA > 0) && (dhCB > 0)) {
			float slopeCB = dyCB / dxCB;
			float slopeBA = dyBA / dxBA;
			float angleBA = atan(slopeBA);
			float angleCB = atan(slopeCB);

			if (dxBA > 0) {
				if (dyBA > 0) {
					if (dxCB > 0) {
						if (dyCB > 0) {
							anglei = angleBA + (PI - angleCB);
						} else if (dyCB <= 0) {
							anglei = PI + angleBA - angleCB;
						}
					} else if (dxCB < 0) {
						if (dyCB > 0) {
							anglei = angleBA - angleCB;
						} else if (dyCB <= 0) {
							float dif = angleBA - angleCB;
							if (dif > 0) {
								anglei = dif;
							} else {
								anglei = TWO_PI + dif;
							}
						}
					}
				} else if (dyBA <= 0) {
					if (dxCB > 0) {
						if (dyCB > 0) {
							anglei = PI + angleBA - angleCB;
						} else if (dyCB <= 0) {
							anglei = PI + angleBA - angleCB;
						}
					} else if (dxCB < 0) {
						if (dyCB > 0) {
							float dif = (0 - angleBA) + angleCB;
							if (dif > 0) {
								anglei = TWO_PI - dif;
							} else {
								anglei = 0 - dif;
							}
						} else if (dyCB <= 0) {
							anglei = TWO_PI + angleBA - angleCB;
						}
					}
				}
			} else if (dxBA < 0) {
				if (dyBA <= 0) {
					if (dxCB < 0) {
						if (dyCB <= 0) {
							anglei = angleBA + PI - angleCB;
						} else if (dyCB > 0) {
							anglei = PI + angleBA - angleCB;
						}
					} else if (dxCB > 0) {
						if (dyCB <= 0) {
							anglei = angleBA - angleCB;
						} else if (dyCB > 0) {
							float dif = angleBA - angleCB;
							if (dif > 0) {
								anglei = dif;
							} else {
								anglei = TWO_PI + dif;
							}
						}
					}
				} else if (dyBA > 0) {
					if (dxCB < 0) {
						if (dyCB > 0) {
							anglei = PI + angleBA - angleCB;
						} else if (dyCB <= 0) {
							anglei = PI + angleBA - angleCB;
						}
					} else if (dxCB > 0) {
						if (dyCB > 0) {
							anglei = TWO_PI - angleCB + angleBA;
						} else if (dyCB <= 0) {
							float dif = (0 - angleBA) + angleCB;
							if (dif > 0) {
								anglei = TWO_PI - dif;
							} else {
								anglei = 0 - dif;
							}
						}
					}
				}
			}
		}

		anglei = (PI - anglei);
		return anglei;
	}

	/***
	 * Point in polygon test. Adapted from http://alienryderflex.com/polygon/
	 * for conversion to doubles & increased accuracy. (Hopefully)
	 * 
	 * @param x
	 *            Point x to test.
	 * @param y
	 *            Point y to test.
	 * @param poly
	 *            Polygon2D (toxiclibs) to test inclusion.
	 * @return
	 */
	public boolean pointInPoly(double x, double y, Polygon2D poly) {
		int j = poly.getNumPoints() - 1;
		boolean inside = false;

		for (int i = 0; i < poly.getNumPoints(); i++) {
			double pX = poly.vertices.get(i).x;
			double pY = poly.vertices.get(i).y;
			double pX2 = poly.vertices.get(j).x;
			double pY2 = poly.vertices.get(j).y;

			if ((pY < y && pY2 >= y || pY2 < y && pY >= y) && (pX <= x || pX2 <= x)) {
				inside ^= (pX + (y - pY) / (pY2 - pY) * (pX2 - pX) < x);
			}
			j = i;
		}

		return inside;
	}

	/**
	 * Draws the voronoi points in raster space, blurs them, and returns the
	 * blurred image.
	 * 
	 * Has to be called outside the main thread sadly due to OPENGL issues with
	 * Processing 2.08b.
	 * 
	 * @param pts
	 *            Points to plot.
	 * @param tx
	 *            Width
	 * @param ty
	 *            Height
	 * @return Blurred PImage
	 */
	public PImage voronoiPtsBlur(List<Vec2D> pts, int tx, int ty) {
		// ptsImg
		PGraphics buffer;
		Convolver c = new Convolver(cVoronoiBSize);
		// multi pass
		buffer = createGraphics(PApplet.floor(tx) + 1, PApplet.floor(ty) + 1);
		buffer.beginDraw();
		buffer.background(color(255, 255, 255));
		buffer.noStroke();
		buffer.smooth();
		buffer.fill(color(0, 0, 0, 24));
		for (Vec2D p : pts) {
			buffer.ellipse(p.x, p.y, 3, 3);
		}
		buffer.endDraw();

		PImage ptsImg = buffer.get(0, 0, buffer.width, buffer.height);

		for (int i = 0; i < cVoronoiBNum; i++) {
			c.blur(ptsImg, 0, 0, buffer.width, buffer.height);
		}

		ptsImg.loadPixels();

		ptsImg.save("output/blur/" + year() + "_" + month() + "_" + day() + "_" + hour() + "_"
				+ minute() + "_" + frameCount + "_" + fileName + ".png");

		return ptsImg;
	}

}
