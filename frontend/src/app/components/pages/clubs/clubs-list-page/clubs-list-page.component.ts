import {ChangeDetectionStrategy, Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Router} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ClubService, ClubSummary, ClubVisibility, CreateClubData,} from '../../../../services/club.service';

@Component({
  selector: 'app-clubs-list-page',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './clubs-list-page.component.html',
  styleUrl: './clubs-list-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubsListPageComponent implements OnInit {
  private clubService = inject(ClubService);
  private router = inject(Router);
  private translate = inject(TranslateService);

  userClubs$ = this.clubService.userClubs$;

  publicClubs: ClubSummary[] = [];
  isFormOpen = false;

  form: Partial<CreateClubData> = this.emptyForm();

  readonly visibilityOptions: Array<{ value: ClubVisibility; label: string }> = [
    { value: 'PUBLIC', label: 'CLUBS_LIST.VISIBILITY_PUBLIC' },
    { value: 'PRIVATE', label: 'CLUBS_LIST.VISIBILITY_PRIVATE' },
  ];

  ngOnInit(): void {
    this.clubService.loadUserClubs();
    this.loadPublicClubs();
  }

  loadPublicClubs(): void {
    this.clubService.browsePublicClubs(0).subscribe((clubs) => {
      this.publicClubs = clubs;
    });
  }

  openCreate(): void {
    this.form = this.emptyForm();
    this.isFormOpen = true;
  }

  closeForm(): void {
    this.isFormOpen = false;
  }

  save(): void {
    if (!this.form.name) return;
    this.clubService.createClub(this.form as CreateClubData).subscribe({
      next: (club: any) => {
        this.isFormOpen = false;
        this.loadPublicClubs();
        this.router.navigate(['/clubs', club.id]);
      },
      error: () => {},
    });
  }

  openDetail(id: string): void {
    this.router.navigate(['/clubs', id]);
  }

  join(clubId: string, event: Event): void {
    event.stopPropagation();
    this.clubService.joinClub(clubId).subscribe({
      next: () => this.loadPublicClubs(),
      error: () => {},
    });
  }

  leave(clubId: string, event: Event): void {
    event.stopPropagation();
    this.clubService.leaveClub(clubId).subscribe({
      next: () => this.loadPublicClubs(),
      error: () => {},
    });
  }

  getMembershipLabel(status: string | undefined): string {
    if (!status) return '';
    if (status.startsWith('ACTIVE_OWNER')) return this.translate.instant('CLUBS_LIST.MEMBERSHIP_OWNER');
    if (status.startsWith('ACTIVE_ADMIN')) return this.translate.instant('CLUBS_LIST.MEMBERSHIP_ADMIN');
    if (status.startsWith('ACTIVE')) return this.translate.instant('CLUBS_LIST.MEMBERSHIP_MEMBER');
    if (status.startsWith('PENDING')) return this.translate.instant('CLUBS_LIST.MEMBERSHIP_PENDING');
    return status;
  }

  isMyClub(club: ClubSummary): boolean {
    return !!club.membershipStatus && club.membershipStatus.startsWith('ACTIVE');
  }

  private emptyForm(): Partial<CreateClubData> {
    return { visibility: 'PUBLIC' };
  }
}
