<script>
  let { name, onBack } = $props()
  let topic = $state(null)
  let agents = $state([])
  let messages = $state([])
  let loading = $state(true)
  let activeTab = $state('messages')

  const statusBadge = (s) => ({
    'WAITING_FOR_INPUT': 'badge-gray', 'IN_REVIEW': 'badge-blue',
    'REVISION_REQUESTED': 'badge-orange', 'APPROVED': 'badge-green',
    'COMPLETED': 'badge-green', 'STALLED': 'badge-yellow', 'BREAKPOINT': 'badge-red'
  }[s] || 'badge-gray')

  const typeBadge = (t) => ({
    'PLAN': 'badge-blue', 'REVISION': 'badge-orange', 'REVIEW': 'badge-yellow',
    'APPROVAL': 'badge-green', 'REJECTION': 'badge-red', 'SYSTEM': 'badge-gray', 'CHAT': 'badge-gray'
  }[t] || 'badge-gray')

  async function load() {
    const [tRes, aRes, mRes] = await Promise.all([
      fetch(`/api/topics/${name}`),
      fetch(`/api/topics/${name}/agents`),
      fetch(`/api/topics/${name}/messages`)
    ])
    if (!tRes.ok) { onBack(); return }
    topic = await tRes.json()
    agents = await aRes.json()
    messages = await mRes.json()
    loading = false
  }

  async function unregisterAgent(agentName) {
    if (!confirm(`Unregister "${agentName}" from this topic?`)) return
    await fetch(`/api/topics/${name}/agents/${agentName}`, { method: 'DELETE' })
    load()
  }

  async function toggleAutoPilot(agentName, current) {
    await fetch(`/api/topics/${name}/agents/${agentName}/autopilot?enabled=${!current}`, { method: 'PUT' })
    load()
  }

  async function deleteTopic() {
    if (!confirm(`Delete topic "${name}" and all data?`)) return
    await fetch(`/api/topics/${name}`, { method: 'DELETE' })
    onBack()
  }

  function timeAgo(iso) {
    const s = Math.floor((Date.now() - new Date(iso)) / 1000)
    if (s < 60) return s + 's ago'
    if (s < 3600) return Math.floor(s / 60) + 'm ago'
    if (s < 86400) return Math.floor(s / 3600) + 'h ago'
    return Math.floor(s / 86400) + 'd ago'
  }

  load()
  const interval = setInterval(load, 3000)
</script>

{#if loading}
  <div class="empty">Loading...</div>
{:else if topic}
  <!-- Status bar -->
  <div class="card" style="cursor:default">
    <div class="row-between">
      <div class="row">
        <span class="badge {statusBadge(topic.status)}">{topic.status}</span>
        <span class="muted">Phase: {topic.currentPhase} ({topic.phaseProgress})</span>
        <span class="muted">Round {topic.currentRound}</span>
        <span class="muted">Quorum: {topic.quorumPolicy}</span>
      </div>
      <button class="danger" onclick={deleteTopic}>Delete Topic</button>
    </div>
    <div class="phase-bar">
      {#each topic.phases as phase, i}
        <span class="phase-step"
          class:active={phase === topic.currentPhase && topic.status !== 'APPROVED'}
          class:done={i < topic.phases.indexOf(topic.currentPhase) || topic.status === 'APPROVED'}>
          {phase}
        </span>
      {/each}
    </div>
  </div>

  <!-- Tab bar -->
  <div class="row mt" style="margin-bottom:12px">
    <button class:primary={activeTab==='messages'} onclick={() => activeTab='messages'}>
      Messages ({messages.length})
    </button>
    <button class:primary={activeTab==='agents'} onclick={() => activeTab='agents'}>
      Agents ({agents.length})
    </button>
  </div>

  <!-- Messages tab -->
  {#if activeTab === 'messages'}
    {#if messages.length === 0}
      <div class="empty">No messages yet</div>
    {:else}
      {#each messages as msg}
        <div class="card" style="cursor:default">
          <div class="row-between">
            <div class="row">
              <strong>{msg.senderName}</strong>
              <span class="muted">({msg.senderRole})</span>
              <span class="badge {typeBadge(msg.type)}">{msg.type}</span>
              {#if msg.phase}
                <span class="muted">{msg.phase}</span>
              {/if}
            </div>
            <div class="row">
              <span class="muted">#{msg.id}</span>
              <span class="muted">R{msg.round}</span>
              <span class="muted">{timeAgo(msg.createdAt)}</span>
            </div>
          </div>
          <div class="msg-content">{msg.content}</div>
        </div>
      {/each}
    {/if}
  {/if}

  <!-- Agents tab -->
  {#if activeTab === 'agents'}
    {#if agents.length === 0}
      <div class="empty">No agents registered</div>
    {:else}
      <table>
        <thead>
          <tr>
            <th>Name</th><th>Role</th><th>Auto-pilot</th><th>Joined Round</th><th>Last Active</th><th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {#each agents as agent}
            <tr>
              <td><strong>{agent.name}</strong></td>
              <td><span class="badge badge-blue">{agent.role}</span></td>
              <td>
                <button onclick={() => toggleAutoPilot(agent.name, agent.autoPilotEnabled)}>
                  {agent.autoPilotEnabled ? 'ON' : 'OFF'}
                </button>
              </td>
              <td>{agent.joinedAtRound}</td>
              <td class="muted">{agent.lastPolledAt ? timeAgo(agent.lastPolledAt) : '-'}</td>
              <td>
                <button class="danger" onclick={() => unregisterAgent(agent.name)}>Unregister</button>
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    {/if}
  {/if}
{/if}
