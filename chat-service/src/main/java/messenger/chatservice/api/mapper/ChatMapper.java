package messenger.chatservice.api.mapper;

import messenger.chatservice.api.dto.ChatResponse;
import messenger.chatservice.domain.Chat;
import messenger.commonlibs.mapper.CommonMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CommonMapperConfig.class)
public interface ChatMapper {
    @Mapping(source = "id", target = "chatId")
    @Mapping(source = "name", target = "chatName")
    ChatResponse toResponse(Chat chat);
}
