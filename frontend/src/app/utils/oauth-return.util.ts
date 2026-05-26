/**
 * Completes an MCP / OAuth authorization handshake by redirecting the browser back to the
 * `returnTo` URL (the backend `/oauth/authorize` endpoint) with the user's JWT appended.
 *
 * Shared by the `/login` route guard (already-authenticated users) and `auth-callback`
 * (fresh provider logins) so the completion logic lives in exactly one place.
 *
 * @returns `true` if a redirect was performed, `false` if `returnTo` or `token` was missing.
 */
export function completeOauthReturn(returnTo: string | null, token: string | null): boolean {
  if (!returnTo || !token) {
    return false;
  }
  const separator = returnTo.includes('?') ? '&' : '?';
  window.location.href = returnTo + separator + 'token=' + encodeURIComponent(token);
  return true;
}
