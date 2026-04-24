package org.example.homeapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
public class LoginController {

    @GetMapping("/")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public void processLogin(@RequestParam String username, 
                            @RequestParam String password,
                            HttpServletResponse response) throws IOException {
        // Здесь будет логика аутентификации
        // Для демонстрации просто перенаправляем на приветственную страницу
        System.out.println("Login attempt: " + username);
        
        // В реальном приложении здесь должна быть проверка пароля
        if (!username.isEmpty() && !password.isEmpty()) {
            response.sendRedirect("/welcome?username=" + username);
        } else {
            response.sendRedirect("/?error=true");
        }
    }

    @GetMapping("/welcome")
    public String welcomePage() {
        return "welcome";
    }
}
