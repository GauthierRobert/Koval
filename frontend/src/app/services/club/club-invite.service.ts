import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {catchError, tap} from 'rxjs/operators';
import {environment} from '../../../environments/environment';
import {ClubInviteCode, ClubMembership} from '../../models/club.model';
import {ClubCrudService} from './club-crud.service';

@Injectable({providedIn: 'root'})
export class ClubInviteService {
  private readonly apiUrl = `${environment.apiUrl}/api/clubs`;
  private http = inject(HttpClient);
  private crud = inject(ClubCrudService);

  private inviteCodesSubject = new BehaviorSubject<ClubInviteCode[]>([]);
  inviteCodes$ = this.inviteCodesSubject.asObservable();

  loadInviteCodes(clubId: string): void {
    this.http
      .get<ClubInviteCode[]>(`${this.apiUrl}/${clubId}/invite-codes`)
      .pipe(catchError(() => of([] as ClubInviteCode[])))
      .subscribe((codes) => this.inviteCodesSubject.next(codes));
  }

  generateInviteCode(
    clubId: string,
    clubGroupId?: string,
    maxUses = 0,
    expiresAt?: string,
  ): Observable<ClubInviteCode> {
    return this.http
      .post<ClubInviteCode>(`${this.apiUrl}/${clubId}/invite-codes`, {
        clubGroupId: clubGroupId || null,
        maxUses,
        expiresAt: expiresAt || null,
      })
      .pipe(tap(() => this.loadInviteCodes(clubId)));
  }

  deactivateInviteCode(clubId: string, codeId: string): Observable<void> {
    return this.http
      .delete<void>(`${this.apiUrl}/${clubId}/invite-codes/${codeId}`)
      .pipe(tap(() => this.loadInviteCodes(clubId)));
  }

  redeemClubInviteCode(code: string): Observable<ClubMembership> {
    return this.http
      .post<ClubMembership>(`${this.apiUrl}/redeem-invite`, {code})
      .pipe(tap(() => this.crud.loadUserClubs()));
  }

  reset(): void {
    this.inviteCodesSubject.next([]);
  }
}
