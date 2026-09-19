<script lang="ts">
  import { previousPageUrl } from '$lib/stores/navigation';

  // "Back" should return to wherever the user actually came from - a filtered
  // list, or the movie/person page they clicked through from - not always
  // the unfiltered "all movies"/"all people" list (V2.8-06). [fallbackHref]
  // is used only when there's no in-app previous page to return to (a direct
  // link, bookmark, or hard refresh landed here first).
  export let fallbackHref: string;
</script>

<a class="page-back" href={$previousPageUrl ?? fallbackHref}>
  <slot />
</a>
