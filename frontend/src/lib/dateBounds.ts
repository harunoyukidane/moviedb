// Client-side mirrors of the server's date-semantics bounds (V2.2-03 domain
// rules; V2.2-09 UI mirrors). Progressive enhancement only: the server stays
// the authority and re-validates regardless — these only steer the native
// date picker's allowed range so an obviously-out-of-bounds date can't even
// be picked.

function todayUtc(): Date {
  const now = new Date();
  return new Date(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()));
}

function toIsoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

/** Release date floor: the first film ever made (matches MovieRules.RELEASE_DATE_FLOOR). */
export const RELEASE_DATE_MIN = '1888-10-14';

/** Release date ceiling: today + 10 years (matches MovieRules.RELEASE_DATE_HORIZON_YEARS). */
export function releaseDateMax(): string {
  const d = todayUtc();
  d.setUTCFullYear(d.getUTCFullYear() + 10);
  return toIsoDate(d);
}

/** Birth date floor (matches PersonRules.BIRTH_DATE_FLOOR). */
export const BIRTH_DATE_MIN = '1850-01-01';

/** Birth date ceiling: today - 2 years (matches PersonRules.BIRTH_DATE_MIN_AGE_YEARS). */
export function birthDateMax(): string {
  const d = todayUtc();
  d.setUTCFullYear(d.getUTCFullYear() - 2);
  return toIsoDate(d);
}

/** Death date floor: same as birth date floor - nobody died before 1850 either. */
export const DEATH_DATE_MIN = BIRTH_DATE_MIN;

/** Death date ceiling: today - a person can't die in the future. */
export function deathDateMax(): string {
  return toIsoDate(todayUtc());
}
