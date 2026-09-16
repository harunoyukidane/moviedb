// Comment storm scenario: many workers add comments to the *same* single
// movie at once, to exercise concurrent inserts against one row (no
// version/optimistic-lock on comments — this is about write contention and
// ordering, not conflict handling).

import type { CatalogueGraphqlClient } from '../graphqlClient.js';
import type { Stats } from '../stats.js';
import { attempt, nextTag } from '../util.js';

export async function commentWorker(
  workerId: number,
  movieId: string,
  client: CatalogueGraphqlClient,
  stats: Stats,
  deadline: number
): Promise<void> {
  let i = 0;
  while (Date.now() < deadline) {
    i += 1;
    await attempt(stats, 'comment.add', () =>
      client.addMovieComment(
        movieId,
        `Load Tester ${workerId}`,
        `Concurrent comment #${i} from worker ${workerId} @ ${nextTag()}`
      )
    );
  }
}
