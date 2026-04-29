import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {forkJoin, of} from 'rxjs';
import {map, switchMap, catchError} from 'rxjs/operators';
import {ClubService} from '../../../../services/club.service';
import {ClubSessionService} from '../../../../services/club-session.service';
import {AuthService} from '../../../../services/auth.service';
import {ClubSummary, ClubTrainingSession} from '../../../../models/club.model';

export interface ClubRow {
  club: ClubSummary;
  memberCount: number | null;
  nextSession: ClubTrainingSession | null;
}


@Component({
  selector: 'app-dashboard-club-cards',
  standalone: true,
  imports: [CommonModule, TranslateModule],
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

  rows: ClubRow[] = [];
  loading = true;

  ngOnInit(): void {
    this.authService.user$.subscribe(u => {
      if (u?.id) {
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
          return of([] as ClubRow[]);
        }
        const today = new Date();
        const from = today.toISOString();
        const future = new Date(today);
        future.setDate(future.getDate() + 14);
        const to = future.toISOString();

        const requests = clubs.map(club =>
          forkJoin({
            detail: this.clubService.getClubDetail(club.id),
            sessions: this.clubSessionService.getSessionsForClub(club.id, from, to, 'SCHEDULED').pipe(
              catchError(() => of([] as ClubTrainingSession[])),
            ),
          }).pipe(
            map(({ detail, sessions }) => {
              const nextSession = sessions
                .filter(s => !s.cancelled && s.scheduledAt)
                .sort((a, b) => (a.scheduledAt ?? '').localeCompare(b.scheduledAt ?? ''))[0] ?? null;
              return {
                club,
                memberCount: detail?.memberCount ?? null,
                nextSession,
              } as ClubRow;
            }),
          ),
        );
        return forkJoin(requests);
      }),
    ).subscribe(rows => {
      this.rows = rows;
      this.loading = false;
      this.cdr.markForCheck();
    });
  }

  getClubInitials(name: string): string {
    return name.split(/\s+/).map(w => w[0]).join('').substring(0, 2).toUpperCase();
  }

  formatNextSession(scheduledAt: string): string {
    const d = new Date(scheduledAt);
    const now = new Date();
    const tomorrow = new Date(now);
    tomorrow.setDate(tomorrow.getDate() + 1);

    const time = d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', hour12: false });

    if (d.toDateString() === now.toDateString()) return `Today ${time}`;
    if (d.toDateString() === tomorrow.toDateString()) return `Tomorrow ${time}`;
    return d.toLocaleDateString(undefined, { weekday: 'short' }).toUpperCase() + ' ' + time;
  }

  navigateToClub(clubId: string): void {
    this.router.navigate(['/clubs', clubId]);
  }

  navigateToClubsList(): void {
    this.router.navigate(['/clubs']);
  }

  trackByClubId(_: number, item: ClubRow): string {
    return item.club.id;
  }
}
