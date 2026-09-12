import { GraphQLClient } from 'graphql-request';
import type { ImporterConfig } from './config.js';
import type { CataloguePort, CreditUpsert, MovieUpsert } from './ports.js';

/**
 * Catalogue GraphQL adapter (§12.3 steps 6–7). Movie upsert uses createMovie with
 * the optional `tmdbId` (server upserts by tmdb_id); credit upsert uses
 * addMovieCredit with the optional `tmdbCreditId` (server upserts by tmdb credit
 * id). Both are therefore idempotent across reruns.
 */
export class CatalogueGraphqlClient implements CataloguePort {
  private readonly client: GraphQLClient;

  constructor(config: ImporterConfig, correlationId: string) {
    this.client = new GraphQLClient(config.catalogueGraphqlUrl, {
      headers: { 'X-Correlation-ID': correlationId }
    });
  }

  async upsertMovie(movie: MovieUpsert): Promise<string> {
    const data = await this.client.request<{ createMovie: { id: string } }>(
      `mutation($input: CreateMovieInput!) { createMovie(input: $input) { id } }`,
      {
        input: {
          title: movie.title,
          originalTitle: movie.originalTitle,
          synopsis: movie.synopsis,
          releaseDate: movie.releaseDate,
          runtimeMinutes: movie.runtimeMinutes,
          originalLanguage: movie.originalLanguage,
          genreCodes: movie.genreCodes,
          tmdbId: movie.tmdbId
        }
      }
    );
    return data.createMovie.id;
  }

  async upsertCredit(credit: CreditUpsert): Promise<string> {
    const data = await this.client.request<{ addMovieCredit: { id: string } }>(
      `mutation($movieId: ID!, $input: CreateCreditInput!) {
        addMovieCredit(movieId: $movieId, input: $input) { id }
      }`,
      {
        movieId: credit.movieId,
        input: {
          personId: credit.personId,
          roleCode: credit.roleCode,
          characterName: credit.characterName,
          billingOrder: credit.billingOrder,
          tmdbCreditId: credit.tmdbCreditId,
          sourceRoleName: credit.sourceRoleName
        }
      }
    );
    return data.addMovieCredit.id;
  }
}
