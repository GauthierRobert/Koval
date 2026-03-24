/* eslint-disable no-undef */
importScripts('https://www.gstatic.com/firebasejs/10.12.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.0/firebase-messaging-compat.js');

firebase.initializeApp({
  apiKey: 'YOUR_API_KEY',
  authDomain: 'YOUR_PROJECT.firebaseapp.com',
  projectId: 'YOUR_PROJECT_ID',
  storageBucket: 'YOUR_PROJECT.appspot.com',
  messagingSenderId: 'YOUR_SENDER_ID',
  appId: 'YOUR_APP_ID',
});

const messaging = firebase.messaging();

messaging.onBackgroundMessage((payload) => {
  const title = payload.notification?.title || 'Koval Training';
  const options = {
    body: payload.notification?.body || '',
    icon: '/assets/logo.svg',
    badge: '/assets/logo.svg',
    data: payload.data || {},
  };

  self.registration.showNotification(title, options);
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();

  const data = event.notification.data || {};
  let url = '/dashboard';

  if (data.type === 'TRAINING_ASSIGNED' || data.type === 'WORKOUT_REMINDER') {
    url = '/calendar';
  } else if (data.type === 'SESSION_CREATED' || data.type === 'SESSION_CANCELLED' || data.type === 'WAITING_LIST_PROMOTED') {
    url = data.clubId ? '/clubs/' + data.clubId : '/dashboard';
  } else if (data.type === 'WORKOUT_COMPLETED') {
    url = '/coach';
  }

  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windowClients) => {
      for (const client of windowClients) {
        if (client.url.includes(self.location.origin) && 'focus' in client) {
          client.focus();
          client.navigate(url);
          return;
        }
      }
      clients.openWindow(url);
    })
  );
});
