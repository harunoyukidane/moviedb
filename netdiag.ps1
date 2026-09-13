# 1. Host -> TMDB
try {
  $r = Invoke-WebRequest -UseBasicParsing 'https://api.themoviedb.org/3/movie/1091' -TimeoutSec 12
  Write-Output ('HOST-TMDB: ' + $r.StatusCode)
} catch {
  Write-Output ('HOST-TMDB-ERR: ' + $_.Exception.Message)
}

# 2. From a container on the importer's network (people-service is on it): can it DNS + TCP TMDB?
Write-Output '--- container curl to TMDB (via a throwaway node:20-alpine on the internal network) ---'
docker run --rm --network moviedb_internal node:20-alpine sh -c "node -e `"fetch('https://api.themoviedb.org/3/configuration').then(r=>console.log('CONTAINER-TMDB:',r.status)).catch(e=>console.log('CONTAINER-TMDB-ERR:',e.cause?.code||e.message))`""
