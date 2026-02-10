import { Routes } from '@angular/router';
import { WorkoutSelectionComponent } from './components/workout-selection/workout-selection.component';
import { LiveDashboardComponent } from './components/live-dashboard/live-dashboard.component';
import { WorkoutHistoryComponent } from './components/workout-history/workout-history.component';
import { CalendarComponent } from './components/calendar/calendar.component';
import { CoachDashboardComponent } from './components/coach-dashboard/coach-dashboard.component';
import { AIChatPageComponent } from './components/ai-chat-page/ai-chat-page.component';
import { LoginComponent } from './components/auth/login.component';
import { AuthCallbackComponent } from './components/auth/auth-callback.component';

export const routes: Routes = [
    { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
    { path: 'dashboard', component: WorkoutSelectionComponent },
    { path: 'active-session', component: LiveDashboardComponent },
    { path: 'history', component: WorkoutHistoryComponent },
    { path: 'calendar', component: CalendarComponent },
    { path: 'coach', component: CoachDashboardComponent },
    { path: 'chat', component: AIChatPageComponent },
    { path: 'login', component: LoginComponent },
    { path: 'auth/callback', component: AuthCallbackComponent },
];
