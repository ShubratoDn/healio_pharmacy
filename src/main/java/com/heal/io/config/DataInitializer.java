package com.heal.io.config;

import com.heal.io.entity.Role;
import com.heal.io.entity.User;
import com.heal.io.repository.RoleRepository;
import com.heal.io.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        initializeRoles();
        initializeAdminUser();
    }

    private void initializeRoles() {
        if (roleRepository.count() == 0) {
            Role adminRole = Role.builder()
                    .name("ADMIN")
                    .description("Administrator role with full access")
                    .build();
            roleRepository.save(adminRole);
            log.info("Created ADMIN role");

            Role staffRole = Role.builder()
                    .name("STAFF")
                    .description("Staff role with limited access")
                    .build();
            roleRepository.save(staffRole);
            log.info("Created STAFF role");
        }
    }

    private void initializeAdminUser() {
        if (!userRepository.existsByUsername("admin")) {
            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseThrow(() -> new RuntimeException("ADMIN role not found"));

            User admin = User.builder()
                    .username("admin")
                    .email("admin@healio.com")
                    .password(passwordEncoder.encode("admin123"))
                    .name("System Administrator")
                    .phone("+8801234567890")
                    .isEnabled(true)
                    .isAccountNonExpired(true)
                    .isAccountNonLocked(true)
                    .isCredentialsNonExpired(true)
                    .roles(Set.of(adminRole))
                    .build();

            userRepository.save(admin);
            log.info("Created default admin user - Username: admin, Password: admin123");
        }
    }
}

