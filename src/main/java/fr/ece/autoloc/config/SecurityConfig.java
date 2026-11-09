package fr.ece.autoloc.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The public site is open. Clients reach their own area only; staff reach the back office, where
 * managers alone delete, and alone edit agencies and employees.
 */
@Configuration
public class SecurityConfig {

    private static final String MANAGER = "MANAGER";

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, RedirectionApresConnexion redirection) {
        return http
                .authorizeHttpRequests(regles -> regles
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/fonts/**", "/vendor/**", "/favicon.ico",
                                "/", "/catalogue", "/catalogue/**", "/inscription",
                                "/connexion", "/premiere-connexion", "/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers("/espace", "/espace/**").hasRole("CLIENT")
                        .requestMatchers("/statistiques").hasRole(MANAGER)
                        .requestMatchers(HttpMethod.POST, "/{section}/{id}/supprimer").hasRole(MANAGER)
                        .requestMatchers(HttpMethod.POST, "/agences/**", "/employes/**").hasRole(MANAGER)
                        .requestMatchers("/agences/nouveau", "/agences/{id}/modifier",
                                "/employes/nouveau", "/employes/{id}/modifier").hasRole(MANAGER)
                        .anyRequest().hasAnyRole("AGENT", MANAGER))
                .formLogin(connexion -> connexion
                        .loginPage("/connexion")
                        .usernameParameter("identifiant")
                        .passwordParameter("motDePasse")
                        .successHandler(redirection))
                .logout(deconnexion -> deconnexion
                        .logoutUrl("/deconnexion")
                        .logoutSuccessUrl("/connexion?deconnecte"))
                .build();
    }
}
