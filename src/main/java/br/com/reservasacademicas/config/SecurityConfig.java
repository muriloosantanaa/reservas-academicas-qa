package br.com.reservasacademicas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/css/**",
                    "/js/**",
                    "/api/health"
                ).permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/relatorios/**")
                    .hasAnyRole("RESPONSAVEL", "ADMIN")
                .requestMatchers(
                    "/api/reservas/*/aprovar",
                    "/api/reservas/*/rejeitar",
                    "/api/reservas/*/iniciar",
                    "/api/reservas/*/concluir",
                    "/api/reservas/*/nao-compareceu",
                    "/api/reservas/*/retirada",
                    "/api/reservas/*/devolucao"
                ).hasAnyRole("RESPONSAVEL", "ADMIN")
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults())
            .build();
    }

    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
            User.withUsername("admin")
                .password(encoder.encode("Admin123!"))
                .roles("ADMIN", "RESPONSAVEL", "SOLICITANTE")
                .build(),
            User.withUsername("responsavel")
                .password(encoder.encode("Resp123!"))
                .roles("RESPONSAVEL", "SOLICITANTE")
                .build(),
            User.withUsername("professor")
                .password(encoder.encode("Prof123!"))
                .roles("SOLICITANTE")
                .build()
        );
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
