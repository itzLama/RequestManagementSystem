package com.requestmanagement.backend.attachmentstorage;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class AttachmentObjectKeyGenerator {
    private final SecureRandom random;

    public AttachmentObjectKeyGenerator() {
        this(new SecureRandom());
    }

    AttachmentObjectKeyGenerator(SecureRandom random) {
        this.random = random;
    }

    public AttachmentObjectKeys generate() {
        byte[] entropy = new byte[32];
        random.nextBytes(entropy);
        String opaqueId = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        return new AttachmentObjectKeys(
                new AttachmentObjectKey("quarantine/" + opaqueId),
                new AttachmentObjectKey("available/" + opaqueId));
    }
}
