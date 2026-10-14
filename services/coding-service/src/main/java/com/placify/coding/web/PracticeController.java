package com.placify.coding.web;

import com.placify.coding.domain.Badge;
import com.placify.coding.domain.UserRating;
import com.placify.coding.repository.BadgeRepository;
import com.placify.coding.repository.UserRatingRepository;
import com.placify.coding.service.PracticeService;
import com.placify.coding.web.CodingDtos.BadgeView;
import com.placify.coding.web.CodingDtos.HallOfFameEntry;
import com.placify.coding.web.CodingDtos.NextProblem;
import com.placify.coding.web.CodingDtos.PracticeStats;
import com.placify.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "Adaptive practice")
@RestController
@RequestMapping("/api/coding")
@RequiredArgsConstructor
public class PracticeController {

    private final PracticeService practice;
    private final BadgeRepository badges;
    private final UserRatingRepository ratings;

    @Operation(summary = "Next recommended problem based on my rating and weak topics")
    @GetMapping("/practice/next")
    @PreAuthorize("hasRole('STUDENT')")
    public NextProblem next() {
        practice.rememberName(CurrentUser.id(), CurrentUser.get().name());
        return practice.next(CurrentUser.id());
    }

    @GetMapping("/practice/stats")
    @PreAuthorize("hasRole('STUDENT')")
    public PracticeStats stats() {
        practice.rememberName(CurrentUser.id(), CurrentUser.get().name());
        return practice.stats(CurrentUser.id());
    }

    @GetMapping("/badges/me")
    public List<BadgeView> myBadges() {
        return badges.findByUserIdOrderByAwardedAtDesc(CurrentUser.id()).stream()
                .map(b -> new BadgeView(b.getCode(), b.getTitle(), b.getDescription(), b.getAwardedAt())).toList();
    }

    @Operation(summary = "Hall of Fame: top students by practice rating")
    @GetMapping("/hall-of-fame")
    public List<HallOfFameEntry> hallOfFame() {
        List<UserRating> top = ratings.findByOrderByRatingDesc(PageRequest.of(0, 20));
        List<HallOfFameEntry> out = new ArrayList<>();
        int rank = 1;
        for (UserRating r : top) {
            List<String> titles = badges.findByUserIdOrderByAwardedAtDesc(r.getUserId()).stream()
                    .map(Badge::getTitle).distinct().limit(5).toList();
            out.add(new HallOfFameEntry(rank++, r.getUserId(), r.getName(), r.getRating(), r.getSolved(), titles));
        }
        return out;
    }
}
