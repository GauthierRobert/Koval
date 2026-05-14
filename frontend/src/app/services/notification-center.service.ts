import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { NotificationService } from './notification.service';

export interface PersistedNotification {
  id: string;
  userId: string;
  type: string;
  title: string;
  body: string;
  data?: { [key: string]: string };
  read: boolean;
  readAt?: string;
  createdAt: string;
}

interface NotificationListResponse {
  notifications: PersistedNotification[];
  total: number;
  page: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationCenterService {
  private readonly http = inject(HttpClient);
  private readonly fcm = inject(NotificationService);
  private readonly apiUrl = `${environment.apiUrl}/api/notifications`;

  private notificationsSubject = new BehaviorSubject<PersistedNotification[]>([]);
  notifications$ = this.notificationsSubject.asObservable();

  private unreadCountSubject = new BehaviorSubject<number>(0);
  unreadCount$ = this.unreadCountSubject.asObservable();

  private totalSubject = new BehaviorSubject<number>(0);
  total$ = this.totalSubject.asObservable();

  constructor() {
    // Refresh unread count whenever a foreground FCM message arrives.
    this.fcm.foregroundNotification$.subscribe((payload) => {
      if (payload) {
        this.refreshUnreadCount();
      }
    });
  }

  loadPage(page = 0, size = 20): void {
    this.http.get<NotificationListResponse>(`${this.apiUrl}?page=${page}&size=${size}`).subscribe({
      next: (resp) => {
        this.notificationsSubject.next(resp.notifications);
        this.totalSubject.next(resp.total);
      },
      error: (err) => console.warn('Failed to load notifications:', err),
    });
  }

  refreshUnreadCount(): void {
    this.http.get<{ count: number }>(`${this.apiUrl}/unread-count`).subscribe({
      next: (resp) => this.unreadCountSubject.next(resp.count),
      error: () => this.unreadCountSubject.next(0),
    });
  }

  markRead(id: string): void {
    this.http
      .post(`${this.apiUrl}/${id}/read`, {})
      .pipe(
        tap(() => {
          const updated = this.notificationsSubject.value.map((n) =>
            n.id === id ? { ...n, read: true, readAt: new Date().toISOString() } : n,
          );
          this.notificationsSubject.next(updated);
          this.unreadCountSubject.next(Math.max(0, this.unreadCountSubject.value - 1));
        }),
      )
      .subscribe({ error: (err) => console.warn('Failed to mark read:', err) });
  }

  markAllRead(): void {
    this.http
      .post<{ marked: number }>(`${this.apiUrl}/read-all`, {})
      .pipe(
        tap(() => {
          const now = new Date().toISOString();
          const updated = this.notificationsSubject.value.map((n) =>
            n.read ? n : { ...n, read: true, readAt: now },
          );
          this.notificationsSubject.next(updated);
          this.unreadCountSubject.next(0);
        }),
      )
      .subscribe({ error: (err) => console.warn('Failed to mark all read:', err) });
  }

  delete(id: string): void {
    this.http
      .delete(`${this.apiUrl}/${id}`)
      .pipe(
        tap(() => {
          const wasUnread = this.notificationsSubject.value.find((n) => n.id === id && !n.read);
          this.notificationsSubject.next(
            this.notificationsSubject.value.filter((n) => n.id !== id),
          );
          if (wasUnread) {
            this.unreadCountSubject.next(Math.max(0, this.unreadCountSubject.value - 1));
          }
        }),
      )
      .subscribe({ error: (err) => console.warn('Failed to delete notification:', err) });
  }
}
