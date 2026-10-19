package com.buildvisionai.data.mapper;

import com.buildvisionai.database.entity.ProjectEntity;
import com.buildvisionai.database.entity.VersionEntity;
import com.buildvisionai.models.BuildingType;
import com.buildvisionai.models.Project;
import com.buildvisionai.models.ProjectStatus;
import com.buildvisionai.models.ProjectVersion;
import com.buildvisionai.models.WorkflowState;

import java.util.List;

/** Project and version headers. */
public final class ProjectMapper {
    private ProjectMapper() {}

    public static ProjectEntity toEntity(Project p, boolean archived) {
        ProjectEntity e = new ProjectEntity();
        e.projectId = p.projectId;
        e.projectName = Codes.orEmpty(p.projectName);
        e.ownerName = p.ownerName;
        e.location = p.location;
        e.county = p.county;
        e.plotWidthM = p.plotWidthM;
        e.plotLengthM = p.plotLengthM;
        e.budgetKes = p.budgetKes;
        e.buildingType = Codes.of(p.buildingType == null ? BuildingType.BUNGALOW : p.buildingType);
        e.createdAt = p.createdAt;
        e.updatedAt = p.updatedAt;
        e.status = Codes.of(p.status == null ? ProjectStatus.DRAFT : p.status);
        e.headVersionId = p.headVersionId;
        e.archived = archived;
        return e;
    }

    public static Project toModel(ProjectEntity e, List<String> warnings) {
        Project p = new Project();
        p.projectId = e.projectId;
        p.projectName = e.projectName;
        p.ownerName = e.ownerName;
        p.location = e.location;
        p.county = e.county;
        p.plotWidthM = e.plotWidthM;
        p.plotLengthM = e.plotLengthM;
        p.budgetKes = e.budgetKes;
        p.buildingType = Codes.parse(BuildingType.class, e.buildingType, BuildingType.BUNGALOW, warnings, "Project " + e.projectId);
        p.createdAt = e.createdAt;
        p.updatedAt = e.updatedAt;
        p.status = Codes.parse(ProjectStatus.class, e.status, ProjectStatus.DRAFT, warnings, "Project " + e.projectId);
        p.headVersionId = e.headVersionId;
        return p;
    }

    public static VersionEntity toEntity(ProjectVersion v) {
        VersionEntity e = new VersionEntity();
        e.id = v.id;
        e.projectId = v.projectId;
        e.number = v.number;
        e.label = v.label;
        e.parentVersionId = v.parentVersionId;
        e.createdAt = v.createdAt;
        e.createdBy = v.createdBy;
        e.workflowState = Codes.of(v.workflowState == null ? WorkflowState.DRAFT : v.workflowState);
        return e;
    }

    /**
     * An unknown workflow state is read as the most restrictive one (LOCKED) rather than DRAFT: when
     * in doubt a design must not become editable.
     */
    public static ProjectVersion toModel(VersionEntity e, List<String> warnings) {
        ProjectVersion v = new ProjectVersion();
        v.id = e.id;
        v.projectId = e.projectId;
        v.number = e.number;
        v.label = e.label;
        v.parentVersionId = e.parentVersionId;
        v.createdAt = e.createdAt;
        v.createdBy = e.createdBy;
        v.workflowState = Codes.parse(WorkflowState.class, e.workflowState, WorkflowState.LOCKED, warnings, "Version " + e.id);
        return v;
    }
}
