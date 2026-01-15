package com.gaia3d.util;

import lombok.Getter;

/**
 * Enum representing celestial bodies with their physical constants and coordinate reference systems.
 * Supports terrain generation for different planetary bodies.
 * 
 * This implementation uses GeoTools 34+ with native IAU authority support via the `gt-iau-wkt`
 * plugin, allowing direct access to IAU coordinate systems such as `IAU:30100` for the Moon.
 */
@Getter
public enum CelestialBody {
    /**
     * Earth - WGS84 ellipsoid model
     */
    EARTH(
        "Earth",
        6378137.0,           // Equatorial radius in meters
        6356752.3142,        // Polar radius in meters
        6.69437999014E-3,    // First eccentricity squared
        "EPSG:4326"          // WGS84 coordinate reference system
    ),

    /**
     * Moon - IAU 2015 sphere model
     * Uses native IAU authority support (GeoTools 34+) via gt-iau-wkt plugin
     * Directly references AUTHORITY["IAU","30100"]
     */
    MOON(
        "Moon",
        1737400.0,           // Equatorial radius in meters (sphere)
        1737400.0,           // Polar radius in meters (sphere)
        0.0,                 // First eccentricity squared (perfect sphere)
        "IAU:30100"          // Native IAU 2015 CRS via gt-iau-wkt plugin
    );

    private final String name;
    private final double equatorialRadius;
    private final double polarRadius;
    private final double firstEccentricitySquared;
    private final String crsCode;

    /**
     * Constructor for CelestialBody enum.
     *
     * @param name Display name of the celestial body
     * @param equatorialRadius Equatorial radius in meters
     * @param polarRadius Polar radius in meters
     * @param firstEccentricitySquared First eccentricity squared (0 for sphere)
     * @param crsCode Coordinate reference system code (e.g., "EPSG:4326" or "IAU:30100")
     */
    CelestialBody(String name, double equatorialRadius, double polarRadius,
                  double firstEccentricitySquared, String crsCode) {
        this.name = name;
        this.equatorialRadius = equatorialRadius;
        this.polarRadius = polarRadius;
        this.firstEccentricitySquared = firstEccentricitySquared;
        this.crsCode = crsCode;
    }

    /**
     * Get the squared equatorial radius.
     *
     * @return Equatorial radius squared in square meters
     */
    public double getEquatorialRadiusSquared() {
        return equatorialRadius * equatorialRadius;
    }

    /**
     * Get the squared polar radius.
     *
     * @return Polar radius squared in square meters
     */
    public double getPolarRadiusSquared() {
        return polarRadius * polarRadius;
    }

    /**
     * Parse a celestial body from a string value.
     * Case-insensitive matching.
     *
     * @param value String representation of the celestial body ("earth" or "moon")
     * @return CelestialBody enum constant
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static CelestialBody fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Celestial body value cannot be null or empty");
        }

        String normalized = value.trim().toUpperCase();

        try {
            return CelestialBody.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                String.format("Unknown celestial body: '%s'. Valid values are: earth, moon", value)
            );
        }
    }
}
