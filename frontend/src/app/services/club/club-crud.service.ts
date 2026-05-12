import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {catchError, tap} from 'rxjs/operators';
import {environment} from '../../../environments/environment';
import {
  ClubDetail,
  ClubSummary,
  CreateClubData,
  MyClubRoleEntry,
} from '../../models/club.model';

@Injectable({providedIn: 'root'})
export class ClubCrudService {
  private readonly apiUrl = `${environment.apiUrl}/api/clubs`;
  private http = inject(HttpClient);

  private userClubsSubject = new BehaviorSubject<ClubSummary[]>([]);
  userClubs$ = this.userClubsSubject.asObservable();

  private userClubsLoadingSubject = new BehaviorSubject<boolean>(true);
  userClubsLoading$ = this.userClubsLoadingSubject.asObservable();

  private selectedClubSubject = new BehaviorSubject<ClubDetail | null>(null);
  selectedClub$ = this.selectedClubSubject.asObservable();

  private myClubRolesSubject = new BehaviorSubject<MyClubRoleEntry[]>([]);
  myClubRoles$ = this.myClubRolesSubject.asObservable();

  loadUserClubs(): void {
    this.userClubsLoadingSubject.next(true);
    this.http
      .get<ClubSummary[]>(this.apiUrl)
      .pipe(catchError(() => of([] as ClubSummary[])))
      .subscribe((clubs) => {
        this.userClubsSubject.next(clubs);
        this.userClubsLoadingSubject.next(false);
      });
  }

  browsePublicClubs(page = 0): Observable<ClubSummary[]> {
    return this.http
      .get<ClubSummary[]>(`${this.apiUrl}/public`, {params: {page: page.toString(), size: '20'}})
      .pipe(catchError(() => of([] as ClubSummary[])));
  }

  createClub(data: CreateClubData): Observable<ClubSummary> {
    return this.http
      .post<ClubSummary>(this.apiUrl, data)
      .pipe(tap(() => this.loadUserClubs()));
  }

  loadClubDetail(id: string): void {
    this.selectedClubSubject.next(null);
    this.http
      .get<ClubDetail>(`${this.apiUrl}/${id}`)
      .pipe(catchError(() => of(null as ClubDetail | null)))
      .subscribe((club) => this.selectedClubSubject.next(club));
  }

  getClubDetail(id: string): Observable<ClubDetail | null> {
    return this.http
      .get<ClubDetail>(`${this.apiUrl}/${id}`)
      .pipe(catchError(() => of(null as ClubDetail | null)));
  }

  loadMyClubRoles(): void {
    this.http
      .get<MyClubRoleEntry[]>(`${this.apiUrl}/my-roles`)
      .pipe(catchError(() => of([] as MyClubRoleEntry[])))
      .subscribe((roles) => this.myClubRolesSubject.next(roles));
  }

  resetSelected(): void {
    this.selectedClubSubject.next(null);
  }
}
