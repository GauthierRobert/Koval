---
name: koval-athlete-profile
description: Personalised training preferences for <Athlete Name>. Loaded automatically by every Koval athlete skill (koval-plan-my-week, koval-find-workout, koval-prep-race, koval-analyze-last-ride, koval-form-check) so generated plans match this athlete's real-world availability, goals, recovery rules and voice. Re-run koval-athlete-onboarding to update.
---

# Athlete Profile — <Athlete Name>

_Last updated: <YYYY-MM-DD>_

## Identity
- **Sports:** <…>
- **Level:** <beginner / intermediate / competitive / elite>
- **Age / category:** <…>
- **Coached by:** <coach name | self-coached>

## Goals
- **3-month focus:** <…>
- **A-priority event:** <name, date>
- **Definition of a good week:** <…>

## Weekly availability
- **Hours / week:** <range>
- **Available days:** <Mon, Tue, …>
- **Long session day(s):** <…>
- **Rest day(s):** <…>
- **Max session length:** weekday <hh:mm> · weekend <hh:mm>
- **Time of day:** <morning / lunch / evening>

## Workout style
- **Environment:** <indoor / outdoor / mix per sport>
- **Structure preference:** <structured intervals / free / mix>
- **Favourite session types:** <…>
- **Avoid:** <…>

## Body, recovery & constraints
- **Injuries / limitations:** <…>
- **Forbidden efforts:** <…>
- **Sleep baseline:** <good / variable / poor>
- **Logs after sessions:** <RPE / sleep / HRV / notes>

## Targets & data
- **Prescription unit:** <% FTP / watts / HR / RPE / pace>
- **Load metric:** <TSS / hours / km>
- **Default zone system:** <name> (id: <…>)

## Voice & communication
- **Description style:** <terse / detailed / motivational / data>
- **Language:** <…>
- **Coaching tone:** <firm / encouraging / data-driven / playful>
- **Never include:** <list>

## How other skills should use this
Any Koval athlete skill that creates a `Training`, `ScheduledWorkout` or `Plan`, or that proposes a week / taper / workout selection, MUST:
1. Read this file first.
2. Only schedule sessions on **available days**; respect **rest days** and **long session day**.
3. Never exceed **max session length** for the weekday/weekend in question.
4. Honour the **never include** list and **forbidden efforts** absolutely.
5. Use the **prescription unit**, **load metric** and **default zone system** specified here.
6. Write descriptions in the configured **style**, **language** and **tone**.
7. Bias intensity decisions on the **sleep baseline** (poor sleep → push hard sessions back).
