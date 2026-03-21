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
  // For Google, use 'mobile' hint so backend uses its server-side mobile callback URI.
  // For Strava, custom schemes are allowed so we pass the deep link directly.
  const redirectParam =
    provider === 'google' ? 'mobile' : MOBILE_REDIRECT_URI;
  const res = await fetch(
    `${API_URL}/api/auth/${provider}?redirectUri=${encodeURIComponent(redirectParam)}`
  );
  if (!res.ok) throw new Error(`Failed to fetch ${provider} auth URL`);
  const data = await res.json();
  return data.authUrl as string;
}

/**
 * Google OAuth: the backend handles the callback and redirects to koval://auth/callback?token=xxx.
 * We just open the browser and wait for the deep link with the token.
 */
async function runGoogleOAuthFlow(): Promise<User> {
  const authUrl = await fetchAuthUrl('google');

  const result = await WebBrowser.openAuthSessionAsync(authUrl, MOBILE_REDIRECT_URI);

  if (result.type !== 'success') {
    throw new Error('OAuth cancelled or failed');
  }

  const parsed = Linking.parse(result.url);

  const error = parsed.queryParams?.['error'] as string | undefined;
  if (error) {
    throw new Error(`Authentication failed: ${error}`);
  }

  const token = parsed.queryParams?.['token'] as string | undefined;
  if (!token) {
    throw new Error('No token in callback URL');
  }

  await saveToken(token);
  return fetchCurrentUser();
}

/**
 * Strava OAuth: custom URL schemes are allowed, so the browser redirects
 * back to the app with a code, which we exchange via the backend.
 */
async function runStravaOAuthFlow(): Promise<User> {
  const authUrl = await fetchAuthUrl('strava');

  const result = await WebBrowser.openAuthSessionAsync(authUrl, MOBILE_REDIRECT_URI);

  if (result.type !== 'success') {
    throw new Error('OAuth cancelled or failed');
  }

  const parsed = Linking.parse(result.url);
  const code = parsed.queryParams?.['code'] as string | undefined;
  if (!code) {
    throw new Error('No authorization code in callback URL');
  }

  const callbackUrl = `${API_URL}/api/auth/strava/callback?code=${encodeURIComponent(code)}`;
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
  return runGoogleOAuthFlow();
}

/** Full Strava OAuth flow for mobile. Returns the authenticated user. */
export async function loginWithStrava(): Promise<User> {
  return runStravaOAuthFlow();
}

export async function fetchCurrentUser(): Promise<User> {
  return apiJson<User>('/api/auth/me');
}

export async function logout(): Promise<void> {
  await removeToken();
}
