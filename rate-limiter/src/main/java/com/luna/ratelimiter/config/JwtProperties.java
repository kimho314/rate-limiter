package com.luna.ratelimiter.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    //Path to RSA public key (PEM, PKCS#8/SPKI format). Used to verify token signatures
    @NotNull
    private Resource publicKey;

    //Custom claim name carrying the user's tier
    @NotBlank
    private String tierClaim = "tier";

    public Resource getPublicKey() {
        return publicKey;
    }

    public String getTierClaim() {
        return tierClaim;
    }

    public void setPublicKey(Resource publicKey) {
        this.publicKey = publicKey;
    }

    public void setTierClaim(String tierClaim) {
        this.tierClaim = tierClaim;
    }
}
