package com.requestmanagement.backend.project;

import org.springframework.jdbc.core.JdbcTemplate;

public final class ProjectWorkTypeTestSupport {
    private ProjectWorkTypeTestSupport() { }

    public static Long workTypeId(JdbcTemplate jdbc, Long projectId, String name) {
        jdbc.update("""
                INSERT INTO project_work_types(project_id,work_type_name)
                VALUES (?,?)
                ON CONFLICT DO NOTHING
                """, projectId, name);
        return jdbc.queryForObject("""
                SELECT work_type_id FROM project_work_types
                WHERE project_id=? AND lower(work_type_name)=lower(?)
                """, Long.class, projectId, name);
    }
}
