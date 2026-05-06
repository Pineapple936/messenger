package messenger.commonlibs.dto.reactionservice;

public record GatewayReactionEventDto(
        EventType type,
        Long chatId,
        String messageId,
        Long userId,
        String reactionType
) {
    public enum EventType {
        REACTION_ADDED,
        REACTION_DELETED
    }

    public static GatewayReactionEventDto reactionAdded(Long chatId, String messageId, Long userId, String reactionType) {
        return new GatewayReactionEventDto(EventType.REACTION_ADDED, chatId, messageId, userId, reactionType);
    }

    public static GatewayReactionEventDto reactionDeleted(Long chatId, String messageId, Long userId, String reactionType) {
        return new GatewayReactionEventDto(EventType.REACTION_DELETED, chatId, messageId, userId, reactionType);
    }
}
