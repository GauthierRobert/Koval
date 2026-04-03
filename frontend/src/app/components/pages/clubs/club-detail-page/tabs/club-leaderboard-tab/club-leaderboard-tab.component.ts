import {ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, inject, OnInit} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {TranslateModule} from '@ngx-translate/core';
import {ClubFeedService} from '../../../../../../services/club-feed.service';
import {AuthService} from '../../../../../../services/auth.service';

@Component({
  selector: 'app-club-leaderboard-tab',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './club-leaderboard-tab.component.html',
  styleUrl: './club-leaderboard-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubLeaderboardTabComponent implements OnInit {
  private clubFeedService = inject(ClubFeedService);
  private authService = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);
  private destroyRef = inject(DestroyRef);

  leaderboard$ = this.clubFeedService.leaderboard$;
  currentUserId: string | null = null;

  ngOnInit(): void {
    this.authService.user$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((u) => {
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

}
