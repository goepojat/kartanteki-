package fi.goepojat.util;

import java.awt.Color;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ColorUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ColorUtils.class);

    private static final Color YELLOW = Color.decode("#ffc150");

    private static final Color BLUE = Color.decode("#1caed7");

    private static final Color GREEN_1 = Color.decode("#c2f4b7");

    private static final Color GREEN_2 = Color.decode("#c2f3bc");

    private static final Color GREEN_3 = Color.decode("#c0f4b6");

    private static final Color GREEN_4 = Color.decode("#84ec71");

    private static final Color BLACK = Color.decode("#00030a");

    private static final Color WHITE = Color.decode("#fffffd");

    private static final Color BROWN = Color.decode("#f5e5d5");

    private static final Color PURPLE = Color.decode("#cb17c5");

    private ColorUtils() {
    }

    public static int getRGBIntOfClassification(char classification) {
        switch (classification) {
        case OClassification.WATER:
            return BLUE.getRGB();
        case OClassification.GROUND:
            return YELLOW.getRGB();
        case OClassification.FOREST:
            return WHITE.getRGB();
        case OClassification.CLIFF:
        case OClassification.MANMADE:
            return BLACK.getRGB();
        case OClassification.ROAD:
            return BROWN.getRGB();
        case OClassification.VEGETATION_1:
            return GREEN_1.getRGB();
        case OClassification.VEGETATION_2:
            return GREEN_2.getRGB();
        case OClassification.VEGETATION_3:
            return GREEN_3.getRGB();
        case OClassification.VEGETATION_4:
            return GREEN_4.getRGB();
        default:
            LOGGER.info((short)classification + " has no color specified");
            return PURPLE.getRGB();
        }
    }
}
