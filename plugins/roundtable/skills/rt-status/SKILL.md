---
name: rt-status
description: "Check roundtable topic and agent status"
argument-hint: "[agent-name]"
---

Check the current status of roundtable topics and agents.

## What to do

- If an argument is provided ("$ARGUMENTS"), treat it as an agent name and call `my_topics` with `agentName`="$0". This shows all topics the agent is registered on, their roles, and status.

- If no argument is provided, call `list_topics` to show all active topics with their current status, phase, and agent counts.

Present the results in a clear, readable format.
