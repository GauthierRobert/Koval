# Reference — Training Methods (Index)

Eight pre-defined endurance training methodologies the coach can adopt as a profile preference during onboarding. Each one biases **intensity distribution**, **hallmark sessions**, **periodization** and **prescription style** in a different direction, so generated workouts and plans match the coach's philosophy.

## How this is used

- During `onboarding.md` the coach is offered this menu (or `None — eclectic` for "I mix everything").
- The chosen method is stored on `coach-profile.md` under `Training method`.
- All workflows that design or assign training — `create-workout.md`, `build-plan.md`, `assign-workout.md`, `weekly-review.md` — **read the chosen method's file** from `training-methods/<slug>.md` before deciding session structure. The "How this skill applies it" section is the operative part.
- If `Training method = none / eclectic`, follow the generic rules in `create-workout.md` and `default-zones.md`.

## The 8 methods

| Slug                  | Name                                        | One-line philosophy                                                        |
|-----------------------|---------------------------------------------|----------------------------------------------------------------------------|
| `norwegian`           | Norwegian Method (Double Threshold)         | Twice-weekly sub-threshold doubles, lactate-guided, ~2.5–3.5 mmol/L cap.   |
| `polarized`           | Polarized 80/20 (Seiler)                    | 80% strictly easy + 20% genuinely hard, almost nothing in the middle.      |
| `pyramidal`           | Pyramidal Training                          | Lots of easy, meaningful tempo/threshold, a sliver of VO2 — race-specific. |
| `sweet-spot`          | Sweet Spot / FasCat                         | High-density 88–94% FTP work, max FTP gain per hour. Time-crunched.        |
| `maffetone`           | Maffetone Method (MAF / 180-Formula)        | HR capped at `180 − age` for months — build the aerobic engine first.      |
| `lydiard`             | Lydiard Method                              | Long aerobic base → hills → anaerobic → sharpening, in phases.             |
| `daniels`             | Daniels' Running Formula (VDOT)             | Five precise paces (E/M/T/I/R) seeded from a recent race result.           |
| `block-periodization` | Block Periodization (Issurin / Rønnestad)   | Concentrated 1-week HIIT blocks then 3 weeks maintenance — front-loaded.   |

## Picking a method during onboarding

Ask the coach:

> _"Do you follow a particular coaching philosophy you want me to apply to every workout and plan I generate for your athletes? I can adopt one of these eight methods, or stay eclectic (mix based on each athlete's needs)._
>
> _1. Norwegian Method — double-threshold days, lactate-guided._
> _2. Polarized 80/20 — strictly easy + truly hard, nothing in between._
> _3. Pyramidal — lots of easy + tempo-rich + a touch of VO2._
> _4. Sweet Spot / FasCat — high-density 88–94% FTP, time-crunched cyclists._
> _5. Maffetone (MAF) — heart-rate-capped aerobic, build the engine._
> _6. Lydiard — phased base → hills → anaerobic → race._
> _7. Daniels' VDOT — pace-based E/M/T/I/R from a race result._
> _8. Block Periodization — concentrated HIIT blocks + maintenance._
> _Or None — adapt per athlete."_

Persist their answer (or `none`) to the profile. Future workflows enforce it silently across the whole squad.

## Cross-cutting rules

- The method is a **bias**, not a contract. It applies on top of `default-zones.md` (or the coach's `defaultZoneSystem`). Always honour the **athlete's** `forbiddenEfforts`, `avoid` and `maxSessionMinutes` first — the coach's method never overrides an individual athlete's health/availability rules.
- If a per-athlete request contradicts the coach method (e.g. coach=`maffetone` but the athlete needs VO2 for a 5 k peak), apply the method to the base of the plan and slot the necessary work in the sharpening phase, or surface the conflict to the coach.
- If a method explicitly suits a sport poorly (e.g. `daniels` is run-specific, `sweet-spot` is bike-centric), apply it only to the matching sport in the athlete's plan and fall back to generic rules for other sports.
- The coach's `neverInclude` list still wins over any method's hallmark session.
