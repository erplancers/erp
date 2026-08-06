package com.erplancers.erp.pojos.sets;

import com.erplancers.erp.pojos.CostCenter;
import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.base.EntitySet;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table
@ToString
public class CostCenterSet extends EntitySet<CostCenter> {
}
