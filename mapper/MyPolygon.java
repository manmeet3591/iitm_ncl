import java.awt.*;

public class MyPolygon extends Polygon {

  public MyPolygon(Point p[], int pts) {
    int i;

    for (i = 0; i < pts; i++) {
      addPoint((int)(p[i].getX()), (int)(p[i].getY()));
    }
  }

}
