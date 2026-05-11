package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.bot.util.ButtonStyle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

/**
 * Handles the /help command and all HELP:* callback actions.
 *
 * Provides a two-level help system:
 *   Level 1 — Help Center main menu (topic selection)
 *   Level 2 — Topic detail screens (how-to guides, rules, commands)
 *
 * Separated from CallbackHandler and MessageHandler to keep
 * help content maintainable in one place.
 */
@Component
@RequiredArgsConstructor
public class HelpHandler {

    /**
     * Entry point — called from MessageHandler (/help command)
     * and CallbackHandler (HELP:BACK).
     */
    public void showHelpMenu(Long chatId, CarpoolBot bot) {
        bot.send(buildWithInline(chatId,
                "📖 <b>Help Center</b>\n\n" +
                        "What would you like to know about?",
                List.of(
                        List.of(
                                BotMessageBuilder.button("🚗 How to Post a Ride", "HELP:POST_RIDE", ButtonStyle.PRIMARY.toString()),
                                BotMessageBuilder.button("🔍 How to Find a Ride", "HELP:FIND_RIDE",  ButtonStyle.SUCCESS.toString())
                        ),
                        List.of(
                                BotMessageBuilder.button("📋 Community Rules",    "HELP:RULES", ButtonStyle.PRIMARY.toString()),
                                BotMessageBuilder.button("💡 Quick Commands",     "HELP:COMMANDS", ButtonStyle.SUCCESS.toString())
                        ),
                        List.of(
                                BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", ButtonStyle.PRIMARY.toString())
                        )
                )));
    }

    /**
     * Handles HELP:* callback topics.
     * Called from CallbackHandler switch statement.
     */
    public void handleTopic(Long chatId, String topic, CarpoolBot bot) {
        switch (topic != null ? topic : "") {
            case "POST_RIDE" -> showPostRideGuide(chatId, bot);
            case "FIND_RIDE" -> showFindRideGuide(chatId, bot);
            case "RULES"     -> showCommunityRules(chatId, bot);
            case "COMMANDS"  -> showQuickCommands(chatId, bot);
            default          -> showHelpMenu(chatId, bot); // HELP:BACK or unknown
        }
    }

    // ── Topic screens ─────────────────────────────────────────────────────

    private void showPostRideGuide(Long chatId, CarpoolBot bot) {
        bot.send(buildWithInline(chatId,
                "🚗 <b>How to Post a Ride</b>\n\n" +
                        "1. Tap <b>Home to Work</b> or <b>Work to Home</b>\n" +
                        "2. Tap <b>Post a Ride</b>\n" +
                        "3. Enter your departure time\n" +
                        "   Example: <code>04/28 07:30</code>\n" +
                        "4. Type your pickup point\n" +
                        "   Example: <code>SM Southmall</code>\n" +
                        "5. Type your dropoff point\n" +
                        "   Example: <code>BGC</code>\n" +
                        "6. Set available seats (1–8)\n" +
                        "7. Set suggested gas share per seat\n" +
                        "8. Add optional notes for passengers\n" +
                        "9. Confirm your vehicle and post!\n\n" +
                        "💡 <i>Your ride is visible to all community members " +
                        "once posted. You can only have one active ride at a time.</i>",
                List.of(
                        List.of(
                                BotMessageBuilder.button("◀️ Back", "HELP:BACK",ButtonStyle.PRIMARY.toString()),
                                BotMessageBuilder.button("🚗 Post a Ride Now", "POST_RIDE", ButtonStyle.SUCCESS.toString()),
                                BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", ButtonStyle.PRIMARY.toString())
                        )
                )));
    }

    private void showFindRideGuide(Long chatId, CarpoolBot bot) {
        bot.send(buildWithInline(chatId,
                "🔍 <b>How to Find a Ride</b>\n\n" +
                        "1. Tap <b>Home to Work</b> or <b>Work to Home</b>\n" +
                        "2. Tap <b>Find a Ride</b>\n" +
                        "3. Select your preferred time window\n" +
                        "4. Browse available rides\n" +
                        "5. Tap <b>View</b> on a ride to see full details\n" +
                        "6. Tap <b>Book This Ride</b>\n" +
                        "7. Wait for driver approval\n\n" +
                        "💡 <i>You'll be notified via Telegram once the driver " +
                        "responds. Booking requests expire after 20 minutes " +
                        "if unanswered.</i>",
                List.of(
                        List.of(
                                BotMessageBuilder.button("◀️ Back", "HELP:BACK",ButtonStyle.PRIMARY.toString()),
                                BotMessageBuilder.button("🔍 Find a Ride Now", "FIND_RIDE", ButtonStyle.SUCCESS.toString()),
                                BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", ButtonStyle.PRIMARY.toString())
                        )
                )));
    }

    private void showCommunityRules(Long chatId, CarpoolBot bot) {
        bot.send(buildWithInline(chatId,
                "📋 <b>Community Rules</b>\n\n" +
                        "⛽ <b>Cost-Recovery Only</b>\n" +
                        "All contributions are for fuel, tolls, and parking ONLY. " +
                        "No profit allowed.\n\n" +
                        "🚫 <b>No Commercial Use</b>\n" +
                        "This is peer-to-peer carpooling — not a ride-hailing service.\n\n" +
                        "📜 <b>LTFRB Compliance</b>\n" +
                        "Drivers must follow the 2-trip/day limit and secure " +
                        "required permits or QR codes.\n\n" +
                        "🛡️ <b>Safety First</b>\n" +
                        "Follow all traffic laws and prioritize passenger safety. " +
                        "The bot admin is not liable for any incidents.\n\n" +
                        "🚨 <b>Zero Tolerance</b>\n" +
                        "Overcharging, random pickups, or operating without permits " +
                        "(\"Colorum\" behavior) = permanent ban.",
                List.of(
                        List.of(
                                BotMessageBuilder.button("◀️ Back", "HELP:BACK", ButtonStyle.PRIMARY.toString()),
                                BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", ButtonStyle.PRIMARY.toString())
                        )
                )));
    }

    private void showQuickCommands(Long chatId, CarpoolBot bot) {
        bot.send(buildWithInline(chatId,
                "💡 <b>Quick Commands</b>\n\n" +
                        "/start — Go to the main menu\n" +
                        "/postride — Post a new ride\n" +
                        "/findride — Find an available ride\n" +
                        "/myrides — View your posted rides\n" +
                        "/mybookings — View your bookings\n" +
                        "/profile — View your profile and stats\n" +
                        "/vehicle — Manage your vehicle info\n" +
                        "/cancel — Cancel current action\n" +
                        "/help — Open the Help Center",
                List.of(
                        List.of(
                                BotMessageBuilder.button("◀️ Back", "HELP:BACK",ButtonStyle.PRIMARY.toString()),
                                BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", ButtonStyle.PRIMARY.toString())
                        )
                )));
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private SendMessage buildWithInline(Long chatId, String text,
                                        List<List<InlineKeyboardButton>> rows) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(BotMessageBuilder.inlineButtons(rows))
                .build();
    }
}