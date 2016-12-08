package fi.goepojat.tiff;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public class LAS2TIFF {

    public LAS2TIFF() {
    }
    
    public void writeTIFF(File file, BufferedImage image) throws IOException {
        // Get the writer
        String format = "tiff";
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);

        if (!writers.hasNext()) {
            throw new IllegalArgumentException("No writer for: " + format);
        }
        ImageWriter writer = writers.next();

        try {
            // Create output stream
            ImageOutputStream output = ImageIO.createImageOutputStream(file);

            try {
                writer.setOutput(output);

                // Optionally, listen to progress, warnings, etc.

                ImageWriteParam param = writer.getDefaultWriteParam();

                // Optionally, control format specific settings of param (requires casting), or
                // control generic write settings like sub sampling, source region, output type etc.

                // Optionally, provide thumbnails and image/stream metadata
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                // Close stream in finally block to avoid resource leaks
                output.close();
            }
        } finally {
            // Dispose writer in finally block to avoid memory leaks
            writer.dispose();
        }
    }
}
