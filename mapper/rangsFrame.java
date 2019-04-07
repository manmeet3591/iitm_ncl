import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.awt.print.*;


public class rangsFrame extends JApplet 
                        implements ActionListener, ItemListener {

  private rangs myMap;
  private JLabel statusLabel;
  private JRadioButtonMenuItem detailItems[], resolutionItems[];
  private JMenuItem colorItems[];
  private JCheckBoxMenuItem fillItem;
  private JButton zoomButton[], scrollButton[], stretchButton[];
  private JTextField latText1, lonText1, latText2, lonText2;
  public PageFormat pf = new PageFormat();

  public void init()
  {
    // default initializer.  Map of the continental USA.
    init(230,50,300,25);
  }

  public void init(int ln1, int lt1, int ln2, int lt2)
  {
    // Initializes a map with upper left/lower right corners
    // as described by ln1/lt1, ln2/lt2.
    JPanel cp = buildUI();
    myMap = new rangs(ln1, lt1, ln2, lt2, this);

    Container c = getContentPane();
    c.setLayout(new BorderLayout(5, 5));
    c.add( cp, BorderLayout.EAST );
    c.add( myMap, BorderLayout.CENTER );
    c.add( statusLabel, BorderLayout.SOUTH );
    pf.setOrientation(PageFormat.LANDSCAPE);
  }


  public void actionPerformed (ActionEvent e) {
    // Button handler
    if (e.getSource() == zoomButton[0]) {
      updateStatus(" Zooming In.");
      myMap.zoomIn();
      return;
    }

    if (e.getSource() == zoomButton[1]) {
      updateStatus(" Zooming Out.");
      myMap.zoomOut();
      return;
    }
    if (e.getSource() == scrollButton[0]) {
      updateStatus(" Scrolling Up.");
      myMap.scrollUp();
      return;
    }
    if (e.getSource() == scrollButton[1]) {
      updateStatus(" Scrolling Down.");
      myMap.scrollDown();
      return;
    }
    if (e.getSource() == scrollButton[2]) {
      updateStatus(" Scrolling Left.");
      myMap.scrollLeft();
      return;
    }
    if (e.getSource() == scrollButton[3]) {
      updateStatus(" Scrolling Right.");
      myMap.scrollRight();
      return;
    }
    if (e.getSource() == stretchButton[0]) {
      updateStatus(" Stretching Longitudinally.");
      myMap.stretchLong();
      return;
    }
    if (e.getSource() == stretchButton[1]) {
      updateStatus(" Stretching Latitudinally.");
      myMap.stretchLat();
      return;
    }

    for (int i = 0; i < detailItems.length; i++) {
      if (e.getSource() == detailItems[i] && detailItems[i].isSelected()) {
	updateStatus(" Changing detail level.");
        myMap.setDetail(i);
	return;
      }
    }

    for (int i = 0; i < resolutionItems.length; i++) {
      if (e.getSource() == resolutionItems[i] && resolutionItems[i].isSelected()) {
	updateStatus(" Changing Resolution level to " + i + ".");
        myMap.setResolution(i);
	return;
      }
    }

    Color c = null;
    for (int i = 0; i < colorItems.length; i++) {
      if (e.getSource() == colorItems[i]) {
	c =  JColorChooser.showDialog(rangsFrame.this, "Choose a color", myMap.getColor(i));
	if (c != null) {
	  myMap.setColor(i, c);
	  return;
	}
      }
    }
  }

  public void itemStateChanged ( ItemEvent e ) 
  {
    // looks at checkboxes and does what is necessary based on them
    if (fillItem.isSelected()) {
      updateStatus(" Filling map");
      myMap.setDetail(-1);
    }
    else {
      updateStatus (" Outlining map");
      myMap.setDetail(-2);
    }
      
  }

  public void updateLatLon(double lat1, double lon1, double lat2, double lon2)
  {
    latText1.setText(printLat(lat1));
    latText2.setText(printLat(lat2));
    lonText1.setText(printLon(lon1));
    lonText2.setText(printLon(lon2));
  } // sets the text for the lat/lon displays

  public String printLat(double l)
  {
    String s;
    s = String.valueOf(Math.abs(l));
    if (s.length() > 6) s = s.substring(0,6);
    if (l < 0) return s + " S";
    else return  s + " N";
  }

  public String printLon(double l)
  {
    String s;
    l = (((l % 360) + 360) % 360);
    if (l > 180) {
      l = 360 - l;
      s = String.valueOf(Math.abs(l));
      if (s.length() > 6) s = s.substring(0,6);
      return s + " W";
    }
    else {
      s = String.valueOf(Math.abs(l));
      if (s.length() > 6) s = s.substring(0,6);
      return s + " E";
    }
  }

  public void updateStatus(String s)
  {
    // updates the status bar
    statusLabel.setText(s);
    statusLabel.invalidate();
    validate();
    statusLabel.repaint();
  }

  private JPanel buildUI() {
    // Builds the user interface.  Creates and places buttons, menus, etc.
    // returns a reference to the control panel.
    int i;
    JPanel controlPanel, controlPanelInner, zoomButtonPanel, scrollButtonPanel, resolutionPanel, zoomStretchPanel, latLonPanel, latLonPanel1, latLonPanel2;
    Icon icons[] = new Icon[8];
    icons[0] = new ImageIcon("MagnifyPlus.gif");
    icons[1] = new ImageIcon("MagnifyMinus.gif");
    icons[2] = new ImageIcon("Up.gif");
    icons[3] = new ImageIcon("Down.gif");
    icons[4] = new ImageIcon("Left.gif");
    icons[5] = new ImageIcon("Right.gif");
    icons[6] = new ImageIcon("Widen.gif");
    icons[7] = new ImageIcon("Lengthen.gif");
    zoomButton = new JButton[2];
    scrollButton = new JButton[4];
    stretchButton = new JButton[2];

    // control panel
    controlPanel = new JPanel();
    controlPanel.setLayout(new BorderLayout(5,5));
    controlPanel.setBorder(BorderFactory.createEtchedBorder());
    // controlPanelInner lets us fake the system out so the panel components don't resize when maximized.
    controlPanelInner = new JPanel();
    controlPanelInner.setLayout(new GridLayout(3, 2));
    controlPanel.add(controlPanelInner, BorderLayout.NORTH);

    // the panel that holds the zoom and stretch controls
    zoomStretchPanel = new JPanel();
    zoomStretchPanel.setLayout(new BoxLayout(zoomStretchPanel, BoxLayout.X_AXIS));

    // zoom panel
    zoomButtonPanel = new JPanel();
    zoomButtonPanel.setLayout(new GridLayout(2, 1, 5, 5));
    zoomButtonPanel.setBorder(BorderFactory.createTitledBorder("Zoom"));
    zoomStretchPanel.add(zoomButtonPanel);

    // Zoom panel buttons
    zoomButton[0] = new JButton(icons[0]);
    zoomButton[1] = new JButton(icons[1]);

    zoomButtonPanel.add(zoomButton[0]);
    zoomButtonPanel.add(zoomButton[1]);
    
    // stretch panel
    JPanel stretchButtonPanel = new JPanel();
    stretchButtonPanel.setLayout(new GridLayout(2, 1, 5, 5));
    stretchButtonPanel.setBorder(BorderFactory.createTitledBorder("Stretch"));
    // stretch buttons
    stretchButton[0] = new JButton(icons[6]);
    stretchButton[1] = new JButton(icons[7]);

    stretchButtonPanel.add(stretchButton[0]);
    stretchButtonPanel.add(stretchButton[1]);

    zoomStretchPanel.add(Box.createHorizontalGlue());
    zoomStretchPanel.add(stretchButtonPanel);
    controlPanelInner.add(zoomStretchPanel);

    // scroll buttons
    scrollButton[0] = new JButton(icons[2]);
    scrollButton[1] = new JButton(icons[3]);
    scrollButton[2] = new JButton(icons[4]);
    scrollButton[3] = new JButton(icons[5]);
    for (i = 0; i < 4; i++) {
      scrollButton[i].setPreferredSize(scrollButton[1].getPreferredSize());
      scrollButton[i].setMinimumSize(scrollButton[1].getMinimumSize());
      scrollButton[i].setMaximumSize(scrollButton[1].getMaximumSize());
    }
    scrollButtonPanel = new JPanel();
    scrollButtonPanel.setBorder(BorderFactory.createTitledBorder("Scroll"));
    GridBagLayout gbl = new GridBagLayout();
    scrollButtonPanel.setLayout(gbl);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 1; gbc.gridy = 0;
    gbc.gridwidth = 2; gbc.gridheight = 1;
    gbl.setConstraints(scrollButton[0], gbc);
    scrollButtonPanel.add(scrollButton[0]);
    gbc.gridy = 2;
    gbl.setConstraints(scrollButton[1], gbc);
    scrollButtonPanel.add(scrollButton[1]);
    gbc.gridx = 0; gbc.gridy = 1;
    gbl.setConstraints(scrollButton[2], gbc);
    scrollButtonPanel.add(scrollButton[2]);
    gbc.gridx = 2;
    gbl.setConstraints(scrollButton[3], gbc);
    scrollButtonPanel.add(scrollButton[3]);

    controlPanelInner.add(scrollButtonPanel);

    // lat/lon display
    latLonPanel = new JPanel();
    latLonPanel.setBorder(BorderFactory.createTitledBorder("Map Dimensions"));
    latLonPanel.setLayout(new BoxLayout(latLonPanel, BoxLayout.X_AXIS));
    latLonPanel1 = new JPanel();
    latLonPanel2 = new JPanel();
    latLonPanel1.setLayout(new BoxLayout(latLonPanel1, BoxLayout.Y_AXIS));
    latLonPanel2.setLayout(new BoxLayout(latLonPanel2, BoxLayout.Y_AXIS));

    latText1 = new JTextField(6);
    lonText1 = new JTextField(6);
    latText2 = new JTextField(6);
    lonText2 = new JTextField(6);
    latText1.setEditable(false);
    latText2.setEditable(false);
    lonText1.setEditable(false);
    lonText2.setEditable(false);

    latLonPanel1.add(latText1);
    latLonPanel1.add(lonText1);
    latLonPanel1.add(Box.createRigidArea(new Dimension(0, 50)));
    latLonPanel2.add(Box.createRigidArea(new Dimension(0, 50)));
    latLonPanel2.add(latText2);
    latLonPanel2.add(lonText2);

    latLonPanel.add(latLonPanel1);
    latLonPanel.add(Box.createRigidArea(new Dimension(20,0)));
    latLonPanel.add(latLonPanel2);

    controlPanelInner.add(latLonPanel);

    // the status bar
    statusLabel = new JLabel(" Click on the map to center at that point.");
    statusLabel.setBorder(BorderFactory.createLoweredBevelBorder());
    controlPanel.invalidate();
    buildMenus();
    makeListeners();
    return controlPanel;
  }

  public void buildMenus() {
    // create the menus
    JMenuBar bar = new JMenuBar();
    setJMenuBar( bar );

    // file menu
    JMenu fileMenu = new JMenu("File");
    fileMenu.setMnemonic('F');
    bar.add(fileMenu);
    // file/page setup
    JMenuItem setupItem = new JMenuItem("Page Setup...");
    setupItem.setMnemonic('u');
    setupItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
	PrinterJob job = PrinterJob.getPrinterJob();
	pf = job.pageDialog(pf);
      }
    });
    fileMenu.add(setupItem);
    
    // file/print preview
    JMenuItem previewItem = new JMenuItem("Print Preview");
    previewItem.setMnemonic('v');
    previewItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
 	Thread runner = new Thread() {
 	  public void run() {
	      PrintPreview pp = new PrintPreview(myMap, pf);
 	  }
 	};
 	runner.start();
      }
    }
    );
    fileMenu.add(previewItem);

    // file/print
    JMenuItem printItem = new JMenuItem("Print Map...");
    printItem.setMnemonic('P');
    printItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Thread runner = new Thread() {
          public void run() { 
	    printMap();
          }
        };
        runner.start();
      }
    }
    );
    fileMenu.add(printItem);
    fileMenu.addSeparator();

    // file/exit
    JMenuItem exitItem = new JMenuItem("Exit");
    exitItem.setMnemonic('x');
    exitItem.addActionListener(
       new ActionListener() {
          public void actionPerformed (ActionEvent e) {
	    myMap.closeRANGSFiles();
	    System.exit(0);
	  }
       }
    );
    fileMenu.add(exitItem);

    // format menu
    JMenu formatMenu = new JMenu( "Format" );
    ButtonGroup detailGroup, resolutionGroup;
    formatMenu.setMnemonic('r');
    bar.add(formatMenu);

    // format/fill polygons or just outlines
    fillItem = new JCheckBoxMenuItem("Fill");
    fillItem.setSelected(true);
    formatMenu.add(fillItem);
    fillItem.addItemListener(this);

    // format/level of map detail
    String level[] = {"Land", "Lakes", "Islands in Lakes", "Ponds on Islands"};
    JMenu detailMenu = new JMenu("Detail");
    detailMenu.setMnemonic('D');
    detailItems = new JRadioButtonMenuItem[level.length];
    detailGroup = new ButtonGroup();
    for (int i = 0; i < level.length; i++) {
      detailItems[i] = new JRadioButtonMenuItem(level[i]);
      detailMenu.add(detailItems[i]);
      detailGroup.add(detailItems[i]);
      detailItems[i].addActionListener(this);
    }
    detailItems[level.length - 1].setSelected(true);
    formatMenu.add(detailMenu);

    // format/level of map resolution
    JMenu resolutionMenu = new JMenu("Resolution");
    resolutionMenu.setMnemonic('R');
    String res[] = { "Finest", "Fine", "Medium", "Coarse", "Coarsest" };
    resolutionItems = new JRadioButtonMenuItem[res.length];
    resolutionGroup = new ButtonGroup();
    for (int i = 0; i < res.length; i++) {
      resolutionItems[i] = new JRadioButtonMenuItem(res[i]);
      resolutionMenu.add(resolutionItems[i]);
      resolutionGroup.add(resolutionItems[i]);
      resolutionItems[i].addActionListener(this);
    }
    resolutionItems[res.length - 2].setSelected(true);
    formatMenu.add(resolutionMenu);
    formatMenu.addSeparator();

    // format/color chooser
    String clrs[] = {"Ocean", "Land", "Lake", "Island in Lake", "Pond on Island", "Outline"};
    JMenu colorMenu = new JMenu("Colors");
    colorMenu.setMnemonic('C');
    colorItems = new JMenuItem[clrs.length];
    for (int i = 0; i < clrs.length; i++) {
      colorItems[i] = new JMenuItem(clrs[i]);
      colorMenu.add(colorItems[i]);
      colorItems[i].addActionListener(this);
    }
    formatMenu.add(colorMenu);

    // ADAMS menu
    JMenu A_menu = new JMenu("ADAMS");
    A_menu.setMnemonic('A');
    bar.add(A_menu);

    // help menu
    JMenu helpMenu = new JMenu( "Help" );
    helpMenu.setMnemonic('H');
    bar.add(helpMenu);
    JMenuItem aboutItem = new JMenuItem("About...");
    aboutItem.setMnemonic('A');
    aboutItem.addActionListener(
       new ActionListener() {
          public void actionPerformed (ActionEvent e) {
	    Icon worldIcon = new ImageIcon("world.gif");
	    JOptionPane.showMessageDialog( rangsFrame.this,
	       "Mapper\nby David Mikesell\nUniversity of Virginia, 2000\ndrm9f@virginia.edu\n\nCoastline data from Rainer Feistel\nBaltic Sea Research Institute\nhttp://www.io-warnemuende.de/public/phy/rfeistel/index.htm\n\nIcons Copyright 1998 by Dean S. Jones\ndean@gallant.com\nwww.gallant.com/icons.htm", "About", JOptionPane.PLAIN_MESSAGE, worldIcon); 
	  }
       }
    );
    helpMenu.add(aboutItem);
  }

  private void printMap() {
    repaint();
    try {
      PrinterJob prnJob = PrinterJob.getPrinterJob();
      prnJob.setPrintable(myMap, pf);
      if (!prnJob.printDialog())
        return;
      setCursor( Cursor.getPredefinedCursor(
        Cursor.WAIT_CURSOR));
      prnJob.print();
      setCursor( Cursor.getPredefinedCursor(
        Cursor.DEFAULT_CURSOR));
      JOptionPane.showMessageDialog(this, 
        "Printing completed successfully", "Info",
        JOptionPane.INFORMATION_MESSAGE);
    }
    catch (PrinterException e) {
      e.printStackTrace();
      System.err.println("Printing error: "+e.toString());
    }
  }
  private void makeListeners() {
    // assign action listeners to buttons
    for (int i = 0; i < 2; i++) {
       zoomButton[i].addActionListener(this);
       stretchButton[i].addActionListener(this);
     }
    for (int i = 0; i < 4; i++) {
      scrollButton[i].addActionListener(this);
    }
  }

  public static void main( String args[] )
  { 
    // when run as an application, applet runs within a JFrame.
    JFrame appWindow = new JFrame("Dave's Java Mapper");

    appWindow.addWindowListener( new WindowAdapter() {
      public void windowClosing( WindowEvent e ) 
        {
          System.exit(0);
        }
    }
                           );
    rangsFrame app = new rangsFrame();
    app.init();
    app.start();

    appWindow.getContentPane().add(app);
    appWindow.setSize(1000, 440);
    appWindow.setVisible(true);

  } 

}



