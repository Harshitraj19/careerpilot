package com.placify.coding.service;

import com.placify.coding.domain.Badge;
import com.placify.coding.domain.UserRating;
import com.placify.coding.repository.BadgeRepository;
import com.placify.common.messaging.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Awards badges for practice milestones and contest results.
 */
@Service
@RequiredArgsConstructor
public class BadgeService {

    private final BadgeRepository badges;
    private final EventPublisher publisher;

    @Transactional
    public void onSolved(String userId, UserRating r) {
        if (r.getSolved() == 1) {
            award(userId, "FIRST_SOLVE", "First Blood", "Solved your first problem");
        }
        if (r.getSolved() == 10) {
            award(userId, "SOLVED_10", "Getting Started", "Solved 10 problems");
        }
        if (r.getSolved() == 50) {
            award(userId, "SOLVED_50", "Problem Crusher", "Solved 50 problems");
        }
        if (r.getSolved() == 100) {
            award(userId, "SOLVED_100", "Centurion", "Solved 100 problems");
        }
        if (r.getHardSolved() == 5) {
            award(userId, "HARD_5", "Hard Hitter", "Solved 5 hard problems");
        }
        if (r.getRating() >= 1800) {
            award(userId, "RATING_1800", "Expert", "Reached a practice rating of 1800");
        }
    }

    @Transactional
    public void award(String userId, String code, String title, String description) {
        if (badges.existsByUserIdAndCode(userId, code)) {
            return;
        }
        badges.save(Badge.builder().userId(userId).code(code).title(title).description(description).build());
        publisher.notifyUser(userId, "Badge earned: " + title, description, "BADGE", "/student/coding", false);
    }
}
