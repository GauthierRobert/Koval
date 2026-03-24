import {Routes} from '@angular/router';
import {authGuard} from './guards/auth.guard';
import {coachGuard} from './guards/coach.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./components/pages/dashboard/dashboard.component').then((m) => m.DashboardComponent),
    canActivate: [authGuard],
  },
  {
    path: 'trainings',
    loadComponent: () =>
      import('./components/pages/workout-selection/workout-selection.component').then(
        (m) => m.WorkoutSelectionComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'builder',
    loadComponent: () =>
      import('./components/pages/workout-builder/workout-builder.component').then(
        (m) => m.WorkoutBuilderComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'builder/:id',
    loadComponent: () =>
      import('./components/pages/workout-builder/workout-builder.component').then(
        (m) => m.WorkoutBuilderComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'active-session',
    loadComponent: () =>
      import('./components/pages/live-session/live-dashboard.component').then(
        (m) => m.LiveDashboardComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'history',
    loadComponent: () =>
      import('./components/pages/workout-history/workout-history.component').then(
        (m) => m.WorkoutHistoryComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'calendar',
    loadComponent: () =>
      import('./components/pages/calendar/calendar.component').then((m) => m.CalendarComponent),
    canActivate: [authGuard],
  },
  {
    path: 'coach',
    loadComponent: () =>
      import('./components/pages/coach-dashboard/coach-dashboard.component').then(
        (m) => m.CoachDashboardComponent,
      ),
    canActivate: [authGuard, coachGuard],
  },
  {
    path: 'zones',
    loadComponent: () =>
      import('./components/pages/zone-manager/zone-manager.component').then((m) => m.ZoneManagerComponent),
    canActivate: [authGuard],
  },
  {
    path: 'chat',
    loadComponent: () =>
      import('./components/pages/ai-chat-page/ai-chat-page.component').then((m) => m.AIChatPageComponent),
    canActivate: [authGuard],
  },
  {
    path: 'analysis/:sessionId',
    loadComponent: () =>
      import('./components/pages/workout-history/workout-history.component').then(
        (m) => m.WorkoutHistoryComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'pmc',
    loadComponent: () =>
      import('./components/pages/pmc-page/pmc-page.component').then((m) => m.PmcPageComponent),
    canActivate: [authGuard],
  },
  {
    path: 'analytics',
    loadComponent: () =>
      import('./components/pages/analytics/analytics-page.component').then(
        (m) => m.AnalyticsPageComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'groups',
    loadComponent: () =>
      import('./components/pages/group-management/group-management.component').then(
        (m) => m.GroupManagementComponent,
      ),
    canActivate: [authGuard, coachGuard],
  },
  {
    path: 'physiology',
    loadComponent: () =>
      import('./components/pages/physiology-page/physiology-page.component').then(
        (m) => m.PhysiologyPageComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'goals',
    loadComponent: () =>
      import('./components/pages/goals-page/goals-page.component').then((m) => m.GoalsPageComponent),
    canActivate: [authGuard],
  },
  {
    path: 'races',
    loadComponent: () =>
      import('./components/pages/races-page/races-page.component').then((m) => m.RacesPageComponent),
    canActivate: [authGuard],
  },
  {
    path: 'pacing',
    loadComponent: () =>
      import('./components/pages/pacing/pacing-page.component').then((m) => m.PacingPageComponent),
    canActivate: [authGuard],
  },
  //TODO temporary — plans disabled
  // {
  //   path: 'plans',
  //   loadComponent: () =>
  //     import('./components/pages/plans/plan-list-page/plan-list-page.component').then(
  //       (m) => m.PlanListPageComponent,
  //     ),
  //   canActivate: [authGuard],
  // },
  // {
  //   path: 'plans/:id',
  //   loadComponent: () =>
  //     import('./components/pages/plans/plan-detail-page/plan-detail-page.component').then(
  //       (m) => m.PlanDetailPageComponent,
  //     ),
  //   canActivate: [authGuard],
  // },
  {
    path: 'onboarding',
    loadComponent: () =>
      import('./components/pages/onboarding/onboarding.component').then((m) => m.OnboardingComponent),
    canActivate: [authGuard],
  },
  {
    path: 'clubs',
    loadComponent: () =>
      import('./components/pages/clubs/clubs-list-page/clubs-list-page.component').then(
        (m) => m.ClubsListPageComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'clubs/:id',
    loadComponent: () =>
      import('./components/pages/clubs/club-detail-page/club-detail-page.component').then(
        (m) => m.ClubDetailPageComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./components/pages/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'auth/callback',
    loadComponent: () =>
      import('./components/pages/auth/auth-callback.component').then((m) => m.AuthCallbackComponent),
  },
  {
    path: 'auth/google/callback',
    loadComponent: () =>
      import('./components/pages/auth/auth-callback.component').then((m) => m.AuthCallbackComponent),
  },
  { path: '**', redirectTo: '/dashboard' },
];
