package fi.goepojat.contour;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Collections;

import org.jaitools.media.jai.contour.ContourOpImage;

public class ContourTest {

    public static BufferedImage createContour(BufferedImage source, Collection<Integer> levels) {
        ContourOpImage op = new ContourOpImage(source, null, 0, levels, null, Collections.singletonList(-1.0f), true, true, true);
        BufferedImage contourImage = op.getAsBufferedImage();
        return contourImage;
    }
    
    public static void main(String[] args) {
        
    }
}
