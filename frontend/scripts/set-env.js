#!/usr/bin/env node

/**
 * Reads Firebase config from .env file (local) or process.env (CI)
 * and generates the Angular environment files + service worker.
 */

const fs = require('fs');
const path = require('path');

const frontendDir = path.resolve(__dirname, '..');

// Parse .env file if it exists (CI uses process.env directly)
const envPath = path.join(frontendDir, '.env');
if (fs.existsSync(envPath)) {
  const lines = fs.readFileSync(envPath, 'utf-8').split('\n');
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eqIndex = trimmed.indexOf('=');
    if (eqIndex === -1) continue;
    const key = trimmed.substring(0, eqIndex);
    const value = trimmed.substring(eqIndex + 1);
    if (!process.env[key]) {
      process.env[key] = value;
    }
  }
}

const required = [
  'FIREBASE_API_KEY',
  'FIREBASE_AUTH_DOMAIN',
  'FIREBASE_PROJECT_ID',
  'FIREBASE_STORAGE_BUCKET',
  'FIREBASE_MESSAGING_SENDER_ID',
  'FIREBASE_APP_ID',
];

const missing = required.filter((k) => !process.env[k]);
if (missing.length) {
  console.error(`Missing required env vars: ${missing.join(', ')}`);
  console.error('Copy .env.template to .env and fill in the values.');
  process.exit(1);
}

const firebase = {
  apiKey: process.env.FIREBASE_API_KEY,
  authDomain: process.env.FIREBASE_AUTH_DOMAIN,
  projectId: process.env.FIREBASE_PROJECT_ID,
  storageBucket: process.env.FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.FIREBASE_APP_ID,
  measurementId: process.env.FIREBASE_MEASUREMENT_ID || '',
};
const vapidKey = process.env.FIREBASE_VAPID_KEY || '';

// --- environment.ts (dev) ---
const envDev = `export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080',
  firebase: {
    apiKey: '${firebase.apiKey}',
    authDomain: '${firebase.authDomain}',
    projectId: '${firebase.projectId}',
    storageBucket: '${firebase.storageBucket}',
    messagingSenderId: '${firebase.messagingSenderId}',
    appId: '${firebase.appId}',
    measurementId: '${firebase.measurementId}',
  },
  firebaseVapidKey: '${vapidKey}',
};
`;

// --- environment.prod.ts ---
const envProd = `export const environment = {
  production: true,
  apiUrl: 'https://api.koval-sky.com',
  firebase: {
    apiKey: '${firebase.apiKey}',
    authDomain: '${firebase.authDomain}',
    projectId: '${firebase.projectId}',
    storageBucket: '${firebase.storageBucket}',
    messagingSenderId: '${firebase.messagingSenderId}',
    appId: '${firebase.appId}',
    measurementId: '${firebase.measurementId}',
  },
  firebaseVapidKey: '${vapidKey}',
};
`;

// --- custom-sw.js (ngsw + Firebase messaging combined) ---
const sw = `/* Import Angular service worker */
importScripts('./ngsw-worker.js');

/* Firebase messaging for push notifications */
importScripts('https://www.gstatic.com/firebasejs/11.0.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/11.0.0/firebase-messaging-compat.js');

firebase.initializeApp({
  apiKey: '${firebase.apiKey}',
  authDomain: '${firebase.authDomain}',
  projectId: '${firebase.projectId}',
  storageBucket: '${firebase.storageBucket}',
  messagingSenderId: '${firebase.messagingSenderId}',
  appId: '${firebase.appId}',
});

const messaging = firebase.messaging();

messaging.onBackgroundMessage((payload) => {
  const notificationTitle = payload.notification?.title || 'Training Planner';
  const notificationOptions = {
    body: payload.notification?.body || '',
    icon: '/assets/icons/icon-192x192.png',
    data: payload.data,
  };

  self.registration.showNotification(notificationTitle, notificationOptions);
});
`;

// Write files
const envsDir = path.join(frontendDir, 'src', 'environments');
fs.writeFileSync(path.join(envsDir, 'environment.ts'), envDev);
fs.writeFileSync(path.join(envsDir, 'environment.prod.ts'), envProd);
fs.writeFileSync(path.join(frontendDir, 'src', 'custom-sw.js'), sw);

console.log('Environment files generated from .env');