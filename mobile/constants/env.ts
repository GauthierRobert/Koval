// Change the dev IP to your machine's local IP address.
// Run `ipconfig` (Windows) or `ifconfig` (Mac/Linux) to find it.
export const API_URL = __DEV__
  ? 'http://192.168.1.100:8080' // ← only line to change for dev
  : 'https://your-prod-url.com';

export const MOBILE_REDIRECT_URI = 'koval://auth/callback';
