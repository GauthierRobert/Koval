package com.koval.trainingplannerbackend.club.test;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.club.test.dto.AppliedReferenceUpdateDto;
import com.koval.trainingplannerbackend.club.test.dto.ClubTestDetailResponse;
import com.koval.trainingplannerbackend.club.test.dto.ClubTestIterationResponse;
import com.koval.trainingplannerbackend.club.test.dto.ClubTestResultResponse;
import com.koval.trainingplannerbackend.club.test.dto.ClubTestSummaryResponse;
import com.koval.trainingplannerbackend.club.test.dto.ReferenceUpdateRuleDto;
import com.koval.trainingplannerbackend.club.test.dto.SegmentResultValueDto;
import com.koval.trainingplannerbackend.club.test.dto.TestSegmentDto;
import com.koval.trainingplannerbackend.club.test.formula.TestPreset;
import com.koval.trainingplannerbackend.club.test.dto.TestPresetResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ClubTestMapper {

    private ClubTestMapper() {}

    public static TestSegmentDto toDto(TestSegment s) {
        return new TestSegmentDto(s.getId(), s.getOrder(), s.getLabel(), s.getSportType(),
                s.getDistanceMeters(), s.getDurationSeconds(), s.getResultUnit(), s.getNotes());
    }

    public static TestSegment fromDto(TestSegmentDto d) {
        TestSegment s = new TestSegment();
        s.setId(d.id());
        s.setOrder(d.order());
        s.setLabel(d.label());
        s.setSportType(d.sportType());
        s.setDistanceMeters(d.distanceMeters());
        s.setDurationSeconds(d.durationSeconds());
        s.setResultUnit(d.resultUnit());
        s.setNotes(d.notes());
        return s;
    }

    public static ReferenceUpdateRuleDto toDto(ReferenceUpdateRule r) {
        return new ReferenceUpdateRuleDto(r.getId(), r.getTarget(), r.getCustomKey(), r.getLabel(),
                r.getUnit(), r.getFormulaExpression(), r.isAutoApply());
    }

    public static ReferenceUpdateRule fromDto(ReferenceUpdateRuleDto d) {
        ReferenceUpdateRule r = new ReferenceUpdateRule();
        r.setId(d.id());
        r.setTarget(d.target());
        r.setCustomKey(d.customKey());
        r.setLabel(d.label());
        r.setUnit(d.unit());
        r.setFormulaExpression(d.formulaExpression());
        r.setAutoApply(d.autoApply());
        return r;
    }

    public static SegmentResultValueDto toDto(SegmentResultValue v) {
        return new SegmentResultValueDto(v.getValue(), v.getUnit(), v.getCompletedSessionId());
    }

    public static SegmentResultValue fromDto(SegmentResultValueDto d) {
        SegmentResultValue v = new SegmentResultValue();
        v.setValue(d.value());
        v.setUnit(d.unit());
        v.setCompletedSessionId(d.completedSessionId());
        return v;
    }

    public static AppliedReferenceUpdateDto toDto(AppliedReferenceUpdate a) {
        return new AppliedReferenceUpdateDto(a.getRuleId(), a.getTarget(), a.getCustomKey(),
                a.getPreviousValue(), a.getNewValue(), a.getAppliedAt(), a.getAppliedBy());
    }

    public static ClubTestSummaryResponse toSummary(ClubTest t, long iterationCount, String currentIterationLabel) {
        return new ClubTestSummaryResponse(
                t.getId(), t.getClubId(), t.getName(), t.getDescription(),
                t.isCompetitionMode(), t.isArchived(),
                t.getSegments().size(), t.getReferenceUpdates().size(),
                iterationCount, t.getCurrentIterationId(), currentIterationLabel,
                t.getCreatedAt());
    }

    public static ClubTestDetailResponse toDetail(ClubTest t, boolean hasResults) {
        return new ClubTestDetailResponse(
                t.getId(), t.getClubId(), t.getName(), t.getDescription(),
                t.getCreatedBy(), t.getCreatedAt(), t.getUpdatedAt(),
                t.isCompetitionMode(), t.getRankingMetric(), t.getRankingTarget(), t.getRankingDirection(),
                t.getSegments().stream().map(ClubTestMapper::toDto).toList(),
                t.getReferenceUpdates().stream().map(ClubTestMapper::toDto).toList(),
                t.getCurrentIterationId(), t.isArchived(), hasResults);
    }

    public static ClubTestIterationResponse toDto(ClubTestIteration it, long resultCount) {
        return new ClubTestIterationResponse(
                it.getId(), it.getTestId(), it.getClubId(), it.getLabel(),
                it.getStartDate(), it.getEndDate(), it.getStatus(),
                it.getCreatedAt(), it.getClosedAt(),
                it.getSegments().stream().map(ClubTestMapper::toDto).toList(),
                it.getReferenceUpdates().stream().map(ClubTestMapper::toDto).toList(),
                resultCount);
    }

    public static ClubTestResultResponse toDto(ClubTestResult r, User athlete) {
        Map<String, SegmentResultValueDto> segDtos = r.getSegmentResults() == null ? Map.of() :
                r.getSegmentResults().entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey, e -> toDto(e.getValue())));
        List<AppliedReferenceUpdateDto> applied = r.getAppliedUpdates() == null ? List.of() :
                r.getAppliedUpdates().stream().map(ClubTestMapper::toDto).toList();
        return new ClubTestResultResponse(
                r.getId(), r.getIterationId(), r.getTestId(), r.getClubId(), r.getAthleteId(),
                athlete == null ? null : athlete.getDisplayName(),
                athlete == null ? null : athlete.getProfilePicture(),
                segDtos, r.getComputedReferences(), applied,
                r.getRank(), r.getNotes(),
                r.getCreatedAt(), r.getUpdatedAt(), r.getRecordedBy());
    }

    public static TestPresetResponse toDto(TestPreset preset) {
        return new TestPresetResponse(
                preset.id(), preset.labelKey(), preset.descriptionKey(),
                preset.segments().stream().map(ClubTestMapper::toDto).toList(),
                preset.referenceUpdates().stream().map(ClubTestMapper::toDto).toList());
    }
}
