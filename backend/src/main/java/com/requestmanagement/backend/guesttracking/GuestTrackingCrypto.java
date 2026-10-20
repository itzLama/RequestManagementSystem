package com.requestmanagement.backend.guesttracking;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class GuestTrackingCrypto {
 private final byte[] secret; private final SecureRandom random=new SecureRandom();
 public GuestTrackingCrypto(@Value("${masar.guest-tracking.secret}") String secret){if(secret.length()<32)throw new IllegalStateException("Guest tracking secret must contain at least 32 characters.");this.secret=secret.getBytes(StandardCharsets.UTF_8);}
 String code(){return String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));}
 String token(){byte[] value=new byte[32];random.nextBytes(value);return HexFormat.of().formatHex(value);}
 String digest(String purpose,String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal((purpose+":"+value).getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
 boolean matches(String expected,String actual){return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),actual.getBytes(StandardCharsets.US_ASCII));}
}
