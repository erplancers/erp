package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.MetadataSet;
import com.erplancers.erp.pojos.sets.SurveyQuestionSet;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table
@ToString
public class SurveyQuestionOption extends EntityBase {
    
    String surveyCategory;
    String surveyStatus;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    SurveyQuestionSet surveyQuestions;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    SurveyResponse surveyResponse;
}
