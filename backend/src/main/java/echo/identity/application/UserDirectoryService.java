package echo.identity.application;

import echo.identity.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserDirectoryService implements UserDirectory {

    private final UserRepository users;

    public UserDirectoryService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID userId) {
        return userId != null && users.existsByIdAndDeletedAtIsNull(userId);
    }
}
