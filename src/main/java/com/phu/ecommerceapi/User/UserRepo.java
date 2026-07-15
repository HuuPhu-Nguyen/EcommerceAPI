package com.phu.ecommerceapi.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepo extends JpaRepository<UserModel, Long> {
    UserModel findByIdentitySubject(String identitySubject);

    Page<UserModel> findAllByOrderByIdAsc(Pageable pageable);

    @Modifying
    @Query(
            value = """
                    INSERT INTO user_model (
                        identity_subject,
                        username,
                        email,
                        first_name,
                        last_name
                    )
                    VALUES (
                        :identitySubject,
                        :username,
                        :email,
                        NULL,
                        NULL
                    )
                    ON CONFLICT (identity_subject) WHERE identity_subject IS NOT NULL DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertProvisionedProfileIfAbsent(
            @Param("identitySubject") String identitySubject,
            @Param("username") String username,
            @Param("email") String email
    );
}
