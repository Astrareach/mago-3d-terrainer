package com.gaia3d.terrain.tile.geotiff;

import com.gaia3d.command.GlobalOptions;
import it.geosolutions.jaiext.JAIExt;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.processing.CoverageProcessor;
import org.geotools.coverage.processing.Operations;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.api.coverage.grid.GridEnvelope;
import org.geotools.api.coverage.processing.Operation;
import org.geotools.api.geometry.Bounds;
import org.geotools.api.parameter.ParameterValueGroup;
import org.geotools.api.referencing.ReferenceIdentifier;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.TransformException;

import org.eclipse.imagen.Interpolation;
import javax.media.jai.JAI;
import javax.media.jai.TileCache;
import javax.media.jai.TileScheduler;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * RasterStandardizer
 * This Class for Standardization data CRS and size.
 */
@Slf4j
@NoArgsConstructor
public class RasterStandardizer {

    static {
        JAIExt.registerJAIDescriptor("Warp");
        JAIExt.registerJAIDescriptor("Affine");
        JAIExt.registerJAIDescriptor("Rescale");
        JAIExt.registerJAIDescriptor("Warp/Affine");

        JAIExt.initJAIEXT();

        JAI jaiInstance = JAI.getDefaultInstance();
        TileCache tileCache = jaiInstance.getTileCache();
        tileCache.setMemoryCapacity(1024 * 1024 * 1024); // 512MB
        tileCache.setMemoryThreshold(0.75f);

        TileScheduler tileScheduler = jaiInstance.getTileScheduler();
        // availableProcessors = Runtime.getRuntime().availableProcessors();
        tileScheduler.setParallelism(Runtime.getRuntime().availableProcessors());
        tileScheduler.setPriority(Thread.NORM_PRIORITY);
    }

    private final GlobalOptions globalOptions = GlobalOptions.getInstance();

    public void standardize(GridCoverage2D source, File outputPath) {
        CoordinateReferenceSystem targetCRS = globalOptions.getOutputCRS();
        try {
            /* split */
            List<RasterInfo> splitTiles = split(source, globalOptions.getMaxRasterSize());

            /* resampling */
            ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
            List<RasterInfo> resampledTiles = pool.submit(() -> splitTiles.parallelStream().map(tile -> {
                        GridCoverage2D gridCoverage2D = tile.getGridCoverage2D();
                        CoordinateReferenceSystem sourceCRS = gridCoverage2D.getCoordinateReferenceSystem();
                        boolean sameCRS = isSameCRS(sourceCRS, targetCRS);

                        log.info("[Resample] Source CRS: {}", sourceCRS.getName());
                        log.info("[Resample] Target CRS: {}", targetCRS.getName());
                        log.info("[Resample] Same CRS: {}", sameCRS);

                        if (sameCRS) {
                            log.info("[Resample] Skipping resampling for tile: {}", tile.getName());
                            return tile;
                        } else {
                            log.info("[Resample] Resampling tile: {} from {} to {}", tile.getName(), sourceCRS.getName(), targetCRS.getName());
                            tile.setGridCoverage2D(resample(gridCoverage2D, targetCRS));
                        }
                        return tile;
                    }
            ).collect(Collectors.toList())).get();

            /* save */
            /*int total = resampledTiles.size();
            AtomicInteger count = new AtomicInteger(0);
            GaiaThreadPool gaiaThreadPool = new GaiaThreadPool();
            List<Runnable> tasks = resampledTiles.stream().map(tile -> (Runnable) () -> {
                int index = count.incrementAndGet();
                GridCoverage2D reprojectedTile = tile.getGridCoverage2D();
                File tileFile = new File(outputPath, tile.getName() + ".tif");

                log.info("[Pre][Standardization][{}/{}] : {}", index, total, tileFile.getAbsolutePath());
                writeGeotiff(reprojectedTile, tileFile);
            }).collect(Collectors.toList());
            gaiaThreadPool.execute(tasks);*/

            int total = resampledTiles.size();
            AtomicInteger count = new AtomicInteger(0);
            resampledTiles.stream().forEach((tile) -> {
                int index = count.incrementAndGet();
                GridCoverage2D reprojectedTile = tile.getGridCoverage2D();
                File tileFile = new File(outputPath, tile.getName() + ".tif");

                log.info("[Pre][Standardization][{}/{}] : {}", index, total, tileFile.getAbsolutePath());
                writeGeotiff(reprojectedTile, tileFile);
            });

        } catch (TransformException | IOException | InterruptedException | ExecutionException e) {
            log.error("Failed to standardization.", e);
            throw new RuntimeException(e);
        }
    }

    public GridCoverage2D readGeoTiff(File file) {
        try {
            GeoTiffReader reader = new GeoTiffReader(file);
            return reader.read(null);
        } catch (Exception e) {
            log.error("Failed to read GeoTiff file : {}", file.getAbsolutePath());
            log.error("Error : ", e);
            throw new RuntimeException(e);
        }
    }

    public void writeGeotiff(GridCoverage2D coverage, File outputFile) {
        try {
            if (outputFile.exists() && outputFile.length() > 0) {
                log.info("[Raster][I/O] File already exists and not Empty : {}", outputFile.getAbsolutePath());
                return;
            }
            FileOutputStream outputStream = new FileOutputStream(outputFile);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
            GeoTiffWriter writer = new GeoTiffWriter(bufferedOutputStream);
            writer.write(coverage, null);
            writer.dispose();
            outputStream.close();
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("Unable to map projection")) {
                // IAU projection encoding failure, use WorldFile fallback
                log.warn("[Raster][I/O] GeoTIFF encoding failed for IAU projection, using WorldFile fallback: {}", outputFile.getName());
                writeGeotiffWithWorldFile(coverage, outputFile);
            } else {
                log.error("Failed to write GeoTiff file : {}", outputFile.getAbsolutePath());
                log.error("Error : ", e);
            }
        } catch (Exception e) {
            log.error("Failed to write GeoTiff file : {}", outputFile.getAbsolutePath());
            log.error("Error : ", e);
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

    @Deprecated
    public void getImageBuffer(GridCoverage2D coverage) {
        RenderedImage image = coverage.getRenderedImage();
        Raster raster = image.getData();
        int width = raster.getWidth();
        int height = raster.getHeight();
        float[] pixels = new float[width * height];

        int minX = raster.getMinX();
        int minY = raster.getMinY();
        raster.getPixels(minX, minY, width, height, pixels);
    }

    public List<RasterInfo> split(GridCoverage2D coverage, int tileSize) throws TransformException, IOException {
        List<RasterInfo> tiles = new ArrayList<>();

        GridGeometry2D gridGeometry = coverage.getGridGeometry();
        GridEnvelope gridRange = gridGeometry.getGridRange();
        int width = gridRange.getSpan(0);
        int height = gridRange.getSpan(1);

        int margin = 4; // 4 pixel margin
        int marginX = Math.max((int) (tileSize * 0.01), margin);
        int marginY = Math.max((int) (tileSize * 0.01), margin);
        for (int x = 0; x < width; x += tileSize) {
            for (int y = 0; y < height; y += tileSize) {
                int xMax = Math.min(x + tileSize, width);
                int yMax = Math.min(y + tileSize, height);

                // when the tile is not at the edge, add margin
                if ((x + tileSize) < width) {
                    xMax += marginX;
                }
                if ((y + tileSize) < height) {
                    yMax += marginY;
                }

                int xAux = x;
                if (xAux > marginX) {
                    xAux -= marginX;
                }

                int yAux = y;
                if (yAux > marginY) {
                    yAux -= marginY;
                }

                ReferencedEnvelope tileEnvelope = new ReferencedEnvelope(gridGeometry.gridToWorld(new GridEnvelope2D(xAux, yAux, xMax - x, yMax - y)), coverage.getCoordinateReferenceSystem());
                GridCoverage2D gridCoverage2D = crop(coverage, tileEnvelope);
                String tileName = gridCoverage2D.getName() + "-" + x / tileSize + "-" + y / tileSize;
                RasterInfo tile = new RasterInfo(tileName, gridCoverage2D);
                tiles.add(tile);
            }
        }
        return tiles;
    }

    public GridCoverage2D crop(GridCoverage2D coverage, ReferencedEnvelope envelope) {
        try {
            Operations ops = Operations.DEFAULT;
            return (GridCoverage2D) ops.crop(coverage, envelope);
        } catch (Exception e) {
            log.error("Failed to crop coverage : {}", coverage.getName());
            log.error("Error : ", e);
            throw new RuntimeException("Failed to crop coverage", e);
        }
    }

    public GridCoverage2D resample(GridCoverage2D sourceCoverage, CoordinateReferenceSystem targetCRS) {
        try {
            CoverageProcessor.updateProcessors();
            CoverageProcessor processor = CoverageProcessor.getInstance();

            Operation operation = processor.getOperation("Resample");
            ParameterValueGroup params = operation.getParameters();
            params.parameter("Source").setValue(sourceCoverage);
            params.parameter("CoordinateReferenceSystem").setValue(targetCRS);
            params.parameter("InterpolationType").setValue(Interpolation.getInstance(Interpolation.INTERP_NEAREST)); // INTERP_BILINEAR
            /*params.parameter("BackgroundValues").setValue(new double[] {0.0});*/

            return (GridCoverage2D) processor.doOperation(params);
        } catch (Exception e) {
            log.error("Failed to reproject tile : {}", sourceCoverage.getName());
            log.error("Error : ", e);
            return sourceCoverage;
        }
    }

    public boolean isSameCRS(CoordinateReferenceSystem sourceCRS, CoordinateReferenceSystem targetCRS) {
        // Quick check: if they're the exact same object
        if (sourceCRS == targetCRS) {
            return true;
        }

        // First, try comparing by identifier (authority + code)
        Iterator<ReferenceIdentifier> sourceCRSIterator = sourceCRS.getIdentifiers().iterator();
        Iterator<ReferenceIdentifier> targetCRSIterator = targetCRS.getIdentifiers().iterator();

        if (sourceCRSIterator.hasNext() && targetCRSIterator.hasNext()) {
            ReferenceIdentifier sourceId = sourceCRSIterator.next();
            ReferenceIdentifier targetId = targetCRSIterator.next();

            // Check both authority and code (important for IAU vs EPSG)
            String sourceAuthority = sourceId.getCodeSpace();
            String targetAuthority = targetId.getCodeSpace();
            String sourceCode = sourceId.getCode();
            String targetCode = targetId.getCode();

            if (sourceAuthority != null && targetAuthority != null) {
                boolean matches = sourceAuthority.equals(targetAuthority) && sourceCode.equals(targetCode);
                if (matches) {
                    return true;
                }
            } else if (sourceCode.equals(targetCode)) {
                return true;
            }
        }

        // Fallback: use GeoTools CRS comparison (ignores metadata like name)
        // This handles cases where CRS names differ but definitions are equivalent
        try {
            boolean equals = org.geotools.referencing.CRS.equalsIgnoreMetadata(sourceCRS, targetCRS);
            if (!equals) {
                // Additional check for IAU lunar CRS, if both refer to Moon with same datum, consider them equal
                String sourceName = sourceCRS.getName().getCode();
                String targetName = targetCRS.getName().getCode();
                if ((sourceName.contains("Moon") || sourceName.contains("Lunar")) &&
                    (targetName.contains("Moon") || targetName.contains("Lunar"))) {
                    // Both are lunar CRSs, compare WKT to be sure
                    String sourceWKT = sourceCRS.toWKT();
                    String targetWKT = targetCRS.toWKT();
                    // Check if both have same spheroid (1737400m for Moon)
                    boolean sameSphere = sourceWKT.contains("1737400") && targetWKT.contains("1737400");
                    if (sameSphere) {
                        log.info("[CRS] Both are IAU lunar CRSs with same datum, treating as equal");
                        return true;
                    }
                }
            }
            return equals;
        } catch (Exception e) {
            log.warn("Failed to compare CRS, assuming different: {}", e.getMessage());
            return false;
        }
    }
}
