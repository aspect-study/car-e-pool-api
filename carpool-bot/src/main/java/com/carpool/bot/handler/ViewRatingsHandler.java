package com.carpool.bot.handler;

import com.carpool.bot.handler.context.BotContext;
import com.carpool.bot.util.BotMessageBuilder;
import com.carpool.common.util.HtmlEscapeUtil;
import com.carpool.domain.entity.RideRating;
import com.carpool.service.rating.RatingService;
import com.carpool.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the ratings wall — paginated view of received ratings for any user.
 *
 * Entry points:
 *   VIEW_RATINGS:{userId}          — page 0, sends a new message (D1)
 *   RATINGS_PAGE:{userId}:{page}   — paginate, edits in place (D1)
 *   CLOSE_RATINGS:{userId}         — deletes the ratings message
 *
 * Stateless — all state is encoded in callback data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewRatingsHandler {

    private static final int PAGE_SIZE = 5;

    private final RatingService ratingService;
    private final UserService   userService;

    // ── VIEW_RATINGS:{userId} ─────────────────────────────────────────────

    public void handleViewRatings(BotContext ctx) {
        Long targetUserId = ctx.entityId();
        if (targetUserId == null) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ Invalid request."));
            return;
        }
        try {
            Page<RideRating> page = ratingService.getRatingsReceivedPaged(targetUserId, 0, PAGE_SIZE);
            String displayName    = resolveDisplayName(page, targetUserId, ctx.carpoolUserId());
            boolean isOwn         = targetUserId.equals(ctx.carpoolUserId());

            ctx.bot().send(buildSendMessage(ctx.chatId(), page, displayName, isOwn, 0, targetUserId));
        } catch (Exception e) {
            log.error("Failed to load ratings: targetUserId={} error={}", targetUserId, e.getMessage(), e);
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ Could not load ratings. Please try again."));
        }
    }

    // ── RATINGS_PAGE:{userId}:{page} ──────────────────────────────────────

    public void handleRatingsPage(BotContext ctx) {
        if (ctx.parts().length < 3) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ Invalid request."));
            return;
        }
        Long targetUserId;
        int requestedPage;
        try {
            targetUserId  = Long.parseLong(ctx.parts()[1]);
            requestedPage = Integer.parseInt(ctx.parts()[2]);
        } catch (NumberFormatException e) {
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ Invalid request."));
            return;
        }
        try {
            // Fetch page 0 first to get totalPages for clamping (D3)
            Page<RideRating> page = ratingService.getRatingsReceivedPaged(targetUserId, 0, PAGE_SIZE);
            int totalPages  = Math.max(1, page.getTotalPages());
            int clampedPage = Math.max(0, Math.min(requestedPage, totalPages - 1));

            if (clampedPage != 0) {
                page = ratingService.getRatingsReceivedPaged(targetUserId, clampedPage, PAGE_SIZE);
            }

            String displayName = resolveDisplayName(page, targetUserId, ctx.carpoolUserId());
            boolean isOwn      = targetUserId.equals(ctx.carpoolUserId());

            ctx.bot().edit(EditMessageText.builder()
                    .chatId(ctx.chatId())
                    .messageId(ctx.messageId())
                    .parseMode("HTML")
                    .text(buildText(page, displayName, isOwn, clampedPage))
                    .replyMarkup(BotMessageBuilder.inlineButtons(buildNavRow(page, targetUserId, clampedPage)))
                    .build());
        } catch (Exception e) {
            log.error("Failed to paginate ratings: targetUserId={} page={} error={}",
                    targetUserId, requestedPage, e.getMessage(), e);
            ctx.bot().send(BotMessageBuilder.text(ctx.chatId(), "⚠️ Could not load ratings. Please try again."));
        }
    }

    // ── CLOSE_RATINGS:{userId} ────────────────────────────────────────────

    public void handleCloseRatings(BotContext ctx) {
        ctx.bot().deleteMessage(ctx.chatId(), ctx.messageId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private SendMessage buildSendMessage(Long chatId, Page<RideRating> page,
                                        String displayName, boolean isOwn,
                                        int pageNumber, Long targetUserId) {
        return SendMessage.builder()
                .chatId(chatId)
                .parseMode("HTML")
                .text(buildText(page, displayName, isOwn, pageNumber))
                .replyMarkup(BotMessageBuilder.inlineButtons(buildNavRow(page, targetUserId, pageNumber)))
                .build();
    }

    private String buildText(Page<RideRating> page, String displayName, boolean isOwn, int pageNumber) {
        long total  = page.getTotalElements();
        String header = isOwn
                ? "⭐ <b>Your Ratings</b> (" + total + " total)"
                : "⭐ <b>Ratings for " + displayName + "</b> (" + total + " total)";

        // D7: zero-ratings empty state
        if (total == 0) {
            return header + "\n\nNo ratings yet.";
        }

        StringBuilder sb = new StringBuilder(header);
        for (RideRating rating : page.getContent()) {
            sb.append("\n\n").append("⭐".repeat(rating.getStars()));
            if (rating.getComment() != null && !rating.getComment().isBlank()) {
                sb.append("\n").append(HtmlEscapeUtil.escape(rating.getComment()));
            }
        }

        int totalPages = page.getTotalPages();
        sb.append("\n\n— Page ").append(pageNumber + 1).append(" of ").append(totalPages).append(" —");
        return sb.toString();
    }

    private List<List<InlineKeyboardButton>> buildNavRow(Page<RideRating> page,
                                                         Long targetUserId, int currentPage) {
        if (page.getTotalElements() == 0) {
            return List.of(List.of(
                    BotMessageBuilder.button("✕ Close", "CLOSE_RATINGS:" + targetUserId, null)
            ));
        }
        List<InlineKeyboardButton> nav = new ArrayList<>();
        if (currentPage > 0) {
            nav.add(BotMessageBuilder.button("« Prev",
                    "RATINGS_PAGE:" + targetUserId + ":" + (currentPage - 1), null));
        }
        if (currentPage < page.getTotalPages() - 1) {
            nav.add(BotMessageBuilder.button("Next »",
                    "RATINGS_PAGE:" + targetUserId + ":" + (currentPage + 1), null));
        }
        nav.add(BotMessageBuilder.button("✕ Close", "CLOSE_RATINGS:" + targetUserId, null));
        return List.of(nav);
    }

    /**
     * Resolves the display name of the target user for the ratings header.
     * When page is non-empty, reads from the already-eager-loaded ratee to avoid
     * an extra DB call. Falls back to UserService for empty pages.
     */
    private String resolveDisplayName(Page<RideRating> page, Long targetUserId, Long currentUserId) {
        if (targetUserId.equals(currentUserId)) return null;
        if (!page.getContent().isEmpty()) {
            return HtmlEscapeUtil.escape(page.getContent().get(0).getRatee().getFullName());
        }
        try {
            return HtmlEscapeUtil.escape(userService.getUserById(targetUserId).fullName());
        } catch (Exception e) {
            log.warn("Could not resolve display name for userId={}", targetUserId);
            return "this user";
        }
    }
}
