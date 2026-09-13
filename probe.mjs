// Probe TMDB reachability from inside a container on the compose network.
fetch('https://api.themoviedb.org/3/configuration', { signal: AbortSignal.timeout(10000) })
  .then((r) => console.log('CONTAINER-TMDB-STATUS:', r.status))
  .catch((e) => console.log('CONTAINER-TMDB-ERR:', e.name, e.message, '| cause:', e.cause && (e.cause.code || e.cause.message)));
