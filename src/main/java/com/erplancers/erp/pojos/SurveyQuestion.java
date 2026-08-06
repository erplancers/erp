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

public class SurveyQuestion extends EntityBase {
    
    String surveyName;
    String surveyDescription;
    String surveyType;
    String surveyCategory;
    String surveyStatus;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    SurveyQuestionSet surveyQuestions;

}
