import { loadConfig } from './config.js';
import { CatalogueGraphqlClient } from './graphqlClient.js';
import { Stats } from './stats.js';
import { makeMovieSlots, movieWorker, cleanupMovieSlots } from './scenarios/movies.js';
import { makePersonSlots, personWorker, cleanupPersonSlots } from './scenarios/people.js';
import { commentWorker } from './scenarios/comments.js';

async function main(): Promise<number> {
  const config = loadConfig();
  const client = new CatalogueGraphqlClient(config.catalogueGraphqlUrl, 'loadtest');
  const stats = new Stats();

  console.log('MovieDB concurrency load test');
  console.log(`  target:          ${config.catalogueGraphqlUrl}`);
  console.log(`  duration:        ${config.durationSeconds}s`);
  console.log(`  movie workers:   ${config.movieWorkers} (pool of ${config.hotMovieSlots} shared rows)`);
  console.log(`  people workers:  ${config.peopleWorkers} (pool of ${config.hotPeopleSlots} shared rows)`);
  console.log(`  comment workers: ${config.commentWorkers} (all on one shared movie)`);
  console.log('');
  console.log('All records created by this run are prefixed "[LOADTEST]" and are');
  console.log('cleaned up (best-effort) after the run. The curated demo catalogue is');
  console.log('never touched.');

  // A dedicated, never-deleted movie for the comment storm so it isn't
  // caught in the create/update/delete churn happening elsewhere.
  const commentTarget = await client.createMovie({
    title: `[LOADTEST] Comment Storm Target ${Date.now()}`,
    synopsis: 'Dedicated target for concurrent comment load testing.'
  });

  const movieSlots = makeMovieSlots(config.hotMovieSlots);
  const personSlots = makePersonSlots(config.hotPeopleSlots);
  // Every id this run ever creates, regardless of which slot (if any) still
  // points at it by the end — see cleanupMovieSlots/cleanupPersonSlots.
  const createdMovieIds = new Set<string>();
  const createdPersonIds = new Set<string>();

  const deadline = Date.now() + config.durationSeconds * 1000;

  const workers: Promise<void>[] = [];

  for (let i = 0; i < config.movieWorkers; i++) {
    workers.push(
      movieWorker(i, movieSlots, client, config.catalogueHttpUrl, stats, deadline, createdMovieIds).catch((err) =>
        console.error('movie worker crashed unexpectedly:', err)
      )
    );
  }
  for (let i = 0; i < config.peopleWorkers; i++) {
    workers.push(
      personWorker(i, personSlots, client, config.peopleHttpUrl, stats, deadline, createdPersonIds).catch((err) =>
        console.error('person worker crashed unexpectedly:', err)
      )
    );
  }
  for (let i = 0; i < config.commentWorkers; i++) {
    workers.push(
      commentWorker(i, commentTarget.id, client, stats, deadline).catch((err) =>
        console.error('comment worker crashed unexpectedly:', err)
      )
    );
  }

  console.log(`\nRunning ${workers.length} concurrent workers for ${config.durationSeconds}s...\n`);
  await Promise.all(workers);

  console.log('Cleaning up synthetic records...');
  await Promise.all([
    cleanupMovieSlots(client, createdMovieIds),
    cleanupPersonSlots(client, createdPersonIds),
    client.deleteMovie(commentTarget.id).catch(() => undefined)
  ]);

  stats.print();
  return stats.hasUnexpectedErrors() ? 1 : 0;
}

main()
  .then((code) => process.exit(code))
  .catch((err) => {
    console.error('load test failed to run:', err);
    process.exit(2);
  });
