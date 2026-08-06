package com.erplancers.erp.pojos.base;

import com.erplancers.erp.pojos.Category;
import com.erplancers.erp.pojos.Project;
import com.erplancers.erp.pojos.sets.ConditionSet;
import com.erplancers.erp.pojos.sets.MetadataSet;
import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;

@MappedSuperclass
@NoArgsConstructor
@Getter
@Setter
public class EntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id;

    @Version
    long version;
    Boolean archived;



    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    MetadataSet metadata;
    String slug;


}
