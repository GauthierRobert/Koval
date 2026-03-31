import { type Page, type APIRequestContext, request } from '@playwright/test';

const API_URL = 'http://localhost:8080';

export interface TestUser {
  id: string;
  displayName: string;
  role: 'COACH' | 'ATHLETE';
  token: string;
}

async function devLogin(
  apiContext: APIRequestContext,
  userId: string,
  displayName: string,
  role: 'COACH' | 'ATHLETE'
): Promise<TestUser> {
  const response = await apiContext.post(`${API_URL}/api/auth/dev/login`, {
    data: { userId, displayName, role },
  });
  if (!response.ok()) {
    throw new Error(`devLogin failed: ${response.status()} ${await response.text()}`);
  }
  const body = await response.json();
  const token = body.token;

  // Accept CGU so the modal doesn't block tests
  if (body.user?.needsCguAcceptance) {
    await apiContext.post(`${API_URL}/api/auth/cgu/accept`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  }

  return { id: userId, displayName, role, token };
}

export async function loginAsCoach(apiContext: APIRequestContext): Promise<TestUser> {
  return devLogin(apiContext, 'e2e-coach', 'E2E Coach', 'COACH');
}

export async function loginAsAthlete(apiContext: APIRequestContext): Promise<TestUser> {
  return devLogin(apiContext, 'e2e-athlete', 'E2E Athlete', 'ATHLETE');
}

export async function loginAsSecondAthlete(apiContext: APIRequestContext): Promise<TestUser> {
  return devLogin(apiContext, 'e2e-athlete-2', 'E2E Athlete 2', 'ATHLETE');
}

export async function injectAuth(page: Page, user: TestUser): Promise<void> {
  await page.addInitScript(
    ({ token, role }) => {
      localStorage.setItem('token', token);
      localStorage.setItem('uiMode', role === 'COACH' ? 'coach' : 'athlete');
    },
    { token: user.token, role: user.role }
  );
}
