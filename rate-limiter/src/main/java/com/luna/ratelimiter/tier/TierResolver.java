package com.luna.ratelimiter.tier;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

public interface TierResolver {
    // Resolves the caller's identity and tier from an HTTP request
    //Returns empty if the request is unauthenticated or invalid

    Optional<ResolvedIdentity> resolve(HttpServletRequest request);

    record ResolvedIdentity(String userId, Tier tier) {}
}
