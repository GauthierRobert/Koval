package com.koval.trainingplannerbackend.club.test.formula;

import com.koval.trainingplannerbackend.club.test.TestSegment;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Evaluates SpEL expressions used by {@code ReferenceUpdateRule}s. Sandboxed via
 * {@link SimpleEvaluationContext} which blocks type references ({@code T(...)}) and bean lookups; only
 * variables and pre-registered functions are reachable.
 *
 * <p>Variables: each segment value is bound as {@code #seg_<segmentId>} (a {@code Double}).
 * Functions: {@code pow, sqrt, abs, min, max, round, secondsPerKm, secondsPer100m, pacePerKm}.
 */
@Component
public class ClubTestFormulaEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Method> registeredFunctions;

    public ClubTestFormulaEvaluator() {
        Map<String, Method> fns = new HashMap<>();
        try {
            fns.put("pow", FormulaHelpers.class.getMethod("pow", double.class, double.class));
            fns.put("sqrt", FormulaHelpers.class.getMethod("sqrt", double.class));
            fns.put("abs", FormulaHelpers.class.getMethod("abs", double.class));
            fns.put("min", FormulaHelpers.class.getMethod("min", double.class, double.class));
            fns.put("max", FormulaHelpers.class.getMethod("max", double.class, double.class));
            fns.put("round", FormulaHelpers.class.getMethod("round", double.class));
            fns.put("secondsPerKm", FormulaHelpers.class.getMethod("secondsPerKm", double.class, double.class));
            fns.put("secondsPer100m", FormulaHelpers.class.getMethod("secondsPer100m", double.class, double.class));
            fns.put("pacePerKm", FormulaHelpers.class.getMethod("pacePerKm", double.class, double.class));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("FormulaHelpers methods missing", e);
        }
        this.registeredFunctions = Map.copyOf(fns);
    }

    /** Parses and dry-runs the expression with synthetic inputs. Throws {@link ValidationException}
     * when parsing fails, an unknown variable is referenced, or evaluation fails. */
    public void validate(String expression, List<TestSegment> segments) {
        if (expression == null || expression.isBlank()) {
            throw new ValidationException("Formula expression must not be blank", "FORMULA_BLANK");
        }
        Map<String, Double> synthetic = new HashMap<>();
        if (segments != null) {
            for (TestSegment s : segments) {
                if (s.getId() != null) synthetic.put(s.getId(), 100.0);
            }
        }
        try {
            Expression parsed = parser.parseExpression(expression);
            SimpleEvaluationContext ctx = buildContext(synthetic);
            Object value = parsed.getValue(ctx);
            if (!(value instanceof Number)) {
                throw new ValidationException("Formula must evaluate to a number, got "
                        + (value == null ? "null" : value.getClass().getSimpleName()), "FORMULA_NOT_NUMERIC");
            }
        } catch (ParseException pe) {
            throw new ValidationException("Formula parse error: " + pe.getMessage(), "FORMULA_PARSE");
        } catch (EvaluationException ee) {
            throw new ValidationException("Formula evaluation error: " + ee.getMessage(), "FORMULA_EVAL");
        }
    }

    /** Evaluates the expression with the given segment values (segmentId → numeric value). Returns empty
     * if any required variable is missing or evaluation cannot produce a number. */
    public Optional<Double> evaluate(String expression, Map<String, Double> segmentValuesById) {
        if (expression == null || expression.isBlank()) return Optional.empty();
        try {
            Expression parsed = parser.parseExpression(expression);
            SimpleEvaluationContext ctx = buildContext(segmentValuesById == null ? Map.of() : segmentValuesById);
            Object value = parsed.getValue(ctx);
            if (value instanceof Number n && Double.isFinite(n.doubleValue())) {
                return Optional.of(n.doubleValue());
            }
            return Optional.empty();
        } catch (ParseException | EvaluationException e) {
            return Optional.empty();
        }
    }

    private SimpleEvaluationContext buildContext(Map<String, Double> segmentValuesById) {
        SimpleEvaluationContext ctx = SimpleEvaluationContext.forReadOnlyDataBinding()
                .withInstanceMethods()
                .build();
        registeredFunctions.forEach(ctx::setVariable);
        segmentValuesById.forEach((id, value) -> ctx.setVariable("seg_" + id, value));
        return ctx;
    }
}
