import { test as base, expect, type APIRequestContext } from '@playwright/test';
import { type TestUser, loginAsCoach, loginAsAthlete, loginAsSecondAthlete, injectAuth } from './auth.fixture';

type Fixtures = {
  apiContext: APIRequestContext;
  coach: TestUser;
  athlete: TestUser;
  athlete2: TestUser;
  authenticatedCoachPage: void;
  authenticatedAthletePage: void;
};

export const test = base.extend<Fixtures>({
  apiContext: async ({ playwright }, use) => {
    const ctx = await playwright.request.newContext();
    await use(ctx);
    await ctx.dispose();
  },

  coach: async ({ apiContext }, use) => {
    const coach = await loginAsCoach(apiContext);
    await use(coach);
  },

  athlete: async ({ apiContext }, use) => {
    const athlete = await loginAsAthlete(apiContext);
    await use(athlete);
  },

  athlete2: async ({ apiContext }, use) => {
    const athlete2 = await loginAsSecondAthlete(apiContext);
    await use(athlete2);
  },

  authenticatedCoachPage: async ({ page, coach }, use) => {
    await injectAuth(page, coach);
    await use();
  },

  authenticatedAthletePage: async ({ page, athlete }, use) => {
    await injectAuth(page, athlete);
    await use();
  },
});

export { expect };
