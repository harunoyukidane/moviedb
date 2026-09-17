<script lang="ts">
  export let id: string;
  export let message: string | undefined = undefined;
</script>

{#if message}
  <p {id} class="field-error" role="alert">{message}</p>
{/if}
