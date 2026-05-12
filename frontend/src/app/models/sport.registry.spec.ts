import {describe, expect, it} from 'vitest';
import {SPORT_META, sportMeta} from './sport.registry';
import {User} from './user.model';

const baseUser: User = {
  id: 'u1',
  displayName: 'Test',
  profilePicture: '',
  role: 'ATHLETE',
  hasCoach: false,
};

describe('sport.registry', () => {
  it('returns CYCLING metadata when key is missing', () => {
    expect(sportMeta(null)).toBe(SPORT_META.CYCLING);
    expect(sportMeta(undefined)).toBe(SPORT_META.CYCLING);
    expect(sportMeta('')).toBe(SPORT_META.CYCLING);
  });

  it('falls back to CYCLING for unknown sports', () => {
    expect(sportMeta('PARAGLIDING')).toBe(SPORT_META.CYCLING);
  });

  it('normalises case', () => {
    expect(sportMeta('running')).toBe(SPORT_META.RUNNING);
    expect(sportMeta('Swimming')).toBe(SPORT_META.SWIMMING);
  });

  it('returns triathlon disciplines in race order', () => {
    expect(sportMeta('TRIATHLON').gpxDisciplines).toEqual(['swim', 'bike', 'run']);
  });

  it('resolveThreshold(CYCLING) returns user FTP', () => {
    expect(SPORT_META.CYCLING.resolveThreshold({...baseUser, ftp: 250})).toBe(250);
    expect(SPORT_META.CYCLING.resolveThreshold({...baseUser})).toBeNull();
    expect(SPORT_META.CYCLING.resolveThreshold(null)).toBeNull();
  });

  it('resolveThreshold(RUNNING) converts m/s threshold pace to s/km', () => {
    // 4 m/s threshold → 250 s/km
    expect(SPORT_META.RUNNING.resolveThreshold({...baseUser, functionalThresholdPace: 4})).toBe(250);
    expect(SPORT_META.RUNNING.resolveThreshold({...baseUser})).toBeNull();
  });

  it('resolveThreshold(SWIMMING) converts CSS m/s to s/100m', () => {
    // 1.25 m/s CSS → 80 s/100m
    expect(SPORT_META.SWIMMING.resolveThreshold({...baseUser, criticalSwimSpeed: 1.25})).toBe(80);
    expect(SPORT_META.SWIMMING.resolveThreshold({...baseUser})).toBeNull();
  });

  it('GYM has no GPX disciplines and no threshold', () => {
    expect(SPORT_META.GYM.gpxDisciplines).toEqual([]);
    expect(SPORT_META.GYM.resolveThreshold({...baseUser, ftp: 300})).toBeNull();
  });
});
