package com.autotest.common_lib.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

public final class AccessTokens {
    public static final long LIFETIME_SECONDS=900;
    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    public AccessTokens(String secret) {
        byte[] bytes=secret.getBytes(StandardCharsets.UTF_8);
        if(bytes.length<32)throw new IllegalArgumentException("AUTH_JWT_SECRET must contain at least 32 bytes");
        var key=new SecretKeySpec(bytes,"HmacSHA256");
        encoder=new NimbusJwtEncoder(new ImmutableSecret<>(key));
        decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ZERO),new JwtIssuerValidator("autotest"),jwt->
            jwt.getAudience().contains("autotest-api")&&jwt.getExpiresAt()!=null&&jwt.getNotBefore()!=null&&
            jwt.getSubject()!=null&&jwt.getSubject().matches("[1-9][0-9]*")&&
            jwt.getClaimAsString("memberId")!=null&&jwt.getClaimAsString("memberId").matches("[1-9][0-9]*")
            ?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
    }
    public String issue(long userId,long memberId,List<String> roles,Instant now) {
        var claims=JwtClaimsSet.builder().issuer("autotest").audience(List.of("autotest-api")).subject(Long.toString(userId))
            .issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(LIFETIME_SECONDS)).claim("memberId",Long.toString(memberId)).claim("roles",roles).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }
    public Jwt verify(String token) { return decoder.decode(token); }
}
