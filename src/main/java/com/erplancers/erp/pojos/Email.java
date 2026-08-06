package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.ContractSet;
import com.erplancers.erp.pojos.sets.MessageSet;
import com.erplancers.erp.pojos.sets.MetadataSet;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
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
public class Email extends EntityBase {
    
    String emailName;
    String emailText;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    MessageSet messages;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Contract receivingContract;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ContractSet sendingContracts;
    String mailType;
    String username;
    String password;
    String intent;
    Boolean activated;

    
}
