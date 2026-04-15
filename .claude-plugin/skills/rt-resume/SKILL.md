---
name: rt-resume
description: "Resume auto-pilot on a topic where you are already registered"
argument-hint: <topic-name> [agent-name]
---

Resume auto-pilot for an agent that is already registered on a roundtable topic.

## Steps

1. Determine agent name: use "$1" if provided, otherwise default to "claude-planner".

2. Call `start_auto_pilot`:
   - `topicName`: "$0"
   - `agentName`: (agent name from step 1)

3. Call `get_messages` with `topicName`="$0", `agentName`=(agent name), `sinceId`=0 to catch up on all prior messages. Note the highest message ID as your starting `lastSeenMessageId`.

4. Enter the auto-pilot loop: call `auto_pilot` with `topicName`="$0", `agentName`=(agent name), `lastSeenMessageId`=(from step 3). Follow the returned instructions, take the recommended action, then call `auto_pilot` again. Repeat until DONE or STOP.
