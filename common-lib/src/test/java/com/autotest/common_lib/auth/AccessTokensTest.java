package com.autotest.common_lib.auth;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccessTokensTest {
    static final String KEY="test-only-secret-at-least-thirty-two-bytes-long";
    @Test void roundTripAndTampering() {
        var tokens=new AccessTokens(KEY);
        String token=tokens.issue(7,1,List.of("ADMIN"),Instant.now());
        assertEquals("7",tokens.verify(token).getSubject());
        assertEquals("1",tokens.verify(token).getClaimAsString("memberId"));
        assertThrows(RuntimeException.class,()->new AccessTokens(KEY+"different").verify(token));
        assertThrows(RuntimeException.class,()->tokens.verify(token.substring(0,token.length()-8)+"tampered"));
    }
    @Test void expiredAndFutureTokensAreRejected() {
        var tokens=new AccessTokens(KEY);
        assertThrows(RuntimeException.class,()->tokens.verify(tokens.issue(1,1,List.of("ADMIN"),Instant.now().minusSeconds(1800))));
        assertThrows(RuntimeException.class,()->tokens.verify(tokens.issue(1,1,List.of("ADMIN"),Instant.now().plusSeconds(600))));
        assertThrows(IllegalArgumentException.class,()->new AccessTokens("short"));
    }
}
