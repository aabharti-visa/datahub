package com.linkedin.datahub.graphql.resolvers.businessattribute;

import static com.linkedin.datahub.graphql.TestUtils.getMockAllowContext;
import static com.linkedin.datahub.graphql.TestUtils.getMockEntityService;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import com.datahub.authentication.Authentication;
import com.linkedin.businessattribute.BusinessAttributeInfo;
import com.linkedin.common.AuditStamp;
import com.linkedin.common.urn.Urn;
import com.linkedin.common.urn.UrnUtils;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.generated.UpdateNameInput;
import com.linkedin.datahub.graphql.resolvers.mutate.MutationUtils;
import com.linkedin.datahub.graphql.resolvers.mutate.UpdateNameResolver;
import com.linkedin.entity.client.EntityClient;
import com.linkedin.metadata.Constants;
import com.linkedin.metadata.entity.EntityService;
import com.linkedin.metadata.entity.EntityUtils;
import com.linkedin.metadata.query.SearchFlags;
import com.linkedin.metadata.query.filter.Filter;
import com.linkedin.metadata.search.SearchResult;
import com.linkedin.mxe.MetadataChangeProposal;
import com.linkedin.schema.BooleanType;
import graphql.schema.DataFetchingEnvironment;
import java.util.concurrent.ExecutionException;
import org.mockito.Mockito;
import org.testng.annotations.Test;

public class UpdateNameResolverTest {
  private static final String TEST_BUSINESS_ATTRIBUTE_NAME = "test-business-attribute";
  private static final String TEST_BUSINESS_ATTRIBUTE_NAME_UPDATED =
      "test-business-attribute-updated";
  private static final String TEST_BUSINESS_ATTRIBUTE_DESCRIPTION = "test-description";
  private static final String TEST_BUSINESS_ATTRIBUTE_URN =
      "urn:li:businessAttribute:7d0c4283-de02-4043-aaf2-698b04274658";
  private static final Urn TEST_BUSINESS_ATTRIBUTE_URN_OBJ =
      UrnUtils.getUrn(TEST_BUSINESS_ATTRIBUTE_URN);
  private EntityClient mockClient;
  private EntityService mockService;
  private QueryContext mockContext;
  private DataFetchingEnvironment mockEnv;
  private Authentication mockAuthentication;
  private SearchResult searchResult;

  private void init() {
    mockClient = Mockito.mock(EntityClient.class);
    mockService = getMockEntityService();
    mockEnv = Mockito.mock(DataFetchingEnvironment.class);
    mockAuthentication = Mockito.mock(Authentication.class);
    searchResult = Mockito.mock(SearchResult.class);
  }

  @Test
  public void testSuccess() throws Exception {
    init();
    setupAllowContext();
    UpdateNameInput testInput =
        new UpdateNameInput(TEST_BUSINESS_ATTRIBUTE_NAME_UPDATED, TEST_BUSINESS_ATTRIBUTE_URN);
    Mockito.when(mockEnv.getArgument(Mockito.eq("input"))).thenReturn(testInput);
    Mockito.when(mockEnv.getArgument("urn")).thenReturn(TEST_BUSINESS_ATTRIBUTE_URN);
    Mockito.when(mockService.exists(TEST_BUSINESS_ATTRIBUTE_URN_OBJ, true)).thenReturn(true);
    Mockito.when(
            EntityUtils.getAspectFromEntity(
                TEST_BUSINESS_ATTRIBUTE_URN_OBJ.toString(),
                Constants.BUSINESS_ATTRIBUTE_INFO_ASPECT_NAME,
                mockService,
                null))
        .thenReturn(businessAttributeInfo());

    Mockito.when(
            mockClient.search(
                Mockito.any(String.class),
                Mockito.any(String.class),
                Mockito.any(Filter.class),
                Mockito.isNull(),
                Mockito.eq(0),
                Mockito.eq(1000),
                Mockito.eq(mockAuthentication),
                Mockito.any(SearchFlags.class)))
        .thenReturn(searchResult);
    Mockito.when(searchResult.getNumEntities()).thenReturn(0);

    BusinessAttributeInfo updatedBusinessAttributeInfo = businessAttributeInfo();
    updatedBusinessAttributeInfo.setName(TEST_BUSINESS_ATTRIBUTE_NAME_UPDATED);
    updatedBusinessAttributeInfo.setFieldPath(TEST_BUSINESS_ATTRIBUTE_NAME_UPDATED);
    MetadataChangeProposal proposal =
        MutationUtils.buildMetadataChangeProposalWithUrn(
            TEST_BUSINESS_ATTRIBUTE_URN_OBJ,
            Constants.BUSINESS_ATTRIBUTE_INFO_ASPECT_NAME,
            updatedBusinessAttributeInfo);

    UpdateNameResolver resolver = new UpdateNameResolver(mockService, mockClient);
    resolver.get(mockEnv).get();

    // verify
    Mockito.verify(mockService, Mockito.times(1))
        .ingestProposal(
            Mockito.argThat(new CreateBusinessAttributeProposalMatcher(proposal)),
            Mockito.any(AuditStamp.class),
            Mockito.eq(false));
  }

  @Test
  public void testNameConflict() throws Exception {
    init();
    setupAllowContext();
    UpdateNameInput testInput =
        new UpdateNameInput(TEST_BUSINESS_ATTRIBUTE_NAME, TEST_BUSINESS_ATTRIBUTE_URN);
    Mockito.when(mockEnv.getArgument(Mockito.eq("input"))).thenReturn(testInput);
    Mockito.when(mockEnv.getArgument("urn")).thenReturn(TEST_BUSINESS_ATTRIBUTE_URN);
    Mockito.when(mockService.exists(TEST_BUSINESS_ATTRIBUTE_URN_OBJ, true)).thenReturn(true);
    Mockito.when(
            EntityUtils.getAspectFromEntity(
                TEST_BUSINESS_ATTRIBUTE_URN_OBJ.toString(),
                Constants.BUSINESS_ATTRIBUTE_INFO_ASPECT_NAME,
                mockService,
                null))
        .thenReturn(businessAttributeInfo());

    Mockito.when(
            mockClient.search(
                Mockito.any(String.class),
                Mockito.any(String.class),
                Mockito.any(Filter.class),
                Mockito.isNull(),
                Mockito.eq(0),
                Mockito.eq(1000),
                Mockito.eq(mockAuthentication),
                Mockito.any(SearchFlags.class)))
        .thenReturn(searchResult);
    Mockito.when(searchResult.getNumEntities()).thenReturn(1);

    UpdateNameResolver resolver = new UpdateNameResolver(mockService, mockClient);
    ExecutionException exception =
        expectThrows(ExecutionException.class, () -> resolver.get(mockEnv).get());

    assertTrue(
        exception
            .getCause()
            .getMessage()
            .equals(
                "\"test-business-attribute\" already exists as Business Attribute. Please pick a unique name."));
    Mockito.verify(mockClient, Mockito.times(0))
        .ingestProposal(
            Mockito.any(MetadataChangeProposal.class), Mockito.any(Authentication.class));
  }

  private void setupAllowContext() {
    mockContext = getMockAllowContext();
    Mockito.when(mockContext.getAuthentication()).thenReturn(mockAuthentication);
    Mockito.when(mockEnv.getContext()).thenReturn(mockContext);
  }

  private BusinessAttributeInfo businessAttributeInfo() {
    BusinessAttributeInfo businessAttributeInfo = new BusinessAttributeInfo();
    businessAttributeInfo.setName(TEST_BUSINESS_ATTRIBUTE_NAME);
    businessAttributeInfo.setFieldPath(TEST_BUSINESS_ATTRIBUTE_NAME);
    businessAttributeInfo.setDescription(TEST_BUSINESS_ATTRIBUTE_DESCRIPTION);
    com.linkedin.schema.SchemaFieldDataType schemaFieldDataType =
        new com.linkedin.schema.SchemaFieldDataType();
    schemaFieldDataType.setType(
        com.linkedin.schema.SchemaFieldDataType.Type.create(new BooleanType()));
    businessAttributeInfo.setType(schemaFieldDataType);
    return businessAttributeInfo;
  }
}
