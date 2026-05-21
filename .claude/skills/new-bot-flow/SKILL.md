---
name: new-bot-flow
description: Use when adding a new multi-step Telegram bot conversation flow. Covers BotFlow enum, MessageHandler routing, UserState management, and terminal step cleanup.
---

# Adding a New Bot Conversation Flow

## Step 1 — Add steps to BotFlow enum

Open `carpool-domain/.../enums/BotFlow.java`. Add an entry for each step in the new flow:

```java
MY_FLOW_STEP_ONE,
MY_FLOW_STEP_TWO,
MY_FLOW_CONFIRM,
```

Steps are used to route text input in `MessageHandler`.

## Step 2 — Route text input in MessageHandler

Open `carpool-bot/.../MessageHandler.java`. In `handleMessage()`, the switch/dispatch is based on `state.getFlow()`. Add cases for each step that expects text input:

```java
case MY_FLOW_STEP_ONE -> myHandler.handleStepOneText(ctx);
case MY_FLOW_STEP_TWO -> myHandler.handleStepTwoText(ctx);
```

Steps that use inline keyboard buttons (not free text) don't need a routing entry here — they're handled via `CallbackHandler`.

## Step 3 — Enter the flow from a callback

From the entry callback, set the initial flow state:

```java
UserState newState = stateManager.getState(chatId)
    .withFlow(BotFlow.MY_FLOW_STEP_ONE)
    .withSomeField(someValue);
stateManager.setState(chatId, newState);
bot.sendMessage(chatId, "Prompt for step one...");
```

Use `UserState.with*()` builder methods — `UserState` is an immutable record. Never mutate directly.

## Step 4 — Advance through steps

Each step handler reads input, validates, updates state, advances flow:

```java
public void handleStepOneText(BotContext ctx) {
    String input = ctx.payload(); // or ctx.parts()[1] depending on context
    // validate input...
    UserState next = ctx.state()
        .withSomeField(parsedValue)
        .withFlow(BotFlow.MY_FLOW_STEP_TWO);
    stateManager.setState(ctx.chatId(), next);
    bot.sendMessage(ctx.chatId(), "Now tell me step two...");
}
```

## Step 5 — Terminal step: reset state

At the final step (after saving data), always reset:

```java
stateManager.reset(ctx.chatId());
```

Or if showing the main menu:

```java
BotFlowHelper.showMainMenu(ctx.chatId(), bot, stateManager);
// showMainMenu internally calls stateManager.reset() — don't double-reset
```

## Step 6 — Add cancel handling

Every flow should have a cancel path. Register a `CANCEL_MY_FLOW` callback that resets state and returns to the main menu. Add it to `CallbackHandler` following the `new-bot-command` skill.

## Checklist

- [ ] `BotFlow` enum entries added for each step
- [ ] `MessageHandler` routes text-input steps to handler methods
- [ ] Entry callback sets initial `UserState` with correct flow
- [ ] Each step advances flow via `state.withFlow(...)`
- [ ] Terminal step calls `stateManager.reset()` or `BotFlowHelper.showMainMenu()`
- [ ] Cancel callback registered and resets state
- [ ] `SessionRecoveryHandler`: flow-sensitive steps added to the appropriate set
