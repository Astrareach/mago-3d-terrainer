package com.gaia3d.terrain.tile.geotiff;

import com.gaia3d.command.GlobalOptions;
import com.gaia3d.terrain.util.GaiaGeoTiffUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.processing.Operations;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.joml.Vector2i;
import org.geotools.api.coverage.grid.GridEnvelope;
import org.geotools.api.coverage.grid.GridGeometry;
import org.geotools.api.geometry.Bounds;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.data.WorldFileReader;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

@Getter
@Setter
@NoArgsConstructor
@Slf4j
public class GaiaGeoTiffManager {
    private final String PROJECTION_CRS = "EPSG:3857";
    private int[] pixel = new int[1];
    private double[] originalUpperLeftCorner = new double[2];
    private Map<String, GridCoverage2D> mapPathGridCoverage2d = new HashMap<>();
    private Map<String, Vector2i> mapPathGridCoverage2dSize = new HashMap<>();
    private Map<String, String> mapGeoTiffToGeoTiff4326 = new HashMap<>();
    private List<String> pathList = new ArrayList<>(); // used to delete the oldest coverage

    public GridCoverage2D loadGeoTiffGridCoverage2D(String geoTiffFilePath) {
        if (mapPathGridCoverage2d.containsKey(geoTiffFilePath)) {
            log.info("ReUsing the GeoTiff coverage : {}", geoTiffFilePath);
            return mapPathGridCoverage2d.get(geoTiffFilePath);
        }

        if (mapGeoTiffToGeoTiff4326.containsKey(geoTiffFilePath)) {
            String geoTiff4326FilePath = mapGeoTiffToGeoTiff4326.get(geoTiffFilePath);
            if (mapPathGridCoverage2d.containsKey(geoTiff4326FilePath)) {
                log.info("ReUsing the GeoTiff coverage 4326: {}", geoTiffFilePath);
                return mapPathGridCoverage2d.get(geoTiff4326FilePath);
            }
        }

        while (mapPathGridCoverage2d.size() > 4) {
            // delete the old coverage. Check the pathList. the 1rst is the oldest
            String oldestPath = pathList.get(0);
            GridCoverage2D oldestCoverage = mapPathGridCoverage2d.get(oldestPath);
            oldestCoverage.dispose(true);
            mapPathGridCoverage2d.remove(oldestPath);
            mapPathGridCoverage2dSize.remove(oldestPath);
            pathList.remove(0);
        }

        log.info("[Raster][I/O] loading the geoTiff file: {}", geoTiffFilePath);
        GridCoverage2D coverage = null;
        try {
            File file = new File(geoTiffFilePath);
            GeoTiffReader reader = new GeoTiffReader(file);
            coverage = reader.read(null);

            // Create a materialized copy of the coverage before disposing the reader
            // This prevents "Input not set!" errors when the RenderedImage tries to access disposed readers
            if (coverage != null) {
                coverage = materializeCoverage(coverage);
            }

            reader.dispose();
        } catch (Exception e) {
            // Check for WorldFile format
            File tfwFile = new File(geoTiffFilePath.replace(".tif", ".tfw"));
            File prjFile = new File(geoTiffFilePath.replace(".tif", ".prj"));

            if (tfwFile.exists() && prjFile.exists()) {
                log.info("[Raster][WorldFile] Detected WorldFile format, reading with sidecar files: {}", geoTiffFilePath);
                coverage = readWorldFileTiff(geoTiffFilePath, tfwFile, prjFile);
            } else {
                log.error("Failed to read GeoTIFF file: {}", geoTiffFilePath, e);
                log.error("WorldFile sidecars not found: .tfw exists={}, .prj exists={}", tfwFile.exists(), prjFile.exists());
                throw new RuntimeException("Failed to load GeoTIFF coverage: " + geoTiffFilePath, e);
            }
        }

        // Add null check before using coverage
        if (coverage == null) {
            throw new RuntimeException("GeoTIFF coverage is null after reading: " + geoTiffFilePath);
        }

        // save the coverage
        mapPathGridCoverage2d.put(geoTiffFilePath, coverage);
        pathList.add(geoTiffFilePath);

        // save the width and height of the coverage
        GridGeometry gridGeometry = coverage.getGridGeometry();
        int width = gridGeometry.getGridRange().getSpan(0);
        int height = gridGeometry.getGridRange().getSpan(1);
        Vector2i size = new Vector2i(width, height);
        mapPathGridCoverage2dSize.put(geoTiffFilePath, size);

        log.debug("Loaded the geoTiff file ok");
        return coverage;
    }

    public Vector2i getGridCoverage2DSize(String geoTiffFilePath) {
        if (!mapPathGridCoverage2dSize.containsKey(geoTiffFilePath)) {
            GridCoverage2D coverage = loadGeoTiffGridCoverage2D(geoTiffFilePath);
            coverage.dispose(true);
        }
        return mapPathGridCoverage2dSize.get(geoTiffFilePath);
    }

    public void deleteObjects() {
        for (GridCoverage2D coverage : mapPathGridCoverage2d.values()) {
            coverage.dispose(true);
        }
        mapPathGridCoverage2d.clear();

    }

    public GridCoverage2D getResizedCoverage2D(GridCoverage2D originalCoverage, double desiredPixelSizeXinMeters, double desiredPixelSizeYinMeters) throws FactoryException {
        GridCoverage2D resizedCoverage = null;

        GridGeometry originalGridGeometry = originalCoverage.getGridGeometry();
        Bounds envelopeOriginal = originalCoverage.getEnvelope();

        int gridSpanX = originalGridGeometry.getGridRange().getSpan(0); // num of pixels
        int gridSpanY = originalGridGeometry.getGridRange().getSpan(1); // num of pixels
        double[] envelopeSpanMeters = new double[2];
        GaiaGeoTiffUtils.getBoundsSpanInMetersOfGridCoverage2D(originalCoverage, envelopeSpanMeters);
        double envelopeSpanX = envelopeSpanMeters[0]; // in meters
        double envelopeSpanY = envelopeSpanMeters[1]; // in meters

        double desiredPixelsCountX = envelopeSpanX / desiredPixelSizeXinMeters;
        double desiredPixelsCountY = envelopeSpanY / desiredPixelSizeYinMeters;
        int minXSize = 24;
        int minYSize = 24;
        int desiredImageWidth = Math.max((int) desiredPixelsCountX, minXSize);
        int desiredImageHeight = Math.max((int) desiredPixelsCountY, minYSize);

        double scaleX = (double) desiredImageWidth / (double) gridSpanX;
        double scaleY = (double) desiredImageHeight / (double) gridSpanY;

        Operations ops = new Operations(null);
        resizedCoverage = (GridCoverage2D) ops.scale(originalCoverage, scaleX, scaleY, 0, 0);

        originalUpperLeftCorner[0] = envelopeOriginal.getMinimum(0);
        originalUpperLeftCorner[1] = envelopeOriginal.getMinimum(1);

        return resizedCoverage;
    }

    public void saveGridCoverage2D(GridCoverage2D coverage, String outputFilePath) throws IOException {
        // now save the newCoverage as geotiff
        File outputFile = new File(outputFilePath);
        try {
            FileOutputStream outputStream = new FileOutputStream(outputFile);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
            GeoTiffWriter writer = new GeoTiffWriter(bufferedOutputStream);
            writer.write(coverage, null);
            writer.dispose();
            outputStream.close();
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("Unable to map projection")) {
                // IAU projection encoding failure - use WorldFile fallback
                log.warn("[Raster][I/O] GeoTIFF encoding failed for IAU projection, using WorldFile fallback: {}", outputFile.getName());
                writeGeotiffWithWorldFile(coverage, outputFile);
            } else {
                log.error("Failed to write GeoTiff file : {}", outputFile.getAbsolutePath());
                log.error("Error : ", e);
                throw e;
            }
        } catch (Exception e) {
            log.error("Failed to write GeoTiff file : {}", outputFile.getAbsolutePath());
            log.error("Error : ", e);
            throw new IOException("Failed to write GeoTiff", e);
        }
    }

    /**
     * Writes a GeoTIFF with WorldFile sidecar when CRS cannot be encoded internally.
     * Used for IAU projections that GeoTIFF format doesn't support.
     *
     * @param coverage GridCoverage2D to write
     * @param outputFile Output GeoTIFF file path
     */
    private void writeGeotiffWithWorldFile(GridCoverage2D coverage, File outputFile) {
        try {
            // Extract geotransformation parameters FIRST (needed for WorldFile)
            GridGeometry2D gridGeometry = coverage.getGridGeometry();
            Bounds envelope = coverage.getEnvelope();
            GridEnvelope gridRange = gridGeometry.getGridRange();

            double minX = envelope.getMinimum(0);
            double maxY = envelope.getMaximum(1);

            // Step 1: Get raw image data from coverage
            RenderedImage image = coverage.getRenderedImage();

            // Step 2: Write TIFF using JAI ImageWriter (no CRS validation, proper stream handling)
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("TIFF");
            if (!writers.hasNext()) {
                throw new IOException("No TIFF ImageWriter found");
            }

            ImageWriter imageWriter = writers.next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile)) {
                imageWriter.setOutput(ios);
                imageWriter.write(image);
                ios.flush();
            } finally {
                imageWriter.dispose();
            }

            // Step 3: Write WorldFile (.tfw)
            File worldFile = new File(
                outputFile.getParentFile(),
                outputFile.getName().replace(".tif", ".tfw")
            );

            // Calculate WorldFile parameters
            int width = gridRange.getSpan(0);
            int height = gridRange.getSpan(1);

            double pixelSizeX = envelope.getSpan(0) / width;
            double pixelSizeY = -envelope.getSpan(1) / height; // Negative for north-up
            double upperLeftX = minX + (pixelSizeX / 2.0); // Center of pixel
            double upperLeftY = maxY + (pixelSizeY / 2.0);

            // Write WorldFile (6 lines)
            try (PrintWriter tfwWriter = new PrintWriter(worldFile)) {
                tfwWriter.println(String.format(Locale.US, "%.10f", pixelSizeX));    // Line 1
                tfwWriter.println("0.0");                                              // Line 2 (rotation)
                tfwWriter.println("0.0");                                              // Line 3 (rotation)
                tfwWriter.println(String.format(Locale.US, "%.10f", pixelSizeY));    // Line 4
                tfwWriter.println(String.format(Locale.US, "%.10f", upperLeftX));    // Line 5
                tfwWriter.println(String.format(Locale.US, "%.10f", upperLeftY));    // Line 6
            }

            // Step 4: Write CRS to separate .prj file
            File prjFile = new File(
                outputFile.getParentFile(),
                outputFile.getName().replace(".tif", ".prj")
            );
            try (PrintWriter prjWriter = new PrintWriter(prjFile)) {
                prjWriter.println(coverage.getCoordinateReferenceSystem().toWKT());
            }

            log.info("[Raster][WorldFile] Wrote GeoTIFF with WorldFile fallback: {}", outputFile.getName());
            log.debug("  -> Created valid TIFF with .tfw and .prj sidecar files for IAU projection");

        } catch (Exception e) {
            log.error("Failed to write GeoTiff with WorldFile : {}", outputFile.getAbsolutePath());
            log.error("Error : ", e);
            throw new RuntimeException("WorldFile write failed", e);
        }
    }

    /**
     * Reads a TIFF file with WorldFile sidecar files.
     * Tries GeoTools auto-detection first, then falls back to manual construction.
     *
     * @param tiffPath Path to the TIFF file
     * @param tfwFile WorldFile (.tfw) with geotransform parameters
     * @param prjFile Projection file (.prj) with CRS definition
     * @return GridCoverage2D containing image and georeferencing
     */
    private GridCoverage2D readWorldFileTiff(String tiffPath, File tfwFile, File prjFile) {
        try {
            File tiffFile = new File(tiffPath);

            // Strategy 1: Try GeoTools auto-detection
            try {
                AbstractGridFormat format = GridFormatFinder.findFormat(tiffFile);
                if (format != null && format.accepts(tiffFile)) {
                    GridCoverage2DReader reader = format.getReader(tiffFile);
                    GridCoverage2D coverage = reader.read(null);

                    // Create a materialized copy before disposing the reader
                    if (coverage != null) {
                        coverage = materializeCoverage(coverage);
                    }

                    reader.dispose();
                    log.debug("[Raster][WorldFile] Successfully read using GridFormatFinder");
                    return coverage;
                }
            } catch (Exception e) {
                log.debug("[Raster][WorldFile] Auto-detection failed, trying manual approach", e);
            }

            // Strategy 2: Manual WorldFile construction
            return readWorldFileManual(tiffFile, tfwFile, prjFile);

        } catch (Exception e) {
            log.error("Failed to read WorldFile TIFF: {}", tiffPath, e);
            throw new RuntimeException("WorldFile read failed: " + tiffPath, e);
        }
    }

    /**
     * Manually constructs a GridCoverage2D from TIFF + WorldFile + PRJ files.
     * Used as fallback when auto-detection fails.
     *
     * @param tiffFile The TIFF image file
     * @param tfwFile WorldFile with geotransform
     * @param prjFile Projection file with CRS
     * @return GridCoverage2D
     * @throws IOException If file reading fails
     * @throws FactoryException If CRS parsing fails
     */
    private GridCoverage2D readWorldFileManual(File tiffFile, File tfwFile, File prjFile)
            throws IOException, FactoryException {
        // Read plain TIFF as RenderedImage
        BufferedImage image = ImageIO.read(tiffFile);
        if (image == null) {
            throw new IOException("Failed to read TIFF image: " + tiffFile);
        }

        // Read WorldFile geotransform
        WorldFileReader wfReader = new WorldFileReader(tfwFile);
        AffineTransform transform = (AffineTransform) wfReader.getTransform();

        // Read CRS from .prj file
        CoordinateReferenceSystem crs;
        if (prjFile.exists()) {
            String wkt = new String(Files.readAllBytes(prjFile.toPath()), StandardCharsets.UTF_8);
            crs = CRS.parseWKT(wkt);
            log.debug("[Raster][WorldFile] Parsed CRS from .prj file: {}", crs.getName());
        } else {
            log.warn("[Raster][WorldFile] Missing .prj file, using configured CRS");
            crs = GlobalOptions.getInstance().getOutputCRS();
        }

        // Calculate envelope from WorldFile transform
        int width = image.getWidth();
        int height = image.getHeight();

        double pixelSizeX = transform.getScaleX();
        double pixelSizeY = -transform.getScaleY(); // Convert from negative
        double upperLeftX = transform.getTranslateX();
        double upperLeftY = transform.getTranslateY();

        // Convert pixel-center to pixel-corner coordinates
        double minX = upperLeftX - (pixelSizeX / 2.0);
        double maxY = upperLeftY - (pixelSizeY / 2.0);
        double maxX = minX + (width * pixelSizeX);
        double minY = maxY - (height * Math.abs(pixelSizeY));

        ReferencedEnvelope envelope = new ReferencedEnvelope(minX, maxX, minY, maxY, crs);

        // Create GridCoverage2D
        GridCoverageFactory factory = new GridCoverageFactory();
        GridCoverage2D coverage = factory.create(tiffFile.getName(), (RenderedImage) image, envelope);

        log.info("[Raster][WorldFile] Successfully read WorldFile TIFF manually: {} ({}x{} pixels)",
                 tiffFile.getName(), width, height);

        return coverage;
    }

    /**
     * Creates a materialized copy of a GridCoverage2D with all raster data loaded into memory.
     * This prevents "Input not set!" errors when readers are disposed before the data is accessed.
     *
     * @param coverage The original GridCoverage2D (may have lazy-loading RenderedImage)
     * @return A new GridCoverage2D with all data materialized in memory
     */
    private GridCoverage2D materializeCoverage(GridCoverage2D coverage) {
        try {
            // Get the rendered image and force all data to be loaded
            RenderedImage originalImage = coverage.getRenderedImage();

            // Create a Raster with all the data materialized
            // This copies all pixel data into memory, independent of the original reader
            java.awt.image.Raster raster = originalImage.getData();

            // Create a WritableRaster from the data
            java.awt.image.WritableRaster writableRaster = raster.createCompatibleWritableRaster();
            writableRaster.setRect(raster);

            // Create a BufferedImage with the materialized data
            java.awt.image.ColorModel colorModel = originalImage.getColorModel();
            BufferedImage bufferedImage = new BufferedImage(
                colorModel,
                writableRaster,
                colorModel.isAlphaPremultiplied(),
                null
            );

            // Create a new GridCoverage2D with the materialized BufferedImage
            GridCoverageFactory factory = new GridCoverageFactory();
            GridCoverage2D materializedCoverage = factory.create(
                coverage.getName().toString(),
                bufferedImage,
                coverage.getEnvelope()
            );

            return materializedCoverage;
        } catch (Exception e) {
            log.warn("[Raster] Failed to materialize coverage, using original: {}", e.getMessage());
            return coverage;
        }
    }
}
