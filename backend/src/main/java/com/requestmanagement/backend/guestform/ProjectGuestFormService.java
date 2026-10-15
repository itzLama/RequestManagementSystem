package com.requestmanagement.backend.guestform;

import com.requestmanagement.backend.project.*;
import com.requestmanagement.backend.projectworktype.*;
import com.requestmanagement.backend.guestform.GuestFormDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import jakarta.persistence.EntityManager;

@Service @RequiredArgsConstructor
public class ProjectGuestFormService {
    public static final int MAX_FIELDS = 30;
    private final ProjectRepository projectRepository; private final ProjectGuestFormSettingsRepository settingsRepository;
    private final ProjectGuestFieldRepository fieldRepository; private final ProjectGuestFieldOptionRepository optionRepository;
    private final RequestGuestFieldAnswerRepository answerRepository; private final ProjectWorkTypeRepository workTypeRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly=true) public SettingsResponse admin(Long projectId) { requireProject(projectId); return response(settings(projectId), false); }
    @Transactional(readOnly=true) public PublicSettingsResponse publicForm(Long projectId) {
        Project p=requireProject(projectId); requireActive(p); SettingsResponse r=response(settings(projectId), true);
        return new PublicSettingsResponse(r.workTypeVisible(),r.priorityVisible(),r.priorityRequired(),r.descriptionVisible(),r.descriptionRequired(),r.fields());
    }
    @Transactional public SettingsResponse updateSettings(Long projectId, SettingsInput in) {
        Project p=requireProject(projectId); requireActive(p); ProjectWorkType defaultType=null;
        if (!in.workTypeVisible()) defaultType=workTypeRepository.findByIdAndProject_IdAndActiveTrue(in.defaultWorkTypeId(),projectId)
                .orElseThrow(() -> bad("A hidden Work Type requires an active default from this Project."));
        ProjectGuestFormSettings s=settings(projectId); s.update(in.workTypeVisible(),defaultType,in.priorityVisible(),in.priorityRequired(),in.descriptionVisible(),in.descriptionRequired());
        return response(settingsRepository.save(s),false);
    }
    @Transactional public FieldResponse create(Long projectId, FieldInput input) {
        Project p=requireProject(projectId); requireActive(p); if(fieldRepository.countByProject_IdAndActiveTrue(projectId)>=MAX_FIELDS) conflict("A Project can have at most 30 active custom fields.");
        String label=label(input.label()); unique(projectId,label,null); validateOptions(input.type(),input.options(),input.optionIds());
        int order=fieldRepository.findByProject_IdOrderByDisplayOrderAscIdAsc(projectId).stream().mapToInt(ProjectGuestField::getDisplayOrder).max().orElse(-1)+1;
        try { ProjectGuestField f=fieldRepository.saveAndFlush(ProjectGuestField.create(p,label,input.type(),input.required(),order)); syncOptions(f,input.options(),input.optionIds()); return fieldResponse(f); }
        catch(DataIntegrityViolationException e){ throw conflictException("A custom field with this label already exists in the Project.",e); }
    }
    @Transactional public FieldResponse update(Long projectId,Long fieldId,FieldInput input) {
        requireActive(requireProject(projectId)); ProjectGuestField f=owned(projectId,fieldId); if(!f.isActive()) conflict("Retired custom fields cannot be modified.");
        String label=label(input.label()); unique(projectId,label,fieldId); if(f.getType()!=input.type()&&answerRepository.existsByField_Id(fieldId)) conflict("A field type cannot change after answers have been submitted. Retire it and create a replacement.");
        validateOptions(input.type(),input.options(),input.optionIds()); f.update(label,input.type(),input.required()); fieldRepository.saveAndFlush(f); syncOptions(f,input.options(),input.optionIds()); return fieldResponse(f);
    }
    @Transactional public void retire(Long projectId,Long fieldId) { requireActive(requireProject(projectId)); ProjectGuestField f=owned(projectId,fieldId); if(f.isActive()){f.retire();fieldRepository.save(f);} }
    @Transactional public List<FieldResponse> reorder(Long projectId,Long fieldId,ReorderInput input) {
        requireActive(requireProject(projectId)); ProjectGuestField f=owned(projectId,fieldId); if(!f.isActive()) conflict("Retired custom fields cannot be reordered.");
        List<ProjectGuestField> active=fieldRepository.findByProject_IdAndActiveTrueOrderByDisplayOrderAscIdAsc(projectId); int at=active.indexOf(f); int other=input.direction()==ReorderInput.Direction.UP?at-1:at+1;
        if(at>=0&&other>=0&&other<active.size()){ProjectGuestField target=active.get(other);int old=f.getDisplayOrder();f.moveTo(target.getDisplayOrder());target.moveTo(old);fieldRepository.saveAll(List.of(f,target));}
        return fieldRepository.findByProject_IdOrderByDisplayOrderAscIdAsc(projectId).stream().map(this::fieldResponse).toList();
    }
    public boolean usesDefaultWorkType(Long workTypeId){ return settingsRepository.findAll().stream().anyMatch(s->!s.isWorkTypeVisible()&&s.getDefaultWorkType()!=null&&s.getDefaultWorkType().getId().equals(workTypeId)); }
    private SettingsResponse response(ProjectGuestFormSettings s,boolean activeOnly){ List<ProjectGuestField> fs=activeOnly?fieldRepository.findByProject_IdAndActiveTrueOrderByDisplayOrderAscIdAsc(s.getProjectId()):fieldRepository.findByProject_IdOrderByDisplayOrderAscIdAsc(s.getProjectId()); return new SettingsResponse(s.isWorkTypeVisible(),s.getDefaultWorkType()==null?null:s.getDefaultWorkType().getId(),s.isPriorityVisible(),s.isPriorityRequired(),s.isDescriptionVisible(),s.isDescriptionRequired(),fs.stream().map(this::fieldResponse).toList()); }
    private FieldResponse fieldResponse(ProjectGuestField f){ List<ProjectGuestFieldOption> os=f.isActive()?optionRepository.findByField_IdAndActiveTrueOrderByDisplayOrderAscIdAsc(f.getId()):optionRepository.findByField_IdOrderByDisplayOrderAscIdAsc(f.getId()); return new FieldResponse(f.getId(),f.getLabel(),f.getType(),f.isRequired(),f.getDisplayOrder(),f.isActive(),os.stream().map(OptionResponse::from).toList()); }
    private void syncOptions(ProjectGuestField f,List<String> requested,List<Long> requestedIds){ List<String> names=f.getType()==GuestFieldType.DROPDOWN?requested.stream().map(this::label).toList():List.of(); List<Long> ids=requestedIds==null?Collections.nCopies(names.size(),null):requestedIds; List<ProjectGuestFieldOption> existing=optionRepository.findByField_IdOrderByDisplayOrderAscIdAsc(f.getId()); Set<Long> kept=new HashSet<>(); for(int i=0;i<names.size();i++){String name=names.get(i);Long requestedId=ids.get(i);ProjectGuestFieldOption match=requestedId==null?existing.stream().filter(o->o.getLabel().equalsIgnoreCase(name)).findFirst().orElse(null):existing.stream().filter(o->o.getId().equals(requestedId)).findFirst().orElseThrow(()->bad("A Dropdown option does not belong to this field."));if(match==null)optionRepository.save(ProjectGuestFieldOption.create(f,name,i));else{match.configure(name,i);kept.add(match.getId());optionRepository.save(match);}} existing.stream().filter(o->!kept.contains(o.getId())&&o.isActive()).forEach(o->{o.retire();optionRepository.save(o);}); }
    private void validateOptions(GuestFieldType type,List<String> options,List<Long> optionIds){ List<String> values=options==null?List.of():options;List<Long> ids=optionIds==null?List.of():optionIds;if(!ids.isEmpty()&&ids.size()!=values.size())throw bad("Dropdown option identifiers are invalid.");if(type==GuestFieldType.DROPDOWN&&values.isEmpty())throw bad("Dropdown fields require at least one option.");if(values.size()>30)throw bad("A Dropdown can have at most 30 active options.");Set<String> seen=new HashSet<>();for(String v:values)if(!seen.add(label(v).toLowerCase(Locale.ROOT)))throw conflictException("Dropdown option labels must be unique.",null);if(type!=GuestFieldType.DROPDOWN&&(!values.isEmpty()||!ids.isEmpty()))throw bad("Only Dropdown fields can define options."); }
    private void unique(Long projectId,String label,Long exclude){boolean found=exclude==null?fieldRepository.existsByProject_IdAndLabelIgnoreCase(projectId,label):fieldRepository.existsByProject_IdAndLabelIgnoreCaseAndIdNot(projectId,label,exclude);if(found)conflict("A custom field with this label already exists in the Project.");}
    private ProjectGuestFormSettings settings(Long id){return settingsRepository.findById(id).orElseThrow(()->new IllegalStateException("Project Guest form settings are missing."));}
    private ProjectGuestField owned(Long p,Long f){return fieldRepository.findByIdAndProject_Id(f,p).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Custom field not found."));}
    private Project requireProject(Long id){Project project=projectRepository.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Project not found."));entityManager.refresh(project);return project;}
    private void requireActive(Project p){if(!p.isActive())conflict("Archived projects cannot be modified.");}
    private String label(String v){String n=v==null?"":v.trim();if(n.isEmpty()||n.length()>100)throw bad("Labels must contain 1 to 100 characters.");return n;}
    private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);} private void conflict(String m){throw conflictException(m,null);} private ResponseStatusException conflictException(String m,Throwable c){return new ResponseStatusException(HttpStatus.CONFLICT,m,c);}
}
