package com.aficio.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.aficio.ratelimit.RateLimitFilter;
import com.aficio.ratelimit.RateLimitService;
import com.aficio.security.JwtAuthenticationFilter;
import com.aficio.security.JwtService;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(JwtService jwtService, org.springframework.beans.factory.ObjectProvider<RateLimitService> rateLimitServiceProvider) {
        this.jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService);
        RateLimitService rateLimitService = rateLimitServiceProvider.getIfAvailable();
        if (rateLimitService != null) {
            this.rateLimitFilter = new RateLimitFilter(rateLimitService);
        } else {
            log.info("RateLimitService not available, skipping RateLimitFilter");
            this.rateLimitFilter = null;
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource(
            @Value("${aficio.cors.allowed-origins:http://localhost:*}") String allowedOrigins) {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        String[] origins = allowedOrigins.split(",");
        java.util.List<String> originList = java.util.Arrays.stream(origins)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        configuration.setAllowedOriginPatterns(originList.isEmpty() ? java.util.List.of("*") : originList);
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        configuration.setAllowCredentials(true);
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {
        http
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/users/register",
                                "/users/login",
                                "/users/oauth/**",
                                "/users/verify-email",
                                "/users/resend-verification",
                                "/users/2fa/verify",
                                "/actuator/**",
                                "/users/internal/**"
                        )
                        .permitAll()
                        .requestMatchers("/users/settings", "/users/settings/**", "/users/account", "/users/api-key", "/users/api-key/**")
                        .authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/users/*", "/users/id/*", "/users/*/privacy")
                        .permitAll()
                        .anyRequest()
                        .authenticated()
                );

        if (rateLimitFilter != null) {
            http.addFilterBefore(
                    rateLimitFilter,
                    UsernamePasswordAuthenticationFilter.class
            );
        }

        http.addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }
}