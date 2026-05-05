package com.carpool.bot.handler.command;

import com.carpool.bot.handler.context.BotContext;

/**
 * Command pattern interface for all bot actions.
 * Each callback action or message flow maps to a BotCommand implementation.
 * Registered in CallbackHandler and MessageHandler via @PostConstruct Map registry.
 *
 * Replaces the large switch statements — adding a new action means registering
 * a new entry in the map, with zero changes to the router.
 */
@FunctionalInterface
public interface BotCommand {
    void execute(BotContext ctx);
}