package com.igornaumov.poker.planning.controller;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.igornaumov.controller.UserStoriesApi;
import com.igornaumov.model.Status;
import com.igornaumov.model.UserStoryRequest;
import com.igornaumov.model.UserStoryResponse;
import com.igornaumov.model.UserStoryStatus;
import com.igornaumov.model.UserStoryStatusUpdateRequest;
import com.igornaumov.poker.planning.entity.SessionEntity;
import com.igornaumov.poker.planning.entity.UserStoryEntity;
import com.igornaumov.poker.planning.entity.VoteEntity;
import com.igornaumov.poker.planning.repository.SessionRepository;
import com.igornaumov.poker.planning.repository.UserStoryRepository;
import com.igornaumov.poker.planning.repository.VotesRepository;

@RestController
public class UserStoriesController implements UserStoriesApi {

    private final SessionRepository sessionRepository;
    private final UserStoryRepository userStoryRepository;
    private final VotesRepository votesRepository;

    public UserStoriesController(SessionRepository sessionRepository, UserStoryRepository userStoryRepository,
                                 VotesRepository votesRepository) {
        this.sessionRepository = sessionRepository;
        this.userStoryRepository = userStoryRepository;
        this.votesRepository = votesRepository;
    }

    @Override
    public ResponseEntity<UserStoryResponse> addUserStory(String sessionId, UserStoryRequest userStoryRequest) {
        Optional<SessionEntity> sessionOptional = sessionRepository.findById(sessionId);
        if (sessionOptional.isEmpty()) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .build();
        }

        UserStoryEntity saved = userStoryRepository.save(
            new UserStoryEntity(
                userStoryRequest.getDescription(),
                sessionOptional.get().getId())
        );
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(toResponse(saved));
    }

    @Override
    public ResponseEntity<List<UserStoryResponse>> getStoriesInSession(String sessionId) {
        Optional<SessionEntity> sessionOptional = sessionRepository.findById(sessionId);
        return sessionOptional
            .map(entity -> ResponseEntity.ok(userStoryRepository.findBySessionId(sessionId)
                .stream()
                .map(UserStoriesController::toResponse)
                .toList()))
            .orElseGet(() -> ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .build());
    }

    @Override
    public ResponseEntity<UserStoryStatus> getUserStoryStatus(String sessionId, String userStoryId) {
        Optional<SessionEntity> sessionOptional = sessionRepository.findById(sessionId);
        if (sessionOptional.isEmpty()) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .build();
        }
        return userStoryRepository.findById(userStoryId)
            .map(status -> {
                List<String> emittedVotes = votesRepository.findAllByUserStoryId(userStoryId)
                    .stream().map(VoteEntity::getUserId)
                    .toList();
                return ResponseEntity.ok(toStatusResponse(status, emittedVotes));
            })
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @Override
    public ResponseEntity<UserStoryStatus> updateUserStoryStatus(String sessionId, String userStoryId,
                                                                 UserStoryStatusUpdateRequest userStoryStatusUpdateRequest) {
        Optional<SessionEntity> sessionOptional = sessionRepository.findById(sessionId);
        if (sessionOptional.isEmpty()) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .build();
        }
        Optional<UserStoryEntity> userStoryOptional = userStoryRepository.findById(userStoryId);
        if (userStoryOptional.isEmpty()) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .build();
        }

        UserStoryEntity entity = userStoryOptional.get();
        entity.setUserStoryStatus(userStoryStatusUpdateRequest.getStatus());
        UserStoryEntity saved = userStoryRepository.save(entity);
        return ResponseEntity.ok(toStatusResponse(saved, Collections.emptyList()));
    }

    @Override
    public ResponseEntity<Void> deleteUserStory(String sessionId, String userStoryId) {
        Optional<SessionEntity> sessionOptional = sessionRepository.findById(sessionId);
        if (sessionOptional.isEmpty()) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .build();
        }
        Optional<UserStoryEntity> userStoryOptional = userStoryRepository.findById(userStoryId);
        if (userStoryOptional.isEmpty()) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .build();
        }

        UserStoryEntity userStoryEntity = userStoryOptional.get();
        if (userStoryEntity.getUserStoryStatus() != Status.PENDING) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        userStoryRepository.delete(userStoryEntity);
        return ResponseEntity
            .status(HttpStatus.NO_CONTENT)
            .build();
    }

    private static UserStoryResponse toResponse(UserStoryEntity entity) {
        return new UserStoryResponse()
            .id(entity.getId())
            .description(entity.getDescription());
    }

    private static UserStoryStatus toStatusResponse(UserStoryEntity entity, List<String> emittedVotes) {
        if (emittedVotes.isEmpty()) {
            return new UserStoryStatus(entity.getUserStoryStatus(), 0);
        }
        return new UserStoryStatus(entity.getUserStoryStatus(), emittedVotes.size())
            .usersVoted(emittedVotes);
    }

}
