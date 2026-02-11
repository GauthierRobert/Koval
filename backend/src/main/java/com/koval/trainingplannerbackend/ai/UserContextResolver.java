package com.koval.trainingplannerbackend.ai;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import org.springframework.stereotype.Component;

/**
 * Resolves user context (role, FTP) for AI prompt parametrization.
 */
@Component
public class UserContextResolver {

    private static final String DEFAULT_ROLE = "ATHLETE";
    private static final int DEFAULT_FTP = 250;

    private final UserRepository userRepository;

    public UserContextResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserContext resolve(String userId) {
        User user = userRepository.findById(userId).orElse(null);
        String role = user != null ? user.getRole().name() : DEFAULT_ROLE;
        int ftp = user != null && user.getFtp() != null ? user.getFtp() : DEFAULT_FTP;
        return new UserContext(userId, role, ftp);
    }

    public record UserContext(String userId, String role, int ftp) {}
}
