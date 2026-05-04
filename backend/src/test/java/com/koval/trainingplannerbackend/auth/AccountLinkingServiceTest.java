package com.koval.trainingplannerbackend.auth;

import com.koval.trainingplannerbackend.integration.terra.TerraApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountLinkingServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private TerraApiClient terraApiClient;

    private AccountLinkingService service;

    @BeforeEach
    void setUp() {
        service = new AccountLinkingService(userRepository, userService, terraApiClient);
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User existing(String id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    @Nested
    class FindOrCreateFromStrava {

        @Test
        void existingByStravaId_updatesTokens() {
            User existing = existing("u1");
            existing.setStravaId("strava-1");
            when(userRepository.findByStravaId("strava-1")).thenReturn(Optional.of(existing));

            User result = service.findOrCreateFromStrava("strava-1", "John", "pic.png",
                    "access", "refresh", 1234L, "j@e.com");

            assertEquals("u1", result.getId());
            assertEquals("access", result.getStravaAccessToken());
            assertEquals("refresh", result.getStravaRefreshToken());
            assertEquals(1234L, result.getStravaTokenExpiresAt());
            assertNotNull(result.getLastLogin());
            verify(userRepository, never()).findByEmail(any());
        }

        @Test
        void noStravaMatch_butEmailMatchesGoogleUser_linksStravaToExisting() {
            User googleUser = existing("g1");
            googleUser.setGoogleId("google-1");
            googleUser.setEmail("shared@e.com");
            when(userRepository.findByStravaId("strava-2")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("shared@e.com")).thenReturn(Optional.of(googleUser));

            User result = service.findOrCreateFromStrava("strava-2", "John", "pic",
                    "a", "r", 0L, "shared@e.com");

            assertEquals("g1", result.getId());
            assertEquals("strava-2", result.getStravaId());
            assertEquals("google-1", result.getGoogleId(), "should keep existing google id");
        }

        @Test
        void noMatchAtAll_createsNewAthlete() {
            when(userRepository.findByStravaId("strava-3")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("new@e.com")).thenReturn(Optional.empty());

            User result = service.findOrCreateFromStrava("strava-3", "Jane", "p",
                    "a", "r", 100L, "new@e.com");

            assertEquals("strava-3", result.getStravaId());
            assertEquals(AuthProvider.STRAVA, result.getAuthProvider());
            assertEquals(UserRole.ATHLETE, result.getRole());
            assertTrue(result.getNeedsOnboarding());
            assertEquals("new@e.com", result.getEmail());
        }

        @Test
        void blankEmail_skipsEmailReconciliation() {
            when(userRepository.findByStravaId("strava-4")).thenReturn(Optional.empty());

            service.findOrCreateFromStrava("strava-4", "X", null, "a", "r", 0L, "   ");

            verify(userRepository, never()).findByEmail(any());
        }
    }

    @Nested
    class FindOrCreateFromGoogle {

        @Test
        void existingByGoogleId_updatesProfile() {
            User existing = existing("u1");
            existing.setGoogleId("google-1");
            when(userRepository.findByGoogleId("google-1")).thenReturn(Optional.of(existing));

            User result = service.findOrCreateFromGoogle("google-1", "New Name", "n@e.com", "newpic");

            assertEquals("New Name", result.getDisplayName());
            assertEquals("n@e.com", result.getEmail());
            assertEquals("newpic", result.getProfilePicture());
        }

        @Test
        void noGoogleMatch_butEmailMatchesStravaUser_linksGoogleToExisting() {
            User stravaUser = existing("s1");
            stravaUser.setStravaId("strava-x");
            stravaUser.setEmail("shared@e.com");
            when(userRepository.findByGoogleId("google-2")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("shared@e.com")).thenReturn(Optional.of(stravaUser));

            User result = service.findOrCreateFromGoogle("google-2", "John", "shared@e.com", "p");

            assertEquals("s1", result.getId());
            assertEquals("google-2", result.getGoogleId());
            assertEquals("strava-x", result.getStravaId());
        }

        @Test
        void noMatch_createsNewGoogleAthlete() {
            when(userRepository.findByGoogleId("google-3")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("new@e.com")).thenReturn(Optional.empty());

            User result = service.findOrCreateFromGoogle("google-3", "Jane", "new@e.com", "p");

            assertEquals(AuthProvider.GOOGLE, result.getAuthProvider());
            assertEquals(UserRole.ATHLETE, result.getRole());
            assertTrue(result.getNeedsOnboarding());
        }
    }

    @Nested
    class LinkAccounts {

        @Test
        void linkStrava_failsWhenStravaAlreadyLinkedToOther() {
            User other = existing("other");
            other.setStravaId("s-1");
            when(userRepository.findByStravaId("s-1")).thenReturn(Optional.of(other));

            assertThrows(IllegalStateException.class,
                    () -> service.linkStrava("me", "s-1", "a", "r", 0L));

            verify(userRepository, never()).save(any());
        }

        @Test
        void linkStrava_succeedsForOwnAccount() {
            User me = existing("me");
            me.setStravaId("s-1");
            when(userRepository.findByStravaId("s-1")).thenReturn(Optional.of(me));
            when(userService.getUserById("me")).thenReturn(me);

            User result = service.linkStrava("me", "s-1", "a", "r", 9L);

            assertEquals("a", result.getStravaAccessToken());
            assertEquals(9L, result.getStravaTokenExpiresAt());
        }

        @Test
        void linkStrava_succeedsWhenNotPreviouslyLinked() {
            User me = existing("me");
            when(userRepository.findByStravaId("s-new")).thenReturn(Optional.empty());
            when(userService.getUserById("me")).thenReturn(me);

            User result = service.linkStrava("me", "s-new", "a", "r", 9L);

            assertEquals("s-new", result.getStravaId());
        }

        @Test
        void linkGoogle_failsWhenAlreadyLinkedToOther() {
            User other = existing("other");
            other.setGoogleId("g-1");
            when(userRepository.findByGoogleId("g-1")).thenReturn(Optional.of(other));

            assertThrows(IllegalStateException.class,
                    () -> service.linkGoogle("me", "g-1", "e@e.com"));
        }

        @Test
        void linkGarmin_failsWhenAlreadyLinkedToOther() {
            User other = existing("other");
            other.setGarminUserId("garmin-1");
            when(userRepository.findByGarminUserId("garmin-1")).thenReturn(Optional.of(other));

            assertThrows(IllegalStateException.class,
                    () -> service.linkGarmin("me", "garmin-1", "tok", "secret"));
        }

        @Test
        void linkZwift_clearsTokensOnUnlink() {
            User me = existing("me");
            me.setZwiftUserId("zwift-1");
            me.setZwiftAccessToken("at");
            me.setZwiftRefreshToken("rt");
            when(userService.getUserById("me")).thenReturn(me);

            User result = service.unlinkZwift("me");

            assertNull(result.getZwiftUserId());
            assertNull(result.getZwiftAccessToken());
            assertNull(result.getZwiftRefreshToken());
        }
    }

    @Nested
    class UnlinkLogins {

        @Test
        void unlinkStrava_failsIfStravaIsOnlyLogin() {
            User me = existing("me");
            me.setStravaId("s-1");
            me.setGoogleId(null);
            when(userService.getUserById("me")).thenReturn(me);

            assertThrows(IllegalStateException.class, () -> service.unlinkStrava("me"));
            verify(userRepository, never()).save(any());
        }

        @Test
        void unlinkStrava_clearsFieldsAndSwitchesAuthProvider() {
            User me = existing("me");
            me.setStravaId("s-1");
            me.setGoogleId("g-1");
            me.setStravaAccessToken("a");
            me.setStravaRefreshToken("r");
            me.setStravaTokenExpiresAt(123L);
            me.setAuthProvider(AuthProvider.STRAVA);
            when(userService.getUserById("me")).thenReturn(me);

            User result = service.unlinkStrava("me");

            assertNull(result.getStravaId());
            assertNull(result.getStravaAccessToken());
            assertNull(result.getStravaRefreshToken());
            assertNull(result.getStravaTokenExpiresAt());
            assertEquals(AuthProvider.GOOGLE, result.getAuthProvider());
        }

        @Test
        void unlinkGoogle_failsIfGoogleIsOnlyLogin() {
            User me = existing("me");
            me.setGoogleId("g-1");
            me.setStravaId(null);
            when(userService.getUserById("me")).thenReturn(me);

            assertThrows(IllegalStateException.class, () -> service.unlinkGoogle("me"));
        }

        @Test
        void unlinkGoogle_switchesAuthProvider() {
            User me = existing("me");
            me.setGoogleId("g-1");
            me.setStravaId("s-1");
            me.setAuthProvider(AuthProvider.GOOGLE);
            when(userService.getUserById("me")).thenReturn(me);

            User result = service.unlinkGoogle("me");

            assertNull(result.getGoogleId());
            assertEquals(AuthProvider.STRAVA, result.getAuthProvider());
        }

        @Test
        void unlinkNolioRead_callsTerraDeauthAndClearsState() {
            User me = existing("me");
            me.setTerraUserId("terra-1");
            me.setTerraProviderNolioConnected(true);
            when(userService.getUserById("me")).thenReturn(me);

            User result = service.unlinkNolioRead("me");

            verify(terraApiClient).deauthenticateUser("terra-1");
            assertNull(result.getTerraUserId());
            assertFalse(result.getTerraProviderNolioConnected());
        }

        @Test
        void unlinkNolioRead_skipsTerraCallWhenNoTerraId() {
            User me = existing("me");
            when(userService.getUserById("me")).thenReturn(me);

            service.unlinkNolioRead("me");

            verify(terraApiClient, never()).deauthenticateUser(any());
        }

        @Test
        void unlinkNolioWrite_clearsAllNolioFields() {
            User me = existing("me");
            me.setNolioUserId("n-1");
            me.setNolioAccessToken("at");
            me.setNolioRefreshToken("rt");
            me.setNolioAutoSyncWorkouts(true);
            when(userService.getUserById("me")).thenReturn(me);

            User result = service.unlinkNolioWrite("me");

            assertNull(result.getNolioUserId());
            assertNull(result.getNolioAccessToken());
            assertNull(result.getNolioRefreshToken());
            assertFalse(result.getNolioAutoSyncWorkouts());
        }

        @Test
        void unlinkGarmin_clearsAllGarminFields() {
            User me = existing("me");
            me.setGarminUserId("garmin-1");
            me.setGarminAccessToken("at");
            me.setGarminAccessTokenSecret("ats");
            when(userService.getUserById("me")).thenReturn(me);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            User result = service.unlinkGarmin("me");

            assertNull(result.getGarminUserId());
            assertNull(result.getGarminAccessToken());
            assertNull(result.getGarminAccessTokenSecret());
        }
    }
}
