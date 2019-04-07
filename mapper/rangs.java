/**********************************
** Java RANGS map drawing tool
** David Mikesell
** University of Virginia
** 6/27/2000
**
** Originally ported from Rainer Feistel's 
** Visual Basic demo found on his web page, and
** expanded beyond recognizability.
** See Baltic Sea Research Institute
** at www.io-warnemuende.de/public/phy/rfeistel/index.htm
** for info on RANGS and GSHHS data, and how to use them.
**
*/

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.event.*;
import java.lang.reflect.*;
import java.util.*;
import java.awt.image.*;
import java.awt.print.*;

public class rangs extends JPanel implements Printable {
  // This class implements the map.

  // default coordinates
  private float lon1 = 348, lat1 = 60, lon2 = 5, lat2 = 48; // GBR & IRL
  private int resolution = 3; // map resolution 0 = highest, 4 = lowest
  // don't touch these
  private boolean firstFill = true, firstShore = true;

  private int flags; // for level of detail

  private RandomAccessFile hCEL, hCAT, hRIM;  // files that hold the map data

  // internal drawing variables
  private Color clr; // the current drawing color
  private int fillPixels = 0, shorePixels = 0;  // indexes into point arrays
  private int opcode; 
  private Point fillPixel[] = new Point[16384], shorePixel[] = new Point[16384];
  private double x0Fill = 0, y0Fill = 0, x0Shore = 0, y0Shore = 0; 
  private boolean fillmap = true;
  private Color mapColor[] = new Color[8];

  // the parent program frame
  private rangsFrame parent;
  
  // used for click & drag rectangle handling
  private Rectangle currentRect, rectToDraw, previousRectDrawn = new Rectangle();

  // file caches for speed.
  private Cache catCache = new Cache(32768);
  // celCache holds Integer objects
  private Cache celCache = new Cache(65536);
  // celCache2 holds Point objects
  private Cache celCache2 = new Cache(65536);
  private Cache rimCache = new Cache(32768);
  // virtual file pointers to keep cache accesses and file accesses in synch.
  private long celPos = 0, catPos = 0, rimPos = 0;
  private boolean useCatCache = true, useCelCache = true, useRimCache = true;


  public rangs(rangsFrame pApp)
  {
    /* Default constructor.
    ** Generates a map of GBR and IRL.
    */
    super();
    parent = pApp;
    // lazy way to do this, vector would be nicer.
    for ( int i = 0; i < 16384; i++) {
      fillPixel[i] = new Point();
      shorePixel[i] = new Point();
    }
    setPreferredSize(new Dimension(800, 300) );

    if (openRANGSFiles() == 0)
      {  
	System.err.println("Could not open RANGS Files.  Aborting.\n");
	System.exit(1);
      } 
    MyListener myListener = new MyListener();
    addMouseListener(myListener);
    addMouseMotionListener(myListener);
    setDetail(3);  // max detail, fill
    initColors();
    parent.updateLatLon(lat1, lon1, lat2, lon2);
  } // c'tor

  public rangs(int ln1, int lt1, int ln2, int lt2, rangsFrame pApp)
  {
    // Constructor, with longitude and latitude specified.
    this(pApp);

    lon1 = ln1;
    lon2 = ln2;
    lat1 = lt1;
    lat2 = lt2;
    parent.updateLatLon(lat1, lon1, lat2, lon2);
  } // c'tor


  public int print(Graphics g, PageFormat pageFormat, int pageIndex) {
    if (pageIndex > 0) {
      return(NO_SUCH_PAGE);
    } else {
      Graphics2D g2d = (Graphics2D)g;
      double scaleFactor = Math.max((double)(getSize().width)/(double)(pageFormat.getImageableWidth()),(double)(getSize().height)/(double)(pageFormat.getImageableHeight()));
      g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
      g2d.scale(1.0/scaleFactor,1.0/scaleFactor);
      disableDoubleBuffering(this);
      paint(g2d);
      enableDoubleBuffering(this);
      return(PAGE_EXISTS);
    }
  }

  public static void disableDoubleBuffering(Component c) {
    RepaintManager currentManager = RepaintManager.currentManager(c);
    currentManager.setDoubleBufferingEnabled(false);
  }

  public static void enableDoubleBuffering(Component c) {
    RepaintManager currentManager = RepaintManager.currentManager(c);
    currentManager.setDoubleBufferingEnabled(true);
  }

  private void initColors() {
    // initialize default colors.
    mapColor[0] = Color.blue;   // ocean
    mapColor[1] = Color.green;  // land
    mapColor[2] = Color.cyan;   // lakes
    mapColor[3] = Color.green;  // islands in lakes
    mapColor[4] = Color.cyan;   // ponds on islands
    mapColor[5] = Color.black;  // outlines
    mapColor[6] = Color.yellow; // error
    mapColor[7] = Color.white;  // unknown
  } // initColors


  class MyListener extends MouseInputAdapter {
  // Handler for all mouse activities on the map.
    private boolean clicking = true;

    public void mouseClicked(MouseEvent e) {
      // centers map on point clicked
      clicking = true;
      float width, height, x, y;
      width = Math.abs(lon2 - lon1);  // keep same aspect ratio
      height = Math.abs(lat1 - lat2);
      x = lon1 + e.getX()*(width/getWidth());
      y = lat1 - e.getY()*(height/getHeight());
      lon1 = x - width/2;
      lon2 = x + width/2;
      lat1 = y + height/2;
      lat2 = y - height/2;
      if (lat1 > 89) lat1 = 89;
      if (lat2 < -89) lat2 = -89;
      parent.updateStatus(" Centering on " + parent.printLat((double)y) + ":" + parent.printLon((double)x) + ".");
      parent.updateLatLon(lat1, lon1, lat2, lon2);
      repaint();
    }
    
    public void mousePressed(MouseEvent e) {
      // start of selecting a region
      clicking = true;
      int x = e.getX();
      int y = e.getY();
      currentRect = new Rectangle(x, y, 0, 0);
      updateDrawableRect(getWidth(), getHeight());
    }

    public void mouseDragged(MouseEvent e) {
      // update the dragging rectangle
      clicking = false;
      updateSize(e);
    }

    public void mouseReleased(MouseEvent e) {
      // zoom to selected region.
      if (!clicking) {
	parent.updateStatus(" Zooming to selected rectangle."); 
	updateSize(e);
	double x = currentRect.getX();
	double y = currentRect.getY();
	float ln1, lt1, ln2, lt2;

	ln1 = (float)((x/getWidth())*(Math.abs(lon2-lon1))+lon1);
	lt1 = (float)(lat1-(y/getHeight())*(Math.abs(lat1-lat2)));
	ln2 = (float)((x+currentRect.getWidth())/getWidth()*(Math.abs(lon2-lon1))+lon1);
	lt2 = (float)(lat1-(y+currentRect.getHeight())/getHeight()*(Math.abs(lat1-lat2)));
	lon1 = ln1;
	lon2 = ln2;
	lat1 = lt1;
	lat2 = lt2;
	parent.updateLatLon(lat1, lon1, lat2, lon2);
        currentRect = null;
	repaint();
      }
    }

    void updateSize(MouseEvent e) {
      // does the update of the dragging rectangle
      int x = e.getX();
      int y = e.getY();
      int yStart = (int)currentRect.getY();
	    
      if (y-yStart > 0)
	currentRect.setSize(x - currentRect.x,
                       (int)((x-currentRect.x)*getHeight()/getWidth()));
      else
        currentRect.setSize(x-currentRect.x,
		       (int)((currentRect.x - x)*getHeight()/getWidth()));
      updateDrawableRect(getWidth(), getHeight());
      Rectangle totalRepaint = rectToDraw.union(previousRectDrawn);
      repaint(totalRepaint.x, totalRepaint.y,
	      totalRepaint.width, totalRepaint.height);
     }
  }  // end inner class MyListener

  void updateDrawableRect(int compWidth, int compHeight) {
    // makes sure the dragging rectangle is well behaved
    int x = currentRect.x;
    int y = currentRect.y;
    int width = currentRect.width;
    int height = currentRect.height;

    //Make the width and height positive, if necessary.
    if (width < 0) {
      width = 0 - width;
      x = x - width + 1; 
      if (x < 0) {
	width += x; 
        x = 0;
      }
    }
    if (height < 0) {
      height = 0 - height;
      y = y - height + 1; 
      if (y < 0) {
        height += y; 
        y = 0;
      }
    } 

    //The rectangle shouldn't extend past the drawing area.
    if ((x + width) > compWidth) {
      width = compWidth - x;
    }
    if ((y + height) > compHeight) {
      height = compHeight - y;
    }
      
    //Update rectToDraw after saving old value.
    if (rectToDraw != null) {
      previousRectDrawn.setBounds(
                  rectToDraw.x, rectToDraw.y, 
                  rectToDraw.width, rectToDraw.height);
      rectToDraw.setBounds(x, y, width, height);
    } 
    else {
      rectToDraw = new Rectangle(x, y, width, height);
    }
  }  // updateDrawableRect


  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    // paint the map
    drawRANGS(g);
    // if we're dragging a rectangle, paint it too.
    if (currentRect != null) {
      g.setColor(Color.white);
      g.drawRect(rectToDraw.x, rectToDraw.y, rectToDraw.width-1, rectToDraw.height-1);
    }

    parent.updateStatus(" Click on the map to center at that point.");
  }  // paintComponent


  public synchronized int drawRANGS(Graphics g)
  {
    /* draws the map defined by the box having
    ** coordinates lat1/lon1, lat2/lon2
    */
    int i, j;

    if (lon1 > lon2) lon1 -= 360;
    if (Math.abs(lat1-lat2) < 0.001) lat2 = lat1 - (float)0.001;
    if (Math.abs(lon1-lon2) < 0.001) lon2 = lon1 + (float)0.001;
     
    for (i = (int)Math.ceil((double)lat1); i >= (int)Math.floor((double)lat2); i--) {
      for (j = (int)Math.floor((double)lon1); j <= (int)Math.ceil((double)lon2); j++) {
	drawRANGSCell(i, j, g);
      }
    }
    return(-1);
  }  // drawRANGS

  /****************************
  ** Scroll Routines.  Pretty self-explanatory.
  **
  ** scrolls 1/4 of the width or height of the map 
  ** per click.
  */

  public void scrollUp() 
  {
    if (lat1 < 88) {
      lat1 += (lat1-lat2)/4;
      lat2 += (lat1-lat2)/4;
      parent.updateLatLon(lat1, lon1, lat2, lon2);
      repaint();
    }
  }  //scrollUp

  public void scrollDown()
  {
    if (lat2 > -88) {
      lat1 -= (lat1-lat2)/4;
      lat2 -= (lat1-lat2)/4;
      parent.updateLatLon(lat1, lon1, lat2, lon2);
      repaint();
    }
  }  // scrollDown

  public void scrollRight()
  {
    lon1 += (lon2-lon1)/4;
    lon2 += (lon2-lon1)/4;
    if (lon1 >= 360) lon1 -= 360;
    if (lon2 >= 360) lon2 -= 360;
    parent.updateLatLon(lat1, lon1, lat2, lon2);
    repaint();
  }  //scrollRight

  public void scrollLeft()
  {
    lon1 -= (lon2-lon1)/4;
    lon2 -= (lon2-lon1)/4;
    if (lon1 < 0 ) lon1 += 360;
    if (lon2 < 0 ) lon2 += 360;
    parent.updateLatLon(lat1, lon1, lat2, lon2);
    repaint();
  } //scrollLeft

  /********************************
  ** End of Scroll Routines
  ********************************/

  /********************************
  ** Zoom and Stretch routines
  ********************************/
  public void zoomIn()
  {
    // Zoom in 10%
    double dx, dy;
    dy = Math.abs((lat1 - lat2)/10.0);
    dx = Math.abs((lon1 - lon2)/10.0);

    if ((dy > 0.002) && (dx > 0.002)) {
      lat1 -= dy;
      lat2 += dy;
      lon1 += dx;
      lon2 -= dx;
      parent.updateLatLon(lat1, lon1, lat2, lon2);
      repaint();
    }
  }  // zoomIn

  public void zoomOut()
  {
    // Zoom out 10%
    double dx, dy;
    dy = Math.abs((lat1 - lat2)/10.0);
    dx = Math.abs((lon1 - lon2)/10.0);
    lat1 += dy;
    lat2 -= dy;
    lon1 -= dx;
    lon2 += dx;
    if (lat1 > 89) lat1 = 89;
    if (lat2 < -90) lat2 = -90;
    parent.updateLatLon(lat1, lon1, lat2, lon2);
    repaint();
  }  // zoomOut

  public void stretchLong() {
    // stretch longitudinally by 10%
    double dx = Math.abs((lon1-lon2)/10.0);
    lon1 += dx;
    lon2 -= dx;
    parent.updateLatLon(lat1, lon1, lat2, lon2);
    repaint();
  }

  public void stretchLat() {
    // stretch latitudinally by 10%
    double dy = Math.abs((lat1-lat2)/10.0);
    lat1 -= dy;
    lat2 += dy;
    parent.updateLatLon(lat1, lon1, lat2, lon2);
    repaint();
  }

  /********************************
  ** End of Zoom routines
  ********************************/

  public void setResolution(int i)
  {
    // change the map resolution to value i.
    // 0 = finest, 4 = coarsest.
    if (i != resolution) {
      resolution = i;
      if (useCelCache) celCache.clear();
      if (useCelCache) celCache2.clear();
      if (useCatCache) catCache.clear();
      if (useRimCache) rimCache.clear();
      closeRANGSFiles();
      openRANGSFiles();
      repaint();
    }
  }  // setResolution

  public void setDetail(int i)
  {
    // change the map level of detail.
    // i == 0: coastlines only
    // i == 1: add lakes
    // i == 2: add islands in lakes
    // i == 3: add ponds in islands
    // i == -1: fill in map
    // i == -2: outline map

    if (i == -1) fillmap = true;
    if (i == -2) fillmap = false;
    switch (i) {
    case 3:
      flags = 256 + 512 + 1024 + 2048 + 4096;
      break;
    case 2:
      flags = 256 + 512 + 1024 + 2048;
      break;
    case 1:
      flags = 256 + 512 + 1024;
      break;
    case 0:
      flags = 256 + 512;
      break;
    case -1:
      flags = (flags | 1) << 8;
    }
    if (!fillmap) { 
      flags = flags >> 8;
      flags = flags & 30;
    }
    repaint();
  } // setDetail

  /*********************************
  ** Low level mapping routines
  *********************************/

  private void drawRANGSCell(int i, int j, Graphics g)
  {
    /* draws a map cell given by latitude i, 
    ** longitude j.
    */
    int ix, iy, xOffset;
    byte mybyte=0, addrByte[];
    int addr=0, count=0;
    Object o;

    addrByte = new byte[4];

    ix = ((j % 360) + 360) % 360;
    xOffset = j - ix ;
    iy = i;
    catPos = 4*((89-iy)*360+ix);

    // first search cache.
    if (useCatCache && ((catCache.get(new Long(catPos)) != null))) {
      // found!
      o = catCache.get(new Long(catPos));
      celPos = ((Long)o).longValue();
      catPos += 4;
    }
    else {
      // not found or not using cache, read from file.
      try {
	// read from cat file and get the address.
      	hCAT.seek(catPos);
	hCAT.readFully(addrByte);
	celPos = (long)RANGSBytesToInt(addrByte) - 1;
	catCache.put(new Long(catPos), new Long(celPos)); 
	catPos += 4;
      } catch(IOException e) {
	JOptionPane.showMessageDialog( this, "Error reading record in cat file", "Error", JOptionPane.ERROR_MESSAGE);
	System.exit (-1);
      }
    }

    count = 0;
    // read from cel file and get the opcode.
    // first search cache.
    if (useCelCache && ((celCache.get(new Long(celPos)) != null))) {
      // found!
      o = celCache.get(new Long(celPos));
      opcode = ((Integer)o).intValue();
      celPos++;
    }
    else {
      // not found or not using cache, read from file.
      try {
	hCEL.seek(celPos);
	mybyte = hCEL.readByte();
	opcode = mybyte &0xff;
	celCache.put(new Long(celPos), new Integer(opcode)); 
	celPos++;
     } catch (IOException e) {
	JOptionPane.showMessageDialog( this, "Error reading opcode in cel file", "Error", JOptionPane.ERROR_MESSAGE);
	System.exit (-1);
      }
    }

    if (opcode != 0) drawRANGSPolygonC(0, xOffset, g);
  }  // drawRANGSCell


  private void drawRANGSPolygonC(int lvl, int xOffset, Graphics g)
  {
    // retrieve and draw a polygon from the RANGS data.
    firstShore = true;
    firstFill = true;
    byte addrByte[] = new byte[4];
    int polyID = 0, fill = 0, shore = 0, ok = 1, nPoints = 0, flg = 0, i=0;
    int piece = 0; // actually unsigned bytes, so do & 0xff
    byte mybyte = 0;
    Object o;
    
    // get the Polygon ID
    if (useCelCache && ((celCache.get(new Long(celPos)) != null))) {
      // found!
      o = celCache.get(new Long(celPos));
      polyID = ((Integer)o).intValue();
      celPos += 4;
    }
    else {
      try {
	// read from cel file and get the address.
	hCEL.seek(celPos);
	hCEL.readFully(addrByte);
	polyID = RANGSBytesToInt(addrByte);
	celCache.put(new Long(celPos), new Integer(polyID));
	celPos += 4;
      } catch(IOException e) {
	JOptionPane.showMessageDialog( this, "Error reading record in cel file", "Error", JOptionPane.ERROR_MESSAGE);
	System.exit (-1);
      }
    }

    // now get the piece
    while (ok == 1) {
      if (useCelCache && ((celCache.get(new Long(celPos)) != null))) {
	// found!
	o = celCache.get(new Long(celPos));
	piece = ((Integer)o).intValue() & 0xff;
	celPos++;
      }
      else {
	try {
	  hCEL.seek(celPos);
	  mybyte = hCEL.readByte();
	  piece = mybyte & 0xff;
	  celCache.put(new Long(celPos), new Integer(piece));
	  celPos++;
	} catch (IOException e) {
	  JOptionPane.showMessageDialog( this, "Error reading record in cel file", "Error", JOptionPane.ERROR_MESSAGE);
	  System.exit (-1);
	} 
      }

      nPoints = piece & 7;
      flg = piece >> 4;
      if (nPoints != 0) {
	i = 1<<(8+flg);
	fill = 0xffff & flags & i;
	shore = (flags & (1 << flg)) & 0xffff;
      }

      switch (nPoints){
      case 0:
	ok = 0;
	break;
      case 1:
      case 2:
      case 3:
      case 4:
      case 5:
      case 6:
	readCellSegment(nPoints, fill, shore, polyID, xOffset, flg, g);
	break;
      case 7:
	readRimSegment(fill, shore, xOffset, flg, g);
	break;
      }
    }

    if (firstFill == false) {
      bufferAddLine ( x0Fill, y0Fill, 1, g);
      bufferRestart ( x0Fill, y0Fill, 1, g);
    }
    
    if (firstShore == false) {
      bufferAddLine ( x0Shore, y0Shore, 0, g);
      bufferRestart ( x0Shore, y0Shore, 0, g);
    }

    int ok1 = 1;
    while (ok1 != 0) {
      if (useCelCache && (celCache.get(new Long(celPos)) != null)) {
	// found!
	o = celCache.get(new Long(celPos));
	opcode = ((Integer)o).intValue();
	celPos++;
      }
      else {
	try {
	  hCEL.seek(celPos);
	  mybyte = hCEL.readByte();
	  opcode = mybyte & 0xff;
	  celCache.put(new Long(celPos), new Integer(opcode));
	  celPos++;
	} catch (IOException e) {
	  JOptionPane.showMessageDialog( this, "Error reading record in cel file", "Error", JOptionPane.ERROR_MESSAGE);
	}
      }
      if (opcode == 0) {  // no more levels to go.
	ok1 = 0;
	bufferRestart (0, 0, 1, g); // empty the fill buffer
	bufferRestart (0, 0, 0, g); // empty the shore buffer
      }
      else drawRANGSPolygonC(lvl + 1, xOffset, g); // drill down another level
    }
  }  // drawRANGSPolygonC

  private void readCellSegment (int nPoints, int fill, int shore, int polyID, int xOffset, int flg, Graphics g)
  {
    // reads RANGS line segments
    int i, x, y;
    Point pnt = new Point(0,0);
    byte xCoord[] = new byte[4];
    byte yCoord[] = new byte[4];
    Object o;

    for (i = 1; i <= nPoints; i++) {

      if (useCelCache && (celCache2.get(new Long(celPos)) != null)) {
	// found!
	o = celCache2.get(new Long(celPos));
	pnt.setLocation(((Point)o).x, ((Point)o).y);
	celPos += 8;
      }
      else {
	try {
	  hCEL.seek(celPos);
	  hCEL.readFully(xCoord);
	  hCEL.readFully(yCoord);
	  pnt.setLocation(RANGSBytesToInt(xCoord), RANGSBytesToInt(yCoord));
	  celCache2.put(new Long(celPos), new Point(pnt));
	  celPos += 8;
	} catch (IOException e) {
	  JOptionPane.showMessageDialog( this, "Error reading record in cel file", "Error", JOptionPane.ERROR_MESSAGE);
	}
      }
      if (fill != 0) {
	addFillPoint(xOffset, pnt, flg, g);
      }
      if (shore != 0) {
	if ((flags & 1) != 0) {
	  addShorePoint(xOffset, pnt, flg, g);
	}
	else {
	  if (polyID >= 0) {
	    if (i == 1) addShorePoint(xOffset, pnt, flg, g);
	    else if (i == nPoints) addFinalShorePoint(xOffset, pnt, g);
	  }
	}
      }
    }
  }  // readCellSegment

  private void readRimSegment(int fill, int shore, int xOffset, int flg, Graphics g)
  {
    // reads RANGS data for cell rims
    int k, nPoints=0, x, y;    
    byte addrByte[] = new byte[4];
    byte nPointsByte[] = new byte[4];
    byte xCoord[] = new byte[4];
    byte yCoord[] = new byte[4];
    Point pnt = new Point(0,0);
    Object o;
    Vector v;

    if (useCelCache && (celCache2.get(new Long(celPos)) != null)) {
      // found!
      o = celCache2.get(new Long(celPos));
      rimPos = ((Point)o).x;
      nPoints = ((Point)o).y;
      celPos += 8;
    }
    else {
      try {
	// read from cel file and get the address.
	hCEL.seek(celPos);
	hCEL.readFully(addrByte);
	hCEL.readFully(nPointsByte);
	rimPos = RANGSBytesToInt(addrByte) - 1;
	nPoints = RANGSBytesToInt(nPointsByte);
	celCache2.put(new Long(celPos), new Point((int)rimPos, nPoints));
        celPos += 8;
      } catch(IOException e) {
	JOptionPane.showMessageDialog( this, "Error reading record in cel file", "Error", JOptionPane.ERROR_MESSAGE);
      }
    }

    if (nPoints == 0) return;

    // finally read rim data and get coordinates
    if (useRimCache && (rimCache.get(new Long(rimPos)) != null)) {
      // found!  Now process each point in the Vector.
      o = rimCache.get(new Long(rimPos));
      Enumeration enum = ((Vector)o).elements();
      while (enum.hasMoreElements()) {
	pnt = (Point)enum.nextElement();
	if (fill != 0) addFillPoint(xOffset, pnt, flg, g);
	if (shore != 0) addShorePoint(xOffset, pnt, flg, g);
	rimPos +=8;
      }
    }
    else {
      v = new Vector(nPoints);
      try {
	hRIM.seek(rimPos);
      } catch (IOException e) {} // nothing
    
      for (k = 0; k < nPoints; k++) {
	try {
	  hRIM.readFully(xCoord);
	  hRIM.readFully(yCoord);
	} catch (IOException e) {} // nothing

	pnt.setLocation(RANGSBytesToInt(xCoord), RANGSBytesToInt(yCoord));
	v.addElement(new Point(pnt));

	if (fill != 0) addFillPoint(xOffset, pnt, flg, g);
	if (shore != 0) addShorePoint(xOffset, pnt, flg, g);
      }
      rimCache.put(new Long(rimPos), v);
      rimPos += 8*nPoints;
    }
  }  // readRimSegment

  private void addShorePoint(int xOffset, Point pnt, int flg, Graphics g)
  {
    // adds a shore point to the buffer
    double xpnt, ypnt;
    xpnt = xOffset + pnt.getX() * 0.000001;
    ypnt = pnt.getY() * 0.000001;
    if (firstShore == true) {
      setShoreColor(flg);
      g.setColor(clr);
      bufferRestart(xpnt, ypnt, 0, g);
      firstShore = false;
      x0Shore = xpnt;
      y0Shore = ypnt;
    }
    else {
      bufferAddLine (xpnt, ypnt, 0, g);
    }
  }  // addShorePoint

  private void addFillPoint(int xOffset, Point pnt, int flg, Graphics g)
  {
    // adds a fill point to the buffer
    double xpnt, ypnt;
    xpnt = xOffset + pnt.getX() * 0.000001;
    ypnt = pnt.getY() * 0.000001;
    if (firstFill == true) {
      setFillColor(flg);
      g.setColor(clr);
      bufferRestart(xpnt, ypnt, 1, g);
      firstFill = false;
      x0Fill = xpnt;
      y0Fill = ypnt;
    }
    else {
      bufferAddLine(xpnt, ypnt, 1, g);
    }
  }  // addFillPoint


  private void addFinalShorePoint(int xOffset, Point pnt, Graphics g)
  {
    // adds the final shore point to the buffer
    double xpnt, ypnt;
    xpnt = xOffset + pnt.getX() * 0.000001;
    ypnt = pnt.getY() * 0.000001;
    bufferRestart (xpnt, ypnt, 0, g);
  } // addFinalShorePoint

  private void setShoreColor(int flg)
  {
    // set the default color for the shore line.
    if (flg <= 4) {
      clr = mapColor[5];
      return;
    }
    else if (flg == 17) {
      clr = mapColor[6];
      return;
    }
    else clr = mapColor[7];
  }  // setShoreColor

  private void setFillColor(int flg)
  {
    // set the default color to fill with.
    if (flg <= 4) { 
      clr = mapColor[flg];
      return;
    }
    else if (flg == 17) {
      clr = mapColor[6];
      return;
    }
    else clr = mapColor[7];
  }  //setFillColor

  public Color getColor(int i) {
    // returns the Color object for each map feature.
    // i = 0: ocean
    // 1: land
    // 2: lake
    // 3: island in lake
    // 4: pond on island
    // 5: outline
    // 6: error
    // 7: unknown
    return mapColor[i];
  } // getColor

  public void setColor(int i, Color c) {
    if (!mapColor[i].equals(c)) {
      mapColor[i] = c;
      repaint();
    }
  }  // setColor

  private void bufferAddLine(double x, double y, int fill, Graphics g)
  {
    // add another point to the line defined in the buffer.
    int coord[] = {0, 0};
    coord = transform(x,y);
    if (fill != 0) {
	fillPixels++;
      fillPixel[fillPixels].setLocation(coord[0], coord[1]);
      if (fillPixels == Array.getLength(fillPixel)) bufferRestart(x, y, fill, g);
	
    }
    else {
	shorePixels++;
	shorePixel[shorePixels].setLocation(coord[0], coord[1]);

      if (shorePixels == Array.getLength(shorePixel)) bufferRestart(x, y, fill, g);
    }
  }  // bufferAddLine

  private void bufferRestart(double x, double y, int fill, Graphics g)
  {
    // draw and clear the buffer
    MyPolygon p;
    int coord[] = {0, 0};

    int[][] lineCoords = new int[2][fillPixels + 1];
    if (fill != 0) {
      if (fillPixels != 0) {
	p = new MyPolygon(fillPixel, fillPixels + 1);
	g.fillPolygon((Polygon)p);
      }
      fillPixels = 0;
      coord = transform(x,y);
      fillPixel[fillPixels].setLocation(coord[0], coord[1]);
    }
    else {
      if (shorePixels != 0) {
  	  lineCoords = pointsToArrays(shorePixel);
	  g.drawPolyline (lineCoords[0], lineCoords[1], lineCoords[0].length);
      }
      shorePixels = 0;
      coord = transform(x,y);
      shorePixel[shorePixels].setLocation(coord[0], coord[1]);
    }
  } // bufferRestart
	
  private int[][] pointsToArrays(Point[] p) 
  {
    // grabs the x and y coordinates for each p 
    // and puts the x into the corresponding retPal[0],
    // and puts the y into the corresponding retVal[1].
    // Used to process shorePixel[].
	
    int i;
    int[][] retVal = new int[2][shorePixels+1];

    for (i = 0; i < shorePixels+1; i++) {
      retVal[0][i] = (int)p[i].getX();
      retVal[1][i] = (int)p[i].getY();
    }
    return retVal;
  }  // pointsToArrays


  private int[] transform(double lon, double lat)
  {
    // transforms lon/lat into screen x/y coordinates.
    // simple transform for now, add fancy ones later.
    // retVal[0] contains the screen x coordinates, retVal[1]
    // contains the screen y.
    int retVal[] = {0, 0};
    Rectangle r = this.getBounds();
    Insets i = this.getInsets();

    retVal[0] = (int)(i.left + (lon - lon1)*((r.getWidth() - i.left - i.right)/(lon2 - lon1 + 1)));
    retVal[1] = (int)(i.top + (lat - lat1 - 1 )*((r.getHeight()- i.top - i.bottom)/(lat2 - lat1 - 1)));
    return retVal;
  }  // transform


  private int openRANGSFiles()
  {
    /* opens the map data files.
    ** returns 0 if failure, 1 if success
    */
    File f = null;

    try {	
      f = new File("." + File.separatorChar + "rangs(" + String.valueOf(resolution) + ").cel");
      hCEL = new RandomAccessFile(f, "r" );
    } catch (IOException e) {
      JOptionPane.showMessageDialog( this, "Error opening file " + f.toString(), "Error", JOptionPane.ERROR_MESSAGE);
      return 0; 
    }

    try {	 
      f = new File("." + File.separatorChar + "rangs(" + String.valueOf(resolution) + ").cat");
      hCAT = new RandomAccessFile( f, "r" );
    } catch (IOException e) {
      JOptionPane.showMessageDialog( this, "Error opening file " + f.toString(), "Error", JOptionPane.ERROR_MESSAGE);
      return 0; 
    }
    try {	
      f = new File("."+ File.separatorChar + "gshhs(" + String.valueOf(resolution) + ").rim");
      hRIM = new RandomAccessFile( f, "r" );
    } catch (IOException e) {
      JOptionPane.showMessageDialog( this, "Error opening file " + f.toString(), "Error", JOptionPane.ERROR_MESSAGE);
      return 0; 
    }
    return 1;
  }  // openRANGSFiles

  public int closeRANGSFiles()
  {
    /* Closes the map data files.
    ** returns 1.
    */
    try {
      if (hCEL != null) hCEL.close();
    } catch ( IOException e) {
      JOptionPane.showMessageDialog( this, "Error closing file", "Error", JOptionPane.ERROR_MESSAGE);
    }
    try {
      if (hCAT != null) hCAT.close();
    } catch ( IOException e) {
      JOptionPane.showMessageDialog( this, "Error closing file", "Error", JOptionPane.ERROR_MESSAGE);
    }
    try {
      if (hRIM != null) hRIM.close();
    } catch ( IOException e) {
      JOptionPane.showMessageDialog( this, "Error closing file", "Error", JOptionPane.ERROR_MESSAGE);
    }
    return 1;
  } // closeRANGSFiles

  private int RANGSBytesToInt(byte a[])
    {
      // expects 4 bytes to be passed in.  Grinds them
      // up and spits out a long in proper format 
	return (UnsignedByte.toInt(a[3])*0x1000000 + UnsignedByte.toInt(a[2])*0x10000 + UnsignedByte.toInt(a[1])*0x100 + UnsignedByte.toInt(a[0]));
    }  // RANGSBytesToInt

}










