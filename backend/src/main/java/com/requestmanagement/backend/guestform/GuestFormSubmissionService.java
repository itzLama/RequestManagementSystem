package com.requestmanagement.backend.guestform;

import com.requestmanagement.backend.guestform.GuestFormDtos.AnswerInput;
import com.requestmanagement.backend.projectworktype.*;
import com.requestmanagement.backend.request.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service @RequiredArgsConstructor
public class GuestFormSubmissionService {
    static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Riyadh");
    private final ProjectGuestFormSettingsRepository settingsRepository; private final ProjectGuestFieldRepository fieldRepository;
    private final ProjectGuestFieldOptionRepository optionRepository; private final ProjectWorkTypeRepository workTypeRepository;
    private final RequestGuestFieldAnswerRepository answerRepository;

    public ResolvedSubmission resolve(Long projectId, Long submittedWorkTypeId, RequestPriority submittedPriority,
                                      String submittedDescription, List<AnswerInput> submittedAnswers) {
        ProjectGuestFormSettings settings=settingsRepository.findById(projectId).orElseThrow(()->new IllegalStateException("Project Guest form settings are missing."));
        ProjectWorkType workType;
        if(settings.isWorkTypeVisible()){
            if(submittedWorkTypeId==null)throw bad("Work Type is required.");
            workType=activeWorkType(projectId,submittedWorkTypeId);
        }else{
            if(submittedWorkTypeId!=null)throw bad("Work Type is managed by this Project and must not be submitted.");
            workType=settings.getDefaultWorkType(); if(workType==null||!workType.isActive()||!workType.getProject().getId().equals(projectId))throw bad("The configured default Work Type is unavailable.");
        }
        RequestPriority priority;
        if(!settings.isPriorityVisible()){if(submittedPriority!=null)throw bad("Priority is managed by this Project and must not be submitted.");priority=RequestPriority.MEDIUM;}
        else if(submittedPriority==null){if(settings.isPriorityRequired())throw bad("Priority is required.");priority=RequestPriority.MEDIUM;}else priority=submittedPriority;
        String description=submittedDescription==null?"":submittedDescription.trim();
        if(!settings.isDescriptionVisible()){if(!description.isEmpty())throw bad("Description is hidden and must not be submitted.");description="";}
        else {if(settings.isDescriptionRequired()&&description.isEmpty())throw bad("Description is required.");if(description.length()>10000)throw bad("Description must be at most 10000 characters.");}
        List<ProjectGuestField> fields=fieldRepository.findByProject_IdAndActiveTrueOrderByDisplayOrderAscIdAsc(projectId);
        List<AnswerInput> inputs=submittedAnswers==null?List.of():submittedAnswers;if(inputs.size()>ProjectGuestFormService.MAX_FIELDS)throw bad("Too many custom field answers.");
        Map<Long,AnswerInput> byId=new HashMap<>();for(AnswerInput a:inputs){if(byId.put(a.fieldId(),a)!=null)throw bad("Duplicate custom field answers are not allowed.");}
        Set<Long> validIds=new HashSet<>();fields.forEach(f->validIds.add(f.getId()));if(!validIds.containsAll(byId.keySet()))throw bad("A custom field is unknown, retired, or belongs to another Project.");
        List<ResolvedAnswer> answers=new ArrayList<>();int textTotal=0;
        for(ProjectGuestField field:fields){AnswerInput input=byId.get(field.getId());String raw=input==null||input.value()==null?"":input.value().trim();if(field.isRequired()&&raw.isEmpty())throw bad(field.getLabel()+" is required.");if(raw.isEmpty()){if(field.getType()==GuestFieldType.CHECKBOX&&field.isRequired())throw bad(field.getLabel()+" must be checked.");continue;}
            String value;String optionLabel=null;switch(field.getType()){
                case TEXT -> {if(raw.length()>500)throw bad(field.getLabel()+" must be at most 500 characters.");value=raw;textTotal+=raw.length();}
                case TEXTAREA -> {if(raw.length()>5000)throw bad(field.getLabel()+" must be at most 5000 characters.");value=raw;textTotal+=raw.length();}
                case NUMBER -> value=number(raw,field.getLabel());
                case DATE -> {try{LocalDate date=LocalDate.parse(raw);if(date.isBefore(LocalDate.now(APPLICATION_ZONE)))throw bad(field.getLabel()+" cannot be in the past.");value=date.toString();}catch(DateTimeParseException e){throw bad(field.getLabel()+" must be a valid date in YYYY-MM-DD format.");}}
                case CHECKBOX -> {if(!raw.equals("true")&&!raw.equals("false"))throw bad(field.getLabel()+" must be true or false.");if(field.isRequired()&&!Boolean.parseBoolean(raw))throw bad(field.getLabel()+" must be checked.");value=raw;}
                case DROPDOWN -> {long optionId;try{optionId=Long.parseLong(raw);}catch(NumberFormatException e){throw bad(field.getLabel()+" has an invalid option.");}ProjectGuestFieldOption option=optionRepository.findByIdAndField_IdAndActiveTrue(optionId,field.getId()).orElseThrow(()->bad(field.getLabel()+" has an invalid option."));value=Long.toString(option.getId());optionLabel=option.getLabel();}
                default -> throw bad("Unsupported custom field type.");
            }answers.add(new ResolvedAnswer(field,value,optionLabel));}
        if(textTotal>30000)throw bad("Custom text answers must total at most 30000 characters.");return new ResolvedSubmission(workType,priority,description,answers);
    }
    public void saveAnswers(Request request,List<ResolvedAnswer> answers){answerRepository.saveAll(answers.stream().map(a->RequestGuestFieldAnswer.create(request,a.field(),a.value(),a.optionLabel())).toList());}
    private ProjectWorkType activeWorkType(Long p,Long w){return workTypeRepository.findByIdAndProject_IdAndActiveTrue(w,p).orElseThrow(()->bad("Work Type must exist, be active, and belong to this Project."));}
    private String number(String raw,String label){if(!raw.matches("-?\\d+(\\.\\d+)?"))throw bad(label+" must be a decimal number.");long digits=raw.chars().filter(Character::isDigit).count();if(digits>15)throw bad(label+" must contain at most 15 numeric digits.");try{BigDecimal n=new BigDecimal(raw).stripTrailingZeros();return n.signum()==0?"0":n.toPlainString();}catch(NumberFormatException e){throw bad(label+" must be a decimal number.");}}
    private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    public record ResolvedSubmission(ProjectWorkType workType,RequestPriority priority,String description,List<ResolvedAnswer> answers){}
    public record ResolvedAnswer(ProjectGuestField field,String value,String optionLabel){}
}
