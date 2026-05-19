# Reference — Training Methods (Index)

Eight pre-defined endurance training methodologies the athlete can adopt as a profile preference during onboarding. Each one biases **intensity distribution**, **hallmark sessions**, **periodization** and **prescription style** in a different direction.

## How this is used

- During `onboarding.md` the athlete is offered this menu (or `None — adaptive` for no specific philosophy).
- The chosen method is stored on `athlete-profile.md` under `Training method`.
- All other workflows — `create-workout.md`, `plan-my-week.md`, `prep-race.md` — **read the chosen method's file** from `training-methods/<slug>.md` before designing or selecting sessions. The "How this skill applies it" section in each file is the operative part.
- If `Training method = none / adaptive`, follow the generic rules in `create-workout.md` and `default-zones.md`.

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

Ask the athlete:

> _"Do you follow a particular training philosophy I should respect? I can apply one of these eight methods, or stay adaptive (mix everything based on your goals)._
>
> _1. Norwegian Method — double-threshold days, lactate-guided._
> _2. Polarized 80/20 — strictly easy + truly hard, nothing in between._
> _3. Pyramidal — lots of easy + tempo-rich + a touch of VO2._
> _4. Sweet Spot / FasCat — high-density 88–94% FTP, time-crunched cyclists._
> _5. Maffetone (MAF) — heart-rate-capped aerobic, build the engine._
> _6. Lydiard — phased base → hills → anaerobic → race._
> _7. Daniels' VDOT — pace-based E/M/T/I/R from your race time._
> _8. Block Periodization — concentrated HIIT blocks + maintenance._
> _Or None — let me adapt to each goal."_

Persist their answer (or `none`) to the profile. Future workflows enforce it silently.

## Cross-cutting rules

- The method is a **bias**, not a contract. It applies on top of `default-zones.md` (or the athlete's custom Default Zone System). Always honour `forbiddenEfforts`, `avoid` and `maxSessionMinutes` from the profile first — a method never overrides health/availability rules.
- If the athlete's request directly contradicts their method (e.g. method=`maffetone` but they ask for "5×5 VO2"), flag once, offer the closest method-compatible alternative, only override if they confirm.
- If a method explicitly suits a sport poorly (e.g. `daniels` is run-specific), apply it only to the matching sport and fall back to generic rules for other sports the athlete also trains.
