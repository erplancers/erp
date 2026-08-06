package com.erplancers.erp.pojos.sets;

import com.erplancers.erp.pojos.ProjectTask;
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
@ToString
public class ProjectTaskSet extends EntitySet<ProjectTask> {
}
