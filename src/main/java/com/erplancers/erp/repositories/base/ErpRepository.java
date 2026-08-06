package com.erplancers.erp.repositories.base;

import com.erplancers.erp.pojos.base.EntityBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.ListQueryByExampleExecutor;

import java.util.List;
import java.util.Set;

@NoRepositoryBean
public interface ErpRepository<T extends EntityBase> extends  JpaRepository<T,Integer>, JpaSpecificationExecutor<T> {
    Set<T> findAllByArchivedIsFalse();
    Set<T> findAllByArchivedIsTrue();
    Set<T> findAllByArchivedIsFalseAndArchivedIsTrue();
    Set<T> findAllBySlugContaining(String slug);
    Set<T> findAllBySlugContainingAndArchivedIsTrue(String slug);
    Set<T> findAllBySlugContainingAndArchivedIsFalse(String slug);

}
