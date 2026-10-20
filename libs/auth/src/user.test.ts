import { describe, expect, it } from 'vitest';
import { getUserFromToken } from './user';

describe('getUserFromToken', () => {
  it('falls back to a generic user when there is no token', () => {
    expect(getUserFromToken(undefined)).toEqual({
      id: undefined,
      username: 'user',
      firstName: 'user',
      email: undefined,
    });
  });

  it('prefers given_name for the first name', () => {
    const user = getUserFromToken({
      sub: 'abc',
      preferred_username: 'tstark',
      given_name: 'Tony',
      name: 'Anthony Stark',
      email: 'tony@example.com',
    });

    expect(user).toEqual({
      id: 'abc',
      username: 'tstark',
      firstName: 'Tony',
      email: 'tony@example.com',
    });
  });

  it('derives the first name from the full name, then the username', () => {
    expect(
      getUserFromToken({ preferred_username: 'tstark', name: 'Anthony Stark' }).firstName,
    ).toBe('Anthony');
    expect(getUserFromToken({ preferred_username: 'tstark' }).firstName).toBe('tstark');
  });
});
