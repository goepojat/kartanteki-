package fi.goepojat.util;

import java.awt.Color;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ColorUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ColorUtils.class);
    
    private ColorUtils() {
    }
    
    /*
     * HELPER FUNCTIONS FOR CLASSIFICATIONS
     * 
     *   Classification Value    Meaning
     *   0                       Created, never classified
     *   1                       Unclassified3
     *   2                       Ground
     *   3                       Low Vegetation
     *   4                       Medium Vegetation
     *   5                       High Vegetation
     *   6                       Building
     *   7                       Low Point (noise)
     *   8                       Reserved
     *   9                       Water
     *   10                      Rail
     *   11                      Road Surface
     *   12                      Reserved
     *   13                      Wire – Guard (Shield)
     *   14                      Wire – Conductor (Phase)
     *   15                      Transmission Tower
     *   16                      Wire-structure Connector (e.g. Insulator)
     *   17                      Bridge Deck
     *   18                      High Noise
     *   19-63                   Reserved
     *   64-255                  User definable
     */
    
    private static final Color BROWN = new Color(0, 0, 0);
    
    public static int getRGBIntOfClassification(char classification) {
        switch (classification) {
        case 0:
        case 1:
            return Color.BLACK.getRGB();
        case 2:
            return Color.YELLOW.getRGB();
        case 3:
        case 4:
        case 5:
            return Color.GREEN.getRGB();
        case 6:
            return BROWN.getRGB();
        case 7:
            return Color.WHITE.getRGB();
        case 9:
            return Color.BLUE.getRGB();
        case 10:
            return Color.DARK_GRAY.getRGB();
        case 11:
        case 14:
            return Color.GRAY.getRGB();
        default:
            LOGGER.info((short)classification + " has no color specified");
            return Color.WHITE.getRGB();
        }
    }
}
