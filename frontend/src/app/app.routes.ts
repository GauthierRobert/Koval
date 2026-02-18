import { Routes } from '@angular/router';
import { WorkoutSelectionComponent } from './components/workout-selection/workout-selection.component';
import { LiveDashboardComponent } from './components/live-dashboard/live-dashboard.component';
import { WorkoutHistoryComponent } from './components/workout-history/workout-history.component';
import { CalendarComponent } from './components/calendar/calendar.component';
import { CoachDashboardComponent } from './components/coach-dashboard/coach-dashboard.component';
import { AIChatPageComponent } from './components/ai-chat-page/ai-chat-page.component';
import { LoginComponent } from './components/auth/login.component';
import { AuthCallbackComponent } from './components/auth/auth-callback.component';
import { authGuard } from './guards/auth.guard';
import { coachGuard } from './guards/coach.guard';
import { ZoneManagerComponent } from './components/zone-manager/zone-manager.component';

export const routes: Routes = [
    { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
    { path: 'dashboard', component: WorkoutSelectionComponent, canActivate: [authGuard] },
    { path: 'active-session', component: LiveDashboardComponent, canActivate: [authGuard] },
    { path: 'history', component: WorkoutHistoryComponent, canActivate: [authGuard] },
    { path: 'calendar', component: CalendarComponent, canActivate: [authGuard] },
    { path: 'coach', component: CoachDashboardComponent, canActivate: [authGuard, coachGuard] },
    { path: 'zones', component: ZoneManagerComponent, canActivate: [authGuard] },
    { path: 'chat', component: AIChatPageComponent, canActivate: [authGuard] },
    { path: 'login', component: LoginComponent },
    { path: 'auth/callback', component: AuthCallbackComponent },
    { path: 'auth/google/callback', component: AuthCallbackComponent },
];
