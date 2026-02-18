import { Injectable, inject } from '@angular/core';
import { AuthService, User } from './auth.service';
import { Training, WorkoutBlock } from './training.service';
import { ZoneSystem } from './zone';

@Injectable({
    providedIn: 'root'
})
export class DurationEstimationService {
    private authService = inject(AuthService);

    estimateDuration(block: WorkoutBlock, training: Training, zoneSystem: ZoneSystem | null): number {
        // 1. If explicit duration exists, use it.
        if (block.durationSeconds && block.durationSeconds > 0) {
            return block.durationSeconds;
        }

        // 2. If explicit distance exists, we can try to estimate.
        if (block.distanceMeters && block.distanceMeters > 0) {
            const user = this.authService.currentUser;
            if (!user) return 0; // Or some fallback

            const speedMps = this.getEstimatedSpeed(block, training, user, zoneSystem);
            if (speedMps > 0) {
                return Math.round(block.distanceMeters / speedMps);
            }
        }

        return 0;
    }

    private getEstimatedSpeed(block: WorkoutBlock, training: Training, user: User, zoneSystem: ZoneSystem | null): number {
        const intensity = block.intensityTarget || 50; // Default 50% if missing

        // --- CYCLING ---
        if (training.sportType === 'CYCLING') {
            // Simplified physics model for visualization:
            // Assume 30km/h (8.33 m/s) at Threshold (100% FTP or Intensity)
            // Speed scales roughly with cube root of power, but let's use sqrt for a "visual feel" or just linear for simplicity.
            // Let's us a simple linear approximation around 30km/h. 
            // 100% -> 30km/h
            // 50% -> 20km/h?

            // Let's use: Speed = BaseSpeed * (Intensity/100)^0.5
            const baseSpeedMps = 8.33; // 30 km/h
            return baseSpeedMps * Math.sqrt(intensity / 100);
        }

        // --- RUNNING ---
        if (training.sportType === 'RUNNING') {
            // Check for Custom Zone System Reference
            if (zoneSystem) {
                const refType = zoneSystem.referenceType;
                let referenceSecondsPerKm = 0;

                // Map Zone Reference Types to User Fields
                if (refType === 'THRESHOLD_PACE') referenceSecondsPerKm = user.functionalThresholdPace || 240; // 4:00/km
                else if (refType === 'PACE_5K') referenceSecondsPerKm = user.pace5k || 220; // 3:40/km
                else if (refType === 'PACE_10K') referenceSecondsPerKm = user.pace10k || 240; // 4:00/km
                else if (refType === 'PACE_HALF_MARATHON') referenceSecondsPerKm = user.paceHalfMarathon || 270; // 4:30/km
                else if (refType === 'PACE_MARATHON') referenceSecondsPerKm = user.paceMarathon || 300; // 5:00/km
                else referenceSecondsPerKm = user.functionalThresholdPace || 240;

                if (referenceSecondsPerKm > 0) {
                    // Intensity is % of Reference Speed (Speed = 1/Pace).
                    // Example: 110% of 5K Pace.
                    // Speed = (1000m / ReferenceSec) * (Intensity/100)
                    const refSpeedMps = 1000 / referenceSecondsPerKm;
                    return refSpeedMps * (intensity / 100);
                }
            }

            // Default Running (Threshold Pace)
            const ftpPace = user.functionalThresholdPace || 240; // 4:00 min/km
            const refSpeedMps = 1000 / ftpPace;
            return refSpeedMps * (intensity / 100);
        }

        // --- SWIMMING ---
        if (training.sportType === 'SWIMMING') {
            // Similar logic for CSS
            const css = user.criticalSwimSpeed || 90; // 1:30 min/100m -> 90s/100m
            const refSpeedMps = 100 / css;
            return refSpeedMps * (intensity / 100);
        }

        return 0;
    }
}
