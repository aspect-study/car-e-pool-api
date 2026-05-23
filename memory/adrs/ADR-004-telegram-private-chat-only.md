# ADR-004: Bot Processes Only Private Chat Messages

**Status:** Accepted  
**Date:** 2024  
**Deciders:** Product + Backend

## Context

The Telegram bot is added to a group chat so it can post ride announcements to topic threads. Without a guard, users could inadvertently (or deliberately) trigger bot commands from within the group chat — creating rides, cancelling bookings, etc., without the context the bot expects.

## Decision

The bot ignores all messages and callback queries that originate from a group or supergroup chat. Only private chat updates are processed.

```java
// In TelegramBot update handler
if (!update.hasMessage() || !update.getMessage().getChat().isUserChat()) {
    return;  // ignore group messages
}
```

Group membership is used for one purpose only: posting ride announcements to the configured topic threads via the `GroupAnnouncementPort`.

## Consequences

- Users must always interact with the bot via a private chat — the bot's link (`t.me/<bot_username>`) opens a private conversation
- Group announcements are one-directional: service → bot → group topic; users cannot interact with announcements
- `TELEGRAM_GROUP_CHAT_ID`, `TELEGRAM_GROUP_HOME_TO_WORK_TOPIC_ID`, and `TELEGRAM_GROUP_WORK_TO_HOME_TOPIC_ID` are env-var-only — never hardcoded
- Bot commands like `/start`, `/help`, and inline keyboard callbacks are only routable from private chats

## Related

The `CallbackHandler` and `MessageHandler` assume `chatId` always refers to a private chat — they use it as the user's Telegram chat ID for sending replies. Group chat IDs must never be stored or used as a user identity.