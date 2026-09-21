package com.requestmanagement.backend.requesttype;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequestTypeService {

    private final RequestTypeRepository requestTypeRepository;

    @Transactional(readOnly = true)
    public List<RequestTypeResponse> getActiveTypes() {
        return requestTypeRepository.findByActiveTrueOrderByTypeNameAsc().stream()
                .map(RequestTypeResponse::from)
                .toList();
    }
}
