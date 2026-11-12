package com.requestmanagement.backend.attachment;

import com.requestmanagement.backend.attachmentstorage.AttachmentObjectKeyGenerator;
import com.requestmanagement.backend.attachmentstorage.AttachmentObjectStorage;
import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.project.ProjectAccessService;
import com.requestmanagement.backend.project.ProjectRepository;
import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentUploadFeatureGateTests {
    @Mock ObjectProvider<AttachmentObjectStorage> storageProvider;
    @Mock AttachmentObjectKeyGenerator keyGenerator;
    @Mock AttachmentFileValidator validator;
    @Mock AttachmentLifecycleService lifecycle;
    @Mock ProjectAccessService access;
    @Mock ProjectRepository projects;
    @Mock RequestRepository requests;
    @Mock UserRepository users;
    @Mock User admin;
    @Mock Project project;

    @Test
    void uploadGateIsDisabledByDefaultAndIndependentFromStorage() {
        AttachmentUploadProperties properties = new AttachmentUploadProperties();
        InternalAttachmentService service = new InternalAttachmentService(properties, storageProvider, keyGenerator,
                validator, lifecycle, access, projects, requests, users);
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(admin.isActive()).thenReturn(true);
        when(admin.getRole()).thenReturn(User.Role.ADMIN);
        when(projects.findById(2L)).thenReturn(Optional.of(project));
        when(requests.existsByIdAndProject_Id(3L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.adminUpload(2L, 3L, 1L,
                new MockMultipartFile("file", "report.pdf", "application/pdf", new byte[]{1})))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
        verify(storageProvider, never()).getIfAvailable();
        verify(validator, never()).validate(org.mockito.ArgumentMatchers.any());
    }
}
