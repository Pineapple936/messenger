package messenger.authenticationservice.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Data
@Entity
@Table(name = "user_credential")
@NoArgsConstructor
public class UserCredential implements UserDetails {
    UserCredential(String email, String password) {
        this.email = email;
        this.password = password;
        this.authToken = "";
        this.refreshToken = "";
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 254, unique = true)
    private String email;

    @Column(name = "password", nullable = false, length = 1024)
    private String password;

    @Column(name = "auth_token", nullable = false, length = 1024, columnDefinition = "varchar(1024) default ''")
    private String authToken;

    @Column(name = "refresh_token", nullable = false, length = 1024, columnDefinition = "varchar(1024) default ''")
    private String refreshToken;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getUsername() {
        return String.valueOf(id);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
