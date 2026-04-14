<script>
  let { onSelect } = $props()
  let topics = $state([])
  let loading = $state(true)

  const statusBadge = (s) => ({
    'WAITING_FOR_INPUT': 'badge-gray', 'IN_REVIEW': 'badge-blue',
    'REVISION_REQUESTED': 'badge-orange', 'APPROVED': 'badge-green',
    'COMPLETED': 'badge-green', 'STALLED': 'badge-yellow', 'BREAKPOINT': 'badge-red'
  }[s] || 'badge-gray')

  async function load() {
    loading = true
    const res = await fetch('/api/topics')
    topics = await res.json()
    loading = false
  }

  async function deleteTopic(e, name) {
    e.stopPropagation()
    if (!confirm(`Delete topic "${name}" and all its messages?`)) return
    await fetch(`/api/topics/${name}`, { method: 'DELETE' })
    load()
  }

  load()
  const interval = setInterval(load, 5000)
</script>

{#if loading && topics.length === 0}
  <div class="empty">Loading topics...</div>
{:else if topics.length === 0}
  <div class="empty">No topics yet. Create one from an agent using <code>create_topic</code>.</div>
{:else}
  {#each topics as topic}
    <div class="card" onclick={() => onSelect(topic.name)}>
      <div class="row-between">
        <div class="row">
          <h2>{topic.name}</h2>
          <span class="badge {statusBadge(topic.status)}">{topic.status}</span>
          <span class="badge badge-blue">{topic.currentPhase}</span>
        </div>
        <div class="row">
          <span class="muted">{topic.agentCount} agent{topic.agentCount !== 1 ? 's' : ''}</span>
          <span class="muted">{topic.messageCount} msg{topic.messageCount !== 1 ? 's' : ''}</span>
          <span class="muted">round {topic.currentRound}</span>
          <button class="danger" onclick={(e) => deleteTopic(e, topic.name)}>Delete</button>
        </div>
      </div>
      {#if topic.description}
        <p class="muted mt">{topic.description}</p>
      {/if}
      <div class="phase-bar">
        {#each topic.phases as phase, i}
          <span class="phase-step"
            class:active={phase === topic.currentPhase && topic.status !== 'APPROVED' && topic.status !== 'COMPLETED'}
            class:done={i < topic.phases.indexOf(topic.currentPhase) || topic.status === 'APPROVED' || topic.status === 'COMPLETED'}>
            {phase}
          </span>
        {/each}
      </div>
    </div>
  {/each}
{/if}
