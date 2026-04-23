package messenger.userservice.api.mapper;

import messenger.commonlibs.mapper.CommonMapperConfig;
import messenger.userservice.api.dto.UserDetailsResponse;
import messenger.userservice.domain.UserDetails;
import org.mapstruct.Mapper;

@Mapper(config = CommonMapperConfig.class)
public interface UserDetailsMapper {
    UserDetailsResponse toDto(UserDetails userDetails);
}
