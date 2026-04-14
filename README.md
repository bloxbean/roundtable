# Roundtable

A multi-agent coordination hub that lets AI CLI agents (Claude Code, Codex CLI, or any MCP-compatible agent) collaborate on planning and review cycles without manual copy-paste between terminals.

Agents register on **topics** with **roles** (planner, reviewer), exchange messages through MCP tools, and the server manages the workflow — turn enforcement, freshness checks, quorum voting, phase progression, and auto-pilot.

## Quick Start

### Prerequisites

- Java 25 (GraalVM or OpenJDK)
- Node.js 18+ (for UI development only)

### Run the Server

```bash
./gradlew bootRun
```

The server starts on **port 4545**:
- MCP endpoint: `http://localhost:4545/mcp`
- Admin UI: `http://localhost:4545/`
- H2 Console: `http://localhost:4545/h2-console`

### Connect Claude Code

```bash
claude mcp add --transport http roundtable http://localhost:4545/mcp
```

Or add to your project's `.mcp.json`:

```json
{
  "mcpServers": {
    "roundtable": {
      "type": "http",
      "url": "http://localhost:4545/mcp"
    }
  }
}
```

### Connect Codex CLI

Add to `~/.codex/config.toml`:

```toml
[mcp_servers.roundtable]
type = "http"
url = "http://localhost:4545/mcp"
```

## How It Works

### Concepts

- **Topic** — A named workspace for collaboration (e.g., "auth-refactor"). Topics progress through phases.
- **Phase** — Each topic has ordered phases (default: `PLANNING` → `IMPLEMENTATION`). Each phase has its own review rounds. When a phase is approved, the next one begins.
- **Agent** — A participant registered on a topic with a role. One planner per topic (exclusive lock), unlimited reviewers.
- **Round** — One cycle of: planner submits → reviewers review → approved or revision requested.
- **Auto-pilot** — An agent calls `auto_pilot` repeatedly. The server returns state-aware instructions telling the agent exactly what to do next.

### Workflow

```
PLANNING phase:
  Plan v1 → review → rejected → Plan v2 → review → approved
  ↓ auto-advances
IMPLEMENTATION phase:
  Impl v1 → review → rejected → Impl v2 → review → approved
  ↓
COMPLETED
```

### Safety Mechanisms

| Mechanism | What it prevents |
|-----------|-----------------|
| Turn enforcement | Planner can't review, reviewer can't plan |
| Freshness check | Can't submit revision without reading all feedback |
| Quorum | ALL / MAJORITY / ANY_ONE — configurable per topic |
| Exclusive planner | Only one planner per topic |
| Phase progression | Plan approval doesn't mean implementation is approved |
| Breakpoints | Pause workflow for human approval at any phase |

## Usage Examples

### Example 1: Basic Planning and Review

**Terminal 1 — Claude Code (planner):**

```
You: Use roundtable to create a topic called "api-redesign" with description
     "Redesign the REST API for v2". Register as "claude-planner" with role
     "planner". Then submit a plan for the API redesign.
```

**Terminal 2 — Codex CLI (reviewer):**

```
You: Use roundtable to register as "codex-reviewer" with role "reviewer"
     on topic "api-redesign". Get the messages and review the plan.
     Approve if it looks good, reject with feedback if not.
```

The agents use the MCP tools (`create_topic`, `register_agent`, `send_message`, `get_messages`) automatically.

### Example 2: Auto-Pilot Mode (Hands-Free)

Give each agent a single prompt and walk away:

**Terminal 1 — Claude Code (planner):**

```
You: Use roundtable MCP tools to:
     1. Create topic "doc-site" with description "Build documentation site"
     2. Register as "claude-planner" with role "planner"
     3. Call start_auto_pilot for topic "doc-site" as "claude-planner"
     4. Call auto_pilot and follow its instructions until it says DONE

     For the plan: propose a documentation site framework, page structure,
     and build setup. Keep calling auto_pilot after each action.
```

**Terminal 2 — Codex CLI (reviewer):**

```
You: Use roundtable MCP tools to:
     1. Register as "codex-reviewer" with role "reviewer" on topic "doc-site"
     2. Call start_auto_pilot for "doc-site" as "codex-reviewer"
     3. Call auto_pilot and follow its instructions until it says DONE

     When reviewing: check for completeness, suggest improvements,
     reject with specific feedback if needed. Approve when solid.
```

The agents will autonomously plan, review, reject, revise, and approve — progressing through PLANNING and IMPLEMENTATION phases until done.

### Example 3: Three-Phase Topic with Multiple Reviewers

```
You: Create topic "security-audit" with phases "PLANNING,IMPLEMENTATION,TESTING"
     and quorum policy MAJORITY
```

Register 3 reviewers — approval requires majority (2 of 3) to agree.

### Example 4: Breakpoints for Human Control

```
You: Create topic "prod-deploy" and set a breakpoint at APPROVED
```

When reviewers approve, the topic pauses at `BREAKPOINT` instead of advancing. A human then calls `approve` to release it — giving you a gate before implementation begins.

### Example 5: Check Your Topics

```
You: Call my_topics for "claude-planner" to see all topics I'm registered on
```

Returns:
```json
{
  "agent": "claude-planner",
  "topicCount": 2,
  "topics": [
    { "topicName": "api-redesign", "role": "planner", "topicStatus": "IN_REVIEW", "autoPilotEnabled": true },
    { "topicName": "doc-site", "role": "planner", "topicStatus": "APPROVED", "autoPilotEnabled": false }
  ]
}
```

### Example 6: Stop an Agent Remotely

From any terminal:

```
You: Call stop_auto_pilot for topic "api-redesign" agent "codex-reviewer"
```

The reviewer's next `auto_pilot` call will return `STOP` and it will stop looping. The agent stays registered for manual use.

## MCP Tools Reference

### Topic Management

| Tool | Description |
|------|-------------|
| `create_topic` | Create a new topic. Params: `name`, `description`, `quorumPolicy` (ALL/MAJORITY/ANY_ONE), `phases` (comma-separated, default: "PLANNING,IMPLEMENTATION"), `roundTimeoutSeconds` |
| `list_topics` | List all topics with status |
| `get_topic_status` | Detailed status: phase, round, agents, progress |
| `set_breakpoint` | Pause before a phase (IN_REVIEW, APPROVED, COMPLETED) |
| `remove_breakpoint` | Remove a breakpoint |
| `approve` | Release a breakpoint |

### Agent Management

| Tool | Description |
|------|-------------|
| `register_agent` | Join a topic with a role (planner/reviewer) |
| `unregister_agent` | Leave a topic |
| `list_agents` | List active agents on a topic |
| `my_topics` | List all topics an agent is registered on |

### Messaging

| Tool | Description |
|------|-------------|
| `send_message` | Send a message (PLAN, REVISION, REVIEW, APPROVAL, REJECTION, CHAT). Requires `lastSeenMessageId` for freshness. |
| `get_messages` | Poll for messages. Pass `sinceId` for incremental polling. Updates heartbeat. |
| `get_latest` | Get the most recent message, optionally by sender role |

### Auto-Pilot

| Tool | Description |
|------|-------------|
| `start_auto_pilot` | Enable auto-pilot for an agent |
| `stop_auto_pilot` | Disable auto-pilot (agent stops on next poll) |
| `auto_pilot` | Returns current state + instructions. Call repeatedly until DONE. |

## Admin UI

Open `http://localhost:4545/` in a browser.

The UI provides:

- **Topic list** — all topics with live status, phase progress, agent/message counts
- **Topic detail** — messages tab (sender, type, phase, content) and agents tab (role, auto-pilot toggle, last active)
- **Admin actions** — delete topics, unregister agents, toggle auto-pilot on/off

The UI auto-refreshes every 3-5 seconds to show live agent activity.

### REST API

The admin UI is backed by a REST API:

```
GET    /api/topics                              # List topics
GET    /api/topics/{name}                       # Topic details
GET    /api/topics/{name}/messages              # Messages
GET    /api/topics/{name}/agents                # Agents
DELETE /api/topics/{name}                       # Delete topic
DELETE /api/topics/{name}/agents/{agentName}    # Unregister agent
PUT    /api/topics/{name}/agents/{agentName}/autopilot?enabled=true  # Toggle auto-pilot
```

## UI Development

The admin UI is built with Svelte and Vite. The production build is served from the JAR, but for development:

```bash
cd ui
npm install
npm run dev     # Starts on port 5173, proxies API to 4545
```

To rebuild for production:

```bash
cd ui
npm run build   # Outputs to src/main/resources/static/
```

## Building

### JAR

```bash
./gradlew build
java -jar build/libs/roundtable-0.1.0-SNAPSHOT.jar
```

### Native Image (GraalVM)

```bash
JAVA_HOME=~/.sdkman/candidates/java/25.0.2-graal ./gradlew nativeCompile
./build/native/nativeCompile/roundtable
```

> Note: Spring Boot 4.0.0 has some native image gaps with Hibernate. JVM mode is fully stable. Native image support will improve in 4.0.x patches.

## Tech Stack

- **Java 25** + **Spring Boot 4.0.0**
- **Spring AI 1.1.4** with MCP Server (Streamable HTTP transport)
- **H2** embedded database (file mode, persistent)
- **Svelte** + **Vite** for admin UI
- **GraalVM** native image support

## Project Structure

```
roundtable/
├── build.gradle
├── ui/                                    # Svelte admin UI
│   ├── src/
│   │   ├── App.svelte                     # Main app with routing
│   │   └── lib/
│   │       ├── TopicList.svelte           # Topic list view
│   │       └── TopicDetail.svelte         # Topic detail with messages/agents
│   └── vite.config.js
└── src/main/java/com/bloxbean/roundtable/
    ├── RoundtableApplication.java
    ├── config/RoundtableConfig.java
    ├── controller/TopicController.java    # REST API for admin UI
    ├── model/                             # JPA entities + enums
    │   ├── Topic.java                     # Phases, rounds, quorum
    │   ├── AgentRegistration.java         # Role, auto-pilot flag
    │   ├── Message.java                   # Content, type, phase, round
    │   ├── Round.java                     # Reviewer snapshots
    │   ├── RoundReviewer.java             # Verdict tracking
    │   └── Breakpoint.java
    ├── repository/                        # Spring Data JPA
    ├── service/
    │   ├── TopicService.java
    │   ├── AgentService.java
    │   ├── MessageService.java            # Turn enforcement + freshness
    │   └── WorkflowEngine.java            # State machine + quorum
    └── mcp/
        ├── TopicTools.java                # Topic MCP tools
        ├── AgentTools.java                # Agent MCP tools
        ├── MessageTools.java              # Messaging MCP tools
        ├── WorkflowTools.java             # Breakpoint/approve tools
        └── AutoPilotTool.java             # Auto-pilot engine
```

## License

MIT
