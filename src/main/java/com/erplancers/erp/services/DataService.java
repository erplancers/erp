package com.erplancers.erp.services;

import com.erplancers.erp.pojos.*;
import com.erplancers.erp.repositories.*;

import jakarta.annotation.PostConstruct;
import jakarta.faces.model.DataModel;
import jakarta.faces.model.DataModelEvent;
import jakarta.faces.model.DataModelListener;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;
import lombok.Getter;

import org.joinfaces.primefaces.SpringDataJpaLazyDataModel;
import org.omnifaces.cdi.ViewScoped;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.Serializable;

@Service
@Transactional
@Getter
@ViewScoped
@Named("data")

public class DataService implements Serializable {
    @Autowired
    private ProductQuoteRepository productQuoteRepository;

    //region Repositories
    public DataService() {
    }
    @Autowired
    ActionRepository actionRepository;
    @Autowired
    AddressRepository addressRepository;
    @Autowired
    AlertRepository alertRepository;
    @Autowired
    AnnouncementRepository announcementRepository;
    @Autowired
    AssignmentRepository assignmentRepository;
    @Autowired
    BusinessHourRepository businessHourRepository;
    @Autowired
    CallRepository callRepository;
    @Autowired
    CampaignRepository campaignRepository;
    @Autowired
    CampaignLogRepository campaignLogRepository;
    @Autowired
    CaseEventRepository caseEventRepository;
    @Autowired
    CaseUpdateRepository caseUpdateRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    CityRepository cityRepository;
    @Autowired
    ConditionRepository conditionRepository;
    @Autowired
    ContactRepository contactRepository;
    @Autowired
    ContentRepository contentRepository;
    @Autowired
    ContractRepository contractRepository;
    @Autowired
    CouponRepository couponRepository;
    @Autowired
    CurrencyRepository currencyRepository;
    @Autowired
    CustomerRepository customerRepository;
    @Autowired
    CustomerGroupRepository customerGroupRepository;
    @Autowired
    DocumentLinkRepository documentLinkRepository;
    @Autowired
    DocumentRepository documentRepository;
    @Autowired
    EmailAddressRepository emailAddressRepository;
    @Autowired
    EmailMarketingRepository emailMarketingRepository;
    @Autowired
    EmailRepository emailRepository;
    @Autowired
    EmailTaskRepository emailTaskRepository;
    @Autowired
    EmailTextRepository emailTextRepository;
    @Autowired
    ErpCaseRepository erpCaseRepository;
    @Autowired
    ErpOrderRepository erpOrderRepository;
    @Autowired
    EventLocationRepository eventLocationRepository;
    @Autowired
    EventRepository eventRepository;
    @Autowired
    FavoriteRepository favoriteRepository;
    @Autowired
    FolderRepository folderRepository;
    @Autowired
    FolderSubscriptionRepository folderSubscriptionRepository;
    @Autowired
    HelpCategoryRepository helpCategoryRepository;
    @Autowired
    HelpRepository helpRepository;
    @Autowired
    InboundMailRepository inboundMailRepository;
    @Autowired
    KnowledgebaseCategoryRepository knowledgebaseCategoryRepository;
    @Autowired
    KnowledgebaseRepository knowledgebaseRepository;
    @Autowired
    LeadRepository leadRepository;
    @Autowired
    LocationRepository locationRepository;
    @Autowired
    ManufacturerRepository manufacturerRepository;
    @Autowired
    MeetingRepository meetingRepository;
    @Autowired
    MessageRepository messageRepository;
    @Autowired
    MetadataRepository metadataRepository;
    @Autowired
    NoteRepository noteRepository;
    @Autowired
    OnlineCustomerRepository onlineCustomerRepository;
    @Autowired
    OpportunityRepository opportunityRepository;
    @Autowired
    OrderDataRepository orderDataRepository;
    @Autowired
    OrderOptionRepository orderOptionRepository;
    @Autowired
    OrderStatusRepository orderStatusRepository;
    @Autowired
    OutboundMailRepository outboundMailRepository;
    @Autowired
    PaymentTransactionRepository paymentTransactionRepository;
    @Autowired
    ProductDiscountRepository productDiscountRepository;
    @Autowired
    ProductOptionRepository productOptionRepository;
    @Autowired
    ProductOptionValueRepository productOptionValueRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    ProductSpecialsRepository productSpecialsRepository;
    @Autowired
    ProductStockLocationRepository productStockLocationRepository;
    @Autowired
    QuoteRepository quoteRepository;
    @Autowired
    ReviewRepository reviewRepository;
    @Autowired
    SaleRepository saleRepository;
    @Autowired
    StockItemRepository stockItemRepository;
    @Autowired
    StockCategoryRepository stockCategoryRepository;
    @Autowired
    StockStatusRepository stockStatusRepository;
    @Autowired
    StoreRepository storeRepository;
    @Autowired
    SupplierRepository supplierRepository;
    @Autowired
    SurveyQuestionOptionRepository surveyQuestionOptionRepository;
    @Autowired
    SurveyQuestionRepository surveyQuestionRepository;
    @Autowired
    SurveyRepository surveyRepository;
    @Autowired
    SurveyResponseRepository surveyResponseRepository;
    @Autowired
    TaxClassRepository taxClassRepository;
    @Autowired
    TaxRateRepository taxRateRepository;
    @Autowired
    ZoneRepository zoneRepository;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private CustomerTransactionRepository customerTransactionRepository;
    @Autowired
    private DocumentRevisionRepository documentRevisionRepository;

    //endregion
    //region JSF data models
    DataModel<Action> actions;
    DataModel<Address> addresses;
    DataModel<Alert> alerts;
    DataModel<Announcement> announcements;
    DataModel<BusinessHour> businessHours;
    DataModel<Call> calls;
    DataModel<CampaignLog> campaignLogs;
    DataModel<Campaign> campaigns;
    DataModel<CaseEvent> caseEvents;
    DataModel<CaseUpdate> caseUpdates;
    DataModel<Category> categories;
    DataModel<City> cities;
    DataModel<Contact> contacts;
    DataModel<Content> contents;
    DataModel<Contract> contracts;
    DataModel<Country> countries;
    DataModel<Coupon> coupons;
    DataModel<Currency> currencies;
    DataModel<CustomerGroup> customerGroups;
    DataModel<Customer> customers;
    DataModel<DocumentLink> documentLinks;
    DataModel<Document> documents;
    DataModel<DocumentRevision> documentRevisions;
    DataModel<EmailAddress> emailAddresses;
    DataModel<EmailMarketing> emailMarketings;
    DataModel<Email> emails;
    DataModel<EmailTask> emailTasks;
    DataModel<EmailText> emailTexts;
    DataModel<ErpCase> cases;
    DataModel<ErpOrder> orders;
    DataModel<EventLocation> eventLocations;
    DataModel<Event> events;
    DataModel<Favorite> favorites;
    DataModel<Folder> folders;
    DataModel<FolderSubscription> folderSubscriptions;
    DataModel<HelpCategory> helpCategories;
    DataModel<Help> helps;
    DataModel<InboundMail> inboundMails;
    DataModel<KnowledgebaseCategory> knowledgebaseCategories;
    DataModel<Knowledgebase> knowledgebases;
    DataModel<Lead> leads;
    DataModel<Location> locations;
    DataModel<Manufacturer> manufacturers;
    DataModel<Meeting>  meetings;
    DataModel<Message> messages;
    DataModel<Note> notes;
    DataModel<Metadata> metadatas;
    DataModel<OnlineCustomer> onlineCustomers;
    DataModel<Opportunity> opportunities;
    DataModel<OrderData> orderDatas;
    DataModel<OrderOption> orderOptions;
    DataModel<OrderStatus> orderStatuses;
    DataModel<OutboundMail> outboundMails;
    DataModel<PaymentTransaction> paymentTransactions;
    DataModel<ProductDiscount> productDiscounts;
    DataModel<ProductOption> productOptions;
    DataModel<ProductOptionValue> productOptionValues;
    DataModel<ProductQuote> productQuotes;
    DataModel<Product> products;
    DataModel<ProductSpecials>  productSpecials;
    DataModel<ProductStockLocation> productStockLocations;
    DataModel<Quote> quotes;
    DataModel<Review> reviews;
    DataModel<Sale> sales;
    DataModel<StockCategory> stockCategories;
    DataModel<StockItem> stockItems;
    DataModel<StockStatus> stockStatuses;
    DataModel<Store> stores;
    DataModel<Supplier> suppliers;
    DataModel<SurveyQuestionOption> surveyQuestionOptions;
    DataModel<SurveyQuestion> surveyQuestions;
    DataModel<Survey> surveys;
    DataModel<SurveyResponse> surveyResponses;
    DataModel<TaxClass> taxClasses;
    DataModel<TaxRate> taxRates;
    DataModel<Zone> zones;
    //endregion
    @PostConstruct
    public void init() {
       actions=new SpringDataJpaLazyDataModel<>(this.actionRepository);
       addresses=new SpringDataJpaLazyDataModel<>(this.addressRepository);
       alerts=new SpringDataJpaLazyDataModel<>(this.alertRepository);
       announcements=new SpringDataJpaLazyDataModel<>(this.announcementRepository);
       businessHours=new SpringDataJpaLazyDataModel<>(this.businessHourRepository);
       calls=new SpringDataJpaLazyDataModel<>(this.callRepository);
       campaignLogs=new SpringDataJpaLazyDataModel<>(this.campaignLogRepository);
       campaigns=new SpringDataJpaLazyDataModel<>(this.campaignRepository);
       caseEvents=new SpringDataJpaLazyDataModel<>(this.caseEventRepository);
       caseUpdates=new SpringDataJpaLazyDataModel<>(this.caseUpdateRepository);
       cities=new SpringDataJpaLazyDataModel<>(this.cityRepository);
       contacts=new SpringDataJpaLazyDataModel<>(this.contactRepository);
       contents=new SpringDataJpaLazyDataModel<>(this.contentRepository);
       contracts=new SpringDataJpaLazyDataModel<>(this.contractRepository);
       countries=new SpringDataJpaLazyDataModel<>(this.countryRepository);
       coupons=new SpringDataJpaLazyDataModel<>(this.couponRepository);
       currencies=new SpringDataJpaLazyDataModel<>(this.currencyRepository);
       customerGroups=new SpringDataJpaLazyDataModel<>(this.customerGroupRepository);
       customers=new SpringDataJpaLazyDataModel<>(this.customerRepository);
       documentLinks=new SpringDataJpaLazyDataModel<>(this.documentLinkRepository);
       documentRevisions=new SpringDataJpaLazyDataModel<>(this.documentRevisionRepository);
       emailAddresses=new SpringDataJpaLazyDataModel<>(this.emailAddressRepository);
       emailMarketings=new SpringDataJpaLazyDataModel<>(this.emailMarketingRepository);
       emails=new SpringDataJpaLazyDataModel<>(this.emailRepository);
       emailTasks=new SpringDataJpaLazyDataModel<>(this.emailTaskRepository);
       emailTexts=new SpringDataJpaLazyDataModel<>(this.emailTextRepository);
       cases=new SpringDataJpaLazyDataModel<>(erpCaseRepository);
       orders=new SpringDataJpaLazyDataModel<>(erpOrderRepository);
       eventLocations=new SpringDataJpaLazyDataModel<>(eventLocationRepository);
       events=new SpringDataJpaLazyDataModel<>(eventRepository);
       favorites=new SpringDataJpaLazyDataModel<>(favoriteRepository);
       folders=new SpringDataJpaLazyDataModel<>(folderRepository);
       folderSubscriptions=new SpringDataJpaLazyDataModel<>(folderSubscriptionRepository);
       helpCategories=new SpringDataJpaLazyDataModel<>(helpCategoryRepository);
       helps=new SpringDataJpaLazyDataModel<>(helpRepository);
       inboundMails=new SpringDataJpaLazyDataModel<>(inboundMailRepository);
       knowledgebaseCategories=new SpringDataJpaLazyDataModel<>(knowledgebaseCategoryRepository);
       knowledgebases=new SpringDataJpaLazyDataModel<>(knowledgebaseRepository);
       leads=new SpringDataJpaLazyDataModel<>(leadRepository);
       locations=new SpringDataJpaLazyDataModel<>(locationRepository);
       manufacturers=new SpringDataJpaLazyDataModel<>(manufacturerRepository);
       meetings=new SpringDataJpaLazyDataModel<>(meetingRepository);
       messages=new SpringDataJpaLazyDataModel<>(messageRepository);
       metadatas=new SpringDataJpaLazyDataModel<>(metadataRepository);
       notes=new SpringDataJpaLazyDataModel<>(noteRepository);
       onlineCustomers=new SpringDataJpaLazyDataModel<>(onlineCustomerRepository);
       opportunities=new SpringDataJpaLazyDataModel<>(opportunityRepository);
       orderDatas=new SpringDataJpaLazyDataModel<>(orderDataRepository);
       orderOptions=new SpringDataJpaLazyDataModel<>(orderOptionRepository);
       orderStatuses=new SpringDataJpaLazyDataModel<>(orderStatusRepository);
       outboundMails=new SpringDataJpaLazyDataModel<>(outboundMailRepository);
       paymentTransactions=new SpringDataJpaLazyDataModel<>(paymentTransactionRepository);
       productDiscounts=new SpringDataJpaLazyDataModel<>(productDiscountRepository);
       productOptions=new SpringDataJpaLazyDataModel<>(productOptionRepository);
       productQuotes=new SpringDataJpaLazyDataModel<>(productQuoteRepository);
       products=new SpringDataJpaLazyDataModel<>(productRepository);
       productSpecials=new SpringDataJpaLazyDataModel<>(productSpecialsRepository);
       productStockLocations=new SpringDataJpaLazyDataModel<>(productStockLocationRepository);
       quotes=new SpringDataJpaLazyDataModel<>(quoteRepository);
       reviews=new SpringDataJpaLazyDataModel<>(reviewRepository);
       sales=new SpringDataJpaLazyDataModel<>(saleRepository);
       stockCategories=new SpringDataJpaLazyDataModel<>(stockCategoryRepository);
       stockItems=new SpringDataJpaLazyDataModel<>(stockItemRepository);
       stockStatuses=new SpringDataJpaLazyDataModel<>(stockStatusRepository);
       stores=new SpringDataJpaLazyDataModel<>(storeRepository);
       suppliers=new SpringDataJpaLazyDataModel<>(supplierRepository);
       surveyQuestionOptions=new SpringDataJpaLazyDataModel<>(surveyQuestionOptionRepository);
       surveyQuestions=new SpringDataJpaLazyDataModel<>(surveyQuestionRepository);
       surveys=new SpringDataJpaLazyDataModel<>(surveyRepository);
       surveyResponses=new SpringDataJpaLazyDataModel<>(surveyResponseRepository);
       taxClasses=new SpringDataJpaLazyDataModel<>(taxClassRepository);
       taxRates=new SpringDataJpaLazyDataModel<>(taxRateRepository);
       zones=new SpringDataJpaLazyDataModel<>(zoneRepository);
    }

}
