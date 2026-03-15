package com.koval.trainingplannerbackend.pacing.gpx;

import com.koval.trainingplannerbackend.pacing.dto.RouteCoordinate;
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

    // Elevation smoothing: Gaussian kernel with this radius (in meters).
    // ~50m smooths GPS jitter while preserving real terrain features.
    private static final double SMOOTHING_RADIUS_M = 200.0;

    /**
     * Parse a GPX input stream into point-to-point course segments (one per GPX track point pair).
     */
    public List<CourseSegment> parse(InputStream gpxStream) {
        return parseAndSmooth(gpxStream).segments();
    }

    /**
     * Parse a GPX input stream into point-to-point course segments, plus downsampled route coordinates for map display.
     */
    public GpxParseResult parseWithCoordinates(InputStream gpxStream) {
        ParsedTrack parsed = parseAndSmooth(gpxStream);
        List<RouteCoordinate> coordinates = downsampleCoordinates(parsed.points());
        return new GpxParseResult(parsed.segments(), coordinates);
    }

    private record ParsedTrack(List<GpxTrackPoint> points, List<CourseSegment> segments) {}

    private ParsedTrack parseAndSmooth(InputStream gpxStream) {
        List<GpxTrackPoint> trackPoints = parseTrackPoints(gpxStream);
        if (trackPoints.size() < 2) {
            throw new IllegalArgumentException("GPX file must contain at least 2 trackpoints");
        }
        trackPoints = smoothElevation(trackPoints);
        List<CourseSegment> segments = buildPointToPointSegments(trackPoints);
        return new ParsedTrack(trackPoints, segments);
    }

    /**
     * Downsample track points to ~1 point per 50m for map display.
     */
    private List<RouteCoordinate> downsampleCoordinates(List<GpxTrackPoint> points) {
        List<RouteCoordinate> result = new ArrayList<>();
        double lastDistance = -50.0; // ensure first point is included

        for (GpxTrackPoint pt : points) {
            if (pt.cumulativeDistance() - lastDistance >= 50.0) {
                result.add(new RouteCoordinate(pt.lat(), pt.lon(), pt.elevation(), pt.cumulativeDistance()));
                lastDistance = pt.cumulativeDistance();
            }
        }

        // Always include the last point
        GpxTrackPoint last = points.getLast();
        if (result.isEmpty() || result.getLast().distance() != last.cumulativeDistance()) {
            result.add(new RouteCoordinate(last.lat(), last.lon(), last.elevation(), last.cumulativeDistance()));
        }

        return result;
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
                    cumulativeDistance += GeoUtils.haversineDistance(prev.lat(), prev.lon(), lat, lon);
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

    /**
     * Smooth elevation using a distance-based Gaussian weighted average.
     * Removes GPS elevation noise/spikes while preserving real terrain shape.
     * Complexity: O(n*k) where k = avg points within 200m radius (~20-40 for typical GPS), effectively linear.
     */
    private List<GpxTrackPoint> smoothElevation(List<GpxTrackPoint> points) {
        int n = points.size();
        double sigma = SMOOTHING_RADIUS_M / 2.0; // sigma so that ~95% weight is within the radius
        double[] smoothed = new double[n];

        for (int i = 0; i < n; i++) {
            double dist_i = points.get(i).cumulativeDistance();
            double weightSum = 0.0;
            double elevSum = 0.0;

            // Scan backward and forward within the radius
            for (int j = i; j >= 0 && dist_i - points.get(j).cumulativeDistance() <= SMOOTHING_RADIUS_M; j--) {
                double d = dist_i - points.get(j).cumulativeDistance();
                double w = Math.exp(-(d * d) / (2.0 * sigma * sigma));
                weightSum += w;
                elevSum += w * points.get(j).elevation();
            }
            for (int j = i + 1; j < n && points.get(j).cumulativeDistance() - dist_i <= SMOOTHING_RADIUS_M; j++) {
                double d = points.get(j).cumulativeDistance() - dist_i;
                double w = Math.exp(-(d * d) / (2.0 * sigma * sigma));
                weightSum += w;
                elevSum += w * points.get(j).elevation();
            }

            smoothed[i] = elevSum / weightSum;
        }

        List<GpxTrackPoint> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            GpxTrackPoint pt = points.get(i);
            result.add(new GpxTrackPoint(pt.lat(), pt.lon(), smoothed[i], pt.cumulativeDistance()));
        }
        return result;
    }

    /**
     * Build one segment per consecutive pair of GPX track points (no aggregation).
     */
    private List<CourseSegment> buildPointToPointSegments(List<GpxTrackPoint> points) {
        List<CourseSegment> segments = new ArrayList<>();

        for (int i = 1; i < points.size(); i++) {
            GpxTrackPoint prev = points.get(i - 1);
            GpxTrackPoint curr = points.get(i);
            double length = curr.cumulativeDistance() - prev.cumulativeDistance();
            if (length <= 0) continue;

            double elevChange = curr.elevation() - prev.elevation();
            double gradient = (elevChange / length) * 100.0;
            double elevGain = elevChange > 0 ? elevChange : 0.0;
            double elevLoss = elevChange < 0 ? Math.abs(elevChange) : 0.0;

            segments.add(new CourseSegment(
                    Math.round(prev.cumulativeDistance() * 10.0) / 10.0,
                    Math.round(curr.cumulativeDistance() * 10.0) / 10.0,
                    Math.round(gradient * 100.0) / 100.0,
                    Math.round(elevGain * 10.0) / 10.0,
                    Math.round(elevLoss * 10.0) / 10.0,
                    Math.round(prev.elevation() * 10.0) / 10.0,
                    Math.round(curr.elevation() * 10.0) / 10.0
            ));
        }

        return segments;
    }

    /**
     * Resample course segments into fixed-distance segments by interpolating elevation
     * from the original track points at regular intervals.
     *
     * @param segmentLengthM target segment length in meters (e.g. 200 for bike, 50 for run)
     */
    public List<CourseSegment> resampleToFixedDistance(List<CourseSegment> segments, double segmentLengthM) {
        if (segments.isEmpty()) return segments;

        double totalDistance = segments.getLast().endDistance();
        if (totalDistance <= 0) return segments;

        List<CourseSegment> result = new ArrayList<>();
        double cursor = segments.getFirst().startDistance();

        while (cursor < totalDistance) {
            double segEnd = Math.min(cursor + segmentLengthM, totalDistance);
            double startElev = elevationAt(segments, cursor);
            double endElev = elevationAt(segments, segEnd);
            double length = segEnd - cursor;

            double elevChange = endElev - startElev;
            double gradient = length > 0 ? (elevChange / length) * 100.0 : 0.0;
            double elevGain = elevChange > 0 ? elevChange : 0.0;
            double elevLoss = elevChange < 0 ? Math.abs(elevChange) : 0.0;

            result.add(new CourseSegment(
                    Math.round(cursor * 10.0) / 10.0,
                    Math.round(segEnd * 10.0) / 10.0,
                    Math.round(gradient * 100.0) / 100.0,
                    Math.round(elevGain * 10.0) / 10.0,
                    Math.round(elevLoss * 10.0) / 10.0,
                    Math.round(startElev * 10.0) / 10.0,
                    Math.round(endElev * 10.0) / 10.0
            ));

            cursor = segEnd;
        }

        return result;
    }

    /**
     * Interpolate elevation at a given distance along the course.
     */
    private double elevationAt(List<CourseSegment> segments, double distance) {
        for (CourseSegment seg : segments) {
            if (distance <= seg.endDistance()) {
                double segLength = seg.endDistance() - seg.startDistance();
                if (segLength <= 0) return seg.startElevation();
                double fraction = (distance - seg.startDistance()) / segLength;
                fraction = Math.max(0, Math.min(1, fraction));
                return seg.startElevation() + fraction * (seg.endElevation() - seg.startElevation());
            }
        }
        return segments.getLast().endElevation();
    }

}
