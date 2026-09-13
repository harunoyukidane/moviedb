// Ports the importer targets — the application's own interfaces (§12.3), never
// direct DB writes. Implementations: PeopleGrpcClient, CatalogueGraphqlClient,
// ArtworkHttpClient. Fakes are used in tests.

export interface PersonUpsert {
  tmdbId: number;
  name: string;
  biography: string;
  birthDate: string | null;
  deathDate: string | null;
  placeOfBirth: string | null;
}

export interface PeoplePort {
  /** Idempotent by tmdb_id: returns the app person id (create-or-find). */
  upsertPerson(person: PersonUpsert): Promise<string>;
}

export interface MovieUpsert {
  tmdbId: number;
  title: string;
  originalTitle: string | null;
  synopsis: string;
  releaseDate: string | null;
  runtimeMinutes: number | null;
  originalLanguage: string | null;
  genreCodes: string[];
}

export interface CreditUpsert {
  movieId: string;
  personId: string;
  roleCode: string;
  characterName: string | null;
  billingOrder: number | null;
  tmdbCreditId: string;
  sourceRoleName: string | null;
}

export interface CataloguePort {
  /** Idempotent by tmdb_id: returns the app movie id. */
  upsertMovie(movie: MovieUpsert): Promise<string>;
  /** Idempotent by tmdb credit id. */
  upsertCredit(credit: CreditUpsert): Promise<string>;
}

export interface ArtworkPort {
  /** Upload/replace a movie poster through the artwork HTTP path. */
  uploadMoviePoster(movieId: string, bytes: Uint8Array, contentType: string): Promise<void>;
  /** Upload/replace a person photo through the person-photo HTTP path. */
  uploadPersonPhoto(personId: string, bytes: Uint8Array, contentType: string): Promise<void>;
}
