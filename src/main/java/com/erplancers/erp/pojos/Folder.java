package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.*;
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

public class Folder extends EntityBase {
    
    //include all entities and folders recursive !!
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ActionSet actions;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    AddressSet addresses;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    AlertSet alerts;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    AnnouncementSet announcements;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    BusinessHourSet businessHours;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CallSet calls;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CampaignLogSet campaignLogs;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CampaignSet campaigns;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CaseEventSet caseEvents;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CaseUpdateSet caseUpdates;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CategorySet categories;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CitySet cities;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ConditionSet conditions;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ContactSet contacts;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ContentSet contents;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ContractSet contracts;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CountrySet countries;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CouponSet coupons;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CurrencySet currencies;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CustomerSet customers;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CustomerGroupSet customerGroups;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CustomerTransactionSet customerTransactions;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    DocumentSet documents;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    DocumentLinkSet documentLinks;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    DocumentRevisionSet documentRevisions;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    EmailSet emails;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    EmailAddressSet emailAddresses;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    EmailMarketingSet emailMarketings;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    EmailTaskSet emailTasks;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    EmailTextSet emailTexts;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ErpCaseSet erpCases;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ErpOrderSet erpOrders;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    EventSet events;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    EventLocationSet eventLocations;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    FavoriteSet favorites;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    FolderSet subFolders;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    FolderSubscription folderSubscriptions;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    HelpSet helpSets;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    HelpCategorySet helpCategories;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    InboundMailSet inboundMails;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    InvoiceSet invoices;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    KnowledgeBaseSet knowledgeBases;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    KnowledgebaseCategorySet knowledgebaseCategories;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    LeadSet leads;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    LocationSet locations;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ManufacturerSet manufacturers;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    MeetingSet meetings;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    MessageSet messages;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    NoteSet notes;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    OnlineCustomerSet onlineCustomers;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    OpportunitySet opportunities;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    OrderDataSet orderDatas;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    OrderOptionSet orderOptions;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    OrderStatusSet orderStatus;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    OutboundMailSet outboundMails;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    PaymentTransactionSet paymentTransactions;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProductSet products;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProductDiscountSet productDiscounts;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProductOptionSet productOptions;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProductOptionValueSet productOptionValues;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProductQuoteSet productQuotes;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProductsFeaturedSet productsFeatureds;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProductStockLocationSet productStockLocations;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProjectSet projects;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProjectTaskSet projectTasks;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProspectSet prospects;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProspectListSet prospectLists;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    PurchaseSet purchases;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    PurchaseDetailSet purchaseDetails;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    QuoteSet quotes;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ReviewSet reviews;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    SaleSet sales;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    StockCategorySet stockCategory;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    StockItemSet stockItems;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    StockStatusSet stockStatuses;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    StoreSet stores;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    SupplierSet suppliers;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    SurveySet surveys;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    SurveyQuestionSet surveyQuestions;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    SurveyQuestionOptionSet surveyQuestionOptions;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    SurveyQuestionResponseSet surveyQuestionResponses;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    SurveyResponseSet surveyResponses;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    TaxClassSet taxClasses;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    TaxRateSet taxRates;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ZoneSet zones;

}
