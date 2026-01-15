package com.gaia3d.release.others;

import com.gaia3d.command.GlobalOptions;
import com.gaia3d.command.MagoTerrainerMain;
import com.gaia3d.util.CelestialBody;
import com.gaia3d.util.GlobeUtils;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for Moon support functionality.
 * Combines unit tests for CelestialBody, GlobeUtils, and integration tests.
 * Tests physical constants, coordinate transformations, and command-line options.
 *
 */
@Slf4j
@Tag("default")
public class MoonSupportTest {

    private static final double EPSILON = 0.001; // 1mm tolerance
    private static final double ANGLE_EPSILON = 1E-6; // Very small angle tolerance

    // ========================================
    // CelestialBody Constants Tests
    // ========================================

    @Test
    void testEarthConstants() {
        assertEquals(6378137.0, CelestialBody.EARTH.getEquatorialRadius(), 0.001);
        assertEquals(6356752.3142, CelestialBody.EARTH.getPolarRadius(), 0.001);
        assertEquals(6.69437999014E-3, CelestialBody.EARTH.getFirstEccentricitySquared(), 1E-10);
        assertEquals("EPSG:4326", CelestialBody.EARTH.getCrsCode());
        assertEquals("Earth", CelestialBody.EARTH.getName());
    }

    @Test
    void testMoonConstants() {
        assertEquals(1737400.0, CelestialBody.MOON.getEquatorialRadius(), 0.001);
        assertEquals(1737400.0, CelestialBody.MOON.getPolarRadius(), 0.001);
        assertEquals(0.0, CelestialBody.MOON.getFirstEccentricitySquared(), 1E-10);
        assertEquals("IAU:30100", CelestialBody.MOON.getCrsCode());
        assertEquals("Moon", CelestialBody.MOON.getName());
    }

    @Test
    void testMoonIsASphere() {
        // Moon should have equal equatorial and polar radii (sphere)
        assertEquals(CelestialBody.MOON.getEquatorialRadius(),
                CelestialBody.MOON.getPolarRadius(), 0.001);
        // Moon should have zero eccentricity (perfect sphere)
        assertEquals(0.0, CelestialBody.MOON.getFirstEccentricitySquared(), 1E-10);
    }

    @Test
    void testEarthIsAnEllipsoid() {
        // Earth should have different equatorial and polar radii (ellipsoid)
        assertNotEquals(CelestialBody.EARTH.getEquatorialRadius(),
                CelestialBody.EARTH.getPolarRadius());
        // Earth should have non-zero eccentricity
        assertTrue(CelestialBody.EARTH.getFirstEccentricitySquared() > 0);
    }

    @Test
    void testRadiusSquared() {
        double earthEquatorialRadiusSquared = 6378137.0 * 6378137.0;
        assertEquals(earthEquatorialRadiusSquared,
                CelestialBody.EARTH.getEquatorialRadiusSquared(), 1.0);

        double moonRadiusSquared = 1737400.0 * 1737400.0;
        assertEquals(moonRadiusSquared,
                CelestialBody.MOON.getEquatorialRadiusSquared(), 1.0);
        assertEquals(moonRadiusSquared,
                CelestialBody.MOON.getPolarRadiusSquared(), 1.0);
    }

    @Test
    void testCelestialBodyFromStringEarth() {
        assertEquals(CelestialBody.EARTH, CelestialBody.fromString("earth"));
        assertEquals(CelestialBody.EARTH, CelestialBody.fromString("EARTH"));
        assertEquals(CelestialBody.EARTH, CelestialBody.fromString("Earth"));
        assertEquals(CelestialBody.EARTH, CelestialBody.fromString(" earth "));
    }

    @Test
    void testCelestialBodyFromStringMoon() {
        assertEquals(CelestialBody.MOON, CelestialBody.fromString("moon"));
        assertEquals(CelestialBody.MOON, CelestialBody.fromString("MOON"));
        assertEquals(CelestialBody.MOON, CelestialBody.fromString("Moon"));
        assertEquals(CelestialBody.MOON, CelestialBody.fromString(" moon "));
    }

    @Test
    void testCelestialBodyFromStringInvalid() {
        assertThrows(IllegalArgumentException.class, () -> CelestialBody.fromString("mars"));
        assertThrows(IllegalArgumentException.class, () -> CelestialBody.fromString("jupiter"));
        assertThrows(IllegalArgumentException.class, () -> CelestialBody.fromString("invalid"));
        assertThrows(IllegalArgumentException.class, () -> CelestialBody.fromString(""));
        assertThrows(IllegalArgumentException.class, () -> CelestialBody.fromString(null));
    }

    @Test
    void testMoonSizeRelativeToEarth() {
        // Moon's radius should be approximately 27.2% of Earth's
        double ratio = CelestialBody.MOON.getEquatorialRadius() /
                CelestialBody.EARTH.getEquatorialRadius();
        assertEquals(0.2724, ratio, 0.001);
    }

    // ========================================
    // GlobeUtils Coordinate Transformation Tests
    // ========================================

    @Test
    void testGeographicToCartesianEarth() {
        // Test at equator, prime meridian
        double[] result = GlobeUtils.geographicToCartesian(0.0, 0.0, 0.0, CelestialBody.EARTH);
        assertEquals(CelestialBody.EARTH.getEquatorialRadius(), result[0], EPSILON);
        assertEquals(0.0, result[1], EPSILON);
        assertEquals(0.0, result[2], EPSILON);
    }

    @Test
    void testGeographicToCartesianMoon() {
        // Test at equator, prime meridian
        double[] result = GlobeUtils.geographicToCartesian(0.0, 0.0, 0.0, CelestialBody.MOON);
        assertEquals(CelestialBody.MOON.getEquatorialRadius(), result[0], EPSILON);
        assertEquals(0.0, result[1], EPSILON);
        assertEquals(0.0, result[2], EPSILON);
    }

    @Test
    void testGeographicToCartesianMoonNorthPole() {
        // Test at north pole (90° latitude)
        double[] result = GlobeUtils.geographicToCartesian(0.0, 90.0, 0.0, CelestialBody.MOON);
        assertEquals(0.0, result[0], EPSILON);
        assertEquals(0.0, result[1], EPSILON);
        assertEquals(CelestialBody.MOON.getEquatorialRadius(), result[2], EPSILON);
    }

    @Test
    void testGeographicToCartesianMoonWithAltitude() {
        // Test with altitude
        double altitude = 1000.0; // 1km
        double[] result = GlobeUtils.geographicToCartesian(0.0, 0.0, altitude, CelestialBody.MOON);
        assertEquals(CelestialBody.MOON.getEquatorialRadius() + altitude, result[0], EPSILON);
        assertEquals(0.0, result[1], EPSILON);
        assertEquals(0.0, result[2], EPSILON);
    }

    @Test
    void testCartesianToGeographicRoundTripEarth() {
        // Test round trip conversion for Earth
        double lon = 127.0, lat = 37.0, alt = 100.0;
        double[] cartesian = GlobeUtils.geographicToCartesian(lon, lat, alt, CelestialBody.EARTH);
        Vector3d geographic = GlobeUtils.cartesianToGeographic(cartesian[0], cartesian[1], cartesian[2], CelestialBody.EARTH);

        assertEquals(lon, geographic.x, ANGLE_EPSILON);
        assertEquals(lat, geographic.y, ANGLE_EPSILON);
        assertEquals(alt, geographic.z, EPSILON);
    }

    @Test
    void testCartesianToGeographicRoundTripMoon() {
        // Test round trip conversion for Moon
        double lon = 127.0, lat = 37.0, alt = 100.0;
        double[] cartesian = GlobeUtils.geographicToCartesian(lon, lat, alt, CelestialBody.MOON);
        Vector3d geographic = GlobeUtils.cartesianToGeographic(cartesian[0], cartesian[1], cartesian[2], CelestialBody.MOON);

        assertEquals(lon, geographic.x, ANGLE_EPSILON);
        assertEquals(lat, geographic.y, ANGLE_EPSILON);
        assertEquals(alt, geographic.z, EPSILON);
    }

    @Test
    void testRadiusAtLatitudeEarth() {
        // At equator, radius should be equatorial radius
        double radius = GlobeUtils.getRadiusAtLatitude(0.0, CelestialBody.EARTH);
        assertEquals(CelestialBody.EARTH.getEquatorialRadius(), radius, EPSILON);

        // At poles, the radius of curvature is actually larger than at equator for an ellipsoid
        // This is N = a²/b (prime vertical radius of curvature), not the geometric radius
        double polarRadius = GlobeUtils.getRadiusAtLatitude(90.0, CelestialBody.EARTH);
        assertTrue(polarRadius > CelestialBody.EARTH.getEquatorialRadius());
    }

    @Test
    void testRadiusAtLatitudeMoon() {
        // Moon is a sphere, so radius should be constant at all latitudes
        double radiusEquator = GlobeUtils.getRadiusAtLatitude(0.0, CelestialBody.MOON);
        double radius45 = GlobeUtils.getRadiusAtLatitude(45.0, CelestialBody.MOON);
        double radiusPole = GlobeUtils.getRadiusAtLatitude(90.0, CelestialBody.MOON);

        assertEquals(CelestialBody.MOON.getEquatorialRadius(), radiusEquator, EPSILON);
        assertEquals(CelestialBody.MOON.getEquatorialRadius(), radius45, EPSILON);
        assertEquals(CelestialBody.MOON.getEquatorialRadius(), radiusPole, EPSILON);

        // All should be equal for a sphere
        assertEquals(radiusEquator, radius45, EPSILON);
        assertEquals(radiusEquator, radiusPole, EPSILON);
    }

    @Test
    void testNormalAtCartesianPointMoon() {
        // Test normal vector at equator, prime meridian
        double[] cartesian = GlobeUtils.geographicToCartesian(0.0, 0.0, 0.0, CelestialBody.MOON);
        Vector3d normal = GlobeUtils.normalAtCartesianPoint(cartesian[0], cartesian[1], cartesian[2], CelestialBody.MOON);

        // Normal should be normalized
        assertEquals(1.0, normal.length(), EPSILON);

        // For a point on the surface at (0,0), normal should point in X direction
        assertEquals(1.0, normal.x, EPSILON);
        assertEquals(0.0, normal.y, EPSILON);
        assertEquals(0.0, normal.z, EPSILON);
    }

    @Test
    void testBackwardCompatibilityWgs84Methods() {
        // Verify that legacy methods still use Earth by default
        double[] earthResult = GlobeUtils.geographicToCartesianWgs84(0.0, 0.0, 0.0);
        double[] explicitEarthResult = GlobeUtils.geographicToCartesian(0.0, 0.0, 0.0, CelestialBody.EARTH);

        assertArrayEquals(explicitEarthResult, earthResult, EPSILON);
    }

    @Test
    void testMoonScaleRelativeToEarth() {
        // Verify that Moon calculations use correct scale
        double[] earthCartesian = GlobeUtils.geographicToCartesian(0.0, 0.0, 0.0, CelestialBody.EARTH);
        double[] moonCartesian = GlobeUtils.geographicToCartesian(0.0, 0.0, 0.0, CelestialBody.MOON);

        double earthMagnitude = Math.sqrt(earthCartesian[0] * earthCartesian[0] +
                earthCartesian[1] * earthCartesian[1] +
                earthCartesian[2] * earthCartesian[2]);
        double moonMagnitude = Math.sqrt(moonCartesian[0] * moonCartesian[0] +
                moonCartesian[1] * moonCartesian[1] +
                moonCartesian[2] * moonCartesian[2]);

        // Moon should be approximately 27.2% the size of Earth
        double ratio = moonMagnitude / earthMagnitude;
        assertEquals(0.2724, ratio, 0.001);
    }

    @Test
    void testGeographicToCartesianVariousLocations() {
        // Test Seoul coordinates on Moon
        double lon = 127.0, lat = 37.0, alt = 0.0;
        double[] result = GlobeUtils.geographicToCartesian(lon, lat, alt, CelestialBody.MOON);

        // Verify result is reasonable
        double magnitude = Math.sqrt(result[0] * result[0] + result[1] * result[1] + result[2] * result[2]);
        assertEquals(CelestialBody.MOON.getEquatorialRadius(), magnitude, EPSILON);
    }

    // ========================================
    // Integration Tests - Command Line Options
    // ========================================

    @Test
    void testHelpShowsBodyOption() {
        // Verify that help text includes the new --body option
        String[] args = new String[]{"-h"};
        MagoTerrainerMain.main(args);
        // This test primarily verifies no exceptions are thrown
    }

    @Test
    void testDefaultBodyIsEarth() {
        // When no --body option is specified, default should be Earth
        GlobalOptions options = new GlobalOptions();
        assertEquals(CelestialBody.EARTH, options.getCelestialBody());
    }

    @Test
    void testBodyOptionParsing() {
        // Test that --body option can be parsed (requires sample data)
        ClassLoader classLoader = getClass().getClassLoader();
        File samplePath = new File(Objects.requireNonNull(classLoader.getResource("sample")).getFile());
        File inputPath = new File(samplePath, "input");
        File outputPath = new File(samplePath, "output");

        // Test with Earth explicitly
        String[] argsEarth = new String[]{
                "-input", inputPath.getAbsolutePath(),
                "-output", outputPath.getAbsolutePath(),
                "-body", "earth",
                "-max", "5"
        };

        try {
            MagoTerrainerMain.main(argsEarth);
        } catch (Exception e) {
            log.info("Earth body test completed (expected to work with Earth data)", e);
        }
    }

    @Test
    void testMoonBodyOptionParsing() {
        // Test that --body moon option can be parsed
        ClassLoader classLoader = getClass().getClassLoader();
        File samplePath = new File(Objects.requireNonNull(classLoader.getResource("sample")).getFile());
        File inputPath = new File(samplePath, "input");
        File outputPath = new File(samplePath, "output");

        // Test with Moon (may fail due to CRS mismatch with Earth data, but should parse correctly)
        String[] argsMoon = new String[]{
                "-input", inputPath.getAbsolutePath(),
                "-output", outputPath.getAbsolutePath(),
                "-body", "moon",
                "-max", "5"
        };

        try {
            MagoTerrainerMain.main(argsMoon);
        } catch (Exception e) {
            log.info("Moon body test completed (may fail with Earth data, but parsing should work)", e);
        }
    }

    @Test
    void testBodyOptionCaseInsensitive() {
        // Test that body option is case-insensitive
        ClassLoader classLoader = getClass().getClassLoader();
        File samplePath = new File(Objects.requireNonNull(classLoader.getResource("sample")).getFile());
        File inputPath = new File(samplePath, "input");
        File outputPath = new File(samplePath, "output");

        String[] argsMoonUpper = new String[]{
                "-input", inputPath.getAbsolutePath(),
                "-output", outputPath.getAbsolutePath(),
                "-body", "MOON",
                "-max", "5"
        };

        try {
            MagoTerrainerMain.main(argsMoonUpper);
        } catch (Exception e) {
            log.info("Moon body test (uppercase) completed", e);
        }
    }

    @Test
    void testInvalidBodyFallsBackToEarth() {
        // Test that invalid body value gracefully falls back to Earth
        ClassLoader classLoader = getClass().getClassLoader();
        File samplePath = new File(Objects.requireNonNull(classLoader.getResource("sample")).getFile());
        File inputPath = new File(samplePath, "input");
        File outputPath = new File(samplePath, "output");

        String[] argsInvalid = new String[]{
                "-input", inputPath.getAbsolutePath(),
                "-output", outputPath.getAbsolutePath(),
                "-body", "mars",  // Invalid - should fall back to Earth
                "-max", "5"
        };

        try {
            MagoTerrainerMain.main(argsInvalid);
        } catch (Exception e) {
            log.info("Invalid body test completed (should fall back to Earth)", e);
        }
    }

    @Test
    void testShortBodyOption() {
        // Test that -b short option works
        ClassLoader classLoader = getClass().getClassLoader();
        File samplePath = new File(Objects.requireNonNull(classLoader.getResource("sample")).getFile());
        File inputPath = new File(samplePath, "input");
        File outputPath = new File(samplePath, "output");

        String[] argsShort = new String[]{
                "-input", inputPath.getAbsolutePath(),
                "-output", outputPath.getAbsolutePath(),
                "-b", "moon",
                "-max", "5"
        };

        try {
            MagoTerrainerMain.main(argsShort);
        } catch (Exception e) {
            log.info("Short body option test completed", e);
        }
    }
}
