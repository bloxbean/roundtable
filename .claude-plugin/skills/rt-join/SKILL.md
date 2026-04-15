---
name: rt-join
description: "Register on a roundtable topic with a role (no auto-pilot)"
argument-hint: <topic-name> <role> [agent-name]
---

Register an agent on a roundtable topic for manual participation (no auto-pilot).

## Steps

1. Call `register_agent`:
   - `topicName`: "$0"
   - `agentName`: "$2" (if not provided, default to "claude-$1", e.g. "claude-planner" or "claude-reviewer")
   - `role`: "$1" (must be "planner" or "reviewer")

2. Call `get_topic_status` with `topicName`="$0" to show the current state of the topic.

3. Report the registration and current topic state to the user. Remind them they can:
   - Use `send_message`, `get_messages`, `get_latest` to participate manually
   - Run `/rt-resume $0` to start auto-pilot later
