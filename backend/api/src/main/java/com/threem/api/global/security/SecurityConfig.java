package com.threem.api.global.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.threem.api.domain.error.UserErrorCode;
import com.threem.api.global.config.AuthProperties;

import lombok.RequiredArgsConstructor;

/**
 * API는 {@code Authorization: Bearer} Access Token으로 인증한다(세션 없음).
 * 세션은 Google 로그인 중 인가 요청(state)을 콜백까지 들고 있는 동안에만 쓴다.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
class SecurityConfig {

    /** Google 로그인 시작: {@code GET /api/v1/auth/oauth/authorize/google} */
    static final String OAUTH_AUTHORIZE_URI = "/api/v1/auth/oauth/authorize";
    /** Google이 돌아오는 주소. yml의 {@code redirect-uri}와 맞춘다. */
    static final String OAUTH_CALLBACK_URI = "/api/v1/auth/oauth/callback";

    private final AuthProperties authProperties;

    /**
     * 핸들러는 생성자가 아니라 여기서 받는다. 핸들러 → AuthService → PasswordEncoder(이 클래스) 순환을 피한다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler securityErrorHandler,
            OAuth2LoginSuccessHandler oauth2LoginSuccessHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, OAUTH_AUTHORIZE_URI + "/*", OAUTH_CALLBACK_URI + "/*").permitAll()
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(endpoint -> endpoint.baseUri(OAUTH_AUTHORIZE_URI))
                        .redirectionEndpoint(endpoint -> endpoint.baseUri(OAUTH_CALLBACK_URI + "/*"))
                        .successHandler(oauth2LoginSuccessHandler)
                        .failureHandler((request, response, exception) ->
                                OAuth2LoginSuccessHandler.redirectToFrontend(request, response,
                                        authProperties.frontendUrl(), UserErrorCode.OAUTH_FAILED.code())))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(JwtProvider.secretKey(authProperties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtProvider.ISSUER));
        return decoder;
    }

    /**
     * 프론트엔드만 허용한다. Refresh Token 쿠키를 주고받으므로 credentials를 허용한다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(authProperties.frontendUrl()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * {@code role} 클레임을 {@code ROLE_*} 권한으로 바꾼다.
     */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(JwtProvider.ROLE_CLAIM);
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
