package org.example.sublearn.config;

import org.example.sublearn.security.JwtAuthenticationFilter;
import org.example.sublearn.security.RestAccessDeniedHandler;
import org.example.sublearn.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final CorsFilter corsFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                           RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                           RestAccessDeniedHandler restAccessDeniedHandler,
                           CorsFilter corsFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
        this.corsFilter = corsFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .addFilterBefore(corsFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
