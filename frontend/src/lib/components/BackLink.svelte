<script lang="ts">
  import { previousPageUrl } from '$lib/stores/navigation';

  // "Back" returns the user to wherever they actually came from - a filtered
  // list, the alphabet page they were on, or the movie/person they clicked a
  // credit from - not a fixed "all movies"/"all people" list, which the
  // top-level nav already links (V2.8-06). [fallbackHref] is used only when
  // there is no in-app page to return to: a direct link, bookmark or hard
  // refresh landed here first, and during SSR.
  //
  // Deliberately a plain <a> with no click handler: middle-click and
  // open-in-new-tab keep working, and it needs no JS to be useful. Popping is
  // handled by the stack itself - navigating to the entry on top of it is
  // recognised as a return and pops it, so the next back goes one step further
  // out rather than straight back to the page just left.
  export let fallbackHref: string;
</script>

<a class="page-back" href={$previousPageUrl ?? fallbackHref}>
  <slot />
</a>
