// Change the dev IP to your machine's local IP address.
// Run `ipconfig` (Windows) or `ifconfig` (Mac/Linux) to find it.
// For Android emulator use 10.0.2.2 which maps to the host machine's localhost.
// For physical device, use your machine's local network IP (e.g. 192.168.x.x).
const DEV_BACKEND_IP = '10.0.2.2'; // ← change to your IP for physical device

export const API_URL = __DEV__
  ? `http://${DEV_BACKEND_IP}:8080`
  : 'https://api.koval-sky.com';

export const MOBILE_REDIRECT_URI = 'koval://auth/callback';
