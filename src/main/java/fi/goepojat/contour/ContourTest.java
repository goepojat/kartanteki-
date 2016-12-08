package fi.goepojat.contour;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jaitools.media.jai.contour.ContourDescriptor;
import org.jaitools.media.jai.contour.ContourOpImage;

import com.vividsolutions.jts.awt.ShapeWriter;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;

public class ContourTest {

    public static BufferedImage createContour(BufferedImage source, Collection<Integer> levels) {
        ContourOpImage op = new ContourOpImage(source, null, 0, levels, null, Collections.singletonList(-1.0f), true,
                true, true);
        Object obj = op.getProperty(ContourDescriptor.CONTOUR_PROPERTY_NAME);
        @SuppressWarnings("unchecked")
        List<LineString> contours = (List<LineString>) obj;
        MultiLineString multi = new MultiLineString(contours.toArray(new LineString[contours.size()]),
                new GeometryFactory());
        ShapeWriter sw = new ShapeWriter();
        Shape shape = sw.toShape(multi);

        BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.draw(shape);

        return image;
    }

}
