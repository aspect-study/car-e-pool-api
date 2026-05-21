---
name: new-bot-command
description: Use when adding a new Telegram bot callback action to carpool-bot. Covers CallbackHandler registration, BotContext usage, session recovery classification, and stale button guard.
---

# Adding a New Bot Command

## Step 1 — Register in CallbackHandler

Open `carpool-bot/.../bot/CallbackHandler.java`. Inside the `@PostConstruct` method, add one line:

```java
commands.put("MY_ACTION", ctx -> myHandler.handleMyAction(ctx));
```

`CallbackHandler` itself never changes structure — one line per new action.

## Step 2 — Implement the handler method

The method signature always receives `BotContext`:

```java
public void handleMyAction(BotContext ctx) {
    Long chatId       = ctx.chatId();
    Long carpoolUserId = ctx.carpoolUserId();
    Long entityId     = ctx.entityId();      // parses ctx.payload() as Long
    UserState state   = ctx.state();
    String payload    = ctx.payload();       // raw string after the colon, e.g. "MY_ACTION:123"
}
```

Use `ctx.entityId()` whenever the payload encodes a DB record ID (`MY_ACTION:{id}`).

## Step 3 — Classify in SessionRecoveryHandler

Open `SessionRecoveryHandler` and decide: is this action **flow-sensitive**?

- **Flow-sensitive** = the handler reads `UserState` fields that are only set during an active conversation flow (e.g., `direction`, `originHubId`, `departureTime`). Add the action name to `POST_RIDE_ACTIONS` or the relevant set. On stale button after bot restart, the user sees a "session expired" message.
- **Non-flow-sensitive** = the handler works with a fresh `UserState.initial()` (e.g., `MY_PROFILE`, `VIEW_RIDE`). No change needed — `SessionRecoveryHandler` already handles unknown actions by resetting state and proceeding.

## Step 4 — Stale button guard (if flow-sensitive)

If the handler reads fields that could be null after a session reset, guard before using them:

```java
if (ctx.state().getOriginHubId() == null) {
    bot.sendMessage(ctx.chatId(), "⚠️ Session expired. Please start again.");
    stateManager.reset(ctx.chatId());
    return;
}
```

Fields to guard: `direction`, `originHubId`, `departureTime`, `seats`, `contribution`. Any field that auto-unboxes (`int seats = state.getSeats()`) will NPE on null — guard it.

## Checklist

- [ ] `commands.put(...)` added in `CallbackHandler.@PostConstruct`
- [ ] Handler method accepts `BotContext`, uses `ctx.entityId()` for ID payloads
- [ ] Classified in `SessionRecoveryHandler` (flow-sensitive or not)
- [ ] Stale button guard added if handler reads nullable state fields
