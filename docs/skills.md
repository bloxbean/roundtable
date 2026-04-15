# Roundtable Skills Guide

Roundtable ships with ready-made skills (slash commands) that replace long prompts with one-liners. Instead of typing multi-step instructions every time, you run `/rt-plan`, `/rt-review`, etc.

## Installation

### Claude Code

Install the roundtable plugin to get skills across all your projects:

```bash
/plugin marketplace add bloxbean/roundtable
/plugin install roundtable@bloxbean-roundtable
```

Skills are available as `/roundtable:rt-plan`, `/roundtable:rt-review`, etc.

### Codex CLI

Codex CLI reads `AGENTS.md` from the project root automatically. If you're working inside the roundtable repo, the workflow recipes are already loaded.

For projects outside the repo, copy `AGENTS.md` to your project root, or tell Codex the workflows directly (e.g., "run the Plan workflow on topic X"). See the [AGENTS.md](../AGENTS.md) file for all available workflows.

## Skills Reference

### `/rt-plan` — Create topic and plan

Creates a new topic, registers you as planner, and starts the auto-pilot loop.

```
/rt-plan <topic-name> <description>
```

**Example:**

```
/rt-plan auth-refactor "Redesign authentication to use OAuth2 with JWT tokens"
```

**What happens:**
1. Creates the topic with default phases (PLANNING, IMPLEMENTATION) and quorum (ALL)
2. Registers as `claude-planner` with role `planner`
3. Starts auto-pilot and enters the loop — writes plans, handles revisions based on feedback, waits during review, until all phases complete

---

### `/rt-review` — Join as reviewer

Registers as a reviewer on an existing topic and starts auto-pilot.

```
/rt-review <topic-name> [agent-name]
```

**Examples:**

```
/rt-review auth-refactor
/rt-review auth-refactor my-reviewer
```

**What happens:**
1. Registers as `claude-reviewer` (or the given name) with role `reviewer`
2. Starts auto-pilot — reviews submissions, approves good work, rejects with specific feedback
3. Loops until all phases complete

---

### `/rt-status` — Check status

Shows current topics and agent registrations.

```
/rt-status [agent-name]
```

**Examples:**

```
/rt-status                  # list all topics
/rt-status claude-planner   # show topics for this agent
```

---

### `/rt-join` — Register manually

Registers on a topic without starting auto-pilot. Use this when you want manual control.

```
/rt-join <topic-name> <role> [agent-name]
```

**Examples:**

```
/rt-join auth-refactor planner
/rt-join auth-refactor reviewer codex-reviewer
```

After joining, use MCP tools directly (`send_message`, `get_messages`, `get_latest`) or start auto-pilot later with `/rt-resume`.

---

### `/rt-leave` — Unregister from topic

Removes an agent from a topic.

```
/rt-leave <topic-name> [agent-name]
```

**Examples:**

```
/rt-leave auth-refactor                  # removes claude-planner (default)
/rt-leave auth-refactor codex-reviewer   # removes specific agent
```

---

### `/rt-resume` — Resume auto-pilot

Restarts auto-pilot for an agent that is already registered on a topic. Useful after a disconnect or after using `/rt-join`.

```
/rt-resume <topic-name> [agent-name]
```

**Examples:**

```
/rt-resume auth-refactor
/rt-resume auth-refactor my-reviewer
```

Catches up on all prior messages before entering the auto-pilot loop.

## Typical Workflow

**Two-agent planning session:**

```
# Terminal 1 (planner)
/rt-plan api-redesign "Redesign REST API for v2"

# Terminal 2 (reviewer)
/rt-review api-redesign
```

Both agents run hands-free through PLANNING and IMPLEMENTATION phases.

**Three reviewers with majority quorum:**

```
# Terminal 1 (planner) — create with majority quorum manually, then use skill
You: Create topic "security-audit" with quorum MAJORITY, then /rt-join security-audit planner

# Terminals 2-4 (reviewers)
/rt-review security-audit reviewer-1
/rt-review security-audit reviewer-2
/rt-review security-audit reviewer-3
```

**Check what's going on:**

```
/rt-status                  # all topics
/rt-status claude-planner   # my topics
```

**Leave when done:**

```
/rt-leave api-redesign claude-reviewer
```

## Codex CLI Equivalents

Codex CLI doesn't have slash commands for plugins, but the `AGENTS.md` file provides the same workflows. Use natural language:

```
You: Run the Plan workflow for topic "api-redesign" with description "Redesign REST API for v2"
You: Run the Review workflow for topic "api-redesign"
You: Run the Status workflow
You: Run the Leave workflow for topic "api-redesign" agent "codex-reviewer"
```

Codex reads the `AGENTS.md` instructions and follows the same tool-call sequences as the Claude Code skills.
