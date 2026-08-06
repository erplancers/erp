package com.erplancers.erp.pojos.sets;

import com.erplancers.erp.pojos.ProductQuote;
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

public class ProductQuoteSet extends EntitySet<ProductQuote> {
}
