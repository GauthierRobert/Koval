import * as WebBrowser from 'expo-web-browser';
import * as Linking from 'expo-linking';
import { API_URL, MOBILE_REDIRECT_URI } from '../constants/env';
import { saveToken, removeToken, apiJson } from './api';

WebBrowser.maybeCompleteAuthSession();

export interface User {
  id: string;
  displayName: string;
  profilePicture?: string;
  role: 'ATHLETE' | 'COACH';
  ftp?: number;
}

interface AuthResponse {
  token: string;
  user: User;
}

/** Step 1: fetch the Google authorization URL from backend (with mobile redirect). */
async function fetchGoogleAuthUrl(): Promise<string> {
  const res = await fetch(
    `${API_URL}/api/auth/google?redirectUri=${encodeURIComponent(MOBILE_REDIRECT_URI)}`
  );
  if (!res.ok) throw new Error('Failed to fetch Google auth URL');
  const data = await res.json();
  return data.authUrl as string;
}

/** Full Google OAuth flow for mobile. Returns the authenticated user. */
export async function loginWithGoogle(): Promise<User> {
  const authUrl = await fetchGoogleAuthUrl();

  // Open the browser — Google redirects to koval://auth/callback?code=...
  const result = await WebBrowser.openAuthSessionAsync(authUrl, MOBILE_REDIRECT_URI);

  if (result.type !== 'success') {
    throw new Error('OAuth cancelled or failed');
  }

  // Parse the deep link URL to extract `code`
  const url = result.url;
  const parsed = Linking.parse(url);
  const code = parsed.queryParams?.['code'] as string | undefined;
  if (!code) {
    throw new Error('No authorization code in callback URL');
  }

  // Exchange the code for a JWT
  const res = await fetch(
    `${API_URL}/api/auth/google/callback?code=${encodeURIComponent(code)}&redirectUri=${encodeURIComponent(MOBILE_REDIRECT_URI)}`
  );
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Token exchange failed: ${text}`);
  }
  const data: AuthResponse = await res.json();
  await saveToken(data.token);
  return data.user;
}

export async function fetchCurrentUser(): Promise<User> {
  return apiJson<User>('/api/auth/me');
}

export async function logout(): Promise<void> {
  await removeToken();
}
