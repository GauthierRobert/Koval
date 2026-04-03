import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {ClubService} from '../../../../services/club.service';

type JoinState = 'loading' | 'success' | 'already_member' | 'error';

@Component({
  selector: 'app-club-join-page',
  standalone: true,
  imports: [CommonModule, RouterModule, TranslateModule],
  templateUrl: './club-join-page.component.html',
  styleUrl: './club-join-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubJoinPageComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private clubService = inject(ClubService);
  private cdr = inject(ChangeDetectorRef);

  state: JoinState = 'loading';
  errorMessage = '';
  private clubId = '';

  ngOnInit(): void {
    const code = this.route.snapshot.paramMap.get('code');
    if (!code) {
      this.state = 'error';
      this.errorMessage = 'CLUB_JOIN.ERROR_NO_CODE';
      this.cdr.markForCheck();
      return;
    }

    this.clubService.redeemClubInviteCode(code).subscribe({
      next: (membership) => {
        this.clubId = membership.clubId;
        this.state = 'success';
        this.cdr.markForCheck();
        setTimeout(() => this.router.navigate(['/clubs', this.clubId]), 1500);
      },
      error: (err) => {
        const msg = err?.error?.message || err?.error || '';
        if (typeof msg === 'string' && msg.toLowerCase().includes('already')) {
          this.state = 'already_member';
          // Try to extract clubId from error or just redirect to clubs list
          this.cdr.markForCheck();
        } else {
          this.state = 'error';
          this.errorMessage = typeof msg === 'string' ? msg : 'CLUB_JOIN.ERROR_GENERIC';
          this.cdr.markForCheck();
        }
      },
    });
  }

  goToClub(): void {
    if (this.clubId) {
      this.router.navigate(['/clubs', this.clubId]);
    }
  }

  goToClubs(): void {
    this.router.navigate(['/clubs']);
  }
}
