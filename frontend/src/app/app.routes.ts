import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { coachGuard } from './guards/coach.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./components/dashboard/dashboard.component').then((m) => m.DashboardComponent),
    canActivate: [authGuard],
  },
  {
    path: 'trainings',
    loadComponent: () =>
      import('./components/workout-selection/workout-selection.component').then(
        (m) => m.WorkoutSelectionComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'active-session',
    loadComponent: () =>
      import('./components/live-dashboard/live-dashboard.component').then(
        (m) => m.LiveDashboardComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'history',
    loadComponent: () =>
      import('./components/workout-history/workout-history.component').then(
        (m) => m.WorkoutHistoryComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'calendar',
    loadComponent: () =>
      import('./components/calendar/calendar.component').then((m) => m.CalendarComponent),
    canActivate: [authGuard],
  },
  {
    path: 'coach',
    loadComponent: () =>
      import('./components/coach-dashboard/coach-dashboard.component').then(
        (m) => m.CoachDashboardComponent,
      ),
    canActivate: [authGuard, coachGuard],
  },
  {
    path: 'zones',
    loadComponent: () =>
      import('./components/zone-manager/zone-manager.component').then((m) => m.ZoneManagerComponent),
    canActivate: [authGuard],
  },
  {
    path: 'chat',
    loadComponent: () =>
      import('./components/ai-chat-page/ai-chat-page.component').then((m) => m.AIChatPageComponent),
    canActivate: [authGuard],
  },
  {
    path: 'analysis/:id',
    loadComponent: () =>
      import('./components/session-analysis/session-analysis.component').then(
        (m) => m.SessionAnalysisComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'pmc',
    loadComponent: () =>
      import('./components/pmc-page/pmc-page.component').then((m) => m.PmcPageComponent),
    canActivate: [authGuard],
  },
  {
    path: 'tags',
    loadComponent: () =>
      import('./components/tag-management/tag-management.component').then(
        (m) => m.TagManagementComponent,
      ),
    canActivate: [authGuard, coachGuard],
  },
  {
    path: 'physiology',
    loadComponent: () =>
      import('./components/physiology-page/physiology-page.component').then(
        (m) => m.PhysiologyPageComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'goals',
    loadComponent: () =>
      import('./components/goals-page/goals-page.component').then((m) => m.GoalsPageComponent),
    canActivate: [authGuard],
  },
  {
    path: 'onboarding',
    loadComponent: () =>
      import('./components/onboarding/onboarding.component').then((m) => m.OnboardingComponent),
    canActivate: [authGuard],
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./components/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'auth/callback',
    loadComponent: () =>
      import('./components/auth/auth-callback.component').then((m) => m.AuthCallbackComponent),
  },
  {
    path: 'auth/google/callback',
    loadComponent: () =>
      import('./components/auth/auth-callback.component').then((m) => m.AuthCallbackComponent),
  },
];