import { describe, expect, it } from 'vitest';
import { FakeHubSession } from './FakeHubSession';

describe('FakeHubSession', () => {
  it('answers from its permission list', () => {
    const session = new FakeHubSession(['a']);
    expect(session.has('a')).toBe(true);
    expect(session.has('b')).toBe(false);
  });

  it('hands out a token and records sign-out', async () => {
    const session = new FakeHubSession();
    await expect(session.getToken()).resolves.toBe('fake-token');
    await session.signOut();
    expect(session.signedOut).toBe(true);
  });
});
