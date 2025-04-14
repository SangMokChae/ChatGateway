package kr.co.dataric.common.jwt.provider;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import kr.co.dataric.common.jwt.entity.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtProvider {
	
	private final JwtProperties jwtProperties;
	
	private SecretKey key;
	
	@PostConstruct
	public void init() {
		this.key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
	}
	
	public String createAccessToken(String username) {
		Date now = new Date();
		Date expiryDate = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration());
		
		return Jwts.builder()
			.subject(username)
			.issuedAt(now)
			.expiration(expiryDate)
			.signWith(key, Jwts.SIG.HS256)
			.compact();
	}
	
	public String createRefreshToken(String username) {
		Date now = new Date();
		Date expiryDate = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration());
		
		return Jwts.builder()
			.subject(username)
			.issuedAt(now)
			.expiration(expiryDate)
			.signWith(key, Jwts.SIG.HS256)
			.compact();
	}
	
	public String extractUserId(String token) {
		try {
			Claims claims = (Claims) Jwts.parser()
				.verifyWith(key)
				.build()
				.parse(token)
				.getPayload();
			
			return claims.getSubject();
		} catch (JwtException e) {
			log.error("JWT 검증 실패: {}", e.getMessage());
			return null;
		}
	}
	
	public boolean isTokenExpired(String token) {
		try {
			Claims claims = (Claims) Jwts.parser()
				.verifyWith(key)
				.build()
				.parse(token)
				.getPayload();
			
			return claims.getExpiration().before(new Date());
		} catch (JwtException e) {
			return true;
		}
	}
	
	public String extractUserIdIgnoreExpiration(String token) {
		try {
			Claims claims = (Claims) Jwts.parser()
				.verifyWith(key)
				.build()
				.parse(token)
				.getPayload();
			return claims.getSubject();
		} catch (Exception e) {
			log.warn("JWT 디코딩 실패: {}", e.getMessage());
			return null;
		}
	}
}