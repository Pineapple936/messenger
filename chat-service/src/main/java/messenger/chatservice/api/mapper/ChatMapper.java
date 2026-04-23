package messenger.chatservice.api.mapper;

import messenger.chatservice.api.dto.ChatResponse;
import messenger.chatservice.domain.Chat;
import messenger.commonlibs.mapper.CommonMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = CommonMapperConfig.class)
public interface ChatMapper {
    ChatResponse toDto(Chat chat);
}
