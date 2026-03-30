package com.koval.trainingplannerbackend.pacing;

import com.koval.trainingplannerbackend.pacing.dto.AthleteProfile;
import com.koval.trainingplannerbackend.pacing.dto.PacingPlanResponse;
import com.koval.trainingplannerbackend.pacing.dto.PacingSegment;
import com.koval.trainingplannerbackend.pacing.dto.PacingSummary;
import com.koval.trainingplannerbackend.pacing.dto.RouteCoordinate;
import com.koval.trainingplannerbackend.pacing.gpx.CourseSegment;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PacingService {

    private final BikePacingService bikePacing;
    private final RunPacingService runPacing;
    private final SwimPacingService swimPacing;

    public PacingService(BikePacingService bikePacing, RunPacingService runPacing, SwimPacingService swimPacing) {
        this.bikePacing = bikePacing;
        this.runPacing = runPacing;
        this.swimPacing = swimPacing;
    }

    public PacingPlanResponse generatePlan(List<CourseSegment> bikeSegments, List<CourseSegment> runSegments,
                                           AthleteProfile profile, String discipline,
                                           List<RouteCoordinate> bikeRouteCoordinates,
                                           List<RouteCoordinate> runRouteCoordinates) {
        List<CourseSegment> bikeSource = bikeSegments != null ? bikeSegments : List.of();
        List<CourseSegment> runSource = runSegments != null ? runSegments : List.of();

        boolean isTriathlon = "TRIATHLON".equals(discipline);

        // Compute effective targets
        BikePacingService.BikeTarget bikeTarget = bikePacing.computeEffectiveTarget(bikeSource, profile, isTriathlon);
        RunPacingService.RunTarget runTarget = runPacing.computeEffectiveTarget(runSource, profile);

        AthleteProfile effectiveProfile = new AthleteProfile(
                profile.ftp(), profile.weightKg(), profile.thresholdPaceSec(), profile.swimCssSec(),
                profile.fatigueResistance(), profile.nutritionPreference(),
                profile.temperature(), profile.windSpeed(),
                bikeTarget.power(), runTarget.pace(),
                profile.swimDistanceM(), profile.targetSwimPaceSecPer100m(),
                profile.bikeType()
        );

        List<PacingSegment> bikePacingSegments = null;
        List<PacingSegment> runPacingSegments = null;
        PacingSummary bikeSummary = null;
        PacingSummary runSummary = null;
        double priorFatigue = 0.0;

        if ("BIKE".equals(discipline) || isTriathlon) {
            bikePacingSegments = bikePacing.generateSegments(bikeSource, effectiveProfile);
            bikeSummary = bikePacing.buildSummary(bikePacingSegments, bikeTarget.basis(), bikeTarget.computed());
            priorFatigue = bikePacingSegments.isEmpty() ? 0.0
                    : bikePacingSegments.getLast().cumulativeFatigue();
        }

        if ("RUN".equals(discipline) || isTriathlon) {
            runPacingSegments = runPacing.generateSegments(runSource, effectiveProfile, priorFatigue);
            runSummary = runPacing.buildSummary(runPacingSegments, runTarget.basis(), runTarget.computed());
        }

        PacingSummary swimSummary = null;
        if ("SWIM".equals(discipline) || isTriathlon) {
            swimSummary = swimPacing.buildSummary(profile);
        }

        return new PacingPlanResponse(bikePacingSegments, runPacingSegments, bikeSummary, runSummary, swimSummary,
                bikeRouteCoordinates, runRouteCoordinates);
    }
}
