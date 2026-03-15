package com.koval.trainingplannerbackend.pacing;

import org.springframework.stereotype.Service;

@Service
class BikeSpeedService {

    // Physics constants
    private static final double DRIVETRAIN_LOSS = 0.03;
    private static final double GRAVITY = 9.81;
    private static final double MAX_SPEED_MS = 22.0;
    private static final double MIN_SPEED_MS = 2.0;
    private static final double INITIAL_SPEED_GUESS_MS = 8.0;
    private static final int NEWTON_ITERATIONS = 20;
    private static final double SEA_LEVEL_AIR_DENSITY = 1.225;
    private static final double SCALE_HEIGHT_M = 8500.0;
    private static final double BIKE_WEIGHT_KG = 8.0;

    // Inertia model
    private static final double TAU = 150.0; // characteristic transition distance (meters)
    private static final double SHORT_SEGMENT_THRESHOLD = 50.0; // meters

    record SpeedResult(double effectiveSpeed, double exitSpeed) {}

    /**
     * Steady-state speed (m/s) from power using simplified physics.
     * P = (Crr * m * g * v) + (0.5 * CdA * rho * (v+w)^2 * v) + (m * g * grade * v)
     * Solved via Newton's method.
     */
    double steadyStateSpeed(double power, double gradient, double elevation, BikePacingService.BikeEnvironment env) {
        double effectivePower = power * (1.0 - DRIVETRAIN_LOSS);
        double mass = env.totalMass();
        double rho = SEA_LEVEL_AIR_DENSITY * Math.exp(-elevation / SCALE_HEIGHT_M);

        double cda = env.aero().cda();
        double crr = env.aero().crr();
        double windSpeed = env.windSpeed();

        // On downhill, start from max speed so the aero v^2 term keeps derivative positive.
        // Starting low (8 m/s) with low CdA causes the gravity term to dominate the derivative,
        // making it negative and causing Newton to diverge to the 1.0 m/s floor.
        double v = gradient < -0.01 ? MAX_SPEED_MS : INITIAL_SPEED_GUESS_MS;
        for (int i = 0; i < NEWTON_ITERATIONS; i++) {
            double airSpeed = v + windSpeed;
            double resistancePower = crr * mass * GRAVITY * v
                    + 0.5 * cda * rho * airSpeed * airSpeed * v
                    + mass * GRAVITY * gradient * v;
            double derivative = crr * mass * GRAVITY
                    + 0.5 * cda * rho * (airSpeed * airSpeed + 2.0 * v * airSpeed)
                    + mass * GRAVITY * gradient;

            double error = resistancePower - effectivePower;
            v -= error / derivative;
            v = Math.max(v, 1.0);
        }

        return Math.min(v, MAX_SPEED_MS);
    }

    /**
     * Compute effective average speed and exit speed for a segment,
     * accounting for inertia (speed carryover from previous segment).
     */
    SpeedResult computeSegmentSpeed(double power, double gradient, double elevation,
                                     double entrySpeed,
                                     double segmentLength, BikePacingService.BikeEnvironment env) {
        double vSteady = steadyStateSpeed(power, gradient, elevation, env);

        double L = segmentLength;

        // For very short segments, use simple average
        if (L < SHORT_SEGMENT_THRESHOLD) {
            double vEff = (entrySpeed + vSteady) / 2.0;
            vEff = Math.max(vEff, MIN_SPEED_MS);
            double vExit = vSteady;
            return new SpeedResult(vEff, vExit);
        }

        // Exponential decay blend: v(x) = v_ss + (v_entry - v_ss) * e^(-x/τ)
        double expFactor = Math.exp(-L / TAU);
        double delta = entrySpeed - vSteady;

        // Average speed over segment
        double vEff = vSteady + delta * (TAU / L) * (1.0 - expFactor);
        vEff = Math.max(vEff, MIN_SPEED_MS);

        // Exit speed
        double vExit = vSteady + delta * expFactor;
        vExit = Math.max(vExit, MIN_SPEED_MS);

        return new SpeedResult(vEff, vExit);
    }
}
