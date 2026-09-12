// SERVER-ONLY GraphQL client (BFF, ADR-11). Placed under `$lib/server/` so
// SvelteKit forbids importing it into browser code at build time — the browser
// never talks to GraphQL directly.
import { GraphQLClient, ClientError } from 'graphql-request';
import { randomUUID } from 'node:crypto';
import { env } from '$env/dynamic/private';

/** Catalogue GraphQL endpoint (server-side network only). */
const endpoint = env.CATALOGUE_GRAPHQL_URL ?? 'http://localhost:8080/graphql';

/** Header the backend reads to propagate a correlation id into gRPC. */
export const CORRELATION_HEADER = 'X-Correlation-ID';

/** A normalized GraphQL error carrying the stable extensions.code (§8.2). */
export class GraphQlRequestError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly correlationId: string
  ) {
    super(message);
    this.name = 'GraphQlRequestError';
  }
}

export interface RequestContext {
  /** Reuse an inbound correlation id if present; otherwise one is generated. */
  correlationId?: string;
}

/**
 * Execute a GraphQL operation server-side. Injects/forwards a correlation id and
 * normalizes the first GraphQL error's `extensions.code` into GraphQlRequestError
 * so callers/UI never parse raw GraphQL error shapes.
 */
export async function gql<T>(
  document: string,
  variables?: Record<string, unknown>,
  ctx: RequestContext = {}
): Promise<T> {
  const correlationId = ctx.correlationId ?? randomUUID();
  const client = new GraphQLClient(endpoint, {
    headers: { [CORRELATION_HEADER]: correlationId }
  });
  try {
    return await client.request<T>(document, variables);
  } catch (e) {
    if (e instanceof ClientError) {
      const first = e.response.errors?.[0];
      const code = (first?.extensions?.code as string | undefined) ?? 'INTERNAL_ERROR';
      const message = first?.message ?? 'Request failed';
      throw new GraphQlRequestError(code, message, correlationId);
    }
    // Network/transport failure: the dependency (Catalogue) is unreachable.
    throw new GraphQlRequestError(
      'DEPENDENCY_UNAVAILABLE',
      e instanceof Error ? e.message : 'Network error',
      correlationId
    );
  }
}
