import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ClubDetail, ClubService, ClubTrainingSession, CreateSessionData } from '../../../../../../services/club.service';
import { AuthService } from '../../../../../../services/auth.service';

@Component({
  selector: 'app-club-sessions-tab',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './club-sessions-tab.component.html',
  styleUrl: './club-sessions-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubSessionsTabComponent implements OnInit {
  @Input() club!: ClubDetail;

  private clubService = inject(ClubService);
  private authService = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);

  sessions$ = this.clubService.sessions$;
  currentUserId: string | null = null;

  isFormOpen = false;
  form: Partial<CreateSessionData> = {};

  readonly sports = ['CYCLING', 'RUNNING', 'SWIMMING', 'TRIATHLON', 'OTHER'];

  ngOnInit(): void {
    this.authService.user$.subscribe((u) => {
      this.currentUserId = u?.id ?? null;
      this.cdr.markForCheck();
    });
  }

  get canCreate(): boolean {
    const role = this.club?.currentMemberRole;
    return role === 'OWNER' || role === 'ADMIN';
  }

  openForm(): void {
    this.form = { sport: 'CYCLING' };
    this.isFormOpen = true;
  }

  closeForm(): void {
    this.isFormOpen = false;
  }

  save(): void {
    if (!this.form.title) return;
    this.clubService.createSession(this.club.id, this.form as CreateSessionData).subscribe({
      next: () => {
        this.isFormOpen = false;
        this.cdr.markForCheck();
      },
      error: () => {},
    });
  }

  joinSession(session: ClubTrainingSession, event: Event): void {
    event.stopPropagation();
    this.clubService.joinSession(this.club.id, session.id).subscribe({ error: () => {} });
  }

  cancelSession(session: ClubTrainingSession, event: Event): void {
    event.stopPropagation();
    this.clubService.cancelSession(this.club.id, session.id).subscribe({ error: () => {} });
  }

  isParticipant(session: ClubTrainingSession): boolean {
    return !!this.currentUserId && session.participantIds.includes(this.currentUserId);
  }

  formatDateTime(dateStr: string | undefined): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleString('en-US', {
      weekday: 'short', month: 'short', day: 'numeric',
      hour: '2-digit', minute: '2-digit',
    });
  }
}
