package com.autotest.test_management_service.application.crawler;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** AES-GCM authenticated envelopes. Shared key is provisioned once, never sent in worker requests. */
@Service
public class CrawlAgentWire {
    private final JdbcTemplate jdbc;
    private final PlaywrightCredentialCipher cipher;
    private final ObjectMapper mapper;
    private static final SecureRandom RANDOM = new SecureRandom();
    public CrawlAgentWire(JdbcTemplate jdbc, PlaywrightCredentialCipher cipher, ObjectMapper mapper) {
        this.jdbc=jdbc; this.cipher=cipher; this.mapper=mapper;
    }
    public record Envelope(String iv, String ciphertext) { }
    public record Session(UUID agentId, byte[] key, UUID nonce, JsonNode body) {
        @Override public String toString() { return "AgentSession[redacted]"; }
    }
    public static byte[] requestAad(UUID id, String action, long timestamp, UUID nonce) {
        return ("autotest-agent-v1\n"+id+"\n"+action+"\n"+timestamp+"\n"+nonce).getBytes(StandardCharsets.UTF_8);
    }
    public static Envelope seal(byte[] key, byte[] aad, byte[] payload) throws Exception {
        byte[] iv=new byte[12]; RANDOM.nextBytes(iv);
        Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
        aes.updateAAD(aad);
        return new Envelope(Base64.getEncoder().encodeToString(iv),Base64.getEncoder().encodeToString(aes.doFinal(payload)));
    }
    public static byte[] unseal(byte[] key, byte[] aad, Envelope envelope) throws Exception {
        byte[] iv=Base64.getDecoder().decode(envelope.iv());
        if(iv.length!=12) throw new IllegalArgumentException();
        Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
        aes.updateAAD(aad);
        return aes.doFinal(Base64.getDecoder().decode(envelope.ciphertext()));
    }
    public Session open(UUID id,String action,long timestamp,UUID nonce,String raw) {
        long now=Instant.now().getEpochSecond();
        if(raw.length()>8_000_000 || timestamp<now-60 || timestamp>now+60) throw unauthorized();
        var secrets=jdbc.query("SELECT secret_ciphertext FROM crawl_agents WHERE agent_id=? AND disabled=false",
                (rs,n)->rs.getString(1),id);
        if(secrets.isEmpty()) throw unauthorized();
        byte[] key=Base64.getUrlDecoder().decode(cipher.decrypt(id,secrets.getFirst()));
        JsonNode body;
        try { body=mapper.readTree(unseal(key,requestAad(id,action,timestamp,nonce),mapper.readValue(raw,Envelope.class))); }
        catch(Exception invalid) { throw unauthorized(); }
        if(jdbc.update("INSERT INTO crawl_agent_nonces(agent_id,nonce) VALUES (?,?) ON CONFLICT DO NOTHING",id,nonce)==0)
            throw unauthorized();
        jdbc.update("DELETE FROM crawl_agent_nonces WHERE created_at < now()-interval '5 minutes'");
        return new Session(id,key,nonce,body);
    }
    public Envelope reply(Session session,Object value) {
        try { return seal(session.key(),("response\n"+session.nonce()).getBytes(StandardCharsets.UTF_8),mapper.writeValueAsBytes(value)); }
        catch(Exception failure) { throw new IllegalStateException("Unable to seal agent response"); }
    }
    private ProductDescriptionException unauthorized() {
        return new ProductDescriptionException(HttpStatus.UNAUTHORIZED,"AGENT_AUTH_INVALID","실행기 인증 또는 요청 유효기간을 확인해 주세요.");
    }
}
