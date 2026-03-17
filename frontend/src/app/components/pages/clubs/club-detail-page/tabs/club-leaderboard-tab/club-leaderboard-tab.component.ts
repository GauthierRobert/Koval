import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ClubService} from '../../../../../../services/club.service';
import {AuthService} from '../../../../../../services/auth.service';

@Component({
  selector: 'app-club-leaderboard-tab',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './club-leaderboard-tab.component.html',
  styleUrl: './club-leaderboard-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubLeaderboardTabComponent implements OnInit {
  private clubService = inject(ClubService);
  private authService = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);

  leaderboard$ = this.clubService.leaderboard$;
  currentUserId: string | null = null;

  ngOnInit(): void {
    this.authService.user$.subscribe((u) => {
      this.currentUserId = u?.id ?? null;
      this.cdr.markForCheck();
    });
  }

  getMaxTss(entries: { weeklyTss: number }[]): number {
    return entries.length > 0 ? Math.max(...entries.map((e) => e.weeklyTss)) : 1;
  }

  getBarWidth(tss: number, maxTss: number): number {
    if (maxTss === 0) return 0;
    return Math.round((tss / maxTss) * 100);
  }

  getRankIcon(rank: number): string {
    if (rank === 1) return '🥇';
    if (rank === 2) return '🥈';
    if (rank === 3) return '🥉';
    return `#${rank}`;
  }
}
