package com.requestmanagement.backend.projectstatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ProjectStatusColumnRepository extends JpaRepository<ProjectStatusColumn,Long>{
 List<ProjectStatusColumn> findByProject_IdAndActiveTrueOrderByDisplayOrderAscIdAsc(Long projectId);
 List<ProjectStatusColumn> findByProject_IdOrderByDisplayOrderAscIdAsc(Long projectId);
 Optional<ProjectStatusColumn> findByIdAndProject_Id(Long id,Long projectId);
 Optional<ProjectStatusColumn> findByProject_IdAndSemanticKey(Long id,ProjectStatusColumn.SemanticKey key);
 long countByProject_IdAndActiveTrue(Long id);
 boolean existsByProject_IdAndActiveTrueAndNameIgnoreCase(Long id,String name);
 boolean existsByProject_IdAndActiveTrueAndNameIgnoreCaseAndIdNot(Long id,String name,Long exclude);
 void deleteAllByProject_Id(Long id);
 @Modifying @Query("update ProjectStatusColumn s set s.displayOrder=s.displayOrder-1 where s.project.id=:p and s.active=true and s.displayOrder>:o")
 void closeOrderGap(@Param("p")Long projectId,@Param("o")int order);
}
