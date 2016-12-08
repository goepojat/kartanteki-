package fi.goepojat.util;

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
public final class LASClassification {
    
    private LASClassification() {
    }
    
    public static final char CREATED = 0;
    public static final char UNCLASSIFIED = 1;
    public static final char GROUND = 2;
    public static final char LOW_VEGETATION = 3;
    public static final char MEDIUM_VEGETATION = 4;
    public static final char HIGH_VEGETATAION = 5;
    public static final char BUILDING = 6;
    public static final char LOW_POINT_NOISE = 7;
    public static final char RESERVED = 8;
    public static final char WATER = 9;
    public static final char RAIL = 10;
    public static final char ROAD_SURGFACE = 11;

}
