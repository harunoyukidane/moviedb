import { readFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { loadConfig } from './config.js';
import { parseManifest, InvalidTokenError } from './errors.js';
import { TmdbClient } from './tmdb.js';
import { PeopleGrpcClient } from './peopleClient.js';
import { CatalogueGraphqlClient } from './catalogueClient.js';
import { ArtworkHttpClient } from './artworkClient.js';
import { runImport, type Dependencies } from './importer.js';

async function main(): Promise<number> {
  const config = loadConfig();

  if (!config.tmdbReadToken) {
    // Never fake success (§12.2). Print exact instructions and exit non-zero so
    // the setup script can start an empty-but-usable app instead.
    console.error('TMDB_READ_TOKEN is not set — cannot seed.');
    console.error('Set it in demo/importer/.env (v4 Read Access Token) and re-run,');
    console.error('or run setup with --skip-seed for an empty, usable app.');
    return 2;
  }

  const ids = parseManifest(readFileSync(config.manifestPath, 'utf8'));
  if (ids.length === 0) {
    console.error(`No movie ids found in manifest ${config.manifestPath}`);
    return 2;
  }

  const correlationId = randomUUID();
  const people = new PeopleGrpcClient(config);
  const deps: Dependencies = {
    tmdb: new TmdbClient(config),
    people,
    catalogue: new CatalogueGraphqlClient(config, correlationId),
    artwork: new ArtworkHttpClient(config, correlationId)
  };

  console.log(`Importing ${ids.length} movies (concurrency ${config.concurrency}, correlation ${correlationId})…`);

  try {
    const report = await runImport(ids, deps, config.concurrency);
    for (const o of report.outcomes) {
      const tag = o.status.toUpperCase().padEnd(8);
      console.log(
        `  ${tag} tmdb=${o.tmdbId} "${o.title ?? ''}" people=${o.peopleImported} photos=${o.photosImported} credits=${o.creditsImported} poster=${o.posterImported}` +
          (o.error ? ` (${o.error})` : '')
      );
    }
    console.log(
      `Done: ${report.imported} imported, ${report.skipped} skipped, ${report.failed} failed of ${report.total}.`
    );
    // Non-zero if any required item failed (skipped 404s are acceptable, §12.3 step 9).
    return report.failed > 0 ? 1 : 0;
  } catch (e) {
    if (e instanceof InvalidTokenError) {
      console.error('TMDB token invalid or missing — stopping. Fix TMDB_READ_TOKEN and re-run.');
      return 2;
    }
    console.error('Import failed:', e instanceof Error ? e.message : e);
    return 1;
  } finally {
    people.close();
  }
}

main().then((code) => process.exit(code));
