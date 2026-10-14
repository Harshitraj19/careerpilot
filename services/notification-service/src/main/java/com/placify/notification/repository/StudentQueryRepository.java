package com.placify.notification.repository;

import com.placify.notification.domain.StudentQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentQueryRepository extends JpaRepository<StudentQuery, Long> {

    @Query("""
            select q from StudentQuery q
            where (:studentUserId is null or q.studentUserId = :studentUserId)
              and (:status is null or q.status = :status)
              and (:category is null or q.category = :category)
              and (:text is null or lower(q.subject) like lower(concat('%', :text, '%'))
                   or lower(q.studentName) like lower(concat('%', :text, '%'))
                   or lower(q.studentLogin) like lower(concat('%', :text, '%')))
            order by q.updatedAt desc
            """)
    Page<StudentQuery> search(@Param("studentUserId") String studentUserId,
                              @Param("status") StudentQuery.Status status,
                              @Param("category") StudentQuery.Category category,
                              @Param("text") String text,
                              Pageable pageable);

    long countByStatus(StudentQuery.Status status);

    long countByStudentUserIdAndStatus(String studentUserId, StudentQuery.Status status);
}
