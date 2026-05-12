import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {catchError, tap} from 'rxjs/operators';
import {environment} from '../../../environments/environment';
import {ClubMember, ClubMemberRole} from '../../models/club.model';
import {ClubCrudService} from './club-crud.service';

@Injectable({providedIn: 'root'})
export class ClubMembershipService {
  private readonly apiUrl = `${environment.apiUrl}/api/clubs`;
  private http = inject(HttpClient);
  private crud = inject(ClubCrudService);

  private membersSubject = new BehaviorSubject<ClubMember[]>([]);
  members$ = this.membersSubject.asObservable();

  private pendingSubject = new BehaviorSubject<ClubMember[]>([]);
  pending$ = this.pendingSubject.asObservable();

  loadMembers(clubId: string): void {
    this.http
      .get<ClubMember[]>(`${this.apiUrl}/${clubId}/members`)
      .pipe(catchError(() => of([] as ClubMember[])))
      .subscribe((members) => this.membersSubject.next(members));
  }

  loadPendingRequests(clubId: string): void {
    this.http
      .get<ClubMember[]>(`${this.apiUrl}/${clubId}/members/pending`)
      .pipe(catchError(() => of([] as ClubMember[])))
      .subscribe((pending) => this.pendingSubject.next(pending));
  }

  joinClub(clubId: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${clubId}/join`, {}).pipe(
      tap(() => {
        this.crud.loadUserClubs();
        this.crud.loadClubDetail(clubId);
      }),
    );
  }

  leaveClub(clubId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${clubId}/leave`).pipe(
      tap(() => {
        this.crud.loadUserClubs();
        this.crud.loadClubDetail(clubId);
      }),
    );
  }

  approveMember(clubId: string, membershipId: string): Observable<void> {
    return this.http
      .post<void>(`${this.apiUrl}/${clubId}/members/${membershipId}/approve`, {})
      .pipe(
        tap(() => {
          this.loadMembers(clubId);
          this.loadPendingRequests(clubId);
          this.crud.loadClubDetail(clubId);
        }),
      );
  }

  rejectMember(clubId: string, membershipId: string): Observable<void> {
    return this.http
      .delete<void>(`${this.apiUrl}/${clubId}/members/${membershipId}/reject`)
      .pipe(tap(() => this.loadPendingRequests(clubId)));
  }

  updateMemberRole(
    clubId: string,
    membershipId: string,
    role: ClubMemberRole,
  ): Observable<void> {
    return this.http
      .put<void>(`${this.apiUrl}/${clubId}/members/${membershipId}/role`, {role})
      .pipe(tap(() => this.loadMembers(clubId)));
  }

  reset(): void {
    this.membersSubject.next([]);
    this.pendingSubject.next([]);
  }
}
