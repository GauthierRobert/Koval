package com.koval.trainingplannerbackend.club.test.formula;

import com.koval.trainingplannerbackend.club.test.SegmentResultUnit;
import com.koval.trainingplannerbackend.club.test.TestSegment;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import com.koval.trainingplannerbackend.training.model.SportType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClubTestFormulaEvaluatorTest {

    private final ClubTestFormulaEvaluator evaluator = new ClubTestFormulaEvaluator();

    @Test
    void evaluatesSwimCssFormulaWithSegmentValues() {
        // 200m in 170s, 400m in 360s → CSS = (360-170)/2 = 95 s/100m
        var segments = List.of(seg("swim200"), seg("swim400"));
        evaluator.validate("#secondsPer100m(#seg_swim400 - #seg_swim200, 200)", segments);

        Optional<Double> css = evaluator.evaluate(
                "#secondsPer100m(#seg_swim400 - #seg_swim200, 200)",
                Map.of("swim200", 170.0, "swim400", 360.0));
        assertTrue(css.isPresent());
        assertEquals(95.0, css.get(), 0.001);
    }

    @Test
    void ftpFormulaMultipliesByConstant() {
        var segments = List.of(seg("ftp20"));
        evaluator.validate("#seg_ftp20 * 0.95", segments);
        Optional<Double> ftp = evaluator.evaluate("#seg_ftp20 * 0.95", Map.of("ftp20", 300.0));
        assertEquals(285.0, ftp.orElseThrow(), 0.001);
    }

    @Test
    void roundHelperWorks() {
        var segments = List.of(seg("x"));
        evaluator.validate("#round(#seg_x * 1.5)", segments);
        assertEquals(15.0, evaluator.evaluate("#round(#seg_x * 1.5)", Map.of("x", 10.0)).orElseThrow(), 0.001);
    }

    @Test
    void missingVariableYieldsEmpty() {
        // Segment values don't include the variable referenced in the formula
        Optional<Double> r = evaluator.evaluate("#seg_unknown * 2", Map.of("seg_other", 1.0));
        assertFalse(r.isPresent());
    }

    @Test
    void blankFormulaThrowsOnValidate() {
        assertThrows(ValidationException.class, () -> evaluator.validate("", List.of()));
        assertThrows(ValidationException.class, () -> evaluator.validate(null, List.of()));
    }

    @Test
    void invalidExpressionThrowsOnValidate() {
        var segments = List.of(seg("a"));
        assertThrows(ValidationException.class, () -> evaluator.validate("#seg_a *", segments));
    }

    @Test
    void typeReferencesAreBlocked() {
        // SimpleEvaluationContext should block T(java.lang.Runtime) — sandbox sanity check.
        var segments = List.of(seg("x"));
        assertThrows(ValidationException.class,
                () -> evaluator.validate("T(java.lang.Runtime).getRuntime().exec('echo')", segments));
    }

    @Test
    void beanReferencesAreBlocked() {
        // SpEL @-bean references must not work.
        var segments = List.of(seg("x"));
        assertThrows(ValidationException.class,
                () -> evaluator.validate("@someBean.something()", segments));
    }

    private static TestSegment seg(String id) {
        TestSegment s = new TestSegment();
        s.setId(id);
        s.setLabel(id);
        s.setSportType(SportType.SWIMMING);
        s.setDistanceMeters(200);
        s.setResultUnit(SegmentResultUnit.SECONDS);
        return s;
    }
}
