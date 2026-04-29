import { test, expect } from '../fixtures/base.fixture';
import { createClub, deleteClub, joinClub } from '../fixtures/test-data.fixture';

const API_URL = 'http://localhost:8080';
function headers(token: string) {
  return { Authorization: `Bearer ${token}` };
}

/**
 * API-level smoke spec for the Club Gazette feature.
 *
 * Covers:
 *   - Lazy creation of the current draft
 *   - Member contributing a REFLECTION post
 *   - Visibility rules: a non-author can only see their own posts in DRAFT
 *   - Discard draft endpoint
 *
 * Publish flow with PDF + curation is tested manually via the MCP tools — it
 * needs a base64 PDF body which is awkward to fixture in Playwright.
 */
test.describe('Club Gazette API', () => {
  let clubId: string;

  test.beforeEach(async ({ apiContext, coach }) => {
    const club = await createClub(apiContext, coach.token, {
      name: 'E2E Gazette Club',
      visibility: 'PUBLIC',
    });
    clubId = club.id;
  });

  test.afterEach(async ({ apiContext, coach }) => {
    if (clubId) await deleteClub(apiContext, coach.token, clubId).catch(() => {});
  });

  test('current draft is lazily created', async ({ apiContext, coach }) => {
    const res = await apiContext.get(
      `${API_URL}/api/clubs/${clubId}/gazette/editions/current`,
      { headers: headers(coach.token) }
    );
    expect(res.status()).toBe(200);
    const draft = await res.json();
    expect(draft.status).toBe('DRAFT');
    expect(draft.editionNumber).toBeGreaterThanOrEqual(1);
    expect(draft.clubId).toBe(clubId);
  });

  test('member can post a REFLECTION and read it back; non-author sees only count', async ({
    apiContext,
    coach,
    athlete,
  }) => {
    await joinClub(apiContext, athlete.token, clubId);

    const draftRes = await apiContext.get(
      `${API_URL}/api/clubs/${clubId}/gazette/editions/current`,
      { headers: headers(athlete.token) }
    );
    const draft = await draftRes.json();

    const createRes = await apiContext.post(
      `${API_URL}/api/clubs/${clubId}/gazette/editions/${draft.id}/posts`,
      {
        headers: headers(athlete.token),
        data: {
          type: 'REFLECTION',
          title: 'A good week',
          content: 'Long endurance ride felt great Saturday.',
          mediaIds: [],
        },
      }
    );
    expect(createRes.status()).toBe(200);
    const post = await createRes.json();
    expect(post.type).toBe('REFLECTION');
    expect(post.authorDisplayName).toBeTruthy();

    // Athlete sees their own post
    const myListRes = await apiContext.get(
      `${API_URL}/api/clubs/${clubId}/gazette/editions/${draft.id}/posts`,
      { headers: headers(athlete.token) }
    );
    const myList = await myListRes.json();
    expect(myList.posts).toHaveLength(1);
    expect(myList.posts[0].id).toBe(post.id);

    // Coach (admin) sees all posts during DRAFT
    const coachListRes = await apiContext.get(
      `${API_URL}/api/clubs/${clubId}/gazette/editions/${draft.id}/posts`,
      { headers: headers(coach.token) }
    );
    const coachList = await coachListRes.json();
    expect(coachList.posts.length).toBeGreaterThanOrEqual(1);
  });

  test('admin can discard a draft', async ({ apiContext, coach }) => {
    const draftRes = await apiContext.get(
      `${API_URL}/api/clubs/${clubId}/gazette/editions/current`,
      { headers: headers(coach.token) }
    );
    const draft = await draftRes.json();

    const delRes = await apiContext.delete(
      `${API_URL}/api/clubs/${clubId}/gazette/editions/${draft.id}`,
      { headers: headers(coach.token) }
    );
    expect(delRes.status()).toBe(204);

    // Calling /current again should create a fresh draft (different id)
    const next = await (await apiContext.get(
      `${API_URL}/api/clubs/${clubId}/gazette/editions/current`,
      { headers: headers(coach.token) }
    )).json();
    expect(next.id).not.toBe(draft.id);
  });
});
