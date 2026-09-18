// Domain types derived from the committed Catalogue GraphQL SDL (§8.1).
// Hand-authored to match the schema; kept minimal to what the UI consumes.

export type CreditCategory = 'CAST' | 'CREW';

export interface GenreCode {
  code: string;
  title: string;
  description: string;
  active: boolean;
}

export interface LanguageCode {
  code: string;
  name: string;
  active: boolean;
}

export interface CountryCode {
  code: string;
  name: string;
  active: boolean;
}

export interface CreditRoleCode {
  code: string;
  title: string;
  category: CreditCategory;
  department?: string | null;
  description: string;
  active: boolean;
}

export interface PersonReference {
  id: string;
  name: string;
  available: boolean;
}

export interface Artwork {
  id: string;
  url: string;
  mediaType: string;
  byteSize: number;
}

export interface MovieCredit {
  id: string;
  category: CreditCategory;
  role: CreditRoleCode;
  characterName?: string | null;
  sourceRoleName?: string | null;
  billingOrder?: number | null;
  person: PersonReference;
}

export interface Movie {
  id: string;
  title: string;
  originalTitle?: string | null;
  synopsis: string;
  releaseDate?: string | null;
  runtimeMinutes?: number | null;
  originalLanguage?: string | null;
  language?: LanguageCode | null;
  version: number;
  artwork?: Artwork | null;
  genres: GenreCode[];
  cast: MovieCredit[];
  creators: MovieCredit[];
}

export interface MoviePage {
  items: Movie[];
  total: number;
  limit: number;
  offset: number;
}

export interface PersonCredit {
  movieId: string;
  movieTitle: string;
  category: CreditCategory;
  role: CreditRoleCode;
  characterName?: string | null;
}

export interface Person {
  id: string;
  name: string;
  biography: string;
  birthDate?: string | null;
  deathDate?: string | null;
  placeOfBirth?: string | null;
  birthCountryCode?: string | null;
  birthCountry?: CountryCode | null;
  version: number;
  /** Same-origin proxy path (`/api/people/{id}/photo`), or null when the person has no uploaded photo. */
  photoUrl?: string | null;
  credits: PersonCredit[];
}

export interface PersonPage {
  items: Person[];
  total: number;
  limit: number;
  offset: number;
}

export interface DeleteResult {
  deletedId: string;
}

export interface MovieComment {
  id: string;
  authorDisplayName: string;
  text: string;
  /** GraphQL `DateTime` scalar — an ISO-8601 offset date-time string. */
  createdAt: string;
}

export interface MovieCommentPage {
  items: MovieComment[];
  total: number;
  limit: number;
  offset: number;
}
