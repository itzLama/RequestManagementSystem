package com.requestmanagement.backend.guestform;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GuestAnswerDisplayService {
    private final ProjectGuestFieldOptionRepository optionRepository;

    public GuestFormDtos.AnswerResponse currentLabels(RequestGuestFieldAnswer answer) {
        ProjectGuestField field = answer.getField();
        boolean currentFieldAvailable = field != null && field.isActive();
        String label = currentFieldAvailable ? field.getLabel() : answer.getFieldLabel();
        String labelAr = currentFieldAvailable ? field.getLabelAr() : answer.getFieldLabelAr();
        String optionLabel = answer.getOptionLabel();
        String optionLabelAr = answer.getOptionLabelAr();

        if (currentFieldAvailable && answer.getFieldType() == GuestFieldType.DROPDOWN) {
            try {
                long optionId = Long.parseLong(answer.getValue());
                ProjectGuestFieldOption option = optionRepository.findByIdAndField_Id(optionId, field.getId())
                        .filter(ProjectGuestFieldOption::isActive)
                        .orElse(null);
                if (option != null) {
                    optionLabel = option.getLabel();
                    optionLabelAr = option.getLabelAr();
                }
            } catch (NumberFormatException ignored) {
                // Legacy answers can contain labels rather than stable option IDs; snapshots remain authoritative.
            }
        }

        return new GuestFormDtos.AnswerResponse(label, labelAr, answer.getFieldType(),
                answer.getDisplayOrder(), answer.getValue(), optionLabel, optionLabelAr);
    }
}
