package org.mapfish.print.test.util;

import com.google.common.collect.FluentIterable;
import com.google.common.io.Files;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRGraphics2DExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleGraphics2DExporterOutput;
import net.sf.jasperreports.export.SimpleGraphics2DReportConfiguration;
import org.apache.batik.transcoder.TranscoderException;
import org.mapfish.print.SvgUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import javax.media.jai.iterator.RandomIter;
import javax.media.jai.iterator.RandomIterFactory;

/**
 * Class for comparing an image to another image.
 *
 * CHECKSTYLE:OFF
 */
public final class ImageSimilarity {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageSimilarity.class);

    private static final int DEFAULT_SAMPLESIZE = 15;
    // The reference image "signature" (25 representative pixels, each in R,G,B).
    // We use instances of Color to make things simpler.
    private final Color[][] signature;
    private final BufferedImage referenceImage;
    // The size of the sampling area.
    private int sampleSize = DEFAULT_SAMPLESIZE;
    // values that are used to generate the position of the sample pixels
    private static final int COMPARE_SIZE = 50;

    private static float prop(int n) {
        return (float) ((0.5 + n) / COMPARE_SIZE);
    }

    /**
     * The constructor, which creates the GUI and start the image processing task.
     */
    public ImageSimilarity(final File referenceImage, final int sampleSize) throws IOException {
        this(ImageIO.read(referenceImage), sampleSize);
    }

    /**
     * The constructor, which creates the GUI and start the image processing task.
     */
    public ImageSimilarity(BufferedImage referenceImage) throws IOException {
        this(referenceImage, (int) Math.floor(
                Math.min(referenceImage.getWidth(), referenceImage.getHeight()) / COMPARE_SIZE / 4));
    }

    /**
     * The constructor, which creates the GUI and start the image processing task.
     */
    public ImageSimilarity(BufferedImage referenceImage, int sampleSize) throws IOException {
        this.referenceImage = referenceImage;
        if (referenceImage.getWidth() * prop(0) < sampleSize) {
            LOGGER.warn("Max: {}", referenceImage.getWidth() * prop(0));
            throw new IllegalArgumentException(String.format("sample width is too big for the image " +
                    "(width: %s, sampleSize: %s).", referenceImage.getWidth(), sampleSize));
        }
        if (referenceImage.getHeight() * prop(0) < sampleSize) {
            LOGGER.warn("Max: {}", referenceImage.getHeight() * prop(0));
            throw new IllegalArgumentException(String.format("sample height is too big for the image " +
                    "(width: %s, sampleSize: %s).", referenceImage.getHeight(), sampleSize));
        }
        this.sampleSize = sampleSize;

        signature = calcSignature(referenceImage);
    }

    /**
     * This method calculates and returns signature vectors for the input image.
     */
    private Color[][] calcSignature(BufferedImage i) {
        // Get memory for the signature.
        Color[][] sig = new Color[COMPARE_SIZE][COMPARE_SIZE];
        // For each of the 25 signature values average the pixels around it.
        // Note that the coordinate of the central pixel is in proportions.
        for (int x = 0; x < COMPARE_SIZE; x++) {
            for (int y = 0; y < COMPARE_SIZE; y++) {
                sig[x][y] = averageAround(i, prop(x), prop(y));
            }
        }
        return sig;
    }

    /**
     * This method averages the pixel values around a central point and return the
     * average as an instance of Color. The point coordinates are proportional to
     * the image.
     */
    private Color averageAround(BufferedImage i, double px, double py) {
        // Get an iterator for the image.
        RandomIter iterator = RandomIterFactory.create(i, null);
        // Get memory for a pixel and for the accumulator.
        double[] pixel = new double[i.getSampleModel().getNumBands()];
        double[] accum = new double[3];
        int numPixels = 0;
        // Sample the pixels.

        int pxwi = (int) Math.round(px * i.getWidth());
        int pyhi = (int) Math.round(py * i.getHeight());
        for (int x = pxwi - sampleSize; x < pxwi + sampleSize; x++) {
            for (int y = pyhi - sampleSize; y < pyhi + sampleSize; y++) {
                iterator.getPixel(x, y, pixel);
                accum[0] += pixel[0];
                accum[1] += pixel[1];
                accum[2] += pixel[2];
                numPixels++;
            }
        }
        // Average the accumulated values.
        accum[0] /= numPixels;
        accum[1] /= numPixels;
        accum[2] /= numPixels;
        return new Color((int) Math.round(accum[0]), (int) Math.round(accum[1]), (int) Math.round(accum[2]));
    }

    /**
     * This method calculates the distance between the signatures of an image and
     * the reference one. The signatures for the image passed as the parameter are
     * calculated inside the method.
     */
    private double calcDistance(final BufferedImage other) {
        // Calculate the signature for that image.
        Color[][] sigOther = calcSignature(other);
        // There are several ways to calculate distances between two vectors,
        // we will calculate the sum of the distances between the RGB values of
        // pixels in the same positions.
        double dist = 0;
        for (int x = 0; x < COMPARE_SIZE; x++) {
            for (int y = 0; y < COMPARE_SIZE; y++) {
                int r1 = this.signature[x][y].getRed();
                int g1 = this.signature[x][y].getGreen();
                int b1 = this.signature[x][y].getBlue();
                int r2 = sigOther[x][y].getRed();
                int g2 = sigOther[x][y].getGreen();
                int b2 = sigOther[x][y].getBlue();
                double tempDist = Math.sqrt((r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2));
                dist += tempDist;
            }
        }
        // Normalise with the previus calculation
        dist = dist * COMPARE_SIZE * COMPARE_SIZE / 25;
        LOGGER.warn("Current distance: {}", dist);
        return dist;
    }

    /**
     * Check that the other image and the image calculated by this object are within the given distance.
     *
     * @param other the image to compare to "this" image.
     * @param maxDistance the maximum distance between the two images.
     */
    public void assertSimilarity(File other, double maxDistance) throws IOException {
        final File actualOutput = new File(other.getParentFile(),
                "actual" + other.getName().replace("expected", "").replace(".tiff", ".png"));
        if (!other.exists()) {
            ImageIO.write(referenceImage, "png", actualOutput);
            throw new AssertionError("The expected file was missing and has been generated: " +
                    actualOutput.getAbsolutePath());
        }
        final double distance = calcDistance(ImageIO.read(other));
        if (distance > maxDistance) {
            ImageIO.write(referenceImage, "png", actualOutput);
            throw new AssertionError(String.format(
                    "similarity difference between images is: %s which is greater than the max distance of" +
                            " %s\nactual=%s\nexpected=%s", distance, maxDistance,
                    actualOutput.getAbsolutePath(),actualOutput.getAbsolutePath()));
        }
    }

    /**
     * Write the image to a file in uncompressed tiff format.
     *
     * @param image image to write
     * @param file path and file name (extension will be ignored and changed to tiff.
     */
    public static void writeUncompressedImage(BufferedImage image, String file) throws IOException {
        FileImageOutputStream out = null;
        try {
            final File parentFile = new File(file).getParentFile();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersBySuffix("tiff");
            final ImageWriter next = writers.next();

            final ImageWriteParam param = next.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_DISABLED);

            final File outputFile = new File(parentFile, Files.getNameWithoutExtension(file) + ".tiff");

            out = new FileImageOutputStream(outputFile);
            next.setOutput(out);
            next.write(image);
        } catch (Throwable e) {
            System.err.println(String.format(
                    "Error writing the image generated by the test: %s%n\t", file));
            e.printStackTrace();
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    /**
     * Merges a list of graphic files into a single graphic.
     *
     * @param graphicFiles a list of graphic files
     * @param width the graphic width (required for svg files)
     * @param height the graphic height (required for svg files)
     * @return a single graphic
     * @throws IOException, TranscoderException
     */
    public static BufferedImage mergeImages(List<URI> graphicFiles, int width, int height)
            throws IOException, TranscoderException {
        if (graphicFiles.isEmpty()) {
            throw new IllegalArgumentException("no graphics given");
        }

        BufferedImage mergedImage = loadGraphic(graphicFiles.get(0), width, height);
        Graphics g = mergedImage.getGraphics();
        for (int i = 1; i < graphicFiles.size(); i++) {
            BufferedImage image = loadGraphic(graphicFiles.get(i), width, height);
            g.drawImage(image, 0, 0, null);
        }
        g.dispose();

        // ImageIO.write(mergedImage, "tiff", new File("/tmp/expectedSimpleImage.tiff"));

        return mergedImage;
    }

    private static BufferedImage loadGraphic(URI path, int width, int height) throws IOException, TranscoderException {
        File file = new File(path);

        if (file.getName().endsWith(".svg")) {
            return convertFromSvg(path, width, height);
        } else {
            BufferedImage originalImage = ImageIO.read(file);
            BufferedImage resizedImage = new BufferedImage(width, height, originalImage.getType());
            Graphics2D g = resizedImage.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(originalImage, 0, 0, width, height, null);
            g.dispose();
            return resizedImage;
        }
    }

    /**
     * Renders an SVG image into a {@link BufferedImage}.
     */
    public static BufferedImage convertFromSvg(URI svgFile, int width, int height) throws TranscoderException {
        return SvgUtil.convertFromSvg(svgFile, width, height);
    }

    /**
     * Exports a rendered {@link JasperPrint} to a {@link BufferedImage}.
     */
    public static BufferedImage exportReportToImage(JasperPrint jasperPrint, Integer page) throws Exception {
        BufferedImage pageImage = new BufferedImage(jasperPrint.getPageWidth(), jasperPrint.getPageHeight(), BufferedImage.TYPE_INT_RGB);

        JRGraphics2DExporter exporter = new JRGraphics2DExporter();

        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));

        SimpleGraphics2DExporterOutput output = new SimpleGraphics2DExporterOutput();
        output.setGraphics2D((Graphics2D)pageImage.getGraphics());
        exporter.setExporterOutput(output);

        SimpleGraphics2DReportConfiguration configuration = new SimpleGraphics2DReportConfiguration();
        configuration.setPageIndex(page);
        exporter.setConfiguration(configuration);

        exporter.exportReport();

        return pageImage;
    }

    /**
     * Exports a rendered {@link JasperPrint} to a graphics file.
     */
    public static void exportReportToFile(JasperPrint jasperPrint, String fileName, Integer page) throws Exception {
        BufferedImage pageImage = exportReportToImage(jasperPrint, page);
        ImageIO.write(pageImage, Files.getFileExtension(fileName), new File(fileName));
    }

    public static void main(String args[]) throws IOException {
        final String path = "core/src/test/resources/map-data";
        final File root = new File(path);
        final FluentIterable<File> files = Files.fileTreeTraverser().postOrderTraversal(root);
        for (File file : files) {
            if (Files.getFileExtension(file.getName()).equals("png")) {
                final BufferedImage img = ImageIO.read(file);
                writeUncompressedImage(img, file.getAbsolutePath());
            }
        }
    }
}
