package messenger.userservice.api;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import messenger.commonlibs.dto.userservice.CreateUserDto;
import messenger.userservice.api.dto.EditUserDto;
import messenger.userservice.api.dto.UserDetailsResponse;
import messenger.userservice.api.mapper.UserDetailsMapper;
import messenger.userservice.domain.UserDetailsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static messenger.commonlibs.Constants.USER_ID_HEADER;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserDetailsService userDetailsService;
    private final UserDetailsMapper userDetailsMapper;

    @PostMapping
    public ResponseEntity<UserDetailsResponse> create(@Valid @RequestBody CreateUserDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userDetailsMapper.toDto(userDetailsService.save(dto)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserDetailsResponse> getByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(userDetailsMapper.toDto(userDetailsService.getByUserId(userId)));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDetailsResponse> getCurrentUser(@RequestHeader(USER_ID_HEADER) Long userId) {
        return ResponseEntity.ok(userDetailsMapper.toDto(userDetailsService.getByUserId(userId)));
    }

    @GetMapping("/exists/{userId}")
    public ResponseEntity<Boolean> existsUserById(@PathVariable @Positive Long userId) {
        return ResponseEntity.ok(userDetailsService.existsUserById(userId));
    }

    @PostMapping("/edit")
    public ResponseEntity<UserDetailsResponse> edit(@RequestHeader(USER_ID_HEADER) Long userId,
                                                    @Valid @RequestBody EditUserDto dto) {
        return ResponseEntity.ok(userDetailsMapper.toDto(userDetailsService.editUser(userId, dto)));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestHeader(USER_ID_HEADER) Long userId,
                                       @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        userDetailsService.deleteById(userId, authorizationHeader);
        return ResponseEntity.noContent().build();
    }
}
