package com.placify.notification.service;

import com.placify.common.messaging.Events.LeaderboardUpdatedEvent;
import com.placify.common.messaging.Events.ProctoringWarningEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/** Real-time channels that are not stored: exam warnings and contest leaderboards. */
@Service
@RequiredArgsConstructor
public class LiveChannelService {

    private final SimpMessagingTemplate ws;

    public void proctoringWarning(ProctoringWarningEvent e) {
        ws.convertAndSendToUser(e.userId(), "/queue/proctoring", e);
    }

    public void leaderboard(LeaderboardUpdatedEvent e) {
        ws.convertAndSend("/topic/contest/" + e.contestId(), e);
    }
}
