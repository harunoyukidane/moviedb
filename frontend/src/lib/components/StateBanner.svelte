<script lang="ts">
  export let variant: 'error' | 'info' = 'info';
</script>

<div class="banner {variant === 'error' ? 'banner-error' : 'banner-info'}" role={variant === 'error' ? 'alert' : 'status'}>
  <slot />
</div>
