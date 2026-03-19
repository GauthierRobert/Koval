package com.koval.trainingplannerbackend.race;

/**
 * Summary DTO that excludes raw GPX bytes from responses.
 */
public record RaceSummary(
        String id, String title, String sport,
        String location, String country, String region, String distance,
        Double swimDistanceM, Double bikeDistanceM, Double runDistanceM,
        Integer elevationGainM, String description, String website,
        String scheduledDate,
        boolean hasSwimGpx, boolean hasBikeGpx, boolean hasRunGpx,
        Integer swimGpxLoops, Integer bikeGpxLoops, Integer runGpxLoops,
        String createdBy, boolean verified
) {
    public static RaceSummary from(Race r) {
        return new RaceSummary(
                r.getId(), r.getTitle(), r.getSport(),
                r.getLocation(), r.getCountry(), r.getRegion(), r.getDistance(),
                r.getSwimDistanceM(), r.getBikeDistanceM(), r.getRunDistanceM(),
                r.getElevationGainM(), r.getDescription(), r.getWebsite(),
                r.getScheduledDate(),
                r.getSwimGpx() != null, r.getBikeGpx() != null, r.getRunGpx() != null,
                r.getSwimGpxLoops(), r.getBikeGpxLoops(), r.getRunGpxLoops(),
                r.getCreatedBy(), r.isVerified()
        );
    }
}
