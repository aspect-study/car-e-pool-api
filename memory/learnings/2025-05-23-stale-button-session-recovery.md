# Stale Button Crash After Bot Restart

**Date:** 2025-05-23  
**Context:** Bot restarted; users tap old inline keyboard buttons from previous sessions

## What Happened

After a bot restart, users had open Telegram messages with inline keyboards from the previous session. When they tapped a button, the callback triggered a handler that tried to read `UserState.getOriginHubId()` — which was `null` because the session had been cleared. The Integer auto-unbox threw a `NullPointerException`.

## Why

`UserState` fields are populated step-by-step during a conversation flow. After a restart, `StateManager` returns a fresh `UserState.initial()` with all fields null. Flow-sensitive handlers (those that read mid-flow state) don't expect null fields.

## Fix

1. Classify the callback in `SessionRecoveryHandler` as flow-sensitive (add to `POST_RIDE_ACTIONS` set)
2. Add a null guard at the start of any handler that reads nullable state fields:

```java
if (ctx.state().getOriginHubId() == null) {
    bot.sendMessage(ctx.chatId(), "Session expired. Please start again from the main menu.");
    stateManager.reset(ctx.chatId());
    return;
}
```

3. Never auto-unbox a state field without checking for null first: `int seats = state.getSeats()` is a NPE bomb.

## Rule Added To

`bot-engineer.md` agent — stale button guard section  
`new-bot-command/SKILL.md` — Step 4 checklist item