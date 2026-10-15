package com.requestmanagement.backend.guestform;

import com.requestmanagement.backend.request.Request;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name = "request_guest_field_answers") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestGuestFieldAnswer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "answer_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "request_id") private Request request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "guest_field_id") private ProjectGuestField field;
    @Column(name = "field_label_snapshot", nullable = false, length = 100) private String fieldLabel;
    @Enumerated(EnumType.STRING) @Column(name = "field_type_snapshot", nullable = false, length = 20) private GuestFieldType fieldType;
    @Column(name = "display_order_snapshot", nullable = false) private int displayOrder;
    @Column(name = "answer_value", nullable = false, columnDefinition = "TEXT") private String value;
    @Column(name = "option_label_snapshot", length = 100) private String optionLabel;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    public static RequestGuestFieldAnswer create(Request request, ProjectGuestField field, String value, String optionLabel) { RequestGuestFieldAnswer a=new RequestGuestFieldAnswer();a.request=request;a.field=field;a.fieldLabel=field.getLabel();a.fieldType=field.getType();a.displayOrder=field.getDisplayOrder();a.value=value;a.optionLabel=optionLabel;a.createdAt=LocalDateTime.now();return a; }
}
