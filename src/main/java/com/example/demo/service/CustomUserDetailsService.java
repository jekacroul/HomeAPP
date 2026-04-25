package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Optional<User> userOpt = userRepository.findByEmail(email);
        
        if (userOpt.isEmpty()) {
            throw new UsernameNotFoundException("Пользователь с email " + email + " не найден");
        }
        
        User user = userOpt.get();
        
        // Создаем кастомный UserDetails с именем пользователя
        return new org.springframework.security.core.userdetails.User(
            user.getEmail(),
            user.getPassword(),
            !user.isBanned(),
            true,
            true,
            !user.isBanned(),
            java.util.Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        ) {
            @Override
            public String toString() {
                // Возвращаем имя для отображения в шаблонах
                return user.getName() != null ? user.getName() : user.getEmail();
            }
            
            public String getDisplayName() {
                return user.getName() != null ? user.getName() : user.getEmail();
            }
        };
    }
}
