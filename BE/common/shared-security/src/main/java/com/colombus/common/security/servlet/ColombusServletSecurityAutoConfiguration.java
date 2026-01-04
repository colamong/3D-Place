package com.colombus.common.security.servlet;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import com.colombus.common.security.core.ActuatorSecurityProperties;
import com.colombus.common.security.core.ColombusJwtSupport;
import com.colombus.common.security.core.JwtSecurityProperties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({JwtSecurityProperties.class, ActuatorSecurityProperties.class})
public class ColombusServletSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder colombusPasswordEncoder() {
        log.info("[ColombusSecurity] Init reactive PasswordEncoder (delegating).");
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    // actuator in-memory user
    @Bean
    @ConditionalOnMissingBean(name = "actuatorUserDetailsService")
    @ConditionalOnProperty(
        prefix = "security.actuator",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public UserDetailsService actuatorUserDetailsService(
        ActuatorSecurityProperties props,
        PasswordEncoder encoder
    ) {
        log.info("[ColombusSecurity] Init actuator userDetailsService. username={}", props.username());
        UserDetails actuatorUser = User.withUsername(props.username())
            .password(encoder.encode(props.password()))
            .roles("ACTUATOR")
            .build();

        return new InMemoryUserDetailsManager(actuatorUser);
    }

    // JWT (servlet)
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "security.jwt", name = "jwk-set-uri")
    public JwtDecoder colombusJwtDecoder(JwtSecurityProperties props) {
        log.info("[ColombusSecurity] Init ReactiveJwtDecoder. issuer={}, jwkSetUri={}, requiredAud={}",
            props.issuer(), props.jwkSetUri(), props.requiredAudience());
        return ColombusJwtSupport.jwtDecoder(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter colombusJwtAuthenticationConverter() {
        log.info("[ColombusSecurity] Init colombusJwtAuthenticationConverter (Servlet).");
        return ColombusJwtSupport.jwtAuthConverter();
    }

    // /actuator/** 체인
    @Bean
    @Order(0)
    @ConditionalOnBean(UserDetailsService.class)
    @ConditionalOnProperty(
        prefix = "security.actuator",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public SecurityFilterChain actuatorSecurityFilterChain(
        HttpSecurity http,
        ActuatorSecurityProperties props
    ) throws Exception {
        log.info("[ColombusSecurity] Building ACTUATOR security chain. path=/actuator/**, username={}", props.username());
        http.securityMatcher("/actuator/**")
            .csrf(csrf -> csrf.disable())
            .httpBasic(Customizer.withDefaults())
            .formLogin(form -> form.disable())
            .authorizeHttpRequests(ex -> ex
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .anyRequest().hasRole("ACTUATOR")
            )
            .exceptionHandling(e -> e.authenticationEntryPoint(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        return http.build();
    }

    // /internal/** local: permitAll
    @Bean
    @Order(1)
    @Profile("local")
    @ConditionalOnProperty(
        prefix = "security.internal",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    @ConditionalOnBean(ServletWebServerApplicationContext.class)
    public SecurityFilterChain internalPermitAllChain(HttpSecurity http) throws Exception {
        log.info("[ColombusSecurity] Building INTERNAL permitAll chain (profile=local). path=/internal/**");
        http.securityMatcher("/internal/**")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    // /internal/** !local: JWT 보호
    @Bean
    @Order(1)
    @Profile("!local")
    @ConditionalOnProperty(
        prefix = "security.internal",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    @ConditionalOnBean(JwtDecoder.class)
    public SecurityFilterChain internalSecureChain(
        HttpSecurity http,
        JwtDecoder jwtDecoder,
        JwtAuthenticationConverter jwtAuthConverter
    ) throws Exception {
        log.info("[BFF-Security] building /api/** secure chain (prod). decoder={}, converter={}",
            jwtDecoder.getClass().getSimpleName(),
            jwtAuthConverter.getClass().getClass().getSimpleName()
        );
        http.securityMatcher("/internal/**")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth
                .jwt(j -> j.decoder(jwtDecoder)
                    .jwtAuthenticationConverter(jwtAuthConverter)));
        return http.build();
    }
}
