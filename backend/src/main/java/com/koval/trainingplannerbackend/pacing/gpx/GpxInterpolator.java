package com.koval.trainingplannerbackend.pacing.gpx;

import java.util.ArrayList;
import java.util.List;

/**
 * Interpolates GPX track points using linear or cubic spline interpolation
 * to increase route resolution before segmentation.
 * <p>
 * Kept for potential sparse-track use where raw GPX has too few points
 * for meaningful gradient estimation.
 *
 * @deprecated Not currently wired into the parsing pipeline.
 */
@Deprecated
class GpxInterpolator {

    enum Method { LINEAR, SPLINE }

    /**
     * Interpolate track points, inserting {@code pointsBetween} new points
     * between each consecutive pair. Cumulative distances are recomputed via haversine.
     */
    List<GpxTrackPoint> interpolate(List<GpxTrackPoint> points, int pointsBetween, Method method) {
        if (points.size() < 2 || pointsBetween <= 0) return points;

        List<GpxTrackPoint> raw = (method == Method.SPLINE && points.size() > 2)
                ? interpolateSpline(points, pointsBetween)
                : interpolateLinear(points, pointsBetween);

        return recomputeDistances(raw);
    }

    // ─── Linear Interpolation ─────────────────────────────────────

    private List<GpxTrackPoint> interpolateLinear(List<GpxTrackPoint> points, int n) {
        List<GpxTrackPoint> result = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            GpxTrackPoint a = points.get(i);
            GpxTrackPoint b = points.get(i + 1);
            result.add(a);

            for (int j = 1; j <= n; j++) {
                double t = (double) j / (n + 1);
                result.add(new GpxTrackPoint(
                        a.lat() + t * (b.lat() - a.lat()),
                        a.lon() + t * (b.lon() - a.lon()),
                        a.elevation() + t * (b.elevation() - a.elevation()),
                        0.0 // recomputed later
                ));
            }
        }
        result.add(points.getLast());
        return result;
    }

    // ─── Cubic Spline Interpolation ──────────────────────────────

    private List<GpxTrackPoint> interpolateSpline(List<GpxTrackPoint> points, int n) {
        int size = points.size();

        // Parameter t = cumulative chord length in degree-space
        double[] t = new double[size];
        for (int i = 1; i < size; i++) {
            double dlat = points.get(i).lat() - points.get(i - 1).lat();
            double dlon = points.get(i).lon() - points.get(i - 1).lon();
            t[i] = t[i - 1] + Math.sqrt(dlat * dlat + dlon * dlon);
        }
        if (t[size - 1] == 0) return points;

        double[] lats = new double[size];
        double[] lons = new double[size];
        double[] eles = new double[size];
        for (int i = 0; i < size; i++) {
            lats[i] = points.get(i).lat();
            lons[i] = points.get(i).lon();
            eles[i] = points.get(i).elevation();
        }

        double[] cLat = naturalCubicSpline(t, lats);
        double[] cLon = naturalCubicSpline(t, lons);
        double[] cEle = naturalCubicSpline(t, eles);

        List<GpxTrackPoint> result = new ArrayList<>();
        for (int i = 0; i < size - 1; i++) {
            result.add(points.get(i));
            for (int j = 1; j <= n; j++) {
                double frac = (double) j / (n + 1);
                double tVal = t[i] + frac * (t[i + 1] - t[i]);
                result.add(new GpxTrackPoint(
                        evalSpline(t, lats, cLat, tVal, i),
                        evalSpline(t, lons, cLon, tVal, i),
                        evalSpline(t, eles, cEle, tVal, i),
                        0.0 // recomputed later
                ));
            }
        }
        result.add(points.getLast());
        return result;
    }

    /**
     * Natural cubic spline: compute second-derivative coefficients.
     */
    private static double[] naturalCubicSpline(double[] x, double[] y) {
        int n = x.length;
        double[] h = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            h[i] = x[i + 1] - x[i];
        }

        double[] alpha = new double[n];
        for (int i = 1; i < n - 1; i++) {
            alpha[i] = 3.0 / h[i] * (y[i + 1] - y[i]) - 3.0 / h[i - 1] * (y[i] - y[i - 1]);
        }

        double[] l = new double[n];
        double[] mu = new double[n];
        double[] z = new double[n];
        l[0] = 1;

        for (int i = 1; i < n - 1; i++) {
            l[i] = 2 * (x[i + 1] - x[i - 1]) - h[i - 1] * mu[i - 1];
            mu[i] = h[i] / l[i];
            z[i] = (alpha[i] - h[i - 1] * z[i - 1]) / l[i];
        }

        double[] c = new double[n];
        l[n - 1] = 1;
        for (int j = n - 2; j >= 0; j--) {
            c[j] = z[j] - mu[j] * c[j + 1];
        }
        return c;
    }

    private static double evalSpline(double[] t, double[] y, double[] c, double tVal, int i) {
        double h = t[i + 1] - t[i];
        if (h == 0) return y[i];
        double b = (y[i + 1] - y[i]) / h - h * (c[i + 1] + 2 * c[i]) / 3.0;
        double d = (c[i + 1] - c[i]) / (3.0 * h);
        double dt = tVal - t[i];
        return y[i] + b * dt + c[i] * dt * dt + d * dt * dt * dt;
    }

    // ─── Distance recomputation ──────────────────────────────────

    private List<GpxTrackPoint> recomputeDistances(List<GpxTrackPoint> points) {
        List<GpxTrackPoint> result = new ArrayList<>(points.size());
        double cumDist = 0.0;
        result.add(new GpxTrackPoint(points.getFirst().lat(), points.getFirst().lon(),
                points.getFirst().elevation(), 0.0));

        for (int i = 1; i < points.size(); i++) {
            GpxTrackPoint prev = points.get(i - 1);
            GpxTrackPoint curr = points.get(i);
            cumDist += GeoUtils.haversineDistance(prev.lat(), prev.lon(), curr.lat(), curr.lon());
            result.add(new GpxTrackPoint(curr.lat(), curr.lon(), curr.elevation(), cumDist));
        }
        return result;
    }

}
