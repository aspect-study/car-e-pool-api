package com.carpool.bot.handler;

import com.carpool.bot.CarpoolBot;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.bot.util.ButtonStyle;
import com.carpool.service.donate.DonateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Handles the /donate command and the DONATE_GCASH callback.
 * <p>
 * Donations are voluntary support for community/server costs — never tied
 * to a specific ride, booking, or fare. Keeping that decoupled matters:
 * PH LTFRB rules ban per-trip/per-passenger fare collection for carpooling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DonateHandler {

    private final DonateService donateService;

    private static final String GCASH_QR_RESOURCE = "/images/gcash-qr.png";
    private static final String GCASH_CHANNEL = "GCASH";

    /**
     * Gates the donate button on post-ride screens so it doesn't nag on
     * every single completion. rideId is a platform-wide auto-increment
     * id, so this lands roughly 1-in-3 completions without needing any
     * per-user tracking state.
     */
    public static boolean shouldPromptOnRideEnd(Long rideId) {
        return rideId != null && rideId % 3 == 0;
    }

    public static InlineKeyboardButton donateButton() {
        return BotMessageBuilder.button("💙 Donate", "DONATE", ButtonStyle.PRIMARY.toString());
    }

    public void showDonate(Long chatId, CarpoolBot bot) {
        bot.send(SendMessage.builder()
                .chatId(chatId)
                .text("""
                        💙 <b>Support SouthPoolCare</b>

                        This community runs on volunteer time and covers its own \
                        server costs. If you'd like to help out, you're welcome to \
                        send a donation below.

                        <i>Completely optional — not tied to any ride or booking.</i>""")
                .parseMode("HTML")
                .replyMarkup(BotMessageBuilder.inlineButtons(List.of(
                        List.of(BotMessageBuilder.button("💙 GCash", "DONATE_GCASH", ButtonStyle.PRIMARY.toString())),
                        List.of(BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", ButtonStyle.PRIMARY.toString()))
                )))
                .build());
    }

    public void showGcash(Long chatId, Long carpoolUserId, CarpoolBot bot) {
        try {
            donateService.recordClick(carpoolUserId, GCASH_CHANNEL);
        } catch (Exception e) {
            log.warn("Failed to record donate click: userId={} error={}", carpoolUserId, e.getMessage(), e);
        }

        try (InputStream qr = getClass().getResourceAsStream(GCASH_QR_RESOURCE)) {
            if (qr == null) {
                log.warn("GCash QR resource not found at {}", GCASH_QR_RESOURCE);
                bot.send(BotMessageBuilder.text(chatId, "⚠️ GCash QR is not available right now."));
                return;
            }

            bot.sendPhoto(SendPhoto.builder()
                    .chatId(chatId.toString())
                    .photo(new InputFile(qr, "gcash-qr.png"))
                    .caption("""
                            💙 <b>GCash</b>

                            Scan this QR code in your GCash app, or send to:
                            📱 <code>0977 806 6342</code>

                            <i>Any amount is appreciated — it helps cover server \
                            and hosting costs for the bot. Voluntary support only, \
                            not a ride payment.</i>""")
                    .parseMode("HTML")
                    .replyMarkup(BotMessageBuilder.inlineButtons(List.of(
                            List.of(BotMessageBuilder.button("🏠 Menu", "MAIN_MENU", ButtonStyle.PRIMARY.toString()))
                    )))
                    .build());
        } catch (IOException e) {
            log.error("Failed to load GCash QR resource for chatId={}: {}", chatId, e.getMessage(), e);
            bot.send(BotMessageBuilder.text(chatId, "⚠️ Something went wrong loading the GCash QR."));
        }
    }
}
