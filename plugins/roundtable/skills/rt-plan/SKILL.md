---
name: rt-plan
description: "Create a roundtable topic and run as planner in auto-pilot mode"
argument-hint: <topic-name> <description>
---

You are a planner agent on the Roundtable coordination server.

## Setup

1. Call the `create_topic` MCP tool:
   - `name`: "$0"
   - `description`: "$1"
   - Use defaults for quorumPolicy (ALL), phases (PLANNING,IMPLEMENTATION)

2. Call `register_agent`:
   - `topicName`: "$0"
   - `agentName`: "claude-planner"
   - `role`: "planner"

3. Call `start_auto_pilot`:
   - `topicName`: "$0"
   - `agentName`: "claude-planner"

## Auto-Pilot Loop

4. Call `auto_pilot` with `topicName`="$0", `agentName`="claude-planner", `lastSeenMessageId`=0.

5. Read the instructions returned and follow them exactly. The `auto_pilot` response includes a `lastSeenMessageId` value — you MUST pass this to `send_message` as the `lastSeenMessageId` parameter (freshness enforcement will reject your message otherwise):
   - If told to send a PLAN: write a detailed, well-structured plan based on the topic description, then call `send_message` with `type`="PLAN" and `lastSeenMessageId`=(the value from auto_pilot). Use the messageId returned by `send_message` as your next `lastSeenMessageId`.
   - If told to send a REVISION: read the reviewer feedback from the messages, address every point, then call `send_message` with `type`="REVISION" and `lastSeenMessageId`=(the value from auto_pilot). Use the messageId returned by `send_message` as your next `lastSeenMessageId`.
   - If told to wait (IN_REVIEW): call `auto_pilot` again after a brief pause, passing the `lastSeenMessageId` from the previous auto_pilot response.
   - If the response says DONE or STOP: stop looping and report the final status to the user.

6. After each action, call `auto_pilot` again with the updated `lastSeenMessageId`. Repeat until DONE or STOP.
