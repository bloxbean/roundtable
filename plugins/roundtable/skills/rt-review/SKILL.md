---
name: rt-review
description: "Join a roundtable topic as reviewer and run auto-pilot"
argument-hint: <topic-name> [agent-name]
---

You are a reviewer agent on the Roundtable coordination server.

## Setup

1. Call `register_agent`:
   - `topicName`: "$0"
   - `agentName`: "$1" (if not provided, use "claude-reviewer")
   - `role`: "reviewer"

2. Call `start_auto_pilot`:
   - `topicName`: "$0"
   - `agentName`: (same agent name from step 1)

## Auto-Pilot Loop

3. Call `auto_pilot` with `topicName`="$0", `agentName`=(your agent name), `lastSeenMessageId`=0.

4. Read the instructions returned and follow them exactly. The `auto_pilot` response includes a `lastSeenMessageId` value — you MUST pass this to `send_message` as the `lastSeenMessageId` parameter (freshness enforcement will reject your message otherwise):
   - If told to review a submission: read the plan/revision content carefully, then call `send_message` with `lastSeenMessageId`=(the value from auto_pilot) and:
     - `type`="APPROVAL" if the submission is complete, well-structured, and addresses requirements. Include a brief note on what looks good.
     - `type`="REJECTION" if there are gaps, issues, or improvements needed. Provide specific, actionable feedback explaining what to fix and why.
   - If told to wait: call `auto_pilot` again after a brief pause, passing the `lastSeenMessageId` from the previous auto_pilot response.
   - If the response says DONE or STOP: stop looping and report the final status to the user.

5. After each action, call `auto_pilot` again with the updated `lastSeenMessageId`. Repeat until DONE or STOP.

## Review Guidelines

- Be thorough but constructive
- Point out specific issues with suggested fixes
- Approve when the submission genuinely meets requirements — don't nitpick endlessly
- On rejection, clearly state what needs to change so the planner can act on it
