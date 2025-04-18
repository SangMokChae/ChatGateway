package kr.co.dataric.gateway.filter;

import kr.co.dataric.common.jwt.provider.JwtProvider;
import kr.co.dataric.common.service.RedisService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class AuthenticationGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthenticationGatewayFilterFactory.Config> {
	
	private final JwtProvider jwtProvider;
	private final RedisService redisService;
	
	public AuthenticationGatewayFilterFactory(JwtProvider jwtProvider, RedisService redisService) {
		super(Config.class);
		this.jwtProvider = jwtProvider;
		this.redisService = redisService;
	}
	
	// 설정값 파싱 방식 (DEFAULT, GATHER_LIST ATC)
	@Override
	public ShortcutType shortcutType() {
		return ShortcutType.DEFAULT;
	}
	
	// application.yml의 필드 순서를 정의
	@Override
	public List<String> shortcutFieldOrder() {
		return List.of("excludePaths");
	}
	
	@Override
	public GatewayFilter apply(Config config) {
		return (exchange, chain) -> {
			String path = exchange.getRequest().getURI().getPath();
			log.info("[AUTH] path: {}", path);
			
			// WebSocket 및 excludePaths 무시 처리
			if (config.excludePaths != null && config.excludePaths.stream().anyMatch(path::equals)) {
				return chain.filter(exchange);
			}
			
			HttpCookie accessTokenCookie = exchange.getRequest().getCookies().getFirst("accessToken");
			HttpCookie refreshTokenCookie = exchange.getRequest().getCookies().getFirst("refreshToken");
			String accessToken = accessTokenCookie != null ? accessTokenCookie.getValue() : null;
			String refreshToken = refreshTokenCookie != null ? refreshTokenCookie.getValue() : null;
			
			// accessToken 없거나 만료된 경우 → refreshToken 확인
			if (accessToken == null || jwtProvider.isTokenExpired(accessToken)) {
				String userId = jwtProvider.extractUserIdIgnoreExpiration(accessToken);
				
				if (userId == null && refreshToken != null) {
					userId = jwtProvider.extractUserIdIgnoreExpiration(refreshToken);
				}
				
				if (userId == null){
					log.warn("[AUTH] accessToken/refreshToken 모두 userId 추출 실패");
					return redirectToLogin(exchange);
				}
				
				// 람다안에서 변경될 수 없다는 보장 때문에 새로 명시해준다.
				String finalUserId = userId;
				
				return redisService.getRefreshToken(userId)
					.filter(saved -> saved.equals(refreshToken)) // Redis : refreshToken 과 쿠키 값 비교
					.filter(valid -> !jwtProvider.isTokenExpired(refreshToken))
					.flatMap(valid -> {
						String newAccessToken = jwtProvider.createAccessToken(finalUserId);
						
						ResponseCookie newAccessCookie = ResponseCookie.from("accessToken", newAccessToken)
							.httpOnly(true)
							.path("/")
							.maxAge(Duration.ofMinutes(30))
							.build();
						
						log.info("[AUTH] AccessToken 재발급 완료 : {}", finalUserId);
						exchange.getResponse().addCookie(newAccessCookie);
						
						return chain.filter(exchange);
					})
					.switchIfEmpty(redirectToLogin(exchange));
			}
			
			// ✅ accessToken 유효한 경우
			String userId = jwtProvider.extractUserId(accessToken);
			if (userId == null) {
				log.warn("[AUTH] AccessToken 유효하지 않음");
				return redirectToLogin(exchange);
			}
			
			return chain.filter(exchange);
		};
	}
	
	private Mono<Void> redirectToLogin(ServerWebExchange exchange) {
		exchange.getResponse().setStatusCode(HttpStatus.SEE_OTHER); // 303
		exchange.getResponse().getHeaders().setLocation(URI.create("/login"));
		return exchange.getResponse().setComplete();
	}
	
	// 예외 처리 경로
	@RequiredArgsConstructor
	@Getter
	@Setter
	public static class Config {
		private List<String> excludePaths = Arrays.asList("/", "/login", "/loginProc", "/favicon.ico", "/logout", "/ws/chat/**", "/ws/notify/**", "/ws/read/**", "/ws/status/**");
	}
}

