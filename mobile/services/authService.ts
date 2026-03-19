import * as WebBrowser from 'expo-web-browser';
import * as Linking from 'expo-linking';
import {API_URL, MOBILE_REDIRECT_URI} from '../constants/env';
import {apiJson, removeToken, saveToken} from './api';

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

/** Fetch an OAuth authorization URL from the backend. */
async function fetchAuthUrl(provider: 'google' | 'strava'): Promise<string> {
  const res = await fetch(
    `${API_URL}/api/auth/${provider}?redirectUri=${encodeURIComponent(MOBILE_REDIRECT_URI)}`
  );
  if (!res.ok) throw new Error(`Failed to fetch ${provider} auth URL`);
  const data = await res.json();
  return data.authUrl as string;
}

/** Run the OAuth browser flow and extract the authorization code. */
async function runOAuthFlow(provider: 'google' | 'strava'): Promise<string> {
  const authUrl = await fetchAuthUrl(provider);

  const result = await WebBrowser.openAuthSessionAsync(authUrl, MOBILE_REDIRECT_URI);

  if (result.type !== 'success') {
    throw new Error('OAuth cancelled or failed');
  }

  const parsed = Linking.parse(result.url);
  const code = parsed.queryParams?.['code'] as string | undefined;
  if (!code) {
    throw new Error('No authorization code in callback URL');
  }
  return code;
}

/** Exchange an authorization code for a JWT via the backend callback. */
async function exchangeCode(provider: 'google' | 'strava', code: string): Promise<User> {
  const callbackUrl =
    provider === 'google'
      ? `${API_URL}/api/auth/google/callback?code=${encodeURIComponent(code)}&redirectUri=${encodeURIComponent(MOBILE_REDIRECT_URI)}`
      : `${API_URL}/api/auth/strava/callback?code=${encodeURIComponent(code)}`;

  const res = await fetch(callbackUrl);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Token exchange failed: ${text}`);
  }
  const data: AuthResponse = await res.json();
  await saveToken(data.token);
  return data.user;
}

/** Full Google OAuth flow for mobile. Returns the authenticated user. */
export async function loginWithGoogle(): Promise<User> {
  const code = await runOAuthFlow('google');
  return exchangeCode('google', code);
}

/** Full Strava OAuth flow for mobile. Returns the authenticated user. */
export async function loginWithStrava(): Promise<User> {
  const code = await runOAuthFlow('strava');
  return exchangeCode('strava', code);
}

export async function fetchCurrentUser(): Promise<User> {
  return apiJson<User>('/api/auth/me');
}

export async function logout(): Promise<void> {
  await removeToken();
}
