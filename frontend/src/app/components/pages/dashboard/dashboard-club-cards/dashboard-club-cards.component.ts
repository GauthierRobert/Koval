import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {forkJoin, of} from 'rxjs';
import {map, switchMap, catchError} from 'rxjs/operators';
import {ClubService} from '../../../../services/club.service';
import {ClubSessionService} from '../../../../services/club-session.service';
import {AuthService} from '../../../../services/auth.service';
import {ClubSummary, ClubTrainingSession, WaitingListEntry} from '../../../../models/club.model';
import {SportIconComponent} from '../../../shared/sport-icon/sport-icon.component';

export type SessionStatus = 'not-joined' | 'attending' | 'waitlist' | 'full' | 'no-session';

export interface ClubWithNextSession {
  club: ClubSummary;
  nextSession: ClubTrainingSession | null;
  status: SessionStatus;
  waitlistPosition?: number;
}


@Component({
  selector: 'app-dashboard-club-cards',
  standalone: true,
  imports: [CommonModule, TranslateModule, SportIconComponent],
  templateUrl: './dashboard-club-cards.component.html',
  styleUrl: './dashboard-club-cards.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardClubCardsComponent implements OnInit {
  private clubService = inject(ClubService);
  private clubSessionService = inject(ClubSessionService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);

  clubs: ClubWithNextSession[] = [];
  loading = true;
  private userId: string | null = null;

  ngOnInit(): void {
    this.authService.user$.subscribe(u => {
      this.userId = u?.id ?? null;
      if (this.userId) {
        this.loadClubs();
      }
    });
  }

  private loadClubs(): void {
    this.loading = true;
    this.clubService.loadUserClubs();
    this.clubService.userClubs$.pipe(
      switchMap(clubs => {
        if (clubs.length === 0) {
          return of([] as ClubWithNextSession[]);
        }
        const today = new Date();
        const from = today.toISOString();
        const future = new Date(today);
        future.setDate(future.getDate() + 14);
        const to = future.toISOString();

        const requests = clubs.map(club =>
          this.clubSessionService.getSessionsForClub(club.id, from, to, 'SCHEDULED').pipe(
            map(sessions => {
              const nextSession = sessions
                .filter(s => !s.cancelled && s.scheduledAt)
                .sort((a, b) => (a.scheduledAt ?? '').localeCompare(b.scheduledAt ?? ''))[0] ?? null;
              return {
                club,
                nextSession,
                ...this.getSessionStatus(nextSession),
              } as ClubWithNextSession;
            }),
            catchError(() => of({
              club,
              nextSession: null,
              status: 'no-session' as SessionStatus,
            } as ClubWithNextSession)),
          ),
        );
        return forkJoin(requests);
      }),
    ).subscribe(clubs => {
      this.clubs = clubs;
      this.loading = false;
      this.cdr.markForCheck();
    });
  }

  private getSessionStatus(session: ClubTrainingSession | null): { status: SessionStatus; waitlistPosition?: number } {
    if (!session) return { status: 'no-session' };
    if (!this.userId) return { status: 'not-joined' };

    if (session.participantIds.includes(this.userId)) {
      return { status: 'attending' };
    }

    const wlIndex = session.waitingList?.findIndex(w => w.userId === this.userId) ?? -1;
    if (wlIndex >= 0) {
      return { status: 'waitlist', waitlistPosition: wlIndex + 1 };
    }

    if (session.maxParticipants && session.participantIds.length >= session.maxParticipants) {
      return { status: 'full' };
    }

    return { status: 'not-joined' };
  }

  getClubInitials(name: string): string {
    return name.split(/\s+/).map(w => w[0]).join('').substring(0, 2).toUpperCase();
  }

  getCapacityText(session: ClubTrainingSession): string {
    const count = session.participantIds.length;
    const max = session.maxParticipants;
    if (max) return `${count}/${max}`;
    return `${count}`;
  }

  formatSessionDate(scheduledAt: string): string {
    const d = new Date(scheduledAt);
    const now = new Date();
    const tomorrow = new Date(now);
    tomorrow.setDate(tomorrow.getDate() + 1);

    const time = d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });

    if (d.toDateString() === now.toDateString()) return `Today ${time}`;
    if (d.toDateString() === tomorrow.toDateString()) return `Tomorrow ${time}`;
    return d.toLocaleDateString('en-US', { weekday: 'short' }) + ' ' + time;
  }

  onJoin(club: ClubWithNextSession, event: Event): void {
    event.stopPropagation();
    if (!club.nextSession) return;
    this.clubSessionService.joinSession(club.club.id, club.nextSession.id).subscribe({
      next: () => this.refreshClub(club),
      error: () => {},
    });
  }

  onLeave(club: ClubWithNextSession, event: Event): void {
    event.stopPropagation();
    if (!club.nextSession) return;
    this.clubSessionService.cancelSession(club.club.id, club.nextSession.id).subscribe({
      next: () => this.refreshClub(club),
      error: () => {},
    });
  }

  private refreshClub(club: ClubWithNextSession): void {
    const today = new Date();
    const from = today.toISOString();
    const future = new Date(today);
    future.setDate(future.getDate() + 14);
    const to = future.toISOString();

    this.clubSessionService.getSessionsForClub(club.club.id, from, to, 'SCHEDULED').subscribe(sessions => {
      const nextSession = sessions
        .filter(s => !s.cancelled && s.scheduledAt)
        .sort((a, b) => (a.scheduledAt ?? '').localeCompare(b.scheduledAt ?? ''))[0] ?? null;
      const index = this.clubs.findIndex(c => c.club.id === club.club.id);
      if (index >= 0) {
        this.clubs[index] = {
          ...this.clubs[index],
          nextSession,
          ...this.getSessionStatus(nextSession),
        };
        this.clubs = [...this.clubs];
        this.cdr.markForCheck();
      }
    });
  }

  navigateToClub(clubId: string): void {
    this.router.navigate(['/clubs', clubId]);
  }

  trackByClubId(_: number, item: ClubWithNextSession): string {
    return item.club.id;
  }
}
