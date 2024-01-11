package com.linkedin.datahub.graphql.resolvers.businessattribute;

import com.datahub.authentication.Authentication;
import com.linkedin.businessattribute.BusinessAttributeInfo;
import com.linkedin.businessattribute.BusinessAttributeKey;
import com.linkedin.common.urn.Urn;
import com.linkedin.data.template.SetMode;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.exception.AuthorizationException;
import com.linkedin.datahub.graphql.generated.CreateBusinessAttributeInput;
import com.linkedin.datahub.graphql.generated.SchemaFieldDataType;
import com.linkedin.datahub.graphql.resolvers.mutate.MutationUtils;
import com.linkedin.datahub.graphql.resolvers.mutate.util.BusinessAttributeUtils;
import com.linkedin.entity.Aspect;
import com.linkedin.entity.EntityResponse;
import com.linkedin.entity.EnvelopedAspect;
import com.linkedin.entity.EnvelopedAspectMap;
import com.linkedin.entity.client.EntityClient;
import com.linkedin.metadata.entity.EntityService;
import com.linkedin.metadata.query.SearchFlags;
import com.linkedin.metadata.query.filter.Filter;
import com.linkedin.metadata.search.SearchResult;
import com.linkedin.metadata.service.BusinessAttributeService;
import com.linkedin.mxe.MetadataChangeProposal;
import com.linkedin.schema.BooleanType;
import graphql.schema.DataFetchingEnvironment;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutionException;

import static com.linkedin.datahub.graphql.TestUtils.getMockAllowContext;
import static com.linkedin.datahub.graphql.TestUtils.getMockDenyContext;
import static com.linkedin.datahub.graphql.TestUtils.getMockEntityService;
import static com.linkedin.metadata.Constants.BUSINESS_ATTRIBUTE_ENTITY_NAME;
import static com.linkedin.metadata.Constants.BUSINESS_ATTRIBUTE_INFO_ASPECT_NAME;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class CreateBusinessAttributeResolverTest {

    private static final String TEST_BUSINESS_ATTRIBUTE_NAME = "test-business-attribute";
    private static final String TEST_BUSINESS_ATTRIBUTE_DESCRIPTION = "test-description";
    private static final CreateBusinessAttributeInput TEST_INPUT = new CreateBusinessAttributeInput(
            TEST_BUSINESS_ATTRIBUTE_NAME,
            TEST_BUSINESS_ATTRIBUTE_DESCRIPTION,
            SchemaFieldDataType.BOOLEAN
    );
    private static final CreateBusinessAttributeInput TEST_INPUT_NULL_NAME = new CreateBusinessAttributeInput(
            null,
            TEST_BUSINESS_ATTRIBUTE_DESCRIPTION,
            SchemaFieldDataType.BOOLEAN
    );
    private static final String BUSINESS_ATTRIBUTE_URN = "urn:li:businessAttribute:7d0c4283-de02-4043-aaf2-698b04274658";
    private EntityClient mockClient;
    private EntityService mockService;
    private QueryContext mockContext;
    private DataFetchingEnvironment mockEnv;
    private BusinessAttributeService businessAttributeService;
    private Authentication mockAuthentication;
    private SearchResult searchResult;

    private void init() {
        mockClient = Mockito.mock(EntityClient.class);
        mockService = getMockEntityService();
        mockEnv = Mockito.mock(DataFetchingEnvironment.class);
        businessAttributeService = Mockito.mock(BusinessAttributeService.class);
        mockAuthentication = Mockito.mock(Authentication.class);
        searchResult = Mockito.mock(SearchResult.class);
    }

    @Test
    public void testSuccess() throws Exception {
        //Mock
        init();
        setupAllowContext();
        Mockito.when(mockEnv.getArgument(Mockito.eq("input"))).thenReturn(TEST_INPUT);
        Mockito.when(mockClient.exists(Mockito.any(Urn.class), Mockito.eq(mockAuthentication))).thenReturn(false);
        Mockito.when(mockClient.search(Mockito.any(String.class), Mockito.any(String.class), Mockito.any(Filter.class),
                Mockito.isNull(), Mockito.eq(0), Mockito.eq(1000), Mockito.eq(mockAuthentication), Mockito.any(SearchFlags.class)
        )).thenReturn(searchResult);
        Mockito.when(searchResult.getNumEntities()).thenReturn(0);
        Mockito.when(mockClient.ingestProposal(Mockito.any(MetadataChangeProposal.class), Mockito.eq(mockAuthentication))).thenReturn(BUSINESS_ATTRIBUTE_URN);
        Mockito.when(
                businessAttributeService.getBusinessAttributeEntityResponse(Mockito.any(Urn.class), Mockito.eq(mockAuthentication))
        ).thenReturn(getBusinessAttributeEntityResponse());

        //Execute
        CreateBusinessAttributeResolver resolver = new CreateBusinessAttributeResolver(mockClient, mockService, businessAttributeService);
        resolver.get(mockEnv).get();

        //verify
        Mockito.verify(mockClient, Mockito.times(1)).ingestProposal(
                Mockito.argThat(new CreateBusinessAttributeProposalMatcher(metadataChangeProposal())),
                Mockito.any(Authentication.class)
        );

    }

    @Test
    public void testNameIsNull() throws Exception {
        //Mock
        init();
        setupAllowContext();
        Mockito.when(mockEnv.getArgument(Mockito.eq("input"))).thenReturn(TEST_INPUT_NULL_NAME);
        Mockito.when(mockClient.exists(Mockito.any(Urn.class), Mockito.eq(mockAuthentication))).thenReturn(false);

        //Execute
        CreateBusinessAttributeResolver resolver = new CreateBusinessAttributeResolver(mockClient, mockService, businessAttributeService);
        ExecutionException actualException = expectThrows(ExecutionException.class, () -> resolver.get(mockEnv).get());

        //verify
        assertTrue(actualException.getCause().getMessage().equals("Failed to create Business Attribute with name: null"));

        Mockito.verify(mockClient, Mockito.times(0)).ingestProposal(
                Mockito.any(MetadataChangeProposal.class), Mockito.any(Authentication.class)
        );
    }

    @Test
    public void testNameAlreadyExists() throws Exception {
        //Mock
        init();
        setupAllowContext();
        Mockito.when(mockEnv.getArgument(Mockito.eq("input"))).thenReturn(TEST_INPUT);
        Mockito.when(mockClient.exists(Mockito.any(Urn.class), Mockito.eq(mockAuthentication))).thenReturn(false);
        Mockito.when(mockClient.search(Mockito.any(String.class), Mockito.any(String.class), Mockito.any(Filter.class),
                Mockito.isNull(), Mockito.eq(0), Mockito.eq(1000), Mockito.eq(mockAuthentication), Mockito.any(SearchFlags.class)
        )).thenReturn(searchResult);
        Mockito.when(searchResult.getNumEntities()).thenReturn(1);

        //Execute
        CreateBusinessAttributeResolver resolver = new CreateBusinessAttributeResolver(mockClient, mockService, businessAttributeService);
        ExecutionException exception = expectThrows(ExecutionException.class, () -> resolver.get(mockEnv).get());

        //Verify
        assertTrue(exception.getCause().getMessage().equals("\"test-business-attribute\" already exists as Business Attribute. Please pick a unique name."));
        Mockito.verify(mockClient, Mockito.times(0)).ingestProposal(
                Mockito.any(MetadataChangeProposal.class), Mockito.any(Authentication.class)
        );
    }
    @Test
    public void testUnauthorized() throws Exception {
        init();
        setupDenyContext();
        Mockito.when(mockEnv.getArgument(Mockito.eq("input"))).thenReturn(TEST_INPUT);

        CreateBusinessAttributeResolver resolver = new CreateBusinessAttributeResolver(mockClient, mockService, businessAttributeService);
        AuthorizationException exception = expectThrows(AuthorizationException.class, () -> resolver.get(mockEnv));

        assertTrue(exception.getMessage().equals("Unauthorized to perform this action. Please contact your DataHub administrator."));
        Mockito.verify(mockClient, Mockito.times(0)).ingestProposal(
                Mockito.any(MetadataChangeProposal.class), Mockito.any(Authentication.class)
        );
    }
    private EntityResponse getBusinessAttributeEntityResponse() throws Exception {
        EnvelopedAspectMap map = new EnvelopedAspectMap();
        BusinessAttributeInfo businessAttributeInfo = businessAttributeInfo();
        map.put(BUSINESS_ATTRIBUTE_INFO_ASPECT_NAME, new EnvelopedAspect().setValue(new Aspect(businessAttributeInfo.data())));
        EntityResponse entityResponse = new EntityResponse();
        entityResponse.setAspects(map);
        entityResponse.setUrn(Urn.createFromString(BUSINESS_ATTRIBUTE_URN));
        return entityResponse;
    }

    private MetadataChangeProposal metadataChangeProposal() {
        BusinessAttributeKey businessAttributeKey = new BusinessAttributeKey();
        BusinessAttributeInfo info = new BusinessAttributeInfo();
        info.setFieldPath(TEST_BUSINESS_ATTRIBUTE_NAME);
        info.setName(TEST_BUSINESS_ATTRIBUTE_NAME);
        info.setDescription(TEST_BUSINESS_ATTRIBUTE_DESCRIPTION);
        info.setType(BusinessAttributeUtils.mapSchemaFieldDataType(SchemaFieldDataType.BOOLEAN), SetMode.IGNORE_NULL);
        return MutationUtils.buildMetadataChangeProposalWithKey(businessAttributeKey, BUSINESS_ATTRIBUTE_ENTITY_NAME,
                BUSINESS_ATTRIBUTE_INFO_ASPECT_NAME, info);
    }
    private void setupAllowContext() {
        mockContext = getMockAllowContext();
        Mockito.when(mockContext.getAuthentication()).thenReturn(mockAuthentication);
        Mockito.when(mockEnv.getContext()).thenReturn(mockContext);
    }

    private void setupDenyContext() {
        mockContext = getMockDenyContext();
        Mockito.when(mockContext.getAuthentication()).thenReturn(mockAuthentication);
        Mockito.when(mockEnv.getContext()).thenReturn(mockContext);
    }
    private BusinessAttributeInfo businessAttributeInfo() {
        BusinessAttributeInfo businessAttributeInfo = new BusinessAttributeInfo();
        businessAttributeInfo.setName(TEST_BUSINESS_ATTRIBUTE_NAME);
        businessAttributeInfo.setFieldPath(TEST_BUSINESS_ATTRIBUTE_NAME);
        businessAttributeInfo.setDescription(TEST_BUSINESS_ATTRIBUTE_DESCRIPTION);
        com.linkedin.schema.SchemaFieldDataType schemaFieldDataType = new com.linkedin.schema.SchemaFieldDataType();
        schemaFieldDataType.setType(com.linkedin.schema.SchemaFieldDataType.Type.create(new BooleanType()));
        businessAttributeInfo.setType(schemaFieldDataType);
        return businessAttributeInfo;
    }
}
