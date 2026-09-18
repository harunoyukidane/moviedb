// Deterministic seed-comment fixtures (V2.2-13). Selection is keyed off
// tmdbId only (no Math.random, no Date.now), so a rerun produces
// byte-identical comments — required for the addComment upsert-by-seedKey
// path to actually be a no-op rerun rather than silently drifting content.
//
// Deliberately synthetic: short generic reactions from a fixed, made-up
// author list, never anything that reads as a real review of a real film by
// a real person.

export interface SeededComment {
  seedKey: string;
  authorDisplayName: string;
  text: string;
}

const AUTHORS = [
  'Riley Byte',
  'Sam Reelson',
  'Casey Frame',
  'Jordan Cutt',
  'Morgan Wipe',
  'Alex Marquee',
  'Taylor Loop',
  'Drew Static'
];

const SHORT_TEXTS = [
  'Loved this one.',
  'A classic, holds up.',
  'Solid rewatch material.',
  'Better than I remembered.',
  'Not for everyone, but I liked it.',
  'Would recommend to a friend.'
];

/** Small deterministic hash — no external dependency, stable across Node versions. */
function hashIndex(seed: string, mod: number): number {
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  return h % mod;
}

function pick<T>(pool: T[], seed: string): T {
  return pool[hashIndex(seed, pool.length)];
}

/** Near the 2,000-character bound, to exercise truncation, wrapping, and the live counter. */
function longComment(tmdbId: number): string {
  const filler =
    'This is a deliberately long seeded comment used to exercise the near-2,000-character bound, wrapping, and the character counter. ';
  let text = `Movie #${tmdbId}: `;
  while (text.length < 1950) text += filler;
  return text.slice(0, 1950);
}

// A ZWJ family sequence (man-ZWJ-woman-ZWJ-girl-ZWJ-boy) proves the comment
// path really accepts what V2.2-06 says it does: emoji allowed, joiners
// allowed strictly between two emoji code points.
const EMOJI_HEAVY = '🎬🍿✨ 👨‍👩‍👧‍👦 amazing family movie night!';
const EMOJI_AUTHOR = 'Casey 🎥';

/** Deterministic per-movie comment set, keyed by tmdbId so a rerun matches byte-for-byte. */
export function commentsFor(tmdbId: number): SeededComment[] {
  const out: SeededComment[] = [
    { seedKey: `seed:${tmdbId}:1`, authorDisplayName: pick(AUTHORS, `author:${tmdbId}:1`), text: pick(SHORT_TEXTS, `text:${tmdbId}:1`) },
    { seedKey: `seed:${tmdbId}:2`, authorDisplayName: pick(AUTHORS, `author:${tmdbId}:2`), text: longComment(tmdbId) },
    { seedKey: `seed:${tmdbId}:3`, authorDisplayName: pick(AUTHORS, `author:${tmdbId}:3`), text: EMOJI_HEAVY },
    { seedKey: `seed:${tmdbId}:4`, authorDisplayName: EMOJI_AUTHOR, text: 'Great pick for movie night!' }
  ];

  // The Shining (694, the manifest's first entry) gets enough comments to
  // push past a 10-per-page pager, so pagination is demonstrable on a
  // freshly seeded stack without every movie carrying the extra volume.
  if (tmdbId === 694) {
    for (let n = 5; n <= 12; n++) {
      out.push({
        seedKey: `seed:${tmdbId}:${n}`,
        authorDisplayName: pick(AUTHORS, `author:${tmdbId}:${n}`),
        text: `${pick(SHORT_TEXTS, `text:${tmdbId}:${n}`)} (#${n})`
      });
    }
  }

  return out;
}
