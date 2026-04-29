import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable} from 'rxjs';
import {tap} from 'rxjs/operators';
import {environment} from '../../environments/environment';
import {
  ClubGazetteEditionResponse,
  ClubGazetteEditionSummary,
  ClubGazettePostResponse,
  ClubGazettePostsResponse,
  CommentEntry,
  CreateGazettePostRequest,
  UpdateGazettePostRequest,
} from '../models/club-gazette.model';

interface AddCommentRequest {
  content: string;
}

/**
 * Frontend wrapper around the gazette REST API. Uses BehaviorSubjects for the
 * editions list and the currently-viewed edition; consumers should subscribe
 * via {@code | async}.
 */
@Injectable({providedIn: 'root'})
export class ClubGazetteService {
  private http = inject(HttpClient);

  private editionsSubject = new BehaviorSubject<ClubGazetteEditionSummary[]>([]);
  editions$ = this.editionsSubject.asObservable();

  private currentEditionSubject = new BehaviorSubject<ClubGazetteEditionResponse | null>(null);
  currentEdition$ = this.currentEditionSubject.asObservable();

  private postsSubject = new BehaviorSubject<ClubGazettePostsResponse | null>(null);
  posts$ = this.postsSubject.asObservable();

  private base(clubId: string): string {
    return `${environment.apiUrl}/api/clubs/${clubId}/gazette`;
  }

  loadEditions(clubId: string, page = 0, size = 20): void {
    this.http
      .get<ClubGazetteEditionSummary[]>(`${this.base(clubId)}/editions`, {params: {page, size}})
      .subscribe((list) => this.editionsSubject.next(list));
  }

  getEdition(clubId: string, editionId: string): Observable<ClubGazetteEditionResponse> {
    return this.http
      .get<ClubGazetteEditionResponse>(`${this.base(clubId)}/editions/${editionId}`)
      .pipe(tap((e) => this.currentEditionSubject.next(e)));
  }

  getCurrentDraft(clubId: string): Observable<ClubGazetteEditionResponse> {
    return this.http
      .get<ClubGazetteEditionResponse>(`${this.base(clubId)}/editions/current`)
      .pipe(tap((e) => this.currentEditionSubject.next(e)));
  }

  getOpenDrafts(clubId: string): Observable<ClubGazetteEditionResponse[]> {
    return this.http.get<ClubGazetteEditionResponse[]>(`${this.base(clubId)}/editions/drafts`);
  }

  discardDraft(clubId: string, editionId: string): Observable<void> {
    return this.http.delete<void>(`${this.base(clubId)}/editions/${editionId}`);
  }

  pdfUrl(clubId: string, editionId: string): string {
    return `${this.base(clubId)}/editions/${editionId}/pdf`;
  }

  markAsRead(clubId: string, editionId: string): Observable<void> {
    return this.http.post<void>(`${this.base(clubId)}/editions/${editionId}/read`, {});
  }

  addComment(clubId: string, editionId: string, content: string): Observable<CommentEntry> {
    const body: AddCommentRequest = {content};
    return this.http.post<CommentEntry>(`${this.base(clubId)}/editions/${editionId}/comments`, body);
  }

  // ── Posts ──────────────────────────────────────────────────────────────

  loadPosts(clubId: string, editionId: string): void {
    this.http
      .get<ClubGazettePostsResponse>(`${this.base(clubId)}/editions/${editionId}/posts`)
      .subscribe((res) => this.postsSubject.next(res));
  }

  createPost(clubId: string, editionId: string,
             req: CreateGazettePostRequest): Observable<ClubGazettePostResponse> {
    return this.http
      .post<ClubGazettePostResponse>(`${this.base(clubId)}/editions/${editionId}/posts`, req)
      .pipe(tap(() => this.loadPosts(clubId, editionId)));
  }

  updatePost(clubId: string, editionId: string, postId: string,
             req: UpdateGazettePostRequest): Observable<ClubGazettePostResponse> {
    return this.http
      .patch<ClubGazettePostResponse>(`${this.base(clubId)}/editions/${editionId}/posts/${postId}`, req)
      .pipe(tap(() => this.loadPosts(clubId, editionId)));
  }

  deletePost(clubId: string, editionId: string, postId: string): Observable<void> {
    return this.http
      .delete<void>(`${this.base(clubId)}/editions/${editionId}/posts/${postId}`)
      .pipe(tap(() => this.loadPosts(clubId, editionId)));
  }

  myCurrentDraftPosts(clubId: string): Observable<ClubGazettePostResponse[]> {
    return this.http.get<ClubGazettePostResponse[]>(`${this.base(clubId)}/editions/current/posts/mine`);
  }
}
