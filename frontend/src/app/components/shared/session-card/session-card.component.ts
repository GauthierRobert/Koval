import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {
  ClubGroup,
  ClubMember,
  ClubTrainingSession,
  GroupLinkedTraining,
  getEffectiveLinkedTrainings,
} from '../../../services/club.service';
import {ResponsiveService} from '../../../services/responsive.service';
import {SportIconComponent} from '../sport-icon/sport-icon.component';
import {RouteMapComponent} from '../../pages/pacing/route-map/route-map.component';
import {RouteCoordinate} from '../../../services/pacing.service';

@Component({
  selector: 'app-session-card',
  standalone: true,
  imports: [CommonModule, TranslateModule, SportIconComponent, RouteMapComponent],
  templateUrl: './session-card.component.html',
  styleUrl: './session-card.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionCardComponent {
  @Input() session!: ClubTrainingSession;
  @Input() members: ClubMember[] = [];
  @Input() currentUserId: string | null = null;
  @Input() clubGroups: ClubGroup[] = [];
  @Input() clubId = '';
  @Input() isCoach = false;
  @Input() canCreate = false;

  @Output() join = new EventEmitter<ClubTrainingSession>();
  @Output() leave = new EventEmitter<ClubTrainingSession>();
  @Output() edit = new EventEmitter<ClubTrainingSession>();
  @Output() cancelSession = new EventEmitter<ClubTrainingSession>();
  @Output() duplicateSession = new EventEmitter<ClubTrainingSession>();
  @Output() linkTraining = new EventEmitter<ClubTrainingSession>();
  @Output() unlinkTraining = new EventEmitter<{ session: ClubTrainingSession; glt: GroupLinkedTraining }>();
  @Output() toggleDetail = new EventEmitter<ClubTrainingSession>();
  @Output() navigateToTraining = new EventEmitter<string>();
  @Output() downloadGpx = new EventEmitter<ClubTrainingSession>();
  @Output() shareGpx = new EventEmitter<ClubTrainingSession>();

  showAllParticipants = false;

  private responsive = inject(ResponsiveService);
  private translate = inject(TranslateService);
  isMobile = toSignal(this.responsive.isMobile$, { initialValue: false });

  // --- Status helpers ---

  isParticipant(): boolean {
    return !!this.currentUserId && this.session.participantIds.includes(this.currentUserId);
  }

  isFull(): boolean {
    return this.session.maxParticipants != null && this.session.participantIds.length >= this.session.maxParticipants;
  }

  isOnWaitingList(): boolean {
    return !!this.currentUserId && !!this.session.waitingList?.some((e) => e.userId === this.currentUserId);
  }

  isInSessionGroup(): boolean {
    if (!this.session.clubGroupId || !this.currentUserId) return true;
    return this.clubGroups
      .filter((g) => g.id === this.session.clubGroupId)
      .some((g) => g.memberIds.includes(this.currentUserId!));
  }

  isOpenToAllNow(): boolean {
    if (!this.session.openToAll || !this.session.scheduledAt) return false;
    const delay = this.session.openToAllDelayValue ?? 2;
    const unit = this.session.openToAllDelayUnit ?? 'DAYS';
    const scheduledMs = new Date(this.session.scheduledAt).getTime();
    const offsetMs = unit === 'HOURS' ? delay * 3600_000 : delay * 86400_000;
    return Date.now() >= scheduledMs - offsetMs;
  }

  canJoin(): boolean {
    if (this.isInSessionGroup()) return true;
    return this.isOpenToAllNow();
  }

  getCannotJoinReason(): string {
    if (!this.session.clubGroupId) return '';
    const groupName = this.getGroupName(this.session.clubGroupId);
    if (!this.session.openToAll) {
      return this.translate.instant('CLUB_SESSIONS.TOOLTIP_RESTRICTED_TO_GROUP', { group: groupName });
    }
    return this.translate.instant('CLUB_SESSIONS.TOOLTIP_NOT_OPEN_YET', { group: groupName });
  }

  getWaitingListPosition(): number {
    if (!this.currentUserId || !this.session.waitingList) return 0;
    const idx = this.session.waitingList.findIndex((e) => e.userId === this.currentUserId);
    return idx >= 0 ? idx + 1 : 0;
  }

  isCancelled(): boolean {
    return !!this.session.cancelled;
  }
  // --- Display helpers ---

  formatTime(): string {
    if (!this.session.scheduledAt) return '';
    return new Date(this.session.scheduledAt).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
  }

  getCreatorName(): string {
    const member = this.members.find((m) => m.userId === this.session.createdBy);
    return member?.displayName || '';
  }

  getGroupName(groupId: string | undefined): string {
    if (!groupId) return '';
    return this.clubGroups.find((g) => g.id === groupId)?.name ?? '';
  }

  getCapacityText(): string {
    if (this.session.maxParticipants == null)
      return this.translate.instant('CLUB_SESSIONS.CAPACITY_PARTICIPANTS', { count: this.session.participantIds.length });
    return `${this.session.participantIds.length}/${this.session.maxParticipants}`;
  }

  getOpenToAllLabel(): string {
    if (!this.session.openToAll || !this.session.scheduledAt) return '';
    const delay = this.session.openToAllDelayValue ?? 2;
    const unit = this.session.openToAllDelayUnit ?? 'DAYS';
    const scheduledMs = new Date(this.session.scheduledAt).getTime();
    const offsetMs = unit === 'HOURS' ? delay * 3600_000 : delay * 86400_000;
    const openFromMs = scheduledMs - offsetMs;
    const nowMs = Date.now();
    if (nowMs >= openFromMs) return this.translate.instant('CLUB_SESSIONS.OPEN_TO_ALL_OPENED');
    const remainMs = openFromMs - nowMs;
    const remainH = Math.ceil(remainMs / 3600_000);
    if (remainH <= 48) return this.translate.instant('CLUB_SESSIONS.OPEN_TO_ALL_IN_HOURS', { remainH });
    const remainD = Math.ceil(remainMs / 86400_000);
    return this.translate.instant('CLUB_SESSIONS.OPEN_TO_ALL_IN_DAYS', { remainD });
  }

  // --- Participant helpers ---

  getParticipantNames(): { name: string; initial: string }[] {
    return this.session.participantIds.map((id) => {
      const member = this.members.find((m) => m.userId === id);
      const name = member?.displayName || id.substring(0, 8);
      return { name, initial: name.charAt(0).toUpperCase() };
    });
  }

  getWaitingListNames(): { name: string; initial: string; position: number }[] {
    return (this.session.waitingList || []).map((entry, i) => {
      const member = this.members.find((m) => m.userId === entry.userId);
      const name = member?.displayName || entry.userId.substring(0, 8);
      return { name, initial: name.charAt(0).toUpperCase(), position: i + 1 };
    });
  }

  // --- Linked trainings ---

  getEffectiveLinkedTrainings(): GroupLinkedTraining[] {
    return getEffectiveLinkedTrainings(this.session);
  }

  hasAnyLinkedTraining(): boolean {
    return this.getEffectiveLinkedTrainings().length > 0;
  }

  canLinkMoreTrainings(): boolean {
    const effective = this.getEffectiveLinkedTrainings();
    if (effective.some((glt) => !glt.clubGroupId)) return false;
    if (this.clubGroups.length === 0) return effective.length === 0;
    const linkedGroupIds = new Set(effective.filter((g) => g.clubGroupId).map((g) => g.clubGroupId));
    return this.clubGroups.some((g) => !linkedGroupIds.has(g.id));
  }

  isInUserGroup(glt: GroupLinkedTraining): boolean {
    if (!glt.clubGroupId) return true;
    return this.getUserGroupIds().has(glt.clubGroupId);
  }

  private getUserGroupIds(): Set<string> {
    if (!this.currentUserId) return new Set();
    return new Set(
      this.clubGroups.filter((g) => g.memberIds.includes(this.currentUserId!)).map((g) => g.id),
    );
  }

  truncate(text: string | undefined, maxLen: number): string {
    if (!text) return '';
    return text.length > maxLen ? text.substring(0, maxLen) + '...' : text;
  }

  // --- GPX helpers ---

  toRouteCoordinates(): RouteCoordinate[] {
    return (this.session.routeCoordinates ?? []) as RouteCoordinate[];
  }

  getElevationGain(): number {
    const coords = this.session.routeCoordinates;
    if (!coords || coords.length < 2) return 0;
    let gain = 0;
    for (let i = 1; i < coords.length; i++) {
      const diff = coords[i].elevation - coords[i - 1].elevation;
      if (diff > 0) gain += diff;
    }
    return Math.round(gain);
  }

  getTotalDistanceKm(): string {
    const coords = this.session.routeCoordinates;
    if (!coords || coords.length === 0) return '0';
    const meters = coords[coords.length - 1].distance;
    return (meters / 1000).toFixed(1);
  }

  // --- Event emitters (stop propagation internally) ---

  onCardClick(): void {
    if (this.isMobile()) return;
    this.toggleDetail.emit(this.session);
  }

  onEdit(event: Event): void {
    event.stopPropagation();
    this.edit.emit(this.session);
  }

  onLinkTraining(event: Event): void {
    event.stopPropagation();
    this.linkTraining.emit(this.session);
  }

  onJoin(event: Event): void {
    event.stopPropagation();
    this.join.emit(this.session);
  }

  onLeave(event: Event): void {
    event.stopPropagation();
    this.leave.emit(this.session);
  }

  onCancelSession(event: Event): void {
    event.stopPropagation();
    this.cancelSession.emit(this.session);
  }

  onDuplicateSession(event: Event): void {
    event.stopPropagation();
    this.duplicateSession.emit(this.session);
  }

  onUnlinkTraining(glt: GroupLinkedTraining, event: Event): void {
    event.stopPropagation();
    this.unlinkTraining.emit({ session: this.session, glt });
  }

  onNavigateToTraining(trainingId: string, event: Event): void {
    event.stopPropagation();
    this.navigateToTraining.emit(trainingId);
  }

  onDownloadGpx(event: Event): void {
    event.stopPropagation();
    this.downloadGpx.emit(this.session);
  }

  onShareGpx(event: Event): void {
    event.stopPropagation();
    this.shareGpx.emit(this.session);
  }

  onToggleAllParticipants(event: Event): void {
    event.stopPropagation();
    this.showAllParticipants = !this.showAllParticipants;
  }

  hasMeetingPoint(): boolean {
    return this.session.meetingPointLat != null && this.session.meetingPointLon != null;
  }

  meetingPointMapUrl(): string {
    return `https://www.google.com/maps?q=${this.session.meetingPointLat},${this.session.meetingPointLon}`;
  }
}
