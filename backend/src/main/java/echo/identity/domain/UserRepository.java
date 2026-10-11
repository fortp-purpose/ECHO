package echo.identity.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<User> findByEmailNotDeleted(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.handle = :handle AND u.deletedAt IS NULL")
    Optional<User> findByHandleNotDeleted(@Param("handle") String handle);

    boolean existsByEmail(String email);

    boolean existsByHandle(String handle);

    boolean existsByIdAndDeletedAtIsNull(UUID id);
}