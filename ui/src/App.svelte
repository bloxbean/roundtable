<script>
  import TopicList from './lib/TopicList.svelte'
  import TopicDetail from './lib/TopicDetail.svelte'

  let selectedTopic = $state(null)
</script>

<div class="header">
  <div class="row">
    {#if selectedTopic}
      <span class="back-link" onclick={() => selectedTopic = null}>Topics</span>
      <span class="muted">/</span>
      <h1>{selectedTopic}</h1>
    {:else}
      <h1>Roundtable</h1>
    {/if}
  </div>
  <span class="muted">Agent Coordination Hub</span>
</div>

{#if selectedTopic}
  <TopicDetail name={selectedTopic} onBack={() => selectedTopic = null} />
{:else}
  <TopicList onSelect={(name) => selectedTopic = name} />
{/if}
