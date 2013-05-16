package tireGenerator;

import geomerative.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Vector;

import processing.core.*;
import toxi.geom.*;
import toxi.geom.mesh.*;
import toxi.geom.mesh2d.*;

//===============================================================
//Started at 1722 lines
//===============================================================
class CopyOfReBuilder extends Thread {
	// Thread Variables

	TireGenerator sketchRef;

	boolean building = false;
	int mode;
	int buildType = 0;
	String textV;
	long buildTimer;
	File hub;
	int stat;
	boolean dir = false;
	boolean hasText = false;
	boolean hasSVG = false;
	float stampX, stampY, stampThick;

	int prog = 0;
	int goal = 0;

	float patternThickness;
	float baseTreadThickness;

	// mesh Variables
	TriangleMesh hubBuild;
	TriangleMesh faceList;
	WETriangleMesh buildMesh;
	WETriangleMesh remapedFaceMesh;
	WETriangleMesh lettersBuild;
	Voronoi voronoiBuild;

	// Geom Stuff
	RPolygon rp;
	RShape grp;
	RGroup g;
	RShape svg;
	RPoint[][] lettersPaths;
	RPoint[] lettersPts;

	Vec3D[] normalList;

	RShape forBlur;
	PGraphics buffer;
	PImage img;
	PImage ptsImg;

	// variables for fancy parse
	float[] x, y, z;
	String[] lines, rawLines;
	String[] newLines = { " " };
	int file = 1;
	int nCurves;
	int nTireCurves;
	int res;
	int defnTreads;
	float deftDepth;
	float deftThick;
	float treadX, treadY;
	float treadXD, treadYD;
	int[] lockCurves;
	int[] tireCurves;
	float[] r;
	float[] temp;
	Vec3D[] centerPts;

	int vMult = 300;

	// Lists
	List<Vec3D[]> toxiHubCurves = new Vector<Vec3D[]>();
	List<Vec3D[]> toxiTireCurves = new Vector<Vec3D[]>();
	List<Vec3D> textPts = new Vector<Vec3D>();
	List<Triangle2D> textTri = new Vector<Triangle2D>();
	List<Polygon2D> cList = new Vector<Polygon2D>();
	List<Vec3D[]> ptsLow = new Vector<Vec3D[]>();
	List<Vec3D[]> ptsHigh = new Vector<Vec3D[]>();

	// test
	List<RPoint[]> pathList = new Vector<RPoint[]>();

	// ===============================================================
	// Generic constructor
	public CopyOfReBuilder(TireGenerator p) {
		sketchRef = p;
		sketchRef.sendMessage("reBuilder - Empty: Starting", true);
		mode = 0;
		stat = 0;
		hub = new File(sketchRef.path + "/Lego/Lego_T10.txt");
		buildTimer = sketchRef.millis();
	}

	// ===============================================================
	// hub specific constructor
	public CopyOfReBuilder(TireGenerator p, File _h) {
		sketchRef = p;
		sketchRef.sendMessage("reBuilder - Empty: Starting", true);
		mode = 0;
		stat = 0;
		hub = _h;
		buildTimer = sketchRef.millis();
	}

	// ===============================================================
	// SVG constructor
	public CopyOfReBuilder(TireGenerator p, RShape _svg, File _h) {
		sketchRef = p;
		sketchRef.sendMessage("reBuilder - SVG: Starting", true);
		mode = 1;
		stat = 0;
		hub = _h;
		this.svg = _svg;
		voronoiBuild = new Voronoi(sketchRef.DSIZE);
		buildTimer = sketchRef.millis();
	}

	// ===============================================================
	// Text constructor
	public CopyOfReBuilder(TireGenerator p, String _t, File _h, boolean rev) {
		sketchRef = p;
		sketchRef.sendMessage("reBuilder - TEXT: Starting", true);
		mode = 2;
		stat = 0;
		hub = _h;
		dir = rev;
		this.textV = _t;
		voronoiBuild = new Voronoi(sketchRef.DSIZE);
		buildTimer = sketchRef.millis();
	}

	// ===============================================================
	// starts the thread
	public void start() {
		this.building = true;
		super.start();
	}

	// ===============================================================
	// Main call for the thread, calls all the heavy lifters
	public void run() {
		List<Vec2D> dtAdd = new Vector<Vec2D>();
		this.building = true;
		this.progHandle(1);
		this.parseLean(hub); // parse the hub file
		this.progHandle(2);
		if (buildType == 0)
			this.buildHub(); // build the hub
		this.progHandle(3);
		this.stat = 1; // hub is done, set status acordingly

		if (mode > 0)
			this.setupDT(); // if its a blank hub, no dt
		this.progHandle(4);
		if (mode == 2) {
			// text version, add the text to the dt
			dtAdd = this.addLetters(textV);
			this.progHandle(5);
			this.voronoiDT(dtAdd);
			this.progHandle(10);
			if (buildType == 0)
				this.dtSmooth();
			this.findShape(grp, false);
			this.progHandle(11);
			this.stat = 2;
		} else if (mode == 1) {
			// svg version
			dtAdd = this.parseSVG();
			this.progHandle(5);
			this.voronoiDT(dtAdd);
			this.progHandle(10);
			if (buildType == 0)
				this.dtSmooth();
			this.findShape(grp, false);
			this.progHandle(11);
			this.stat = 3;
		}
		if (buildType == 0) {
			this.buildTire(); // start building the tire
		} else if (buildType == 1) {
			this.buildStamp();
		}
		this.progHandle(12);

		this.closeMesh();
		this.progHandle(13);

		if (hasText)
			sketchRef.sendMessage("Tire Generation w/ message: " + sketchRef.textValue + " Done");
		if (sketchRef.shapeLoaded)
			sketchRef.sendMessage("Tire Generation w/ SVG: Done");
		if (!hasText && !sketchRef.shapeLoaded)
			sketchRef.sendMessage("Tire Generation: Done");

		this.stat = 4; // the tire is done
		this.building = false;
	}

	// ===============================================================
	// quit & report time
	public void quit() {
		super.interrupt();
		buildTimer = sketchRef.millis() - buildTimer; // report on the time it
														// took
		sketchRef.sendMessage("reBuilder: Done - " + buildTimer);
	}

	// ===============================================================
	// quit w/ errors used on interupt
	public void quit(String s) {
		super.interrupt();
		buildTimer = sketchRef.millis() - buildTimer;
		sketchRef.sendMessage("reBuilder: Done with error: " + s + " - " + buildTimer);
	}

	public void done() {
		this.stat = 5;
		this.quit();
	}

	// ===============================================================
	// Reads the numbered hub file
	// files are generated in grasshopper as some basic info
	// then a series of center points and r values
	// refer to the template file for how to structure custom hubs
	public void parseLean(File file) {
  sketchRef.sendMessage("Parsing: "+file.getName());
  sketchRef.fileName=file.getName();
  sketchRef.fileName = sketchRef.fileName.substring(0, sketchRef.fileName.length() - 4);
  List<String> fileLines = new Vector<String>();
  //println(file.getAbsolutePath()); //verb

  rawLines = sketchRef.loadStrings(file.getAbsolutePath());
  for (String s : rawLines) {
    if (PApplet.trim(s).charAt(0) != '#') {
      fileLines.add(s);
    }
  }

  if (sketchRef.fileName.startsWith("Stamp")) {

    sketchRef.sendMessage("Stamp!");
    buildType=1;
    stampX = Float.parseFloat(PApplet.trim(fileLines.get(1)));
    stampY = Float.parseFloat(PApplet.trim(fileLines.get(2)));
    stampThick = sketchRef.stampThickness * sketchRef.D_SF;
    res = 50;

    treadY = stampY*sketchRef.D_SF;
    treadX = stampX*sketchRef.D_SF;
    treadYD = (stampY-sketchRef.cMeshChamf*2) * sketchRef.D_SF;
    treadXD = (stampX-sketchRef.cMeshChamf*2) * sketchRef.D_SF;
  }
  else {
	  sketchRef.autoStamp = false;
    //Set default Values
    nCurves = Integer.parseInt(PApplet.trim(fileLines.get(0)));
    deftThick = Float.parseFloat(PApplet.trim(fileLines.get(1)));
    deftDepth = Float.parseFloat(PApplet.trim(fileLines.get(2)));
    res = Integer.parseInt(PApplet.trim(PApplet.trim(fileLines.get(3))));
    float treadR;

    //Set Lock Curves ids and number of radii, and centers
    r = new float[nCurves];
    centerPts = new Vec3D[nCurves];
    lockCurves = new int[2];
    lockCurves[0] = Integer.parseInt(PApplet.split(fileLines.get(4), ", ")[0]);
    lockCurves[1] = Integer.parseInt(PApplet.split(fileLines.get(4), ", ")[1]);

    tireCurves= new int[2];
    tireCurves[0]= Integer.parseInt(PApplet.split(fileLines.get(5), ", ")[0]);
    tireCurves[1]= Integer.parseInt(PApplet.split(fileLines.get(5), ", ")[1]);

    //parse center points
    for (int i = 0; i<nCurves; i++) {
      //add the center points
      centerPts[i] = new Vec3D(0, 0, Float.parseFloat(fileLines.get(i+6)));
    }

    //parse radii
    for (int i = 0; i<nCurves; i++) {
      r[i] = Float.parseFloat(PApplet.trim(fileLines.get(i+6+nCurves)));
    }

    treadR = deftThick;

    //determine and remap the bounds of the tread
    treadY = centerPts[tireCurves[1]].z-centerPts[tireCurves[0]].z-(sketchRef.cMeshChamf*2);
    treadX = PConstants.PI*2*(treadR);
    treadYD = treadY * sketchRef.D_SF; //map(treadY, 0, treadY, 0, displayScale);
    treadXD = treadX * sketchRef.D_SF; //map(treadX, 0, treadY, 0, displayScale)
  }

  sketchRef.sendMessage("Parsing: "+file.getName()+" Done");
}

	// ===============================================================
	// Builds the hub
	// sets up some basic curves needed for the inside, or lock curves
	// of the tire.
	public void buildHub() {
		sketchRef.sendMessage("Hub Generation : Starting");
		hubBuild = new TriangleMesh();

		float xT, yT;
		float theta;
		float dt = PConstants.TWO_PI / res;

		// Draws a circle of [res] points around the center points at the given
		// radii
		// for each of the curves listed in the file
		// setting up the base shape of the tire
		for (int i = 0; i < nCurves; i++) {
			Vec3D[] temp = sketchRef.makeCirc(r[i], res, centerPts[i]);
			toxiHubCurves.add(temp);
		}

		// meshes between each of the curves
		// 2 low, 1 high, 2 high 1 low to give
		// a saw tooth mesh
		// (j+1)%temp1.length allows the loop to call back to the first point
		for (int i = 0; i < (nCurves); i++) {
			if (i + 1 >= nCurves)
				continue;
			Vec3D[] temp1 = toxiHubCurves.get(i);
			Vec3D[] temp2 = toxiHubCurves.get((i + 1));
			for (int j = 0; j < res; j++) {
				hubBuild.addFace(temp1[j], temp2[j], temp1[(j + 1) % temp1.length]);
				hubBuild.addFace(temp2[j], temp2[(j + 1) % temp2.length], temp1[(j + 1)
						% temp1.length]);
			}
		}

		// cap hub
		// makes a simple radial mesh,
		// this area would be where to add detail to the hub
		Vec3D[] temp = toxiHubCurves.get(0);
		Vec3D cp = new Vec3D(0, 0, temp[0].z);
		for (int j = 0; j < res; j++) {
			hubBuild.addFace(cp, temp[j], temp[(j + 1) % res]);
		}

		// cap the other side
		temp = toxiHubCurves.get(nCurves - 1);
		cp = new Vec3D(0, 0, temp[0].z);
		for (int j = 0; j < res; j++) {
			hubBuild.addFace(cp, temp[(j + 1) % res], temp[j]);
		}

		// since we know this mesh is simple and easily closed
		// calculate starting normals, and double check that they
		// all poitn out of the hub.
		hubBuild.computeFaceNormals();
		hubBuild.faceOutwards();
		sketchRef.sendMessage("Hub Generation : Done");
	}

	// ===============================================================
	// Sets up the Delauny Triangulation
	// makes a bar where:
	// height is the width of the tire tread area (after taking off the
	// cMeshChamf)
	// width is the circumfrence of the tire with res points evenly spaced to
	// match the other curves
	public void setupDT() {
		sketchRef.sendMessage("DT Setup: Starting");
		voronoiBuild = new Voronoi(sketchRef.DSIZE);
		float stepY = 0;
		float stepX = treadXD / res;
		int step = 8;
		if (buildType == 1)
			stepY = treadYD / res;
		if (buildType == 1)
			step = res;
		if (buildType == 0)
			stepY = treadYD / 8;
		for (int i = 0; i < res; i++) { // steps points across the top and
										// bottom
			try {
				voronoiBuild.addPoint(new Vec2D((i * stepX), 0));
			} catch (NoSuchElementException exception) {
				sketchRef.sendMessage("Dup Vertex");
			}
			try {
				voronoiBuild.addPoint(new Vec2D((i * stepX), treadYD));
			} catch (NoSuchElementException exception) {
				sketchRef.sendMessage("Dup Vertex");
			}
		}
		for (int i = 0; i < step; i++) { // steps points down the left and right
			try {
				voronoiBuild.addPoint(new Vec2D(0, i * stepY));
			} catch (NoSuchElementException exception) {
				sketchRef.sendMessage("Dup Vertex");
			}
			try {
				voronoiBuild.addPoint(new Vec2D(treadXD, i * stepY));
			} catch (NoSuchElementException exception) {
				sketchRef.sendMessage("Dup Vertex");
			}
		}
		try {
			voronoiBuild.addPoint(new Vec2D(treadXD, treadYD));
		} catch (NoSuchElementException exception) {
			sketchRef.sendMessage("Dup Vertex");
		}
		sketchRef.sendMessage("DT Setup: Done");
	}

	// ===============================================================
	// This takes the delauny triangulation after letters or svgs have been
	// added
	// and applies a voronoi diagram to it
	// this smooths the mesh, and places points inbetween letters fairly
	// intelegently
	// to enable the meshes to be much smoother
	// this process takes a long time, but it makes the best meshes
	public void voronoiDT(List<Vec2D> dtAdd) {
		sketchRef.sendMessage("Adding the voronoi to the mesh.");
		List<Vec2D> uniqueVerts = new Vector<Vec2D>(); // first culled set of
														// voronoi points
		List<Vec2D> filteredVerts = new Vector<Vec2D>(); // second culling
		List<Vec2D> culledVerts = new Vector<Vec2D>(); // second culling
		List<Vec2D> prefinalVerts = new Vector<Vec2D>(); // second to final set
		List<Vec2D> finalVerts = new Vector<Vec2D>(); // final set, done as a
														// hash set to dodge
														// duplicates
		float[][] distance; // distance matrix for point to point comparisons
		Rect bound = new Rect(new Vec2D(0, 0), new Vec2D(treadXD, treadYD)); // a
																				// boundry
																				// to
																				// clip
																				// the
																				// voronoi.
		int n = 0;

		goal = dtAdd.size();
		prog = 0;

		for (Vec2D p : dtAdd) {
			prog++;
			try {
				voronoiBuild.addPoint(p);
			} catch (NoSuchElementException exception) {
				sketchRef.sendMessage("Dup Vertex");
			}
		}

		this.progHandle(5, voronoiBuild.getRegions().size());

		// exclude all voronoi points outside of the tire tread
		for (Polygon2D region : voronoiBuild.getRegions()) {
			prog++;
			for (Vec2D v : region.vertices) {
				v.x = (float) Math.round(v.x * ((float) Math.pow(10, sketchRef.cVoronoiRound)) / Math.pow(10, sketchRef.cVoronoiRound)); // round
																									// them
																									// slightly
																									// was
																									// 100000
				v.y = (float) Math.round(v.y * ((float) Math.pow(10, sketchRef.cVoronoiRound)) / Math.pow(10, sketchRef.cVoronoiRound)); // round
																									// them
																									// slightly
				n++;
				// if its in the bounds, if we haven't picked it already
				// (rounding could group some points)
				// and we pick every 5th point to just reduce the inital set a
				// little
				if (bound.containsPoint(v) && uniqueVerts.contains(v) == false) { // &&
																					// n
																					// %
																					// 5
																					// ==
																					// 0
					uniqueVerts.add(v);
				}
			}
		}

		// ===============================================================
		// PTS Blur Filtering

		ptsImg = sketchRef.voronoiPtsBlur(uniqueVerts, (int) treadXD, (int) treadYD);

		float[][] blurValue = new float[uniqueVerts.size()][1];

		this.progHandle(6, uniqueVerts.size());

		int ptsImgW = ptsImg.width;
		int[] ptsImgPixels = ptsImg.pixels; //color?
		for (int i = 0; i < uniqueVerts.size(); i++) {
			prog++;
			Vec2D ithVert = uniqueVerts.get(i);
			int ithVertX = (int) PApplet.floor(ithVert.x);
			int ithVertY = (int) PApplet.floor(ithVert.y);
			int ithVertIndex = (ithVertY * ptsImgW) + ithVertX;
			int ithVertColor = ptsImgPixels[ithVertIndex];
			int rawValue = (ithVertColor & 0x000000FF); // fetch the blue
														// component
			blurValue[i][0] = rawValue / 255.0f;
		}

		// ===============================================================
		// Rework the data set
		float maxVal = 0;
		float minVal = 100000;

		for (int i = 0; i < uniqueVerts.size(); i++) {
			if (blurValue[i][0] > maxVal)
				maxVal = blurValue[i][0];
			if (blurValue[i][0] < minVal)
				minVal = blurValue[i][0];
		}

		for (int i = 0; i < uniqueVerts.size(); i++) {
			blurValue[i][0] = PApplet.map(blurValue[i][0], minVal, maxVal, 0, 1);
		}

		// ===============================================================
		// Cull Filtering

		List<Vec2D> baseKill = new Vector<Vec2D>();
		List<Vec2D> valleyKill = new Vector<Vec2D>();
		List<Vec2D> winners = new Vector<Vec2D>();

		List<Vec2D> valleyPts = new Vector<Vec2D>();

		for (int i = 0; i < uniqueVerts.size(); i++) {
			if (blurValue[i][0] > sketchRef.cVoronoiTThresh) {
				// very light
				valleyPts.add(uniqueVerts.get(i));
			} else {
				// middle & bottom do the valley check
				valleyPts.add(uniqueVerts.get(i)); // finalVerts
				baseKill.add(uniqueVerts.get(i));
			}
		}

		// ===============================================================
		// Valley Checking
		int[] stat = new int[valleyPts.size()]; // 1 alive 0 dead
		Arrays.fill(stat, 1);

		this.progHandle(7, valleyPts.size());

		float ep = PApplet.pow(10, -sketchRef.cVoronoiValEps);
		for (int i = 0; i < valleyPts.size(); i++) {
			prog++;
			// Find a group
			List<Vec2D> myGrp = new Vector<Vec2D>();
			myGrp.add(valleyPts.get(i)); // add me first!
			for (Vec2D v : valleyPts) {
				if (v.distanceTo(valleyPts.get(i)) < sketchRef.cVoronoiValSearch
						&& v.distanceTo(valleyPts.get(i)) > ep) {
					// should not add itself thnx to ep
					myGrp.add(v);
				}
			}
			// Get the Values of that Group
			float[] values = new float[myGrp.size()];
			for (int j = 0; j < myGrp.size(); j++) {
				Vec2D ithVert = myGrp.get(j);
				int ithVertX = (int) PApplet.floor(ithVert.x);
				int ithVertY = (int) PApplet.floor(ithVert.y);
				int ithVertIndex = (ithVertY * ptsImgW) + ithVertX;
				int ithVertColor = ptsImgPixels[ithVertIndex];
				float rawValue = (ithVertColor & 0x000000FF); // fetch the blue
																// component
				values[j] = rawValue;
			}

			for (int j = 1; j < myGrp.size(); j++) {
				// sketchRef.sendMessage(values[j] +" vs "+ values[0]);
				if (values[j] < values[0]) {
					stat[i] = 0;
					valleyKill.add(myGrp.get(0));
				} else if (PApplet.abs(values[j] - values[0]) < 0.1) { // ep test so
																// they are
																// equal
					if (myGrp.get(0).x < myGrp.get(j).x) {
						stat[i] = 0;
						valleyKill.add(myGrp.get(0));
					}
				}
			}
		}

		for (int i = 0; i < valleyPts.size(); i++) {
			if (stat[i] == 1) {
				prefinalVerts.add(valleyPts.get(i));
				winners.add(valleyPts.get(i));
			}
		}
		// ===============================================================
		sketchRef.sendMessage(valleyPts.size() + " Pts filtered to " + prefinalVerts.size()
				+ " by pts blur.");

		// ===============================================================
		// Update Img
		for (int i = 0; i < baseKill.size(); i++) {
			ptsImg.set(PApplet.floor(baseKill.get(i).x), PApplet.floor(baseKill.get(i).y), sketchRef.color(255, 0, 0));
		}
		ptsImg.updatePixels();

		for (int i = 0; i < valleyKill.size(); i++) {
			ptsImg.set(PApplet.floor(valleyKill.get(i).x), PApplet.floor(valleyKill.get(i).y), sketchRef.color(255, 100, 0));
		}
		ptsImg.updatePixels();

		for (int i = 0; i < winners.size(); i++) {
			ptsImg.set(PApplet.floor(winners.get(i).x), PApplet.floor(winners.get(i).y), sketchRef.color(0, 255, 0));
		}
		ptsImg.updatePixels();
		// ===============================================================

		// ===============================================================
		// setup to check how close a given voronoi pt is to a letter
		float[][] pairs;
		pairs = new float[3][prefinalVerts.size()];

		this.progHandle(8, prefinalVerts.size());

		// loop through all the voronoi points
		// check them against all the letter points
		// find the letter point its the closest to
		// record that info
		for (int j = 0; j < prefinalVerts.size(); j++) {
			prog++;
			int closeID = 999999;
			float closeD = 10000;
			for (int k = 0; k < lettersPts.length; k++) {
				if (prefinalVerts.get(j).distanceTo(new Vec2D(lettersPts[k].x, lettersPts[k].y)) < closeD) {
					closeID = k;
					closeD = prefinalVerts.get(j).distanceTo(
							new Vec2D(lettersPts[k].x, lettersPts[k].y));
				}
			}
			pairs[0][j] = j; // point id
			pairs[1][j] = closeID; // letter point id
			pairs[2][j] = closeD; // distance
		}

		this.progHandle(9, prefinalVerts.size());

		// now check all of the pairing we found above
		// for a voronoi point v to be allowed, it should be k times as far
		// from letter point l, and l is from l+1 on the letter curve
		// this prevent obtuse triangles near the edge of the letters
		// which helps when selecting tiangles that are parts of the letters
		for (int i = 0; i < prefinalVerts.size(); i++) {
			prog++;
			int closeID = (int) (pairs[1][i]);
			int neighborID = (int) ((pairs[1][i]) + 1) % lettersPts.length;
			float segDist = lettersPts[closeID].dist(lettersPts[neighborID]);
			if (pairs[2][i] > sketchRef.cVoronoiLettter * segDist) {
				finalVerts.add(prefinalVerts.get(i));
			}
		}

		// the final points are setup
		// add them to the dt
		// also can be time intensive
		this.progHandle(9, finalVerts.size());
		for (Vec2D v : finalVerts) {
			prog++;
			try {
				voronoiBuild.addPoint(v);
			} catch (NoSuchElementException exception) {
				sketchRef.sendMessage("Dup Vertex");
			}
		}
	}

	

	// ===============================================================
	// Smooth the DT
	// Calls the smoother recursivly
	void dtSmooth() {
		boolean flat = checkFlats(sketchRef.cMeshSmoothNum);
	}

	// ===============================================================
	// Actual smoother
	boolean checkFlats(int _max) {
		sketchRef.sendMessage("Smoothing Mesh, Pass " + ((sketchRef.cMeshSmoothNum - _max) + 1) + " of "
				+ sketchRef.cMeshSmoothNum + ".");
		int count = 0;
		float w = 0;
		List<Triangle2D> triDiv = new Vector<Triangle2D>();

		// x > y, means stout tire, so worry about the x value
		for (Triangle2D t : voronoiBuild.getTriangles()) {
			// ignore any triangles which share a vertex with the initial root
			// triangle
			if ((Math.abs(t.a.x) != sketchRef.DSIZE && Math.abs(t.a.y) != sketchRef.DSIZE)) {
				// if the width of a triangle is > the circumfrence / something,
				// divide it
				if (t.a.x < t.b.x && t.a.x < t.c.x) {
					// start at a
					w = (t.b.x < t.c.x) ? (t.c.x - t.a.x) : (t.c.x - t.a.x); // c
																				// :
																				// b
				} else if (t.b.x < t.a.x && t.b.x < t.c.x) {
					// start at b
					w = (t.a.x < t.c.x) ? (t.c.x - t.b.x) : (t.a.x - t.b.x); // c
																				// :
																				// a
				} else {
					// start at c
					w = (t.b.x < t.a.x) ? (t.a.x - t.c.x) : (t.b.x - t.c.x); // a
																				// :
																				// b
				}

				// if the width is larger than the desired ammount, subdivide
				if (Math.abs(w) > treadXD / res) {
					count++;
					triDiv.add(t);
				}
			}
		}

		for (Triangle2D t : triDiv) {
			try {
				t.computeCentroid();
				voronoiBuild.addPoint(t.centroid);
			} catch (NoSuchElementException exception) {
				if (sketchRef.verbose)
					sketchRef.sendMessage("Bad Triangle");
			}
		}

		triDiv.clear();

		if (_max <= 1) {
			return true;
		}

		if (count == 0) {
			if (sketchRef.verbose)
				sketchRef.sendMessage("Smoothing Completed Early.");
			return true;
		} else {
			_max--;
			return checkFlats(_max);
		}
	}

	// ===============================================================
	// Add letters to the DT
	// Uses geomerative to generate a RShape of the letters in the message
	// scales, rotates, and repeats that message as needed.
	// adds
	List<Vec2D> addLetters(String theText) {
		sketchRef.sendMessage("Adding letters " + theText);
		List<Vec2D> dtAdd = new Vector<Vec2D>();
		float scaleFactor;
		float textY;
		float translateY;
		float textX;
		float translateX;
		float blockW;
		float num;

		grp = sketchRef.font.toShape(theText);

		hasText = true;

		if (buildType == 0) {

			// Reverse The Text
			if (dir) {
				grp = sketchRef.font.toShape(theText);
				grp.scale(-1, 1);
				grp.translate(grp.getWidth() + (treadYD * (sketchRef.cStripThickness / 100)), 0);
			}

			// Rotate the Text to be vert
			if (sketchRef.vert) {
				grp.rotate(-PConstants.PI / 2);
				grp.translate(grp.getWidth(), grp.getHeight());
			}

			// First scale based on Text height
			scaleFactor = (treadYD - treadYD * (sketchRef.cMessageBorder / 100) * 2) / grp.getHeight();
			if (sketchRef.topBars)
				scaleFactor = (treadYD - (treadYD * (sketchRef.cMessageBorder / 100) * 4)
						- (treadYD * (sketchRef.cStripThickness / 100) * 2) - (treadYD
						* (sketchRef.cMessageBorder / 100) / 2))
						/ grp.getHeight();
			grp.scale(scaleFactor);

			// Backup scale Based on Width
			if (grp.getWidth() > treadXD - (treadYD * (sketchRef.cMessageBorder / 100)) * 2) {
				scaleFactor = (treadXD - (treadYD * (sketchRef.cMessageBorder / 100)) * 2) / grp.getWidth();
				grp.scale(scaleFactor);
			}

			// calculate offset
			textY = grp.getCenter().y;
			translateY = treadYD / 2 - textY;

			// Move the text into place
			grp.translate(0, translateY);

			// If the text needs to be repeated calculate that
			if (sketchRef.repeat) {
				// n times
				num = treadXD / (grp.getWidth() + sketchRef.patternSpace);
				num = (float) Math.floor(num);
				sketchRef.sendMessage("Repeating Shape " + num + " times.");

				// Calc spacing
				float remainder = treadXD - ((grp.getWidth()) * num)
						- (treadYD * (sketchRef.cMessageBorder / 100)) * 2;
				remainder = remainder / num;

				// shape to repeat
				RShape startingShape = new RShape(grp);

				// repeat it, by duplicating the base shape
				for (int i = 1; i < num; i++) {
					RShape temp = new RShape(startingShape);
					temp.translate(grp.getWidth() + remainder, 0);
					grp.addChild(temp);
				}
			}

			// ADD STRIPS AT TOP AND BOTTOM
			if (sketchRef.topBars) {
				blockW = grp.getWidth();

				RShape block = genBlock(9, blockW, (treadYD * (sketchRef.cStripThickness / 100)),
						(treadYD * (sketchRef.cMessageBorder / 100)));
				block.translate((treadYD * (sketchRef.cMessageBorder / 100)),
						(treadYD * (sketchRef.cMessageBorder / 100)) / 2); // (treadYD*(cMessageBorder/100))/2
				grp.addChild(block);

				block = genBlock(9, blockW, (treadYD * (sketchRef.cStripThickness / 100)),
						(treadYD * (sketchRef.cMessageBorder / 100)));
				block.translate((treadYD * (sketchRef.cMessageBorder / 100)), treadYD
						- (treadYD * (sketchRef.cMessageBorder / 100)) / 2
						- (treadYD * (sketchRef.cStripThickness / 100)));
				grp.addChild(block);
			}

			if (sketchRef.tireBlocking) {
				// calcuate the left over empty space
				blockW = treadXD - grp.getWidth() - (treadYD * (sketchRef.cMessageBorder / 100)) * 2;

				// if there is enough, drop in a block
				if (blockW > (treadYD * (sketchRef.cMessageBorder / 100)) * 3) {
					// generate the block here
					RShape block = genBlock(sketchRef.blockType, blockW, treadYD,
							(treadYD * (sketchRef.cMessageBorder / 100)));

					// move the block
					textY = block.getCenter().y;
					translateY = treadYD / 2 - textY;
					block.translate(((treadYD * (sketchRef.cMessageBorder / 100)) * 2) + grp.getWidth(),
							translateY);

					// add it
					grp.addChild(block);
				}
			}
		} else {

			if (sketchRef.autoStamp) {
				sketchRef.sendMessage("Auto Stamp On");

				stampX = sketchRef.stampWidth;
				treadX = stampX * sketchRef.D_SF;
				treadXD = treadX - (sketchRef.cMeshChamf * sketchRef.D_SF * 2);

				scaleFactor = (treadXD - (treadYD * (sketchRef.cMessageBorder / 100)) * 2) / grp.getWidth();
				grp.scale(scaleFactor);

				treadYD = grp.getHeight() + (treadYD * (sketchRef.cMessageBorder / 100)) * 2;
				treadY = treadYD + (sketchRef.cMeshChamf * sketchRef.D_SF * 2);
				stampY = treadY / sketchRef.D_SF;

				textY = grp.getCenter().y;
				translateY = treadYD / 2 - textY;
				textX = grp.getCenter().x;
				translateX = treadXD / 2 - textX;

				// Move the text into place
				grp.translate(translateX, translateY);

				setupDT();
			} else {

				// First scale based on Text height
				scaleFactor = (treadYD - (treadYD * (sketchRef.cMessageBorder / 100)) * 2) / grp.getHeight();
				grp.scale(scaleFactor);

				// Backup scale Based on Width
				if (grp.getWidth() > treadXD - (treadYD * (sketchRef.cMessageBorder / 100)) * 2) {
					scaleFactor = (treadXD - (treadYD * (sketchRef.cMessageBorder / 100)) * 2)
							/ grp.getWidth();
					grp.scale(scaleFactor);
				}

				// calculate offset
				textY = grp.getCenter().y;
				translateY = treadYD / 2 - textY;
				textX = grp.getCenter().x;
				translateX = treadXD / 2 - textX;

				// Move the text into place
				grp.translate(translateX, translateY);
			}
		}

		// resample the text
		for (int i = 1; i <= sketchRef.cResamplePassNum; i++) { // 5
			if (sketchRef.verbose)
				sketchRef.sendMessage("reSample Pass: " + i);
			grp = new RShape(reSampleSVG(grp));
		}
		// Clip sharp corners
		grp = new RShape(clipContours(grp));

		// setup output
		lettersPts = grp.getPoints();
		lettersPaths = grp.getPointsInPaths();

		normalList = new Vec3D[lettersPts.length];

		int count = 0;
		for (RPoint[] r : lettersPaths) { // normalList
			float xDif, yDif;

			for (int i = 0; i < r.length; i++) {
				RPoint x1 = r[i];
				RPoint x2 = r[(i + 1) % r.length];

				xDif = x2.x - x1.x;
				yDif = x2.y - x1.y;

				normalList[count] = new Vec3D(yDif, -xDif, 0);

				count++;
			}
		}

		for (int j = 0; j < lettersPts.length; j++) {
			textPts.add(new Vec3D(lettersPts[j].x, lettersPts[j].y, 0));
			dtAdd.add(new Vec2D(lettersPts[j].x, lettersPts[j].y));
		}

		// return a list of points
		return dtAdd;
	}

	// add better top bar subdiv control
	RShape genBlock(int type, float w, float h, float border) {
		String loadPath;
		RShape tempSVG;
		RShape block = new RShape();
		RPath bp = new RPath();

		RSVG svg = new RSVG();

		float scaleFactor = 1;
		float space = 10;
		float num = 1;
		float startingWidth = 0;
		RShape startingShape;
		int nRep;

		List<RPath> initPaths = new Vector<RPath>();

		float step = w / res;
		float stepH = h / 5;
		float stepHL = (h - border) / res;
		float stepW = w / (res * 3);
		float stepWL = (w - border * 2) / (res * 3);

		if (type == 0) {
			// block
			RPoint p = new RPoint(0, 0);
			bp.addLineTo(p);

			for (int i = 0; i < (res * 3); i++) {
				p = new RPoint((i * stepWL), 0);
				bp.addLineTo(p);
			}

			p = new RPoint(w - border * 2, 0);
			bp.addLineTo(p);

			for (int i = 0; i < res; i++) {
				p = new RPoint(w - border * 2, (i * stepHL));
				bp.addLineTo(p);
			}

			p = new RPoint(w - border * 2, h - border);
			bp.addLineTo(p);

			for (int i = (res * 3); i > 0; i--) {
				p = new RPoint((i * stepWL), h - border);
				bp.addLineTo(p);
			}

			p = new RPoint(0, h - border);
			bp.addLineTo(p);

			for (int i = res; i > 0; i--) {
				p = new RPoint(0, (i * stepHL));
				bp.addLineTo(p);
			}
			bp.addClose();
			block.addPath(bp);
			return block;
		}

		if (type == 9) {
			// top block

			RPoint p = new RPoint(0, 0);
			bp.addLineTo(p);

			for (int i = 0; i < (res * 3); i++) {
				p = new RPoint(i * stepW, 0);
				bp.addLineTo(p);
			}

			p = new RPoint(w, 0);
			bp.addLineTo(p);

			for (int i = 0; i < 5; i++) {
				p = new RPoint(w, i * stepH);
				bp.addLineTo(p);
			}

			p = new RPoint(w, h);
			bp.addLineTo(p);

			for (int i = (res * 3); i > 0; i--) {
				p = new RPoint(i * stepW, h);
				bp.addLineTo(p);
			}

			p = new RPoint(0, h);
			bp.addLineTo(p);

			for (int i = 5; i > 0; i--) {
				p = new RPoint(0, i * stepH);
				bp.addLineTo(p);
			}

			bp.addClose();

			block.addPath(bp);
			return block;
		}

		switch (type) {
		case 1:
			loadPath = (sketchRef.sketchPath("data/svg/spacers/heart.svg"));
			tempSVG = svg.toShape(loadPath);
			break;
		case 2:
			loadPath = (sketchRef.sketchPath("data/svg/spacers/circle.svg"));
			tempSVG = svg.toShape(loadPath);
			break;
		case 3:
			loadPath = (sketchRef.sketchPath("data/svg/spacers/arrow.svg"));
			tempSVG = svg.toShape(loadPath);
			break;
		default:
			loadPath = (sketchRef.sketchPath("data/svg/spacers/arrow.svg"));
			tempSVG = svg.toShape(loadPath); // RG.loadShape
			break;
		}

		tempSVG = RG.loadShape(loadPath);

		for (RShape child : tempSVG.children) {
			for (RPoint[] pp : child.getPointsInPaths()) { // lettersPaths ->
															// g.getPointsInPaths(
				RPath newP = new RPath(pp);
				initPaths.add(newP);
			}
		}

		tempSVG = new RShape();

		for (RPath pat : initPaths) {
			tempSVG.addChild(new RShape(pat));
		}

		scaleFactor = (h - border) / tempSVG.getHeight();
		tempSVG.scale(scaleFactor);

		if (tempSVG.getWidth() >= treadXD - border * 2) {
			scaleFactor = (treadXD - border * 2) / tempSVG.getWidth();
			tempSVG.scale(scaleFactor);
		}

		startingWidth = tempSVG.getWidth();
		startingShape = new RShape(tempSVG.toShape());
		nRep = (int) Math.floor(w / (tempSVG.getWidth() + border * 1));

		float remainder = (w - ((startingWidth + border * 2) * nRep)) / (nRep - 1);

		if (nRep > 1) {
			for (int i = 0; i < nRep; i++) {
				RShape temp = new RShape(startingShape);
				temp.translate(((startingWidth + (border * 2) + remainder) * i), 0); // +remainder
				block.addChild(temp);
			}
		} else {
			block.addChild(tempSVG);
		}

		return block;
	}

	RShape clipContours(RShape s) {
		RShape clippedShape = new RShape();
		List<RPath> initPaths = new Vector<RPath>();

		for (RPoint[] pp : s.getPointsInPaths()) { // lettersPaths ->
													// g.getPointsInPaths(
			RPath newP = new RPath(pp);
			initPaths.add(newP);
		}

		for (RPath path : initPaths) {
			boolean ptAdded = false;
			RPath tempPath;
			List<RPoint> initPts = new Vector<RPoint>();
			List<RPoint> reSampledPts = new Vector<RPoint>();

			for (RPoint p : path.getPoints()) {
				initPts.add(p);
			}

			for (int i = 0; i < initPts.size(); i++) {
				RPoint z = initPts.get((i + initPts.size() - 2) % initPts.size());
				RPoint a = initPts.get((i + initPts.size() - 1) % initPts.size());
				RPoint b = initPts.get(i);
				RPoint c = initPts.get((i + 1) % initPts.size());
				RPoint d = initPts.get((i + 2) % initPts.size());
				if (sketchRef.getJointAngle(a.x, a.y, b.x, b.y, c.x, c.y) > -PConstants.PI * sketchRef.cPathClipAngle
						&& sketchRef.getJointAngle(a.x, a.y, b.x, b.y, c.x, c.y) < PConstants.PI * sketchRef.cPathClipAngle) {
					reSampledPts.add(b);
				} else {
					if (sketchRef.verbose)
						sketchRef.sendMessage("Clipped "
								+ sketchRef.getJointAngle(a.x, a.y, b.x, b.y, c.x, c.y));
				}
			}
			tempPath = new RPath(reSampledPts.toArray(new RPoint[reSampledPts.size()]));

			clippedShape.addPath(tempPath);
		}

		return clippedShape;
	}

	// reSample SVG
	RShape reSampleSVG(RShape s) {
		float mDist;
		RShape reSampledShape = new RShape();
		List<RPath> initPaths = new Vector<RPath>();

		for (RPoint[] pp : s.getPointsInPaths()) { // lettersPaths ->
													// g.getPointsInPaths(
			RPath newP = new RPath(pp);
			initPaths.add(newP);
		}

		for (RPath path : initPaths) {
			if (sketchRef.verbose)
				sketchRef.sendMessage("Path Length: " + path.getCurveLength());
			mDist = PApplet.map(path.getCurveLength(), 1, 1000, 2, 6); // 1, 1000, 2, 6

			List<RPoint> initPts = new Vector<RPoint>();
			List<RPoint> reSampledPts = new Vector<RPoint>();

			RPath tempPath;

			for (RPoint p : path.getPoints()) {
				initPts.add(p);
			}

			for (int i = 0; i < initPts.size(); i++) {
				// println(initPts.get(i).dist(initPts.get((i+1)%initPts.size()))
				// +" seg dist");
				if (initPts.get(i).dist(initPts.get((i + 1) % initPts.size())) > mDist) {
					int nPts = (int) Math.floor(initPts.get(i).dist(initPts.get((i + 1) % initPts.size()))
							/ mDist);
					reSampledPts.add(initPts.get(i));

					for (int j = 1; j <= nPts; j++) {
						RPoint tempP;
						float lerpF = (float) j / (nPts + 1);
						float x = PApplet.lerp(initPts.get(i).x, initPts.get((i + 1) % initPts.size()).x,
								lerpF);
						float y = PApplet.lerp(initPts.get(i).y, initPts.get((i + 1) % initPts.size()).y,
								lerpF);
						tempP = new RPoint(x, y);
						reSampledPts.add(tempP);
					}
				} else if (initPts.get(i).dist(initPts.get((i + 1) % initPts.size())) < sketchRef.cPathMinSeg) {
					if (sketchRef.verbose)
						sketchRef.sendMessage("Too Close "
								+ initPts.get(i).dist(initPts.get((i + 1) % initPts.size())));
				} else {
					reSampledPts.add(initPts.get(i));
				}
			}
			tempPath = new RPath(reSampledPts.toArray(new RPoint[reSampledPts.size()]));
			reSampledShape.addPath(tempPath);
		}

		return reSampledShape;
	}

	void findShape(RShape g, boolean rev) {

		sketchRef.sendMessage("Finding Shape Triangles: Starting");
		int count = 0;
		int off = 0;
		boolean inHole = false;
		hasText = true;

		cList.clear();

		for (RPoint[] pp : lettersPaths) { // lettersPaths ->
											// g.getPointsInPaths(
			Polygon2D newP = new Polygon2D();
			for (RPoint p : pp) {
				newP.add(new Vec2D(p.x, p.y));
			}
			if (rev) {
				newP.flipVertexOrder();
			}
			cList.add(newP);
		}

		for (Triangle2D t : voronoiBuild.getTriangles()) {
			t.computeCentroid();
			Vec2D pt = t.centroid;
			for (Polygon2D c : cList) {
				if (sketchRef.pointInPoly((double) pt.x, (double) pt.y, c)) {
					// /sketchRef.sendMessage("Inside");
					inHole = true;
					for (Polygon2D cc : cList) {
						if (cc.getArea() < 0) {
							if (sketchRef.pointInPoly((double) pt.x, (double) pt.y, cc)) {
								inHole = false;
							}
						}
					}
				}
			}

			if (inHole) {
				textTri.add(t);
				count++;
			}
			inHole = false;
		}
		sketchRef.sendMessage("Finding Shape Triangles: Done");
	}

	// ===============================================================
	// Parsing the SVG
	// needs refinement
	List<Vec2D> parseSVG() {
		sketchRef.sendMessage("Adding SVG");
		List<Vec2D> dtAdd = new Vector<Vec2D>();
		float scaleFactor;
		float textY;
		float translateY;
		float blockW;
		float num;

		grp = new RShape(svg);

		hasText = true;

		// Reverse The Text
		if (dir) {
			grp.scale(-1, 1);
			grp.translate(grp.getWidth() + (treadYD * (sketchRef.cMessageBorder / 100)), 0);
		}

		// Rotate the Text to be vert
		if (sketchRef.vert) {
			grp.rotate(-PConstants.PI / 2);
			grp.translate(grp.getWidth(), grp.getHeight());
		}

		// First scale based on Text height
		scaleFactor = (treadYD - treadYD * (sketchRef.cMessageBorder / 100) * 2) / grp.getHeight();
		if (sketchRef.topBars)
			scaleFactor = (treadYD - (treadYD * (sketchRef.cMessageBorder / 100) * 4)
					- (treadYD * (sketchRef.cStripThickness / 100) * 2) - (treadYD * (sketchRef.cMessageBorder / 100) / 2))
					/ grp.getHeight();

		grp.scale(scaleFactor);

		// Backup scale Based on Width
		if (grp.getWidth() > treadXD - (treadYD * (sketchRef.cMessageBorder / 100)) * 3) {
			scaleFactor = (treadXD - (treadYD * (sketchRef.cMessageBorder / 100)) * 2) / grp.getWidth();
			grp.scale(scaleFactor);
		}

		// calculate offset
		textY = grp.getCenter().y;
		translateY = treadYD / 2 - textY;

		// Move the text into place
		grp.translate((treadYD * (sketchRef.cMessageBorder / 100)), translateY);

		// If the text needs to be repeated calculate that
		if (sketchRef.repeat) {
			// n times
			num = treadXD / (grp.getWidth() + sketchRef.patternSpace);
			num = (int) Math.floor(num);
			sketchRef.sendMessage("Repeating Shape " + num + " times.");

			// Calc spacing
			float remainder = treadXD - ((grp.getWidth()) * num)
					- (treadYD * (sketchRef.cMessageBorder / 100)) * 2;
			remainder = remainder / num;

			// shape to repeat
			RShape startingShape = new RShape(grp);

			// repeat it, by duplicating the base shape
			for (int i = 1; i < num; i++) {
				RShape temp = new RShape(startingShape);
				temp.translate(grp.getWidth() + remainder, 0);
				grp.addChild(temp);
			}
		}

		// ADD STRIPS AT TOP AND BOTTOM
		if (sketchRef.topBars) {
			blockW = grp.getWidth();

			RShape block = genBlock(9, blockW, (treadYD * (sketchRef.cStripThickness / 100)),
					(treadYD * (sketchRef.cMessageBorder / 100)));
			block.translate((treadYD * (sketchRef.cMessageBorder / 100)),
					(treadYD * (sketchRef.cMessageBorder / 100)) / 2); // (treadYD*(sketchRef.cMessageBorder/100))/2
			grp.addChild(block);

			block = genBlock(9, blockW, (treadYD * (sketchRef.cStripThickness / 100)),
					(treadYD * (sketchRef.cMessageBorder / 100)));
			block.translate((treadYD * (sketchRef.cMessageBorder / 100)), treadYD
					- (treadYD * (sketchRef.cMessageBorder / 100)) / 2 - (treadYD * (sketchRef.cStripThickness / 100)));
			grp.addChild(block);
		}

		if (sketchRef.tireBlocking) {
			// calcuate the left over empty space
			blockW = treadXD - grp.getWidth() - (treadYD * (sketchRef.cMessageBorder / 100)) * 1;

			// if there is enough, drop in a block
			if (blockW > (treadYD * (sketchRef.cMessageBorder / 100)) * 2) {
				// generate the block here
				RShape block = genBlock(sketchRef.blockType, blockW, treadYD,
						(treadYD * (sketchRef.cMessageBorder / 100)));
				// if (topBars) block = genBlock(blockType, blockW,
				// treadYD-(treadYD*(cStripThickness/100))*2-(treadYD*(sketchRef.cMessageBorder/100))/2,
				// (treadYD*(sketchRef.cMessageBorder/100)));

				// move the block
				textY = block.getCenter().y;
				translateY = treadYD / 2 - textY;
				block.translate(((treadYD * (sketchRef.cMessageBorder / 100)) * 2) + grp.getWidth(),
						translateY);

				// add it
				grp.addChild(block);
			}
		}

		// resample the text
		for (int i = 1; i <= sketchRef.cResamplePassNum; i++) { // 5
			if (sketchRef.verbose)
				sketchRef.sendMessage("reSample Pass: " + i);
			grp = new RShape(reSampleSVG(grp));
		}
		// Clip sharp corners
		grp = new RShape(clipContours(grp));

		// setup output
		lettersPts = grp.getPoints();
		lettersPaths = grp.getPointsInPaths();

		normalList = new Vec3D[lettersPts.length];

		int count = 0;
		for (RPoint[] r : lettersPaths) { // normalList
			float xDif, yDif;

			for (int i = 0; i < r.length; i++) {
				RPoint x1 = r[i];
				RPoint x2 = r[(i + 1) % r.length];

				xDif = x2.x - x1.x;
				yDif = x2.y - x1.y;

				normalList[count] = new Vec3D(yDif, -xDif, 0);

				count++;
			}
		}

		for (int j = 0; j < lettersPts.length; j++) {
			textPts.add(new Vec3D(lettersPts[j].x, lettersPts[j].y, 0));
			dtAdd.add(new Vec2D(lettersPts[j].x, lettersPts[j].y));
		}

		// return a list of points
		return dtAdd;
	}

	// ===============================================================
	// Build a tire
	// Needs Extensive Commenting
	void buildTire() {
		sketchRef.sendMessage("Tire Generation : Starting");
		buildMesh = new WETriangleMesh();
		faceList = new TriangleMesh();
		lettersBuild = new WETriangleMesh();

		remapedFaceMesh = new WETriangleMesh();

		Vec3D a = new Vec3D();
		Vec3D b = new Vec3D();
		Vec3D c = new Vec3D();
		float dt = PConstants.TWO_PI / res;
		float theta = 0;
		float test, test2;
		float treadR = deftThick;
		Vec3D[] temp = new Vec3D[res];
		int off = 0;

		if (sketchRef.treadDepth > 0) {
			patternThickness = treadR;
			baseTreadThickness = treadR - sketchRef.treadDepth;
		} else {
			patternThickness = treadR + sketchRef.treadDepth;
			baseTreadThickness = treadR;
		}

		temp = sketchRef.makeCirc(baseTreadThickness, res, new Vec3D(centerPts[tireCurves[0]].x,
				centerPts[tireCurves[0]].y, centerPts[tireCurves[0]].z + sketchRef.cMeshChamf));
		toxiTireCurves.add(temp);
		temp = sketchRef.makeCirc(baseTreadThickness - sketchRef.cMeshChamf, res, centerPts[tireCurves[0]]);
		toxiTireCurves.add(temp);

		if (tireCurves[1] == lockCurves[1] && tireCurves[0] == lockCurves[0]) {
			// sketchRef.sendMessage("ack");
			temp =sketchRef.makeCirc(r[tireCurves[0]], res, centerPts[tireCurves[0]]);
			toxiTireCurves.add(temp);
			temp =sketchRef.makeCirc(r[tireCurves[1]], res, centerPts[tireCurves[1]]);
			toxiTireCurves.add(temp);
		} else {
			toxiTireCurves.add(toxiHubCurves.get(0));
			temp =sketchRef.makeCirc(r[0], res, centerPts[lockCurves[0]]);
			toxiTireCurves.add(temp);
			toxiTireCurves.add(toxiHubCurves.get(lockCurves[0]));
			toxiTireCurves.add(toxiHubCurves.get(lockCurves[1]));
			temp =sketchRef.makeCirc(r[0], res, centerPts[lockCurves[1]]);
			toxiTireCurves.add(temp);
			toxiTireCurves.add(toxiHubCurves.get(nCurves - 1));
		}

		temp =sketchRef.makeCirc(baseTreadThickness - sketchRef.cMeshChamf, res, new Vec3D(centerPts[tireCurves[1]].x,
				centerPts[tireCurves[1]].y, centerPts[tireCurves[1]].z));
		toxiTireCurves.add(temp);

		temp =sketchRef.makeCirc(baseTreadThickness, res, new Vec3D(centerPts[tireCurves[1]].x,
				centerPts[tireCurves[1]].y, centerPts[tireCurves[1]].z - sketchRef.cMeshChamf));
		toxiTireCurves.add(temp);

		nTireCurves = toxiTireCurves.size();

		// meshes between each of the curves
		for (int i = 0; i < (nTireCurves); i++) {
			if (i + 1 >= nTireCurves)
				continue;
			Vec3D[] temp1 = toxiTireCurves.get(i);
			Vec3D[] temp2 = toxiTireCurves.get((i + 1));
			for (int j = 0; j < res; j++) {
				buildMesh.addFace(temp2[j], temp1[j], temp1[(j + 1) % temp1.length]);
				buildMesh.addFace(temp2[(j + 1) % temp2.length], temp2[j], temp1[(j + 1)
						% temp1.length]);
			}
		}

		// parse the dt and re map it to the tire
		// keep in mind that right now this ignoes other vertexes
		// and relies on the cleanup code to merge duplicate verticies within
		// a set tollerance
		float n = sketchRef.cMeshChamf * sketchRef.D_SF_2;
		if (mode > 0) {
			goal = voronoiBuild.getTriangles().size();
			prog = 0;
			for (Triangle2D t : voronoiBuild.getTriangles()) {
				prog++;
				t.computeCentroid();
				boolean yes = false;
				// is this one we care about (not connecting to the huge
				// triangle
				if ((Math.abs(t.a.x) != sketchRef.DSIZE && Math.abs(t.a.y) != sketchRef.DSIZE)) {
					for (Triangle2D tt : textTri) {
						tt.computeCentroid();
						if (t.centroid.distanceTo(tt.centroid) <= .5)
							yes = true;
						if (yes)
							break;
					}
					if (yes) {// if a triangle is a text tirangle
						// remap the x,y to r,theta etc
						test = PConstants.TWO_PI - PApplet.map(t.a.x, 0, treadXD, 0, PConstants.TWO_PI);
						test2 = ((treadY - PApplet.map(t.a.y, 0, treadY * sketchRef.D_SF, 0, treadY)) * sketchRef.D_SF_2)
								+ (centerPts[tireCurves[0]].z * sketchRef.D_SF_2);
						a = new Vec3D((patternThickness) *((float) Math.cos(test)) * sketchRef.D_SF_2,
								(patternThickness) *((float) Math.sin(test)) * sketchRef.D_SF_2, test2 + n);

						test = PConstants.TWO_PI - PApplet.map(t.b.x, 0, treadXD, 0, PConstants.TWO_PI);
						test2 = ((treadY - PApplet.map(t.b.y, 0, treadY * sketchRef.D_SF, 0, treadY)) * sketchRef.D_SF_2)
								+ (centerPts[tireCurves[0]].z * sketchRef.D_SF_2);
						b = new Vec3D((patternThickness) * (float) Math.cos(test) * sketchRef.D_SF_2,
								(patternThickness) * (float) Math.sin(test) * sketchRef.D_SF_2, test2 + n);

						test = PConstants.TWO_PI - PApplet.map(t.c.x, 0, treadXD, 0, PConstants.TWO_PI);
						test2 = ((treadY - PApplet.map(t.c.y, 0, treadY * sketchRef.D_SF, 0, treadY)) * sketchRef.D_SF_2)
								+ (centerPts[tireCurves[0]].z * sketchRef.D_SF_2);
						c = new Vec3D((patternThickness) * (float) Math.cos(test) * sketchRef.D_SF_2,
								(patternThickness) * (float) Math.sin(test) * sketchRef.D_SF_2, test2 + n);

						// add meshes
						lettersBuild.addFace(c, b, a);
						remapedFaceMesh.addFace(c, b, a);
						prog = remapedFaceMesh.getNumFaces();
						yes = false;
					} else {
						// remap the x,y to r,theta etc
						test = PConstants.TWO_PI - PApplet.map(t.a.x, 0, treadXD, 0, PConstants.TWO_PI);
						test2 = ((treadY - PApplet.map(t.a.y, 0, treadY * sketchRef.D_SF, 0, treadY)) * sketchRef.D_SF_2)
								+ (centerPts[tireCurves[0]].z * sketchRef.D_SF_2);
						a = new Vec3D(baseTreadThickness * (float) Math.cos(test) * sketchRef.D_SF_2,
								baseTreadThickness * (float) Math.sin(test) * sketchRef.D_SF_2, test2 + n);

						test = PConstants.TWO_PI - PApplet.map(t.b.x, 0, treadXD, 0, PConstants.TWO_PI);
						test2 = ((treadY - PApplet.map(t.b.y, 0, treadY * sketchRef.D_SF, 0, treadY)) * sketchRef.D_SF_2)
								+ (centerPts[tireCurves[0]].z * sketchRef.D_SF_2);
						b = new Vec3D(baseTreadThickness * (float) Math.cos(test) * sketchRef.D_SF_2,
								baseTreadThickness * (float) Math.sin(test) * sketchRef.D_SF_2, test2 + n);

						test = PConstants.TWO_PI - PApplet.map(t.c.x, 0, treadXD, 0, PConstants.TWO_PI);
						test2 = ((treadY - PApplet.map(t.c.y, 0, treadY * sketchRef.D_SF, 0, treadY)) * sketchRef.D_SF_2)
								+ (centerPts[tireCurves[0]].z * sketchRef.D_SF_2);
						c = new Vec3D(baseTreadThickness * (float) Math.cos(test) * sketchRef.D_SF_2,
								baseTreadThickness * (float) Math.sin(test) * sketchRef.D_SF_2, test2 + n);

						// add meshes

						remapedFaceMesh.addFace(a, b, c);

						prog = remapedFaceMesh.getNumFaces();
					}
				}
			}

			if (hasText || hasSVG) {
				int pathC = 0;
				for (RPoint[] pp : lettersPaths) {
					pathC++;
					Vec3D[] temp1 = new Vec3D[pp.length];
					Vec3D[] temp2 = new Vec3D[pp.length];
					for (int i = 0; i < pp.length; i++) {
						if (hasText)
							temp1[i] = (new Vec3D(pp[i].x, pp[i].y, 0));
						if (hasText)
							temp2[i] = (new Vec3D(pp[i].x, pp[i].y, 0));

						if (hasSVG)
							temp1[i] = (new Vec3D(pp[i].x, pp[i].y, 0));
						if (hasSVG)
							temp2[i] = (new Vec3D(pp[i].x, pp[i].y, 0));
					}
					ptsLow.add(temp1);
					ptsHigh.add(temp2);
				}

				for (Vec3D[] vv : ptsLow) {
					for (int i = 0; i < vv.length; i++) {
						// remap low
						test = PConstants.TWO_PI - PApplet.map(vv[i].x, 0, treadXD, 0, PConstants.TWO_PI);
						test2 = (treadY - PApplet.map(vv[i].y, 0, treadY * sketchRef.D_SF, 0, treadY))
								* sketchRef.D_SF_2 + (centerPts[tireCurves[0]].z * sketchRef.D_SF_2);
						vv[i] = new Vec3D(baseTreadThickness * (float) Math.cos(test) * sketchRef.D_SF_2,
								baseTreadThickness * (float) Math.sin(test) * sketchRef.D_SF_2, test2 + n);
					}
				}
				for (Vec3D[] vv : ptsHigh) {
					for (int i = 0; i < vv.length; i++) {
						// remap high
						test = PConstants.TWO_PI - PApplet.map(vv[i].x, 0, treadXD, 0, PConstants.TWO_PI);
						test2 = (treadY - PApplet.map(vv[i].y, 0, treadY * sketchRef.D_SF, 0, treadY))
								* sketchRef.D_SF_2 + (centerPts[tireCurves[0]].z * sketchRef.D_SF_2);
						vv[i] = new Vec3D((patternThickness) * (float) Math.cos(test) * sketchRef.D_SF_2,
								(patternThickness) * (float) Math.sin(test) * sketchRef.D_SF_2, test2 + n);
					}
				}

				int count = 0;
				// attempt to mesh between the two
				for (int i = 0; i < (pathC); i++) {
					Vec3D[] temp1 = ptsLow.get(i);
					Vec3D[] temp2 = ptsHigh.get(i);
					for (int j = 0; j < temp1.length; j++) {

						Face fa = new Face(new Vertex(temp1[j], 0), new Vertex(temp2[j], 1),
								new Vertex(temp1[(j + 1) % temp1.length], 2));
						fa.computeNormal();

						Vec3D va = temp1[j];
						Vec3D vc = temp1[(j + 1) % temp1.length];
						Vec2D dif = new Vec2D(vc.y - va.y, -(vc.x - va.x));
						Vec2D fNorm = new Vec2D(fa.normal.x, fa.normal.y);

						if (Math.abs(dif.angleBetween(fNorm)) < PConstants.PI / 6) {
							buildMesh.addFace(fa.c, fa.b, fa.a);
							sketchRef.sendMessage("Flipped");
						} else {
							buildMesh.addFace(fa.a, fa.b, fa.c);
						}

						fa = new Face(new Vertex(temp2[j], 0), new Vertex(temp2[(j + 1)
								% temp2.length], 1), new Vertex(temp1[(j + 1) % temp1.length], 2));
						fa.computeNormal();

						va = temp2[j];
						vc = temp2[(j + 1) % temp2.length];
						dif = new Vec2D(vc.y - va.y, -(vc.x - va.x));
						fNorm = new Vec2D(fa.normal.x, fa.normal.y);

						if (Math.abs(dif.angleBetween(fNorm)) < PConstants.PI / 6) {
							buildMesh.addFace(fa.c, fa.b, fa.a);
							sketchRef.sendMessage("Flipped");
						} else {
							buildMesh.addFace(fa.a, fa.b, fa.c);
						}
						count++;
					}
				}
			}
		} else {
			Vec3D[] temp1 = toxiTireCurves.get(0);
			Vec3D[] temp2 = toxiTireCurves.get(toxiTireCurves.size() - 1);
			for (int j = 0; j < res; j++) {
				buildMesh.addFace(temp1[j], temp2[j], temp1[(j + 1) % temp1.length]);
				buildMesh.addFace(temp2[j], temp2[(j + 1) % temp2.length], temp1[(j + 1)
						% temp1.length]);
				prog = buildMesh.getNumFaces();
			}
		}
	}

	void buildStamp() {
		sketchRef.sendMessage("Stamp Generation : Starting");
		buildMesh = new WETriangleMesh();
		faceList = new TriangleMesh();
		lettersBuild = new WETriangleMesh();
		remapedFaceMesh = new WETriangleMesh();

		Vec3D a = new Vec3D();
		Vec3D b = new Vec3D();
		Vec3D c = new Vec3D();
		float dt = PConstants.TWO_PI / res;
		float theta = 0;
		float test, test2;
		float n = sketchRef.cMeshChamf * sketchRef.D_SF;

		if (sketchRef.treadDepth > 0) {
			patternThickness = stampThick;
			baseTreadThickness = stampThick - sketchRef.treadDepth * sketchRef.D_SF;
		} else {
			patternThickness = stampThick + sketchRef.treadDepth * sketchRef.D_SF;
			baseTreadThickness = stampThick;
		}

		Vec3D[] temp = new Vec3D[res];

		// Generate Base Stamp Shape
		float stepX = treadX / res;
		float stepY = treadY / res;
		float stepXc = treadXD / res;
		float stepYc = treadYD / res;

		List<Vec3D> lowerCurve = new Vector<Vec3D>();
		List<Vec3D> upperCurve = new Vector<Vec3D>();
		List<Vec3D> upperCurveChamf = new Vector<Vec3D>();

		for (int i = 0; i < res; i++) { // steps points across the top and
										// bottom
			lowerCurve.add(new Vec3D((i * stepX) - n, -n, 0));
			upperCurve.add(new Vec3D((i * stepX) - n, -n, baseTreadThickness - n));
			upperCurveChamf.add(new Vec3D((i * stepXc - n), -n, baseTreadThickness));
		}
		for (int i = 0; i < res; i++) { // steps points down the left and right
			lowerCurve.add(new Vec3D(treadX - n, i * stepY - n, 0));
			upperCurve.add(new Vec3D(treadX - n, i * stepY - n, baseTreadThickness - n));
			upperCurveChamf.add(new Vec3D(treadXD - n, i * stepYc - n, baseTreadThickness));
		}

		for (int i = res; i > 0; i--) {
			lowerCurve.add(new Vec3D((i * stepX) - n, treadY - n, 0));
			upperCurve.add(new Vec3D((i * stepX) - n, treadY - n, baseTreadThickness - n));
			upperCurveChamf.add(new Vec3D((i * stepXc) - n, treadYD - n, baseTreadThickness));
		}

		for (int i = res; i > 0; i--) {
			lowerCurve.add(new Vec3D(-n, i * stepY - n, 0));
			upperCurve.add(new Vec3D(-n, i * stepY - n, baseTreadThickness - n));
			upperCurveChamf.add(new Vec3D(-n, i * stepYc - n, baseTreadThickness));
		}

		for (int i = 0; i < upperCurveChamf.size(); i++) {
			Vec3D v = upperCurveChamf.get(i);
			upperCurveChamf.set(i, v.add(n, n, 0));
		}

		for (int j = 0; j < upperCurve.size(); j++) {
			remapedFaceMesh.addFace(lowerCurve.get(j), upperCurve.get(j),
					lowerCurve.get((j + 1) % lowerCurve.size()));
			remapedFaceMesh.addFace(upperCurve.get(j), upperCurve.get((j + 1) % upperCurve.size()),
					lowerCurve.get((j + 1) % lowerCurve.size()));
		}

		for (int j = 0; j < upperCurve.size(); j++) {
			remapedFaceMesh.addFace(upperCurve.get(j), upperCurveChamf.get(j),
					upperCurve.get((j + 1) % upperCurve.size()));
			remapedFaceMesh.addFace(upperCurveChamf.get(j),
					upperCurveChamf.get((j + 1) % upperCurveChamf.size()),
					upperCurve.get((j + 1) % upperCurve.size()));
		}

		Voronoi vStamp;
		vStamp = new Voronoi(sketchRef.DSIZE);

		for (Vec3D v : upperCurve) {

			try {
				vStamp.addPoint(new Vec2D(v.x, v.y));
			} catch (NoSuchElementException exception) {
				sketchRef.sendMessage("Dup Vertex");
			}
		}

		for (int i = 1; i < res / 2; i += 2) {
			for (int j = 1; j < res / 2; j += 2) {
				try {
					vStamp.addPoint(new Vec2D(j * stepX * 2, i * stepY * 2));
				} catch (NoSuchElementException exception) {
					sketchRef.sendMessage("Dup Vertex");
				}
			}
		}

		for (Triangle2D t : vStamp.getTriangles()) {
			// is this one we care about (not connecting to the huge triangle
			if ((Math.abs(t.a.x) != sketchRef.DSIZE && Math.abs(t.a.y) != sketchRef.DSIZE)) {
				Vec2D ta = t.a;
				Vec2D tb = t.b;
				Vec2D tc = t.c;
				a = new Vec3D(ta.x, ta.y, 0);
				b = new Vec3D(tb.x, tb.y, 0);
				c = new Vec3D(tc.x, tc.y, 0);
				remapedFaceMesh.addFace(c, b, a);
			}
		}

		float tempThick = 0;
		if (mode > 0) {
			goal = voronoiBuild.getTriangles().size();
			prog = 0;
			for (Triangle2D t : voronoiBuild.getTriangles()) {
				prog++;
				t.computeCentroid();
				boolean yes = false;
				// is this one we care about (not connecting to the huge
				// triangle
				if ((Math.abs(t.a.x) != sketchRef.DSIZE && Math.abs(t.a.y) != sketchRef.DSIZE)) {
					Vec2D ta = t.a;
					Vec2D tb = t.b;
					Vec2D tc = t.c;
					for (Triangle2D tt : textTri) {
						tt.computeCentroid();
						if (t.centroid.distanceTo(tt.centroid) <= .5)
							yes = true;
						if (yes)
							break;
					}
					if (yes)
						tempThick = patternThickness;
					if (!yes)
						tempThick = baseTreadThickness;

					a = new Vec3D(ta.x, ta.y, tempThick);
					b = new Vec3D(tb.x, tb.y, tempThick);
					c = new Vec3D(tc.x, tc.y, tempThick);

					// add meshes
					if (yes)
						remapedFaceMesh.addFace(c, b, a);
					remapedFaceMesh.addFace(c, b, a);
					prog = buildMesh.getNumFaces();
					yes = false;
				}
			}

			if (hasText || hasSVG) {
				int pathC = 0;
				for (RPoint[] pp : lettersPaths) {
					pathC++;
					Vec3D[] temp1 = new Vec3D[pp.length];
					Vec3D[] temp2 = new Vec3D[pp.length];
					for (int i = 0; i < pp.length; i++) {
						if (hasText)
							temp1[i] = (new Vec3D(pp[i].x, pp[i].y, baseTreadThickness));
						if (hasText)
							temp2[i] = (new Vec3D(pp[i].x, pp[i].y, patternThickness));

						if (hasSVG)
							temp1[i] = (new Vec3D(pp[i].x, pp[i].y, baseTreadThickness));
						if (hasSVG)
							temp2[i] = (new Vec3D(pp[i].x, pp[i].y, patternThickness));
					}
					ptsLow.add(temp1);
					ptsHigh.add(temp2);
				}

				for (int i = 0; i < (pathC); i++) {
					Vec3D[] temp1 = ptsLow.get(i);
					Vec3D[] temp2 = ptsHigh.get(i);
					for (int j = 0; j < temp1.length; j++) {
						buildMesh.addFace(temp1[j], temp2[j], temp1[(j + 1) % temp1.length]);
						buildMesh.addFace(temp2[j], temp2[(j + 1) % temp2.length], temp1[(j + 1)
								% temp1.length]);
						prog = buildMesh.getNumFaces();
					}
				}
			}
		} else {
			vStamp = new Voronoi(sketchRef.DSIZE);

			for (Vec3D v : upperCurveChamf) {
				try {
					vStamp.addPoint(new Vec2D(v.x, v.y));
				} catch (NoSuchElementException exception) {
					sketchRef.sendMessage("Dup Vertex");
				}
			}

			for (int i = 1; i < res / 2; i += 2) {
				for (int j = 1; j < res / 2; j += 2) {
					try {
						vStamp.addPoint(new Vec2D(j * stepX * 2, i * stepY * 2));
					} catch (NoSuchElementException exception) {
						sketchRef.sendMessage("Dup Vertex");
					}
				}
			}

			for (Triangle2D t : vStamp.getTriangles()) {
				// is this one we care about (not connecting to the huge
				// triangle
				if ((Math.abs(t.a.x) != sketchRef.DSIZE && Math.abs(t.a.y) != sketchRef.DSIZE)) {
					Vec2D ta = t.a;
					Vec2D tb = t.b;
					Vec2D tc = t.c;
					a = new Vec3D(ta.x, ta.y, baseTreadThickness);
					b = new Vec3D(tb.x, tb.y, baseTreadThickness);
					c = new Vec3D(tc.x, tc.y, baseTreadThickness);
					remapedFaceMesh.addFace(c, b, a);
				}
			}
		}
	}

	void closeMesh() {
		sketchRef.sendMessage("Cleaning Mesh: Starting");
		float a;
		float b;
		float c;
		float p;
		float area;
		List<Face> meshF = new Vector<Face>(buildMesh.getFaces());
		List<Vertex> meshV = new Vector<Vertex>(buildMesh.getVertices());
		int count = 0;
		double eps = .00125; // epsilon

		// go through each mesh face, then through each vertex, if one matches
		// another
		// merge the two, this closes the mesh, and removes math errors from
		// the x,y to r,theta translation

		meshF = new Vector<Face>(remapedFaceMesh.getFaces());
		meshV = new Vector<Vertex>(remapedFaceMesh.getVertices());

		goal = meshV.size();

		// go through each mesh face, then through each vertex, if one matches
		// another
		// merge the two, this closes the mesh, and removes (minor, 10^-5) math
		// errors from
		// the x,y to r,theta translation
		for (Face f : meshF) {
			prog++;
			for (Vertex v : meshV) {
				if (f.a.distanceTo(v) <= eps) {
					f.a = v;
					count++;
				} else if (f.b.distanceTo(v) <= eps) {
					f.b = v;
					count++;
				} else if (f.c.distanceTo(v) <= eps) {
					f.c = v;
					count++;
				}
			}
		}

		remapedFaceMesh.faceOutwards();
		buildMesh.addMesh(remapedFaceMesh);

		meshF = new Vector<Face>(buildMesh.getFaces());
		meshV = new Vector<Vertex>(buildMesh.getVertices());

		goal = meshV.size();

		// go through each mesh face, then through each vertex, if one matches
		// another
		// merge the two, this closes the mesh, and removes (minor, 10^-5) math
		// errors from
		// the x,y to r,theta translation
		for (Face f : meshF) {
			prog++;
			for (Vertex v : meshV) {
				if (f.a.distanceTo(v) <= eps) {
					f.a = v;
					count++;
				} else if (f.b.distanceTo(v) <= eps) {
					f.b = v;
					count++;
				} else if (f.c.distanceTo(v) <= eps) {
					f.c = v;
					count++;
				}
			}
		}
		sketchRef.sendMessage("Cleaning Mesh: Done");
	}

	// ===============================================================
	// series of fuctions to acess stuff insude the thread safely
	boolean isBuilding() {
		return this.building;
	}

	// ===============================================================
	int getStatus() {
		return stat;
	}

	// ===============================================================
	TriangleMesh getHub() {
		return this.hubBuild;
	}

	// ===============================================================
	Voronoi getV() {
		return this.voronoiBuild;
	}

	// ===============================================================
	WETriangleMesh getTire() {
		return this.buildMesh;
	}

	// ===============================================================
	WETriangleMesh getLetters() {
		return this.lettersBuild;
	}

	// ===============================================================
	int getGoal() {
		return goal;
	}

	// ===============================================================
	int getProg() {
		return prog;
	}

	// ===============================================================
	int getMode() {
		return mode;
	}

	// ===============================================================
	float getTreadX() {
		return treadXD;
	}

	// ===============================================================
	float getTreadY() {
		return treadYD;
	}

	// ===============================================================
	public List<Triangle2D> gettextTri() {
		return textTri;
	}

	// ===============================================================
	PImage getPtsImg() {
		return ptsImg;
	}

	// ===============================================================
	void progHandle(int step, int _goal) {
		sketchRef.appStatus.setText("Step " + step + " of 13");
		prog = 0;
		goal = _goal;
	}

	void progHandle(int step) {
		sketchRef.appStatus.setText("Step " + step + " of 13");
		prog = 0;
		goal = 0;
	}
}
