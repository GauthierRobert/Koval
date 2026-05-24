import { Provenance } from './ai-analysis.model';

/** Section title → markdown content. */
export type ContextSections = Record<string, string>;

/** The caller's own context (athlete self-context, or coach philosophy). */
export interface MyContext {
  role: 'ATHLETE' | 'COACH';
  sections: ContextSections | null;
  provenance?: Provenance | null;
  updatedAt?: string | null;
}

/** A single stored context document. */
export interface ContextEntry {
  sections: ContextSections | null;
  provenance?: Provenance | null;
  updatedAt?: string | null;
}

/** A coach's view of an athlete: the athlete's self-context plus the coach's private notes. */
export interface CoachAthleteContext {
  athleteSelf: ContextEntry | null;
  coachContext: ContextEntry | null;
}

/** Recommended section headings, aligned with the onboarding skill templates. */
export const ATHLETE_CONTEXT_SECTIONS: readonly string[] = [
  'Identity',
  'Goals',
  'Weekly availability',
  'Workout style',
  'Body, recovery & constraints',
  'Targets & data',
  'Voice & communication',
];

export const COACH_PHILOSOPHY_SECTIONS: readonly string[] = [
  'Coaching philosophy',
  'Volume & week shape',
  'Workout structure',
  'Targets & zones',
  'Recovery & monitoring',
  'Voice & communication',
];

export const COACH_ATHLETE_CONTEXT_SECTIONS: readonly string[] = [
  'Plan for this athlete',
  'Strengths & limiters',
  'Constraints to respect',
  'Notes',
];

/** Per-section guidance: what to write and why it matters. Shown under the
 * section label to replace the generic "Add details…" placeholder so athletes
 * and coaches know exactly what shapes the AI's plans and messages. */
export interface SectionGuidance {
  /** What this section is asking for and why it matters. */
  hint: string;
  /** A concrete example shown inside the empty textarea. */
  placeholder: string;
}

export const ATHLETE_CONTEXT_SECTION_GUIDANCE: Record<string, SectionGuidance> = {
  Identity: {
    hint: 'Who you are as an athlete: discipline focus, years training, biggest wins or setbacks, what got you into this. Helps the AI write to you — not a generic athlete profile.',
    placeholder:
      'e.g. 36, triathlete since 2019, two Ironman 70.3 finishes. Strong cyclist, average runner, working on swim form. Train mostly to stay sane.',
  },
  Goals: {
    hint: 'The races, times or breakthroughs you are chasing this season, and the deeper reason behind them. Frames every workout decision and how aggressive the plan should be.',
    placeholder:
      'e.g. Sub-5h at Nice 70.3 in September. Stretch goal: Kona slot in 2027. Why: prove to myself I can train smart through a busy work year.',
  },
  'Weekly availability': {
    hint: 'Realistic training windows — which days, what time of day, total weekly hours, immovable commitments. So the plan fits around your life instead of asking you to break it.',
    placeholder:
      'e.g. 8–10 hrs/week. Mon/Wed/Fri early AM (60–75 min). Tue/Thu lunch swim. Long ride Sat 3–4 hrs. Sunday off — family day.',
  },
  'Workout style': {
    hint: 'Sessions you love and ones that crush your motivation: indoor vs outdoor, group vs solo, music or podcast, fasted, structured intervals vs free riding. Lets the AI pick formats you will actually finish.',
    placeholder:
      'e.g. Love long Zwift races, hate steady Z2 trainer rides. Outdoor whenever possible. Group runs on Saturday. No music for hard sessions.',
  },
  'Body, recovery & constraints': {
    hint: 'Injuries (past and current), sleep patterns, stress levels, allergies, life events. Lets the plan adapt intensity, impact and recovery — and flag when something looks risky.',
    placeholder:
      'e.g. Chronic right Achilles flare-up — no back-to-back hard runs. 6 hrs sleep most weeknights. High work stress Q1. Lactose intolerant.',
  },
  'Targets & data': {
    hint: 'Current FTP, CSS, threshold pace and HR zones, plus the devices and data sources you trust. Without this, every intensity target is a guess.',
    placeholder:
      'e.g. FTP 265W (tested March). Run threshold 4:15/km. Swim CSS 1:38/100m. Garmin 965 + Stages power. Trust HR more than power on hot days.',
  },
  'Voice & communication': {
    hint: 'The tone you respond to — pushy or gentle, terse or detailed, when to celebrate, when to back off. Sets how the AI (and your coach) talks to you.',
    placeholder:
      'e.g. Blunt and short — skip the cheerleading. Tell me when I am undertraining. Weekly check-in is enough; no daily reminders.',
  },
};

export const COACH_PHILOSOPHY_SECTION_GUIDANCE: Record<string, SectionGuidance> = {
  'Coaching philosophy': {
    hint: 'What you stand for in one paragraph — what makes a "good" plan to you. The AI uses this as the north star for every plan and workout it builds.',
    placeholder:
      'e.g. Consistency beats intensity. Plans should be repeatable for 12+ weeks without burnout. I would rather an athlete miss one hard session than three easy ones.',
  },
  'Volume & week shape': {
    hint: 'Typical weekly hours/TSS ranges, your intensity distribution (e.g. 80/20), how rest weeks land. Drives how the AI builds periodization for your athletes.',
    placeholder:
      'e.g. 8–14 hrs/week base block, 80/20 polarized. 3 weeks build + 1 recovery. Long ride Sat, brick Sun. Threshold work midweek only.',
  },
  'Workout structure': {
    hint: 'The session formats you reach for — interval prescriptions, warm-up/cool-down norms, the signatures that mark a workout as yours. The AI mimics your patterns instead of inventing new ones.',
    placeholder:
      'e.g. Favorite: 4x8min @ 105% FTP, 4min easy. Always 15min warm-up with 3x30s openers. Cool-down 10min Z1. No sweet spot in build phase.',
  },
  'Targets & zones': {
    hint: 'Preferred zone system per sport, testing cadence, how you set and revise FTP/threshold. Keeps prescribed targets aligned with how you actually coach.',
    placeholder:
      'e.g. Coggan 7-zone for bike, Daniels for run. FTP test every 6 weeks via 20-min protocol. Round HR zones down — never up.',
  },
  'Recovery & monitoring': {
    hint: 'How you read fatigue — HRV, RPE, sleep, mood, TSB thresholds — and the rules that trigger cutting volume. Plans adjust dynamically using your guardrails.',
    placeholder:
      'e.g. TSB below -25 → cut next hard session. HRV drop > 10% for 3 days → recovery week. Always trust subjective RPE over numbers.',
  },
  'Voice & communication': {
    hint: 'Your tone with athletes, how often you check in, how you deliver hard feedback. Shapes every AI-generated message that goes out under your name.',
    placeholder:
      'e.g. Warm but direct. Weekly summary every Monday morning. Hard truths in private DM, never in club chat. Avoid emojis except 💪 after a PR.',
  },
};

export const COACH_ATHLETE_CONTEXT_SECTION_GUIDANCE: Record<string, SectionGuidance> = {
  'Plan for this athlete': {
    hint: 'Where you are taking them and the shape of the next 8–12 weeks. The AI assigns work that follows your direction instead of guessing.',
    placeholder:
      'e.g. Building toward Nice 70.3 in Sept. 4 weeks general prep → 6 weeks specific → 2 weeks taper. Bike-led block first, then add run volume.',
  },
  'Strengths & limiters': {
    hint: 'What this athlete does well, what holds them back, what your history together has taught you. Lets the AI personalize intensity and focus.',
    placeholder:
      'e.g. Diesel engine — great Z2 durability. Limiter: high-cadence work falls apart > 100rpm. Tends to over-train when life gets stressful.',
  },
  'Constraints to respect': {
    hint: 'Injuries, life events, no-go days, equipment limits — private to your relationship with this athlete. Hard rules the AI will never cross.',
    placeholder:
      'e.g. No running > 60min on consecutive days (Achilles). No training Wednesdays — kids handover. Pool access only Mon/Wed/Fri AM.',
  },
  Notes: {
    hint: 'Anything else worth remembering — race tactics, conversations, life changes, things you want to revisit. Your private working memory for this athlete.',
    placeholder:
      'e.g. Mentioned in last call: planning to switch jobs in May, expect 3 weeks of disrupted training. Wants to try a duathlon by year end.',
  },
};
