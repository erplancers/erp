package com.erplancers.erp.pojos.sets;

import com.erplancers.erp.pojos.DocumentRevision;
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

public class DocumentRevisionSet extends EntitySet<DocumentRevision> {
}
