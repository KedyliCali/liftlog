package com.behlul.liftlog.security;

/**
 * Tells services who is making the request. Services depend on this interface rather than on
 * Spring Security directly, so the MVP demo user can later be swapped for the JWT user.
 */
public interface CurrentUserProvider {

    Long currentUserId();
}
