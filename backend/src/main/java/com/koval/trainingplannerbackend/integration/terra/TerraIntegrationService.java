package com.koval.trainingplannerbackend.integration.terra;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.auth.UserService;
import org.springframework.stereotype.Service;

/**
 * Orchestrates Terra-backed provider connections (currently only Nolio).
 */
@Service
public class TerraIntegrationService {

    private final TerraWidgetService widgetService;
    private final TerraApiClient terraApiClient;
    private final UserService userService;
    private final UserRepository userRepository;

    public TerraIntegrationService(TerraWidgetService widgetService,
                                   TerraApiClient terraApiClient,
                                   UserService userService,
                                   UserRepository userRepository) {
        this.widgetService = widgetService;
        this.terraApiClient = terraApiClient;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    public TerraApiClient.WidgetSession startNolioConnect(String userId) {
        return widgetService.generateNolioSession(userId);
    }

    public void disconnectNolio(String userId) {
        User user = userService.getUserById(userId);
        if (user.getTerraUserId() != null) {
            terraApiClient.deauthenticateUser(user.getTerraUserId());
        }
        user.setTerraUserId(null);
        user.setTerraProviderNolioConnected(false);
        userRepository.save(user);
    }
}
