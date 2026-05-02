package com.koval.trainingplannerbackend.club.session;

import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionMaterializer;
import com.koval.trainingplannerbackend.pacing.gpx.GpxParseResult;
import com.koval.trainingplannerbackend.pacing.gpx.GpxParser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@Service
public class SessionGpxService {

    private final ClubTrainingSessionRepository sessionRepository;
    private final ClubAuthorizationService authorizationService;
    private final GpxParser gpxParser;
    private final RecurringSessionMaterializer materializer;

    public SessionGpxService(ClubTrainingSessionRepository sessionRepository,
                             ClubAuthorizationService authorizationService,
                             GpxParser gpxParser,
                             RecurringSessionMaterializer materializer) {
        this.sessionRepository = sessionRepository;
        this.authorizationService = authorizationService;
        this.gpxParser = gpxParser;
        this.materializer = materializer;
    }

    public ClubTrainingSession uploadGpx(String userId, String clubId, String sessionId, MultipartFile file) {
        ClubTrainingSession session = materializer.resolveOrMaterialize(sessionId);
        if (!session.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Session does not belong to this club");
        }
        authorizeSessionModification(userId, clubId, session);
        try {
            byte[] gpxBytes = file.getBytes();
            GpxParseResult result = gpxParser.parseWithCoordinates(new java.io.ByteArrayInputStream(gpxBytes));
            session.setGpxData(gpxBytes);
            session.setGpxFileName(file.getOriginalFilename());
            session.setRouteCoordinates(result.routeCoordinates());
            return sessionRepository.save(session);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Failed to process GPX file: " + e.getMessage(), e);
        }
    }

    public ClubTrainingSession deleteGpx(String userId, String clubId, String sessionId) {
        ClubTrainingSession session = materializer.resolveOrMaterialize(sessionId);
        if (!session.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Session does not belong to this club");
        }
        authorizeSessionModification(userId, clubId, session);
        session.setGpxData(null);
        session.setGpxFileName(null);
        session.setRouteCoordinates(null);
        return sessionRepository.save(session);
    }

    public ResponseEntity<byte[]> getGpxDownload(String userId, String clubId, String sessionId) {
        authorizationService.requireActiveMember(userId, clubId);
        ClubTrainingSession session = materializer.resolveOrMaterialize(sessionId);
        if (session.getGpxData() == null) {
            return ResponseEntity.notFound().build();
        }
        String filename = Optional.ofNullable(session.getGpxFileName()).orElse("route.gpx");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(session.getGpxData());
    }

    private void authorizeSessionModification(String userId, String clubId, ClubTrainingSession session) {
        if (session.getCategory() == SessionCategory.OPEN && session.getCreatedBy().equals(userId)) {
            authorizationService.requireActiveMember(userId, clubId);
        } else {
            authorizationService.requireAdminOrCoach(userId, clubId);
        }
    }
}
