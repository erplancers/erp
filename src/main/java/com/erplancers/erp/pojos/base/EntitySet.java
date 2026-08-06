package com.erplancers.erp.pojos.base;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@MappedSuperclass
@NoArgsConstructor
@Getter
@Setter
public class EntitySet<T extends EntityBase> extends EntityBase {
    HashSet<T> dataset;
    String datasetName;
    String datasetDescription;
}
