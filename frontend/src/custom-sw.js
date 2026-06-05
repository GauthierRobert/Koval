/* Import Angular service worker (only present in production builds) */
try {
  importScripts('./ngsw-worker.js');
} catch (e) {
  /* ngsw-worker.js not available in dev mode — ignore */
}

/* Firebase messaging for push notifications */
importScripts('https://www.gstatic.com/firebasejs/11.0.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/11.0.0/firebase-messaging-compat.js');

firebase.initializeApp({
  apiKey: 'AIzaSyDvRZIPt5Q4sjlg1srELv60kWR_4oycRR8',
  authDomain: 'koval-489519.firebaseapp.com',
  projectId: 'koval-489519',
  storageBucket: 'koval-489519.firebasestorage.app',
  messagingSenderId: '71896495990',
  appId: '1:71896495990:web:c3e63c02d589b572d4ec57',
});

const messaging = firebase.messaging();

messaging.onBackgroundMessage((payload) => {
  /* The backend sends data-only messages so this handler is the single display
     path. A "notification" payload would also be auto-displayed by the FCM SDK
     and the Angular service worker, duplicating every push. */
  const data = payload.data || {};
  const notificationTitle = data.title || payload.notification?.title || 'Training Planner';
  const notificationOptions = {
    body: data.body || payload.notification?.body || '',
    icon: '/assets/icons/icon-192x192.png',
    data: data,
  };

  self.registration.showNotification(notificationTitle, notificationOptions);
});
