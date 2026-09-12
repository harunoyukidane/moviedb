// Importer configuration from environment (§14: secrets only via env, never
// hard-coded). The TMDB read token is required to import; everything else has a
// safe default for local Compose.

export interface ImporterConfig {
  tmdbReadToken: string;
  tmdbApiBase: string;
  tmdbImageBase: string;
  tmdbPosterSize: string;
  catalogueGraphqlUrl: string;
  catalogueHttpUrl: string;
  peopleGrpcTarget: string;
  /** Path to the committed manifest of stable TMDB movie ids. */
  manifestPath: string;
  concurrency: number;
  requestTimeoutMs: number;
  maxRetries: number;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): ImporterConfig {
  return {
    tmdbReadToken: env.TMDB_READ_TOKEN ?? '',
    tmdbApiBase: env.TMDB_API_BASE ?? 'https://api.themoviedb.org',
    tmdbImageBase: env.TMDB_IMAGE_BASE_URL ?? 'https://image.tmdb.org/t/p/',
    tmdbPosterSize: env.TMDB_POSTER_SIZE ?? 'w500',
    catalogueGraphqlUrl: env.CATALOGUE_GRAPHQL_URL ?? 'http://localhost:8080/graphql',
    catalogueHttpUrl: env.CATALOGUE_HTTP_URL ?? 'http://localhost:8080',
    peopleGrpcTarget: normalizeGrpcTarget(env.PEOPLE_GRPC_TARGET ?? 'localhost:9090'),
    manifestPath: env.TMDB_MANIFEST_PATH ?? 'demo/tmdb-movie-ids.txt',
    concurrency: Number(env.IMPORT_CONCURRENCY ?? '4'),
    requestTimeoutMs: Number(env.IMPORT_REQUEST_TIMEOUT_MS ?? '10000'),
    maxRetries: Number(env.IMPORT_MAX_RETRIES ?? '5')
  };
}

/** Accept both `static://host:port` (Spring style) and plain `host:port`. */
export function normalizeGrpcTarget(target: string): string {
  return target.replace(/^static:\/\//, '').replace(/^dns:\/\//, '');
}
