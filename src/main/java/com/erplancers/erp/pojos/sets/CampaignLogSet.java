package com.erplancers.erp.pojos.sets;

import com.erplancers.erp.pojos.CampaignLog;
import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.base.EntitySet;
import jakarta.persistence.Entity;
import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table

public class CampaignLogSet extends EntitySet<CampaignLog> {
}
