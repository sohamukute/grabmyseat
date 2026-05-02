package com.grabmyseat.auth.config;

import com.grabmyseat.auth.model.OrganizerProfile;
import com.grabmyseat.auth.model.Role;
import com.grabmyseat.auth.model.User;
import com.grabmyseat.auth.repository.OrganizerProfileRepository;
import com.grabmyseat.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

@Configuration
@Profile("demo")
public class DemoAccountSeeder {

    @Bean
    ApplicationRunner seedDemoAccounts(UserRepository users, OrganizerProfileRepository profiles,
                                       PasswordEncoder encoder,
                                       @Value("${demo.admin.username}") String adminUsername,
                                       @Value("${demo.admin.password}") String adminPassword,
                                       @Value("${demo.staff.username}") String staffUsername,
                                       @Value("${demo.staff.password}") String staffPassword,
                                       @Value("${demo.organizer.username}") String organizerUsername,
                                       @Value("${demo.organizer.password}") String organizerPassword) {
        return args -> seed(users, profiles, encoder, adminUsername, adminPassword, staffUsername, staffPassword,
                organizerUsername, organizerPassword);
    }

    @Transactional
    void seed(UserRepository users, OrganizerProfileRepository profiles, PasswordEncoder encoder,
              String adminUsername, String adminPassword, String staffUsername, String staffPassword,
              String organizerUsername, String organizerPassword) {
        ensureUser(users, encoder, adminUsername, adminPassword, Role.ROLE_ADMIN);
        ensureUser(users, encoder, staffUsername, staffPassword, Role.ROLE_STAFF);
        User organizer = ensureUser(users, encoder, organizerUsername, organizerPassword, Role.ROLE_ORGANIZER);
        if (!profiles.existsByCompanyEmail("demo@northstarlive.in")) {
            profiles.save(new OrganizerProfile(organizer.getId(), "North Star Live", "demo@northstarlive.in", "9876543210"));
        }
    }

    private User ensureUser(UserRepository users, PasswordEncoder encoder, String username,
                            String password, Role role) {
        return users.findByUsername(username).orElseGet(() -> users.save(new User(
                username, encoder.encode(password), EnumSet.of(role))));
    }
}
