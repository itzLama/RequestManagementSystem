package com.requestmanagement.backend.requesttype;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestTypeRepository extends JpaRepository<RequestType, Long> {
    List<RequestType> findByActiveTrueOrderByTypeNameAsc();
}
