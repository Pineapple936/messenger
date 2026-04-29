package messenger.gatewayservice.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

import static messenger.commonlibs.Constants.*;

@Configuration
public class RateLimiterKeyResolverConfig {


    @Bean("authIpKeyResolver")
    public KeyResolver authIpKeyResolver() {
        return this::resolveClientIp;
    }

    @Bean("userIdKeyResolver")
    @Primary
    public KeyResolver userIdKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(USER_ID_HEADER);
            if (StringUtils.hasText(userId)) {
                return Mono.just(userId.trim());
            }
            return resolveClientIp(exchange);
        };
    }

    private Mono<String> resolveClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst(X_FORWARDED_FOR_HEADER);
        if (StringUtils.hasText(forwardedFor)) {
            String firstIp = forwardedFor.split(",")[0].trim();
            if (StringUtils.hasText(firstIp)) {
                return Mono.just(firstIp);
            }
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            String hostAddress = remoteAddress.getAddress().getHostAddress();
            if (StringUtils.hasText(hostAddress)) {
                return Mono.just(hostAddress);
            }
        }

        return Mono.just(UNKNOWN_CLIENT_KEY);
    }
}
