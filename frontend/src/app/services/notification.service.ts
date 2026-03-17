import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject} from 'rxjs';
import {environment} from '../../environments/environment';
import {FirebaseApp, initializeApp} from 'firebase/app';
import {getMessaging, getToken, MessagePayload, Messaging, onMessage} from 'firebase/messaging';

@Injectable({
  providedIn: 'root',
})
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/api/notifications`;

  private app: FirebaseApp | null = null;
  private messaging: Messaging | null = null;
  private currentToken: string | null = null;

  private foregroundMessageSubject = new BehaviorSubject<MessagePayload | null>(null);
  foregroundNotification$ = this.foregroundMessageSubject.asObservable();

  init(): void {
    try {
      this.app = initializeApp(environment.firebase);
      this.messaging = getMessaging(this.app);

      onMessage(this.messaging, (payload) => {
        this.foregroundMessageSubject.next(payload);
      });
    } catch (e) {
      console.warn('Firebase messaging init failed:', e);
    }
  }

  async requestPermissionAndRegisterToken(): Promise<void> {
    if (!this.messaging) {
      this.init();
    }
    if (!this.messaging) return;

    try {
      const permission = await Notification.requestPermission();
      if (permission !== 'granted') {
        console.info('Notification permission denied');
        return;
      }

      const swRegistration = await navigator.serviceWorker.register('/firebase-messaging-sw.js');

      const token = await getToken(this.messaging, {
        vapidKey: environment.firebaseVapidKey,
        serviceWorkerRegistration: swRegistration,
      });

      if (token && token !== this.currentToken) {
        this.currentToken = token;
        this.http.post(`${this.apiUrl}/register-token`, { token }).subscribe({
          error: (err) => console.warn('Failed to register FCM token:', err),
        });
      }
    } catch (err) {
      console.warn('Failed to get FCM token:', err);
    }
  }

  unregisterToken(): void {
    if (this.currentToken) {
      this.http
        .delete(`${this.apiUrl}/unregister-token`, { body: { token: this.currentToken } })
        .subscribe({
          error: (err) => console.warn('Failed to unregister FCM token:', err),
        });
      this.currentToken = null;
    }
  }
}
