package com.koval.trainingplannerbackend.config;

import com.koval.trainingplannerbackend.ai.tools.action.CreationTrainingToolService;
import com.koval.trainingplannerbackend.ai.tools.action.CreationTrainingWithClubSessionToolService;
import com.koval.trainingplannerbackend.ai.tools.club.ClubToolService;
import com.koval.trainingplannerbackend.ai.tools.coach.CoachToolService;
import com.koval.trainingplannerbackend.ai.tools.goal.GoalToolService;
import com.koval.trainingplannerbackend.ai.tools.history.HistoryToolService;
import com.koval.trainingplannerbackend.ai.tools.plan.PlanToolService;
import com.koval.trainingplannerbackend.ai.tools.race.RaceToolService;
import com.koval.trainingplannerbackend.ai.tools.scheduling.SchedulingToolService;
import com.koval.trainingplannerbackend.ai.tools.training.TrainingToolService;
import com.koval.trainingplannerbackend.ai.tools.zone.ZoneToolService;
import com.koval.trainingplannerbackend.club.feed.ClubFeedBroadcastMessage;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(NativeImageHints.Registrar.class)
public class NativeImageHints {

    static class Registrar implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            registerToolServiceHints(hints);
            registerSerializationHints(hints);
            registerResourceHints(hints);
        }

        private void registerSerializationHints(RuntimeHints hints) {
            // Records serialized via Jackson (e.g. Pub/Sub broker payloads) need reflection
            hints.reflection().registerType(ClubFeedBroadcastMessage.class,
                    MemberCategory.ACCESS_DECLARED_FIELDS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS);
        }

        private void registerToolServiceHints(RuntimeHints hints) {
            // All @Tool service classes need full reflection for Spring AI tool discovery
            Class<?>[] toolServices = {
                    SchedulingToolService.class,
                    TrainingToolService.class,
                    ZoneToolService.class,
                    HistoryToolService.class,
                    ClubToolService.class,
                    CreationTrainingWithClubSessionToolService.class,
                    CreationTrainingToolService.class,
                    RaceToolService.class,
                    GoalToolService.class,
                    CoachToolService.class,
                    PlanToolService.class
            };

            for (Class<?> toolService : toolServices) {
                hints.reflection().registerType(toolService,
                        MemberCategory.ACCESS_DECLARED_FIELDS,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.INVOKE_PUBLIC_METHODS);

                // Register inner classes (records defined inside tool services)
                for (Class<?> inner : toolService.getDeclaredClasses()) {
                    hints.reflection().registerType(inner,
                            MemberCategory.ACCESS_DECLARED_FIELDS,
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_DECLARED_METHODS);
                }
            }
        }

        private void registerResourceHints(RuntimeHints hints) {
            // Prompt files loaded via ClassPathResource in AIConfig
            String[] prompts = {
                    "prompts/action-training-session.md",
                    "prompts/action-zone.md",
                    "prompts/analysis.md",
                    "prompts/club-management.md",
                    "prompts/coach-management.md",
                    "prompts/common-rules.md",
                    "prompts/general.md",
                    "prompts/planner.md",
                    "prompts/race-completion.md",
                    "prompts/router.md",
                    "prompts/scheduling.md",
                    "prompts/training-creation.md"
            };

            for (String prompt : prompts) {
                hints.resources().registerPattern(prompt);
            }

            // Koval skill folders served by SkillController. Each skill has a SKILL.md
            // at the root and optional resources/ assets — register all of them.
            String[] skillResources = {
                    "skills/koval-analyze-last-ride/SKILL.md",
                    "skills/koval-athlete-onboarding/SKILL.md",
                    "skills/koval-athlete-onboarding/resources/athlete-profile.template.md",
                    "skills/koval-coach-onboarding/SKILL.md",
                    "skills/koval-coach-onboarding/resources/coach-profile.template.md",
                    "skills/koval-coach-weekly-review/SKILL.md",
                    "skills/koval-create-workout/SKILL.md",
                    "skills/koval-find-workout/SKILL.md",
                    "skills/koval-form-check/SKILL.md",
                    "skills/koval-plan-my-week/SKILL.md",
                    "skills/koval-power-curve-report/SKILL.md",
                    "skills/koval-prep-race/SKILL.md",
                    "skills/koval-zone-setup/SKILL.md"
            };
            for (String skill : skillResources) {
                hints.resources().registerPattern(skill);
            }
        }
    }
}
