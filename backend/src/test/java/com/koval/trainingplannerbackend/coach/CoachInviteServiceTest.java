package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.auth.UserRole;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.invite.ClubInviteCodeRepository;
import com.koval.trainingplannerbackend.club.invite.ClubInviteCodeService;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import com.koval.trainingplannerbackend.training.group.GroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoachInviteServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private InviteCodeRepository inviteCodeRepository;
    @Mock
    private ClubInviteCodeRepository clubInviteCodeRepository;
    @Mock
    private ClubInviteCodeService clubInviteCodeService;
    @Mock
    private GroupService groupService;

    private CoachInviteService service;

    @BeforeEach
    void setUp() {
        service = new CoachInviteService(userRepository, userService, inviteCodeRepository,
                clubInviteCodeRepository, clubInviteCodeService, groupService);
        lenient().when(inviteCodeRepository.save(any(InviteCode.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User coach(String id) {
        User u = new User();
        u.setId(id);
        u.setRole(UserRole.COACH);
        return u;
    }

    private User athlete(String id) {
        User u = new User();
        u.setId(id);
        u.setRole(UserRole.ATHLETE);
        return u;
    }

    @Nested
    class GenerateInviteCode {

        @Test
        void notFoundCoach_throwsResourceNotFound() {
            when(userRepository.findById("missing")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> service.generateInviteCode("missing", List.of(), 5, null, null));
        }

        @Test
        void userIsNotCoach_throwsForbidden() {
            when(userRepository.findById("a1")).thenReturn(Optional.of(athlete("a1")));

            assertThrows(ForbiddenOperationException.class,
                    () -> service.generateInviteCode("a1", List.of(), 5, null, null));
        }

        @Test
        void coach_blankCustomCode_generatesUniqueCode() {
            when(userRepository.findById("c1")).thenReturn(Optional.of(coach("c1")));
            // Simulate uniqueness check passing on first try
            when(inviteCodeRepository.findByCode(any())).thenReturn(Optional.empty());

            InviteCode result = service.generateInviteCode("c1", List.of("g1"), 10, null, "  ");

            assertNotNull(result.getCode());
            assertEquals(8, result.getCode().length());
            assertEquals("c1", result.getCoachId());
            assertEquals(List.of("g1"), result.getGroupIds());
            assertEquals(10, result.getMaxUses());
            assertEquals("GROUP", result.getType());
        }

        @Test
        void coach_customCode_isNormalizedToUpperTrimmed() {
            when(userRepository.findById("c1")).thenReturn(Optional.of(coach("c1")));

            InviteCode result = service.generateInviteCode("c1", List.of("g1"), 5, null, "  hello123 ");

            assertEquals("HELLO123", result.getCode());
            // Custom code shouldn't trigger uniqueness lookup
            verify(inviteCodeRepository, never()).findByCode(any());
        }

        @Test
        void coach_nullGroupIds_yieldsEmptyList() {
            when(userRepository.findById("c1")).thenReturn(Optional.of(coach("c1")));

            InviteCode result = service.generateInviteCode("c1", null, 5, null, "ABC123");

            assertNotNull(result.getGroupIds());
            assertTrue(result.getGroupIds().isEmpty());
        }

        @Test
        void coach_uniqueCodeFailsTenTimes_throws() {
            when(userRepository.findById("c1")).thenReturn(Optional.of(coach("c1")));
            // Always return existing — generator should retry then bail
            InviteCode existing = new InviteCode();
            when(inviteCodeRepository.findByCode(any())).thenReturn(Optional.of(existing));

            assertThrows(ValidationException.class,
                    () -> service.generateInviteCode("c1", List.of(), 5, null, null));
        }
    }

    @Nested
    class RedeemInviteCode {

        private InviteCode validCode(String code, String coachId, List<String> groupIds) {
            InviteCode ic = new InviteCode();
            ic.setId("ic1");
            ic.setCode(code);
            ic.setCoachId(coachId);
            ic.setGroupIds(groupIds);
            ic.setMaxUses(5);
            ic.setCurrentUses(0);
            ic.setActive(true);
            return ic;
        }

        @Test
        void invalidCode_throwsResourceNotFound() {
            when(userService.getUserById("a1")).thenReturn(athlete("a1"));
            when(inviteCodeRepository.findByCode("BADCODE")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> service.redeemInviteCode("a1", "BADCODE"));
        }

        @Test
        void inactiveCode_throwsValidation() {
            when(userService.getUserById("a1")).thenReturn(athlete("a1"));
            InviteCode code = validCode("ABCDEF", "c1", List.of("g1"));
            code.setActive(false);
            when(inviteCodeRepository.findByCode("ABCDEF")).thenReturn(Optional.of(code));

            assertThrows(ValidationException.class,
                    () -> service.redeemInviteCode("a1", "ABCDEF"));
        }

        @Test
        void expiredCode_throwsValidation() {
            when(userService.getUserById("a1")).thenReturn(athlete("a1"));
            InviteCode code = validCode("ABCDEF", "c1", List.of("g1"));
            code.setExpiresAt(LocalDateTime.now().minusDays(1));
            when(inviteCodeRepository.findByCode("ABCDEF")).thenReturn(Optional.of(code));

            assertThrows(ValidationException.class,
                    () -> service.redeemInviteCode("a1", "ABCDEF"));
        }

        @Test
        void maxUsesExhausted_throwsValidation() {
            when(userService.getUserById("a1")).thenReturn(athlete("a1"));
            InviteCode code = validCode("ABCDEF", "c1", List.of("g1"));
            code.setMaxUses(2);
            code.setCurrentUses(2);
            when(inviteCodeRepository.findByCode("ABCDEF")).thenReturn(Optional.of(code));

            assertThrows(ValidationException.class,
                    () -> service.redeemInviteCode("a1", "ABCDEF"));
        }

        @Test
        void zeroMaxUses_meansUnlimited() {
            when(userService.getUserById("a1")).thenReturn(athlete("a1"));
            InviteCode code = validCode("ABCDEF", "c1", List.of("g1"));
            code.setMaxUses(0);
            code.setCurrentUses(1000);
            when(inviteCodeRepository.findByCode("ABCDEF")).thenReturn(Optional.of(code));
            when(userRepository.findById("a1")).thenReturn(Optional.of(athlete("a1")));

            User result = service.redeemInviteCode("a1", "ABCDEF");

            assertNotNull(result);
            assertEquals(1001, code.getCurrentUses());
        }

        @Test
        void validRedeem_addsAthleteToAllGroupsAndIncrementsUsage() {
            when(userService.getUserById("a1")).thenReturn(athlete("a1"));
            InviteCode code = validCode("ABCDEF", "c1", List.of("g1", "g2"));
            when(inviteCodeRepository.findByCode("ABCDEF")).thenReturn(Optional.of(code));
            when(userRepository.findById("a1")).thenReturn(Optional.of(athlete("a1")));

            service.redeemInviteCode("a1", "ABCDEF");

            verify(groupService).addAthleteToGroup("g1", "a1");
            verify(groupService).addAthleteToGroup("g2", "a1");

            ArgumentCaptor<InviteCode> captor = ArgumentCaptor.forClass(InviteCode.class);
            verify(inviteCodeRepository).save(captor.capture());
            assertEquals(1, captor.getValue().getCurrentUses());
        }

        @Test
        void codeIsTrimmedAndUppercasedBeforeLookup() {
            when(userService.getUserById("a1")).thenReturn(athlete("a1"));
            InviteCode code = validCode("ABCDEF", "c1", List.of());
            when(inviteCodeRepository.findByCode("ABCDEF")).thenReturn(Optional.of(code));
            when(userRepository.findById("a1")).thenReturn(Optional.of(athlete("a1")));

            service.redeemInviteCode("a1", "  abcdef ");

            verify(inviteCodeRepository).findByCode("ABCDEF");
        }
    }

    @Nested
    class DeactivateInviteCode {

        @Test
        void missingCode_throws() {
            when(inviteCodeRepository.findById("missing")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> service.deactivateInviteCode("c1", "missing"));
        }

        @Test
        void notOwnedByCoach_throwsForbidden() {
            InviteCode ic = new InviteCode();
            ic.setId("ic1");
            ic.setCoachId("other-coach");
            ic.setActive(true);
            when(inviteCodeRepository.findById("ic1")).thenReturn(Optional.of(ic));

            assertThrows(ForbiddenOperationException.class,
                    () -> service.deactivateInviteCode("c1", "ic1"));
        }

        @Test
        void ownedByCoach_setsInactiveAndSaves() {
            InviteCode ic = new InviteCode();
            ic.setId("ic1");
            ic.setCoachId("c1");
            ic.setActive(true);
            when(inviteCodeRepository.findById("ic1")).thenReturn(Optional.of(ic));

            service.deactivateInviteCode("c1", "ic1");

            assertFalse(ic.getActive());
            verify(inviteCodeRepository).save(ic);
        }
    }

    @Nested
    class GetInviteCodes {

        @Test
        void delegatesToRepository() {
            InviteCode ic = new InviteCode();
            when(inviteCodeRepository.findByCoachId("c1")).thenReturn(List.of(ic));

            List<InviteCode> result = service.getInviteCodes("c1");

            assertEquals(1, result.size());
            assertSame(ic, result.get(0));
        }
    }
}
