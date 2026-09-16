import { GraphQLClient, ClientError } from 'graphql-request';

export interface MovieRef {
  id: string;
  version: number;
}

export interface PersonRef {
  id: string;
  version: number;
}

/** A recognised, stable `extensions.code` (see docs/architecture/interfaces.md),
 *  or undefined if the failure doesn't carry one (network error, non-GraphQL
 *  failure, etc). */
export class GraphqlOpError extends Error {
  constructor(message: string, readonly code: string | undefined) {
    super(message);
    this.name = 'GraphqlOpError';
  }
}

function toOpError(err: unknown): GraphqlOpError {
  if (err instanceof ClientError) {
    const first = err.response?.errors?.[0];
    const code = (first?.extensions as { code?: string } | undefined)?.code;
    return new GraphqlOpError(first?.message ?? err.message, code);
  }
  const message = err instanceof Error ? err.message : String(err);
  return new GraphqlOpError(message, undefined);
}

export class CatalogueGraphqlClient {
  private readonly client: GraphQLClient;

  constructor(url: string, correlationId: string) {
    this.client = new GraphQLClient(url, {
      headers: { 'X-Correlation-ID': correlationId }
    });
  }

  async createMovie(input: {
    title: string;
    synopsis?: string;
    genreCodes?: string[];
  }): Promise<MovieRef> {
    try {
      const data = await this.client.request<{ createMovie: MovieRef }>(
        `mutation($input: CreateMovieInput!) {
          createMovie(input: $input) { id version }
        }`,
        { input }
      );
      return data.createMovie;
    } catch (err) {
      throw toOpError(err);
    }
  }

  async updateMovie(id: string, expectedVersion: number, title: string): Promise<MovieRef> {
    try {
      const data = await this.client.request<{ updateMovie: MovieRef }>(
        `mutation($id: ID!, $v: Long!, $input: UpdateMovieInput!) {
          updateMovie(id: $id, expectedVersion: $v, input: $input) { id version }
        }`,
        { id, v: expectedVersion, input: { title } }
      );
      return data.updateMovie;
    } catch (err) {
      throw toOpError(err);
    }
  }

  async deleteMovie(id: string): Promise<void> {
    try {
      await this.client.request(
        `mutation($id: ID!) { deleteMovie(id: $id) { deletedId } }`,
        { id }
      );
    } catch (err) {
      throw toOpError(err);
    }
  }

  async createPerson(input: { name: string; biography?: string }): Promise<PersonRef> {
    try {
      const data = await this.client.request<{ createPerson: PersonRef }>(
        `mutation($input: CreatePersonInput!) {
          createPerson(input: $input) { id version }
        }`,
        { input }
      );
      return data.createPerson;
    } catch (err) {
      throw toOpError(err);
    }
  }

  async updatePerson(id: string, expectedVersion: number, name: string): Promise<PersonRef> {
    try {
      const data = await this.client.request<{ updatePerson: PersonRef }>(
        `mutation($id: ID!, $v: Long!, $input: UpdatePersonInput!) {
          updatePerson(id: $id, expectedVersion: $v, input: $input) { id version }
        }`,
        { id, v: expectedVersion, input: { name } }
      );
      return data.updatePerson;
    } catch (err) {
      throw toOpError(err);
    }
  }

  async deletePerson(id: string): Promise<void> {
    try {
      await this.client.request(
        `mutation($id: ID!) { deletePerson(id: $id) { deletedId } }`,
        { id }
      );
    } catch (err) {
      throw toOpError(err);
    }
  }

  async addMovieComment(movieId: string, authorDisplayName: string, text: string): Promise<void> {
    try {
      await this.client.request(
        `mutation($movieId: ID!, $input: AddMovieCommentInput!) {
          addMovieComment(movieId: $movieId, input: $input) { id }
        }`,
        { movieId, input: { authorDisplayName, text } }
      );
    } catch (err) {
      throw toOpError(err);
    }
  }
}
