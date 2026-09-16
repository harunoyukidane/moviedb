// Load-test configuration from environment. Every knob has a safe default so
// `npm run run` works with nothing set, against a local Compose stack.

export interface LoadTestConfig {
  catalogueGraphqlUrl: string;
  catalogueHttpUrl: string;
  peopleHttpUrl: string;
  durationSeconds: number;
  movieWorkers: number;
  peopleWorkers: number;
  commentWorkers: number;
  hotMovieSlots: number;
  hotPeopleSlots: number;
  requestTimeoutMs: number;
}

function int(env: NodeJS.ProcessEnv, name: string, fallback: number): number {
  const raw = env[name];
  if (!raw) return fallback;
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? Math.floor(n) : fallback;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): LoadTestConfig {
  return {
    catalogueGraphqlUrl: env.CATALOGUE_GRAPHQL_URL ?? 'http://localhost:8080/graphql',
    catalogueHttpUrl: env.CATALOGUE_HTTP_URL ?? 'http://localhost:8080',
    peopleHttpUrl: env.PEOPLE_HTTP_URL ?? 'http://localhost:8081',
    durationSeconds: int(env, 'LOAD_DURATION_SECONDS', 30),
    movieWorkers: int(env, 'LOAD_MOVIE_WORKERS', 20),
    peopleWorkers: int(env, 'LOAD_PEOPLE_WORKERS', 10),
    commentWorkers: int(env, 'LOAD_COMMENT_WORKERS', 20),
    // Small pools on purpose: few slots + many workers forces the same
    // movie/person to be hit concurrently instead of everyone getting their own row.
    hotMovieSlots: int(env, 'LOAD_HOT_MOVIES', 6),
    hotPeopleSlots: int(env, 'LOAD_HOT_PEOPLE', 6),
    requestTimeoutMs: int(env, 'LOAD_REQUEST_TIMEOUT_MS', 10000)
  };
}
