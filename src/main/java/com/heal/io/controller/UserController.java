package com.heal.io.controller;

import com.heal.io.entity.Role;
import com.heal.io.entity.User;
import com.heal.io.repository.RoleRepository;
import com.heal.io.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public String listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<User> users;

        if (search != null && !search.isEmpty()) {
            users = userRepository.searchUsers(search, pageable);
        } else {
            users = userRepository.findByIsActiveTrue(pageable);
        }

        model.addAttribute("users", users);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", users.getTotalPages());
        model.addAttribute("search", search);
        return "users/list";
    }

    @GetMapping("/new")
    public String showUserForm(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("selectedRoles", new HashSet<Long>());
        return "users/form";
    }

    @PostMapping("/save")
    public String saveUser(
            @ModelAttribute User user,
            @RequestParam(required = false) List<Long> roleIds,
            @RequestParam(required = false) String password,
            RedirectAttributes redirectAttributes) {
        
        if (user.getId() != null) {
            // Update existing user
            User existing = userRepository.findById(user.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            existing.setName(user.getName());
            existing.setEmail(user.getEmail());
            existing.setPhone(user.getPhone());
            existing.setIsEnabled(user.getIsEnabled());
            existing.setIsAccountNonExpired(user.getIsAccountNonExpired());
            existing.setIsAccountNonLocked(user.getIsAccountNonLocked());
            existing.setIsCredentialsNonExpired(user.getIsCredentialsNonExpired());
            
            // Update password only if provided
            if (password != null && !password.isEmpty()) {
                existing.setPassword(passwordEncoder.encode(password));
            }
            
            // Update roles
            if (roleIds != null && !roleIds.isEmpty()) {
                Set<Role> roles = roleIds.stream()
                        .map(roleRepository::findById)
                        .filter(java.util.Optional::isPresent)
                        .map(java.util.Optional::get)
                        .collect(Collectors.toSet());
                existing.setRoles(roles);
            }
            
            userRepository.save(existing);
        } else {
            // Create new user
            if (userRepository.existsByUsername(user.getUsername())) {
                redirectAttributes.addFlashAttribute("error", "Username already exists!");
                return "redirect:/users/new";
            }
            if (userRepository.existsByEmail(user.getEmail())) {
                redirectAttributes.addFlashAttribute("error", "Email already exists!");
                return "redirect:/users/new";
            }
            
            user.setPassword(passwordEncoder.encode(password != null ? password : "password123"));
            
            if (roleIds != null && !roleIds.isEmpty()) {
                Set<Role> roles = roleIds.stream()
                        .map(roleRepository::findById)
                        .filter(java.util.Optional::isPresent)
                        .map(java.util.Optional::get)
                        .collect(Collectors.toSet());
                user.setRoles(roles);
            }
            
            userRepository.save(user);
        }
        
        redirectAttributes.addFlashAttribute("success", "User saved successfully!");
        return "redirect:/users";
    }

    @GetMapping("/edit/{id}")
    public String editUser(@PathVariable Long id, Model model) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("selectedRoles", user.getRoles() != null ? 
                user.getRoles().stream().map(Role::getId).collect(Collectors.toSet()) : new HashSet<Long>());
        return "users/form";
    }

    @GetMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Prevent deleting the admin user
        if ("admin".equals(user.getUsername())) {
            redirectAttributes.addFlashAttribute("error", "Cannot delete the admin user!");
            return "redirect:/users";
        }
        
        user.setIsActive(false);
        user.setIsEnabled(false);
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("success", "User deleted successfully!");
        return "redirect:/users";
    }
}

