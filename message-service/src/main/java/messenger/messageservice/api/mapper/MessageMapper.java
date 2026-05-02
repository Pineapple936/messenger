package messenger.messageservice.api.mapper;

import messenger.commonlibs.dto.messageservice.MessageDto;
import messenger.commonlibs.mapper.CommonMapperConfig;
import messenger.messageservice.api.dto.MessageResponse;
import messenger.messageservice.domain.Message;
import org.mapstruct.Mapper;

@Mapper(config = CommonMapperConfig.class)
public interface MessageMapper {
    MessageDto toDto(Message message);
}
