package com.koval.trainingplannerbackend.pacing.gpx;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class GpxParser {

    private static final double EARTH_RADIUS_M = 6_371_000.0;
    private static final double SEGMENT_LENGTH_M = 500.0;

    /**
     * Parse a GPX input stream into a list of course segments (~500m each).
     */
    public List<CourseSegment> parse(InputStream gpxStream) {
        List<GpxTrackPoint> trackPoints = parseTrackPoints(gpxStream);
        if (trackPoints.size() < 2) {
            throw new IllegalArgumentException("GPX file must contain at least 2 trackpoints");
        }
        return buildSegments(trackPoints);
    }

    private List<GpxTrackPoint> parseTrackPoints(InputStream gpxStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            // Disable external entities for security
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(gpxStream);

            NodeList trkpts = doc.getElementsByTagName("trkpt");
            if (trkpts.getLength() == 0) {
                // Try route points as fallback
                trkpts = doc.getElementsByTagName("rtept");
            }

            List<GpxTrackPoint> points = new ArrayList<>();
            double cumulativeDistance = 0.0;

            for (int i = 0; i < trkpts.getLength(); i++) {
                Element trkpt = (Element) trkpts.item(i);
                double lat = Double.parseDouble(trkpt.getAttribute("lat"));
                double lon = Double.parseDouble(trkpt.getAttribute("lon"));

                double elevation = 0.0;
                NodeList eleNodes = trkpt.getElementsByTagName("ele");
                if (eleNodes.getLength() > 0) {
                    elevation = Double.parseDouble(eleNodes.item(0).getTextContent().trim());
                }

                if (i > 0) {
                    GpxTrackPoint prev = points.get(i - 1);
                    cumulativeDistance += haversineDistance(prev.lat(), prev.lon(), lat, lon);
                }

                points.add(new GpxTrackPoint(lat, lon, elevation, cumulativeDistance));
            }

            return points;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse GPX file: " + e.getMessage(), e);
        }
    }

    private List<CourseSegment> buildSegments(List<GpxTrackPoint> points) {
        List<CourseSegment> segments = new ArrayList<>();
        double totalDistance = points.get(points.size() - 1).cumulativeDistance();

        double segStart = 0.0;
        int pointIndex = 0;

        while (segStart < totalDistance) {
            double segEnd = Math.min(segStart + SEGMENT_LENGTH_M, totalDistance);

            // Find the trackpoints bracketing this segment
            double elevationGain = 0.0;
            double elevationLoss = 0.0;
            double startElevation = interpolateElevation(points, segStart, pointIndex);
            double endElevation = interpolateElevation(points, segEnd, pointIndex);

            // Walk through points within this segment to accumulate gain/loss
            while (pointIndex < points.size() - 1 && points.get(pointIndex + 1).cumulativeDistance() <= segEnd) {
                double elevDiff = points.get(pointIndex + 1).elevation() - points.get(pointIndex).elevation();
                if (elevDiff > 0) elevationGain += elevDiff;
                else elevationLoss += Math.abs(elevDiff);
                pointIndex++;
            }

            double length = segEnd - segStart;
            double elevChange = endElevation - startElevation;
            double gradient = length > 0 ? (elevChange / length) * 100.0 : 0.0;

            segments.add(new CourseSegment(
                    Math.round(segStart * 10.0) / 10.0,
                    Math.round(segEnd * 10.0) / 10.0,
                    Math.round(gradient * 100.0) / 100.0,
                    Math.round(elevationGain * 10.0) / 10.0,
                    Math.round(elevationLoss * 10.0) / 10.0,
                    Math.round(startElevation * 10.0) / 10.0,
                    Math.round(endElevation * 10.0) / 10.0
            ));

            segStart = segEnd;
            // Reset pointIndex to search from a valid position for next segment
            if (pointIndex > 0) pointIndex--;
        }

        return segments;
    }

    private double interpolateElevation(List<GpxTrackPoint> points, double distance, int startIdx) {
        for (int i = Math.max(0, startIdx); i < points.size() - 1; i++) {
            GpxTrackPoint a = points.get(i);
            GpxTrackPoint b = points.get(i + 1);
            if (distance >= a.cumulativeDistance() && distance <= b.cumulativeDistance()) {
                double segLength = b.cumulativeDistance() - a.cumulativeDistance();
                if (segLength == 0) return a.elevation();
                double ratio = (distance - a.cumulativeDistance()) / segLength;
                return a.elevation() + ratio * (b.elevation() - a.elevation());
            }
        }
        return points.get(points.size() - 1).elevation();
    }

    /**
     * Haversine distance between two lat/lon points in meters.
     */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }
}
