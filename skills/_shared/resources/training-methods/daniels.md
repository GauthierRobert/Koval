# Method — Daniels' Running Formula (VDOT)

Also called the **VDOT system**.

## Origin

**Dr. Jack Daniels** (American exercise physiologist, named "World's Best Coach" by *Runner's World* in 2000). Codified in *Daniels' Running Formula* (1998, now 4th edition 2021). Built on his VO2max research at the University of Wisconsin and decades coaching at SUNY Cortland.

## Core principle

Translate a recent race result into a **VDOT number** (pseudo-VO2max), then derive **five precise training paces** so every session targets a specific physiological adaptation at the right intensity.

The five paces:

| Code | Name        | Purpose                         | Approx. effort                |
|------|-------------|---------------------------------|-------------------------------|
| E    | Easy        | Aerobic base, recovery          | ~59–74% VO2max                |
| M    | Marathon    | Marathon-pace tempo             | ~75–84% VO2max                |
| T    | Threshold   | Lactate threshold               | ~83–88% VO2max / "comfortably hard" |
| I    | Interval    | VO2max                          | ~95–100% VO2max               |
| R    | Repetition  | Speed, economy                  | Faster than I, full recovery  |

## Weekly intensity distribution

- **~70–80%** Easy (E)
- **~10–15%** combined Marathon (M) + Threshold (T)
- **~10–15%** combined Interval (I) + Repetition (R)

Pyramidal-to-polarized depending on phase.

## Hallmark sessions

- **Cruise intervals**: **4–6 × 1 mile @ T-pace** with 1 min jog.
- **VO2max intervals**: **5–6 × 1000 m @ I-pace** with equal-time recovery.
- **R-pace strides / 200s** for economy and speed.

## Periodization

Phased four-stage build:
- **Phase I** — Foundation / Injury Prevention (Easy + strides only)
- **Phase II** — Early Quality (R focus)
- **Phase III** — Transition Quality (I focus)
- **Phase IV** — Final Quality (T + race-pace focus)

"2Q" plans (two quality days per week) cycle within each phase.

## Profile fit

Runners of all levels who train **by pace / GPS**, have a recent race result to seed VDOT, and want a transparent rule-based system. Especially strong for **5 k–marathon** focused athletes, 4–7 days/week.

## Anti-fit

Heart-rate-only athletes (no GPS / track). Trail and ultra runners on highly variable terrain where pace is meaningless. Very-low-volume runners (<20 mpw) for whom the quality days may dominate weekly load.

## How this skill applies it

When designing workouts under this method:

1. **Compute VDOT** from the athlete's most recent race or `getPersonalRecords`. Store the seed race in the profile. If no race exists, ask for a recent 5k/10k/half time.
2. Use **pace targets, not %FTP**. For each block: set `intensityTarget` to the % of threshold pace that maps to E/M/T/I/R for that VDOT (see Daniels' tables or vdoto2.com).
3. Block labels use the **letter code**: `"T 4×1mi"`, `"I 5×1k"`, `"R 8×200m"`.
4. Always include **strides** (4–8 × 20 s @ R-pace) at the end of Easy days — a Daniels signature.
5. **Run-specific.** For cycling/swim sports the athlete also trains, fall back to default methods.
6. Re-test VDOT every 4–6 weeks when the athlete races; bump targets accordingly.

## References

- Jack Daniels, *Daniels' Running Formula* 4th ed. (2021).
- VDOT calculator — vdoto2.com.
- Coached athletes: Jim Ryun, Joan Benoit, Janet Cherobon-Bawcom.
