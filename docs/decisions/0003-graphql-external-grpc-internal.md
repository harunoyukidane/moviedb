# ADR-3: GraphQL externally, gRPC internally

Status: accepted

## Context
The Catalogue is the application-data API for the UI, which benefits from a
typed, flexible query surface. Cross-service calls to People are internal,
high-frequency, and benefit from a strict contract and deadlines rather than an
over-the-wire query language.

## Decision
Expose the Catalogue's public API as GraphQL (committed SDL, §8.1) and keep
service-to-service calls to People on gRPC (§8.3). GraphQL is the primary
application-data API; binary artwork uses small dedicated media endpoints
(phase 4) rather than being forced through GraphQL.

## Evidence (phase 3)
- Committed SDL: `catalogue-service/src/main/resources/graphql/schema.graphqls`,
  matching §8.1 exactly (Query/Mutation/type shapes, `Date`/`Long` scalars,
  `expectedVersion: Long!` on updateMovie/updatePerson, no expectedVersion on
  updateMovieCredit).
- Resolvers: `MovieController`, `QueryControllers`, `MutationController`; custom
  scalars in `GraphQlScalarConfig`; a single stable error boundary in
  `GraphQlExceptionResolver` producing `extensions.code` per §8.2.
- Internal gRPC: `PeopleGrpcClient` calls the People Service with read/write
  deadlines and maps gRPC Status to domain exceptions; `PeopleGrpcClientTest`
  verifies the mapping and deadline behaviour.
- `CatalogueGraphQlIntegrationTest` exercises the GraphQL surface end-to-end and
  asserts the stable error codes.

## Consequences
- The browser/BFF gets a single typed GraphQL endpoint; GraphQL is never exposed
  directly to the browser (see ADR-11).
- People stays behind a strict internal contract with explicit timeouts and a
  degraded-tolerant hydration path (§13).
