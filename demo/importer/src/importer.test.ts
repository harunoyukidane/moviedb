import { describe, expect, it } from 'vitest';
import { importMovie, runImport, mapGenres, resolveBirthCountry, selectCredits, type Dependencies } from './importer.js';
import { commentsFor } from './comments.js';
import type {
  ArtworkPort,
  CataloguePort,
  CommentsPort,
  CommentUpsert,
  CountryCode,
  PeoplePort,
  MovieUpsert,
  CreditUpsert,
  PersonUpsert
} from './ports.js';
import { InvalidTokenError, NotFoundError, TransientError } from './errors.js';
import type { TmdbMovie, TmdbPersonDetails } from './tmdb.js';

// --- in-memory fakes standing in for the application interfaces ---

const FAKE_COUNTRIES: CountryCode[] = [
  { code: 'US', name: 'United States', active: true },
  { code: 'GB', name: 'United Kingdom', active: true },
  { code: 'FR', name: 'France', active: true }
];

class FakePeople implements PeoplePort {
  byTmdb = new Map<number, string>();
  upserts: PersonUpsert[] = [];
  calls = 0;
  async upsertPerson(p: PersonUpsert): Promise<string> {
    this.calls++;
    this.upserts.push(p);
    const existing = this.byTmdb.get(p.tmdbId);
    if (existing) return existing; // idempotent by tmdb id
    const id = `person-${p.tmdbId}`;
    this.byTmdb.set(p.tmdbId, id);
    return id;
  }
  async listCountries(): Promise<CountryCode[]> {
    return FAKE_COUNTRIES;
  }
}

class FakeCatalogue implements CataloguePort {
  movies = new Map<number, string>();
  credits = new Map<string, string>(); // tmdbCreditId -> id
  async upsertMovie(m: MovieUpsert): Promise<string> {
    const existing = this.movies.get(m.tmdbId);
    if (existing) return existing;
    const id = `movie-${m.tmdbId}`;
    this.movies.set(m.tmdbId, id);
    return id;
  }
  async upsertCredit(c: CreditUpsert): Promise<string> {
    const existing = this.credits.get(c.tmdbCreditId);
    if (existing) return existing;
    const id = `credit-${c.tmdbCreditId}`;
    this.credits.set(c.tmdbCreditId, id);
    return id;
  }
}

class FakeComments implements CommentsPort {
  bySeedKey = new Map<string, CommentUpsert>();
  calls = 0;
  async upsertComment(c: CommentUpsert): Promise<string> {
    this.calls++;
    const existing = this.bySeedKey.get(c.seedKey);
    if (existing) {
      this.bySeedKey.set(c.seedKey, c); // upsert-in-place, matching the real backend
      return `comment-${c.seedKey}`;
    }
    this.bySeedKey.set(c.seedKey, c);
    return `comment-${c.seedKey}`;
  }
}

class FakeArtwork implements ArtworkPort {
  uploads = new Map<string, number>();
  photoUploads = new Map<string, number>();
  fail = false;
  async uploadMoviePoster(movieId: string): Promise<void> {
    if (this.fail) throw new Error('upload failed');
    this.uploads.set(movieId, (this.uploads.get(movieId) ?? 0) + 1);
  }
  async uploadPersonPhoto(personId: string): Promise<void> {
    if (this.fail) throw new Error('photo upload failed');
    this.photoUploads.set(personId, (this.photoUploads.get(personId) ?? 0) + 1);
  }
}

// A fake TMDB client (structurally compatible with what importer uses).
class FakeTmdb {
  constructor(
    private movieProvider: (id: number) => Promise<TmdbMovie>,
    private posterProvider: () => Promise<{ bytes: Uint8Array; contentType: string } | null> = async () => ({
      bytes: new Uint8Array([1, 2, 3]),
      contentType: 'image/jpeg'
    }),
    private personProvider: (id: number) => Promise<TmdbPersonDetails> = async (id) => ({
      id,
      name: `Person ${id}`,
      biography: '',
      birthday: null,
      deathday: null,
      place_of_birth: null
    })
  ) {}
  getMovie(id: number) {
    return this.movieProvider(id);
  }
  getPoster() {
    return this.posterProvider();
  }
  getPerson(id: number) {
    return this.personProvider(id);
  }
  getProfileImage(profilePath: string | null | undefined) {
    if (!profilePath) return Promise.resolve(null);
    return Promise.resolve({ bytes: new Uint8Array([4, 5, 6]), contentType: 'image/jpeg' });
  }
}

function sampleMovie(id: number): TmdbMovie {
  return {
    id,
    title: `Movie ${id}`,
    original_title: `Orig ${id}`,
    overview: 'A film.',
    release_date: '1980-05-23',
    runtime: 120,
    original_language: 'en',
    poster_path: '/poster.jpg',
    genres: [{ id: 27, name: 'Horror' }, { id: 9999, name: 'Unmapped' }],
    credits: {
      cast: [
        { id: 100, name: 'Star One', character: 'Hero', order: 0, credit_id: 'c-100', profile_path: '/p100.jpg' },
        { id: 100, name: 'Star One', character: 'Hero2', order: 1, credit_id: 'c-100b' }, // same person twice
        { id: 101, name: 'Star Two', character: 'Villain', order: 2, credit_id: 'c-101' }
      ],
      crew: [
        { id: 200, name: 'Dir Ector', job: 'Director', credit_id: 'c-200' },
        { id: 201, name: 'Wri Ter', job: 'Screenplay', credit_id: 'c-201' },
        { id: 202, name: 'Cam Era', job: 'Director of Photography', credit_id: 'c-202' } // considered but unmapped -> skipped
      ]
    }
  };
}

function deps(
  tmdb: FakeTmdb,
  people = new FakePeople(),
  catalogue = new FakeCatalogue(),
  artwork = new FakeArtwork(),
  comments = new FakeComments()
): Dependencies & { people: FakePeople; catalogue: FakeCatalogue; artwork: FakeArtwork; comments: FakeComments } {
  return { tmdb: tmdb as any, people, catalogue, artwork, comments } as any;
}

describe('mapping helpers', () => {
  it('maps only controlled genre ids, dropping unmapped', () => {
    expect(mapGenres(sampleMovie(1))).toEqual(['HORROR']);
  });

  it('maps a movie tagged with several TMDB genre ids to all their controlled codes', () => {
    const movie = {
      ...sampleMovie(2),
      genres: [
        { id: 28, name: 'Action' },
        { id: 80, name: 'Crime' },
        { id: 53, name: 'Thriller' },
        { id: 9999, name: 'Unmapped' },
      ],
    };
    expect(mapGenres(movie).sort()).toEqual(['ACTION', 'CRIME', 'THRILLER']);
  });

  it('selects top cast + mapped crew, skipping unmapped crew jobs', () => {
    const sel = selectCredits(sampleMovie(1));
    const roles = sel.map((s) => s.roleCode);
    expect(roles).toContain('ACTOR');
    expect(roles).toContain('DIRECTOR');
    expect(roles).toContain('WRITER');
    // Director of Photography has no controlled code -> not selected
    expect(sel.find((s) => s.sourceRoleName === 'Director of Photography')).toBeUndefined();
    // Screenplay maps to WRITER but preserves the original job as source role
    expect(sel.find((s) => s.sourceRoleName === 'Screenplay')?.roleCode).toBe('WRITER');
  });
});

describe('importMovie', () => {
  it('imports a movie, dedupes people by tmdb id, and uploads a poster', async () => {
    const d = deps(new FakeTmdb(async (id) => sampleMovie(id)));
    const outcome = await importMovie(42, d);

    expect(outcome.status).toBe('imported');
    expect(outcome.movieId).toBe('movie-42');
    // cast people 100 (twice), 101 + crew 200, 201 (DoP 202 skipped) -> 4 distinct
    expect(d.people.byTmdb.size).toBe(4);
    expect(d.people.byTmdb.has(100)).toBe(true);
    expect(outcome.posterImported).toBe(true);
    expect(outcome.creditsImported).toBeGreaterThanOrEqual(4);
    // person 100 has a profile_path -> exactly one photo upload despite two cast rows
    expect(d.artwork.photoUploads.get('person-100')).toBe(1);
    expect(outcome.photosImported).toBe(1);
  });

  it('a failed person-photo upload does not fail the movie import', async () => {
    const artwork = new FakeArtwork();
    artwork.fail = true; // both poster and photo uploads throw
    const outcome = await importMovie(43, deps(new FakeTmdb(async (id) => sampleMovie(id)), new FakePeople(), new FakeCatalogue(), artwork));
    expect(outcome.status).toBe('imported');
    expect(outcome.photosImported).toBe(0);
  });

  it('is idempotent: a second run produces identical record counts (no duplicates)', async () => {
    const people = new FakePeople();
    const catalogue = new FakeCatalogue();
    const artwork = new FakeArtwork();
    const d1 = deps(new FakeTmdb(async (id) => sampleMovie(id)), people, catalogue, artwork);
    await importMovie(42, d1);
    const moviesAfter1 = catalogue.movies.size;
    const creditsAfter1 = catalogue.credits.size;
    const peopleAfter1 = people.byTmdb.size;

    // rerun with the SAME fakes (same tmdb keys) -> upserts, not inserts
    await importMovie(42, d1);
    expect(catalogue.movies.size).toBe(moviesAfter1);
    expect(catalogue.credits.size).toBe(creditsAfter1);
    expect(people.byTmdb.size).toBe(peopleAfter1);
  });

  it('401 InvalidTokenError is fatal and propagates (no infinite retry here)', async () => {
    const d = deps(new FakeTmdb(async () => { throw new InvalidTokenError(); }));
    await expect(importMovie(1, d)).rejects.toBeInstanceOf(InvalidTokenError);
  });

  it('404 records a skip and does not throw', async () => {
    const d = deps(new FakeTmdb(async () => { throw new NotFoundError('404'); }));
    const outcome = await importMovie(999999, d);
    expect(outcome.status).toBe('skipped');
  });

  it('an unresolved transient error records a failure without crashing', async () => {
    const d = deps(new FakeTmdb(async () => { throw new TransientError('gave up'); }));
    const outcome = await importMovie(5, d);
    expect(outcome.status).toBe('failed');
    expect(outcome.error).toContain('gave up');
  });

  it('missing poster still imports the movie (no artwork)', async () => {
    const tmdb = new FakeTmdb(async (id) => ({ ...sampleMovie(id), poster_path: null }), async () => null);
    const outcome = await importMovie(7, deps(tmdb));
    expect(outcome.status).toBe('imported');
    expect(outcome.posterImported).toBe(false);
  });

  it('a failed poster upload does not fail the movie', async () => {
    const artwork = new FakeArtwork();
    artwork.fail = true;
    const outcome = await importMovie(8, deps(new FakeTmdb(async (id) => sampleMovie(id)), new FakePeople(), new FakeCatalogue(), artwork));
    expect(outcome.status).toBe('imported');
    expect(outcome.posterImported).toBe(false);
  });

  it('handles a movie with no credits (partial data)', async () => {
    const tmdb = new FakeTmdb(async (id) => ({ ...sampleMovie(id), credits: { cast: [], crew: [] } }));
    const outcome = await importMovie(9, deps(tmdb));
    expect(outcome.status).toBe('imported');
    expect(outcome.creditsImported).toBe(0);
  });

  it('fetches person details and forwards biography/dates/place of birth/country into the upsert', async () => {
    const people = new FakePeople();
    const tmdb = new FakeTmdb(
      async (id) => sampleMovie(id),
      undefined,
      async (id) => ({
        id,
        name: `Person ${id}`,
        biography: 'A notable career.',
        birthday: '1960-01-01',
        deathday: null,
        place_of_birth: 'Los Angeles, California, USA'
      })
    );
    await importMovie(10, deps(tmdb, people));
    expect(people.upserts).toHaveLength(4); // 4 distinct people, per the dedupe test above
    for (const u of people.upserts) {
      expect(u.biography).toBe('A notable career.');
      expect(u.birthDate).toBe('1960-01-01');
      expect(u.placeOfBirth).toBe('Los Angeles, California, USA');
      expect(u.birthCountryCode).toBe('US'); // "USA" resolved via the alias table
    }
  });

  it('a failed person-detail fetch falls back to empty values instead of failing the person', async () => {
    const people = new FakePeople();
    const tmdb = new FakeTmdb(
      async (id) => sampleMovie(id),
      undefined,
      async () => {
        throw new TransientError('tmdb person lookup failed');
      }
    );
    const outcome = await importMovie(11, deps(tmdb, people));
    expect(outcome.status).toBe('imported');
    for (const u of people.upserts) {
      expect(u.biography).toBe('');
      expect(u.birthDate).toBeNull();
      expect(u.placeOfBirth).toBeNull();
      expect(u.birthCountryCode).toBeNull();
    }
  });
});

describe('resolveBirthCountry', () => {
  const people = { listCountries: async (): Promise<CountryCode[]> => FAKE_COUNTRIES };

  it('returns null for a null place of birth', async () => {
    expect(await resolveBirthCountry(null, people)).toBeNull();
  });

  it('matches via the alias table (USA -> United States)', async () => {
    expect(await resolveBirthCountry('Chicago, Illinois, USA', people)).toBe('US');
  });

  it('matches an exact country name case-insensitively with no alias needed', async () => {
    expect(await resolveBirthCountry('Paris, france', people)).toBe('FR');
  });

  it('leaves the country unset (never fabricates a code) when nothing matches', async () => {
    expect(await resolveBirthCountry('Atlantis', people)).toBeNull();
  });
});

describe('seed comments (V2.2-13)', () => {
  it('seeds the fixture comments for each movie', async () => {
    const d = deps(new FakeTmdb(async (id) => sampleMovie(id)));
    const outcome = await importMovie(42, d);
    const expected = commentsFor(42);
    expect(outcome.commentsImported).toBe(expected.length);
    expect(d.comments.bySeedKey.size).toBe(expected.length);
    for (const c of expected) {
      expect(d.comments.bySeedKey.get(c.seedKey)).toMatchObject({
        authorDisplayName: c.authorDisplayName,
        text: c.text
      });
    }
  });

  it('a rerun produces identical comment counts and content', async () => {
    const comments = new FakeComments();
    const d1 = deps(new FakeTmdb(async (id) => sampleMovie(id)), new FakePeople(), new FakeCatalogue(), new FakeArtwork(), comments);
    await importMovie(42, d1);
    const afterFirst = new Map(comments.bySeedKey);

    await importMovie(42, d1);
    expect(comments.bySeedKey.size).toBe(afterFirst.size);
    for (const [key, value] of afterFirst) {
      expect(comments.bySeedKey.get(key)).toEqual(value);
    }
  });

  it('a movie with enough seeded comments spans more than one comment page', async () => {
    // 694 is the one fixture id deliberately seeded with more than a 10-per-page pager.
    expect(commentsFor(694).length).toBeGreaterThan(10);
  });

  it('a failed comment upsert does not fail the movie import', async () => {
    class FailingComments implements CommentsPort {
      async upsertComment(): Promise<string> {
        throw new Error('comment upsert failed');
      }
    }
    const outcome = await importMovie(42, {
      tmdb: new FakeTmdb(async (id) => sampleMovie(id)) as any,
      people: new FakePeople(),
      catalogue: new FakeCatalogue(),
      artwork: new FakeArtwork(),
      comments: new FailingComments()
    });
    expect(outcome.status).toBe('imported');
    expect(outcome.commentsImported).toBe(0);
  });
});

describe('runImport', () => {
  it('runs the manifest with bounded concurrency and aggregates outcomes; a 404 does not stop others', async () => {
    const tmdb = new FakeTmdb(async (id) => {
      if (id === 2) throw new NotFoundError('404');
      return sampleMovie(id);
    });
    const report = await runImport([1, 2, 3], deps(tmdb), 4);
    expect(report.total).toBe(3);
    expect(report.imported).toBe(2);
    expect(report.skipped).toBe(1);
    expect(report.failed).toBe(0);
  });

  it('propagates a fatal 401 to stop the whole run', async () => {
    const tmdb = new FakeTmdb(async (id) => {
      if (id === 1) throw new InvalidTokenError();
      return sampleMovie(id);
    });
    await expect(runImport([1, 2, 3], deps(tmdb), 1)).rejects.toBeInstanceOf(InvalidTokenError);
  });
});
