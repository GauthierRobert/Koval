import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { describe, expect, it, beforeEach } from 'vitest';
import { of } from 'rxjs';

import { PlanDetailPageComponent } from './plan-detail-page.component';
import { PlanService } from '../../../../services/plan.service';
import { DayOfWeek, PlanDay, PlanWeek, TrainingPlan } from '../../../../models/plan.model';

/**
 * Multi-workout-per-day semantics live mostly in onPickerConfirmed / openWorkoutPickerForAdd /
 * openWorkoutPickerForEdit. These tests exercise the mutation logic against an in-memory
 * plan structure — no HTTP, no rendering.
 */
describe('PlanDetailPageComponent — multi-workout days', () => {
  let component: PlanDetailPageComponent;

  function makePlan(days: PlanDay[] = []): TrainingPlan {
    const week: PlanWeek = { weekNumber: 1, days };
    return {
      id: 'plan-1',
      title: 'Tri base',
      sportType: 'BRICK',
      createdBy: 'me',
      startDate: '2030-01-07',
      durationWeeks: 1,
      status: 'DRAFT',
      weeks: [week],
      athleteIds: [],
      createdAt: '2030-01-01T00:00:00Z',
    };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PlanDetailPageComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        // Stub PlanService.updatePlan so the picker logic doesn't try to call the API.
        {
          provide: PlanService,
          useValue: {
            selectedPlan$: of(null),
            progress$: of(null),
            analytics$: of(null),
            updatePlan: () => of({}),
            loadPlan: () => undefined,
            loadProgress: () => undefined,
            loadAnalytics: () => undefined,
          },
        },
      ],
    });
    component = TestBed.createComponent(PlanDetailPageComponent).componentInstance;
  });

  it('append mode creates a new day with one training when the day is empty', () => {
    const plan = makePlan();
    component.pickerWeekNumber = 1;
    component.pickerDayOfWeek = 'MONDAY' as DayOfWeek;
    component.pickerEditIndex = null;

    component.onPickerConfirmed(plan, { trainingId: 'swim-1', notes: 'Easy swim' });

    expect(plan.weeks[0].days).toHaveLength(1);
    expect(plan.weeks[0].days[0].trainingIds).toEqual(['swim-1']);
    expect(plan.weeks[0].days[0].notes).toBe('Easy swim');
  });

  it('append mode adds a second training to an existing day instead of replacing', () => {
    const plan = makePlan([
      { dayOfWeek: 'MONDAY', trainingIds: ['swim-1'], scheduledWorkoutIds: [] },
    ]);
    component.pickerWeekNumber = 1;
    component.pickerDayOfWeek = 'MONDAY' as DayOfWeek;
    component.pickerEditIndex = null;

    component.onPickerConfirmed(plan, { trainingId: 'bike-1', notes: '' });

    expect(plan.weeks[0].days).toHaveLength(1);
    expect(plan.weeks[0].days[0].trainingIds).toEqual(['swim-1', 'bike-1']);
  });

  it('append mode is idempotent on duplicate training (does not add the same id twice)', () => {
    const plan = makePlan([
      { dayOfWeek: 'MONDAY', trainingIds: ['swim-1'], scheduledWorkoutIds: [] },
    ]);
    component.pickerWeekNumber = 1;
    component.pickerDayOfWeek = 'MONDAY' as DayOfWeek;
    component.pickerEditIndex = null;

    component.onPickerConfirmed(plan, { trainingId: 'swim-1', notes: '' });

    expect(plan.weeks[0].days[0].trainingIds).toEqual(['swim-1']);
  });

  it('edit mode replaces the training at the given index, leaving siblings untouched', () => {
    const plan = makePlan([
      {
        dayOfWeek: 'MONDAY',
        trainingIds: ['swim-1', 'bike-1', 'run-1'],
        scheduledWorkoutIds: [],
      },
    ]);
    component.pickerWeekNumber = 1;
    component.pickerDayOfWeek = 'MONDAY' as DayOfWeek;
    component.pickerEditIndex = 1; // replace the bike

    component.onPickerConfirmed(plan, { trainingId: 'bike-2', notes: '' });

    expect(plan.weeks[0].days[0].trainingIds).toEqual(['swim-1', 'bike-2', 'run-1']);
  });

  it('edit mode with result=null removes only that one training and keeps the others', () => {
    const plan = makePlan([
      {
        dayOfWeek: 'MONDAY',
        trainingIds: ['swim-1', 'bike-1', 'run-1'],
        scheduledWorkoutIds: [],
      },
    ]);
    component.pickerWeekNumber = 1;
    component.pickerDayOfWeek = 'MONDAY' as DayOfWeek;
    component.pickerEditIndex = 1; // remove the bike

    component.onPickerConfirmed(plan, null);

    expect(plan.weeks[0].days[0].trainingIds).toEqual(['swim-1', 'run-1']);
  });

  it('edit mode removing the last training drops the day entirely', () => {
    const plan = makePlan([
      { dayOfWeek: 'MONDAY', trainingIds: ['swim-1'], scheduledWorkoutIds: [] },
    ]);
    component.pickerWeekNumber = 1;
    component.pickerDayOfWeek = 'MONDAY' as DayOfWeek;
    component.pickerEditIndex = 0;

    component.onPickerConfirmed(plan, null);

    expect(plan.weeks[0].days).toHaveLength(0);
  });

  it('append mode with result=null on a non-existent day is a no-op', () => {
    const plan = makePlan();
    component.pickerWeekNumber = 1;
    component.pickerDayOfWeek = 'TUESDAY' as DayOfWeek;
    component.pickerEditIndex = null;

    component.onPickerConfirmed(plan, null);

    expect(plan.weeks[0].days).toHaveLength(0);
  });

  it('dayHasWorkouts returns true only when trainingIds is non-empty', () => {
    const plan = makePlan([
      { dayOfWeek: 'MONDAY', trainingIds: ['swim-1'], scheduledWorkoutIds: [] },
      { dayOfWeek: 'TUESDAY', trainingIds: [], scheduledWorkoutIds: [] },
    ]);
    expect(component.dayHasWorkouts(plan.weeks[0], 'MONDAY')).toBe(true);
    expect(component.dayHasWorkouts(plan.weeks[0], 'TUESDAY')).toBe(false);
    expect(component.dayHasWorkouts(plan.weeks[0], 'WEDNESDAY')).toBe(false);
  });

  it('openWorkoutPickerForEdit pre-populates the picker with the targeted training', () => {
    const plan = makePlan([
      {
        dayOfWeek: 'MONDAY',
        trainingIds: ['swim-1', 'bike-1'],
        notes: 'Brick day',
        scheduledWorkoutIds: [],
      },
    ]);
    component.openWorkoutPickerForEdit(plan, plan.weeks[0], 'MONDAY' as DayOfWeek, 1);

    expect(component.pickerOpen).toBe(true);
    expect(component.pickerEditIndex).toBe(1);
    expect(component.pickerCurrentTrainingId).toBe('bike-1');
    expect(component.pickerCurrentNotes).toBe('Brick day');
  });

  it('openWorkoutPickerForAdd leaves currentTrainingId undefined and editIndex null', () => {
    const plan = makePlan([
      {
        dayOfWeek: 'MONDAY',
        trainingIds: ['swim-1'],
        notes: 'Existing',
        scheduledWorkoutIds: [],
      },
    ]);
    component.openWorkoutPickerForAdd(plan, plan.weeks[0], 'MONDAY' as DayOfWeek);

    expect(component.pickerOpen).toBe(true);
    expect(component.pickerEditIndex).toBeNull();
    expect(component.pickerCurrentTrainingId).toBeUndefined();
    expect(component.pickerCurrentNotes).toBe('Existing');
  });
});
