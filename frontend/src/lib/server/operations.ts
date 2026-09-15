// Server-only typed GraphQL operations for the BFF.
import { gql, type RequestContext } from './graphql';
import type {
  CreditRoleCode,
  DeleteResult,
  GenreCode,
  Movie,
  MoviePage,
  Person,
  PersonPage
} from './types';

const MOVIE_FIELDS = `
  id title originalTitle synopsis releaseDate runtimeMinutes originalLanguage version
  artwork { id url mediaType byteSize }
  genres { code title description active }
  cast { id category characterName sourceRoleName billingOrder
    role { code title category department description active }
    person { id name available } }
  creators { id category characterName sourceRoleName billingOrder
    role { code title category department description active }
    person { id name available } }
`;

const PERSON_FIELDS = `
  id name biography birthDate deathDate placeOfBirth version
  credits { movieId movieTitle category characterName
    role { code title category department description active } }
`;

export interface MovieFilterInput {
  genreCode?: string | null;
  releaseYear?: number | null;
}

export function listMovies(
  limit: number,
  offset: number,
  filter?: MovieFilterInput | null,
  ctx?: RequestContext
) {
  return gql<{ movies: MoviePage }>(
    `query($limit: Int!, $offset: Int!, $filter: MovieFilterInput) {
      movies(page: { limit: $limit, offset: $offset }, filter: $filter) {
        total limit offset
        items {
          id title synopsis releaseDate runtimeMinutes version
          artwork { id url mediaType byteSize }
          genres { code title description active }
        }
      }
    }`,
    { limit, offset, filter: filter ?? null },
    ctx
  ).then((r) => r.movies);
}

export function getMovie(id: string, ctx?: RequestContext) {
  return gql<{ movie: Movie | null }>(
    `query($id: ID!) { movie(id: $id) { ${MOVIE_FIELDS} } }`,
    { id },
    ctx
  ).then((r) => r.movie);
}

export function listGenres(ctx?: RequestContext) {
  return gql<{ genres: GenreCode[] }>(`query { genres { code title description active } }`, {}, ctx).then(
    (r) => r.genres
  );
}

export function listCreditRoles(ctx?: RequestContext) {
  return gql<{ creditRoles: CreditRoleCode[] }>(
    `query { creditRoles { code title category department description active } }`,
    {},
    ctx
  ).then((r) => r.creditRoles);
}

export interface CreateMovieInput {
  title: string;
  originalTitle?: string | null;
  synopsis?: string;
  releaseDate?: string | null;
  runtimeMinutes?: number | null;
  originalLanguage?: string | null;
  genreCodes?: string[];
}

export function createMovie(input: CreateMovieInput, ctx?: RequestContext) {
  return gql<{ createMovie: Movie }>(
    `mutation($input: CreateMovieInput!) { createMovie(input: $input) { id version } }`,
    { input },
    ctx
  ).then((r) => r.createMovie);
}

export function updateMovie(
  id: string,
  expectedVersion: number,
  input: Record<string, unknown>,
  ctx?: RequestContext
) {
  return gql<{ updateMovie: Movie }>(
    `mutation($id: ID!, $expectedVersion: Long!, $input: UpdateMovieInput!) {
      updateMovie(id: $id, expectedVersion: $expectedVersion, input: $input) { id version }
    }`,
    { id, expectedVersion, input },
    ctx
  ).then((r) => r.updateMovie);
}

export function deleteMovie(id: string, ctx?: RequestContext) {
  return gql<{ deleteMovie: DeleteResult }>(
    `mutation($id: ID!) { deleteMovie(id: $id) { deletedId } }`,
    { id },
    ctx
  ).then((r) => r.deleteMovie);
}

// --- credits ---

export interface CreateCreditInput {
  personId: string;
  roleCode: string;
  characterName?: string | null;
  billingOrder?: number | null;
}

export function addMovieCredit(movieId: string, input: CreateCreditInput, ctx?: RequestContext) {
  return gql<{ addMovieCredit: { id: string } }>(
    `mutation($movieId: ID!, $input: CreateCreditInput!) {
      addMovieCredit(movieId: $movieId, input: $input) { id }
    }`,
    { movieId, input },
    ctx
  ).then((r) => r.addMovieCredit);
}

export function removeMovieCredit(id: string, ctx?: RequestContext) {
  return gql<{ removeMovieCredit: DeleteResult }>(
    `mutation($id: ID!) { removeMovieCredit(id: $id) { deletedId } }`,
    { id },
    ctx
  ).then((r) => r.removeMovieCredit);
}

// --- people ---

export function listPeople(query: string | null, limit: number, offset: number, ctx?: RequestContext) {
  return gql<{ people: PersonPage }>(
    `query($query: String, $limit: Int!, $offset: Int!) {
      people(query: $query, page: { limit: $limit, offset: $offset }) {
        total limit offset
        items { id name birthDate deathDate version }
      }
    }`,
    { query, limit, offset },
    ctx
  ).then((r) => r.people);
}

export function getPerson(id: string, ctx?: RequestContext) {
  return gql<{ person: Person | null }>(
    `query($id: ID!) { person(id: $id) { ${PERSON_FIELDS} } }`,
    { id },
    ctx
  ).then((r) => r.person);
}

export interface CreatePersonInput {
  name: string;
  biography?: string;
  birthDate?: string | null;
  deathDate?: string | null;
  placeOfBirth?: string | null;
}

export function createPerson(input: CreatePersonInput, ctx?: RequestContext) {
  return gql<{ createPerson: Person }>(
    `mutation($input: CreatePersonInput!) { createPerson(input: $input) { id version } }`,
    { input },
    ctx
  ).then((r) => r.createPerson);
}

export function updatePerson(
  id: string,
  expectedVersion: number,
  input: Record<string, unknown>,
  ctx?: RequestContext
) {
  return gql<{ updatePerson: Person }>(
    `mutation($id: ID!, $expectedVersion: Long!, $input: UpdatePersonInput!) {
      updatePerson(id: $id, expectedVersion: $expectedVersion, input: $input) { id version }
    }`,
    { id, expectedVersion, input },
    ctx
  ).then((r) => r.updatePerson);
}

export function deletePerson(id: string, ctx?: RequestContext) {
  return gql<{ deletePerson: DeleteResult }>(
    `mutation($id: ID!) { deletePerson(id: $id) { deletedId } }`,
    { id },
    ctx
  ).then((r) => r.deletePerson);
}

// --- search ---

export interface MovieSearchHit {
  id: string;
  title: string;
  releaseDate?: string | null;
  matchedPersonNames: string[];
}

export interface PersonSearchHit {
  id: string;
  name: string;
}

export interface SearchResult {
  movies: MovieSearchHit[];
  people: PersonSearchHit[];
}

export function search(query: string, limit: number, offset: number, ctx?: RequestContext) {
  return gql<{ search: SearchResult }>(
    `query($query: String!, $limit: Int!, $offset: Int!) {
      search(query: $query, page: { limit: $limit, offset: $offset }) {
        movies { id title releaseDate matchedPersonNames }
        people { id name }
      }
    }`,
    { query, limit, offset },
    ctx
  ).then((r) => r.search);
}
