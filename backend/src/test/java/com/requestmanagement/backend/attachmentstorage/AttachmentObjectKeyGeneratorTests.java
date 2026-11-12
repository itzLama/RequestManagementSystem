package com.requestmanagement.backend.attachmentstorage;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentObjectKeyGeneratorTests {
    @Test
    void generatesOpaqueUniquePairedKeysWithoutFilenameInput() {
        AttachmentObjectKeyGenerator generator = new AttachmentObjectKeyGenerator();
        Set<String> identifiers = new HashSet<>();

        for (int index = 0; index < 1_000; index++) {
            AttachmentObjectKeys keys = generator.generate();
            assertThat(keys.quarantine().value()).startsWith("quarantine/");
            assertThat(keys.available().value()).startsWith("available/");
            String quarantineId = keys.quarantine().value().substring("quarantine/".length());
            String availableId = keys.available().value().substring("available/".length());
            assertThat(availableId).isEqualTo(quarantineId);
            assertThat(quarantineId).matches("[A-Za-z0-9_-]{43}");
            assertThat(identifiers.add(quarantineId)).isTrue();
        }
    }

    @Test
    void rejectsUnsafeOrFilenameBasedKeys() {
        assertThatThrownBy(() -> new AttachmentObjectKey("quarantine/../../report.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AttachmentObjectKey("available/user@example.com/report.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AttachmentObjectKey("other/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
