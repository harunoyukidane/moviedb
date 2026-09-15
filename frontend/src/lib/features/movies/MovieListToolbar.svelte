<script lang="ts">
  import SearchBox from '$lib/components/SearchBox.svelte';
  import MovieFilters from './MovieFilters.svelte';
  import type { GenreCode } from '$lib/server/types';

  export let genres: GenreCode[] = [];
  export let years: number[] = [];
  export let selectedGenre: string | null = null;
  export let selectedYear: number | null = null;
  export let view: 'cluster' | 'list' = 'cluster';
</script>

<div class="movie-list-toolbar">
  <SearchBox />
  <MovieFilters {genres} {years} {selectedGenre} {selectedYear} {view} />
</div>

<style>
  .movie-list-toolbar {
    display: flex;
    flex-direction: column;
    gap: var(--sp-1);
  }
</style>
