package com.koval.trainingplannerbackend.club;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Document(collection = "club_invite_codes")
public class ClubInviteCode {
    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    @Indexed
    private String clubId;

    private String createdBy;

    /** Optional: if set, joining via this code also adds the user to this club group */
    private String clubGroupId;

    private int maxUses; // 0 = unlimited
    private int currentUses;
    private LocalDateTime expiresAt; // null = never expires
    private boolean active = true;
    private LocalDateTime createdAt;
}
