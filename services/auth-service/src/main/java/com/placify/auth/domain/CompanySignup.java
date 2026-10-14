package com.placify.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Details a company gives at registration, shown to the TPO for approval.
 */
@Entity
@Table(name = "company_signups")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanySignup {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    private String website;

    private String industry;

    @Column(name = "contact_person")
    private String contactPerson;
}
