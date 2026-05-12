import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {catchError, tap} from 'rxjs/operators';
import {environment} from '../../../environments/environment';
import {ClubGroup} from '../../models/club.model';
import {ClubMembershipService} from './club-membership.service';

@Injectable({providedIn: 'root'})
export class ClubGroupService {
  private readonly apiUrl = `${environment.apiUrl}/api/clubs`;
  private http = inject(HttpClient);
  private membership = inject(ClubMembershipService);

  private groupsSubject = new BehaviorSubject<ClubGroup[]>([]);
  groups$ = this.groupsSubject.asObservable();

  getClubGroups(clubId: string): Observable<ClubGroup[]> {
    return this.http
      .get<ClubGroup[]>(`${this.apiUrl}/${clubId}/groups`)
      .pipe(catchError(() => of([] as ClubGroup[])));
  }

  loadGroups(clubId: string): void {
    this.http
      .get<ClubGroup[]>(`${this.apiUrl}/${clubId}/groups`)
      .pipe(catchError(() => of([] as ClubGroup[])))
      .subscribe((groups) => this.groupsSubject.next(groups));
  }

  createGroup(clubId: string, name: string): Observable<ClubGroup> {
    return this.http
      .post<ClubGroup>(`${this.apiUrl}/${clubId}/groups`, {name})
      .pipe(tap(() => this.loadGroups(clubId)));
  }

  deleteGroup(clubId: string, groupId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${clubId}/groups/${groupId}`).pipe(
      tap(() => {
        this.loadGroups(clubId);
        this.membership.loadMembers(clubId);
      }),
    );
  }

  addMemberToGroup(clubId: string, groupId: string, userId: string): Observable<ClubGroup> {
    return this.http
      .post<ClubGroup>(`${this.apiUrl}/${clubId}/groups/${groupId}/members/${userId}`, {})
      .pipe(
        tap(() => {
          this.loadGroups(clubId);
          this.membership.loadMembers(clubId);
        }),
      );
  }

  removeMemberFromGroup(
    clubId: string,
    groupId: string,
    userId: string,
  ): Observable<ClubGroup> {
    return this.http
      .delete<ClubGroup>(`${this.apiUrl}/${clubId}/groups/${groupId}/members/${userId}`)
      .pipe(
        tap(() => {
          this.loadGroups(clubId);
          this.membership.loadMembers(clubId);
        }),
      );
  }

  joinGroupSelf(clubId: string, groupId: string): Observable<ClubGroup> {
    return this.http.post<ClubGroup>(`${this.apiUrl}/${clubId}/groups/${groupId}/join`, {});
  }

  leaveGroupSelf(clubId: string, groupId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${clubId}/groups/${groupId}/leave`);
  }

  reset(): void {
    this.groupsSubject.next([]);
  }
}
