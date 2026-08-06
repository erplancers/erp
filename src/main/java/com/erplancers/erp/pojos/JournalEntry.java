package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(indexes={
        @Index(columnList="company_id,entryNumber",unique=true),
        @Index(columnList="postingDate"),
        @Index(columnList="documentDate"),
        @Index(columnList="status"),
        @Index(columnList="currency_id")
})
@Getter
@Setter
@ToString
public class JournalEntry extends EntityBase {
    
}
