package com.civicflow.security;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe session holder supporting both single-user JavaFX desktop mode
 * and per-request scoped sessions when serving the embedded CIVICFLOW Live Web Server.
 */
public class SessionManager {

    private final AtomicReference<UserSession> desktopSession = new AtomicReference<>();
    private final ThreadLocal<Boolean> threadScopedMode = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<UserSession> threadSession = new ThreadLocal<>();

    public void beginThreadScope(UserSession initialSession) {
        threadScopedMode.set(Boolean.TRUE);
        threadSession.set(initialSession);
    }

    public void endThreadScope() {
        threadSession.remove();
        threadScopedMode.remove();
    }

    public void startSession(UserSession session) {
        if (Boolean.TRUE.equals(threadScopedMode.get())) {
            threadSession.set(session);
        } else {
            desktopSession.set(session);
        }
    }

    public Optional<UserSession> getCurrentSession() {
        if (Boolean.TRUE.equals(threadScopedMode.get())) {
            return Optional.ofNullable(threadSession.get());
        }
        return Optional.ofNullable(desktopSession.get());
    }

    public boolean isAuthenticated() {
        return getCurrentSession().isPresent();
    }

    public void clearSession() {
        if (Boolean.TRUE.equals(threadScopedMode.get())) {
            threadSession.set(null);
        } else {
            desktopSession.set(null);
        }
    }
}
