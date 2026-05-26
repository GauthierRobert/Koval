// @vitest-environment jsdom
import { describe, expect, it, beforeEach } from 'vitest';
import { completeOauthReturn } from './oauth-return.util';

describe('completeOauthReturn', () => {
  let href: string;

  beforeEach(() => {
    href = '';
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: {
        get href() {
          return href;
        },
        set href(value: string) {
          href = value;
        },
      },
    });
  });

  it('returns false and does not navigate when returnTo is missing', () => {
    expect(completeOauthReturn(null, 'jwt')).toBe(false);
    expect(href).toBe('');
  });

  it('returns false and does not navigate when token is missing', () => {
    expect(completeOauthReturn('https://api.koval-sky.com/oauth/authorize?x=1', null)).toBe(false);
    expect(href).toBe('');
  });

  it('appends token with & when returnTo already has a query string', () => {
    expect(completeOauthReturn('https://api.koval-sky.com/oauth/authorize?x=1', 'jwt')).toBe(true);
    expect(href).toBe('https://api.koval-sky.com/oauth/authorize?x=1&token=jwt');
  });

  it('appends token with ? when returnTo has no query string', () => {
    expect(completeOauthReturn('https://api.koval-sky.com/oauth/authorize', 'jwt')).toBe(true);
    expect(href).toBe('https://api.koval-sky.com/oauth/authorize?token=jwt');
  });

  it('url-encodes the token', () => {
    expect(completeOauthReturn('https://x/authorize', 'a b/c+d')).toBe(true);
    expect(href).toBe('https://x/authorize?token=a%20b%2Fc%2Bd');
  });
});
