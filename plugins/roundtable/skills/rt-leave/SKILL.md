---
name: rt-leave
description: "Unregister an agent from a roundtable topic"
argument-hint: <topic-name> [agent-name]
---

Remove an agent from a roundtable topic.

## Steps

1. Call `unregister_agent`:
   - `topicName`: "$0"
   - `agentName`: "$1" (if not provided, default to "claude-planner")

2. Confirm the removal to the user and show which agent was unregistered from which topic.
