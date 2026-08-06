package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.*;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table
@ToString
public class Campaign extends EntityBase {
    String campaignName;
    String trackerKey;
    Integer trackerCount;
    String trackerText;
    LocalDate trackerDate;
    LocalDateTime trackerTime;
    LocalDateTime startTime;
    LocalDateTime endTime;
    String note;
    String description;
    Integer impressions;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Currency currency;
    Long budget;
    Long expectedCost;
    Long actualCost;
    Long expectedRevenue;
    Long actualRevenue;
    String campaignType;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CampaignLogSet campaigns;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    EmailMarketingSet emailMarketings;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    LeadSet leadSet;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Project project;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    OpportunitySet opportunities;
    
}
