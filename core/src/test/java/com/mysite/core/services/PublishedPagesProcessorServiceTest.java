package com.mysite.core.services;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;
import java.util.Calendar;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublishedPagesProcessorServiceTest {

    @Mock
    private ResourceResolverFactory resourceResolverFactory;

    @Mock
    private ResourceResolver resourceResolver;

    @Mock
    private Session session;

    @Mock
    private QueryManager queryManager;

    @Mock
    private Query query;

    @Mock
    private QueryResult queryResult;

    @Mock
    private RowIterator rowIterator;

    @Mock
    private Row row;

    @Mock
    private Node pageNode;

    @Mock
    private Property processedDateProperty;

    @InjectMocks
    private PublishedPagesProcessorService service;

    private PublishedPagesProcessorService.Config config;

    @BeforeEach
    void setUp() {
        // Mock the config
        config = mock(PublishedPagesProcessorService.Config.class);
        when(config.enabled()).thenReturn(true);
        when(config.contentPath()).thenReturn("/content");

        // Set up service with mocked config
        service.activate(config);
    }

   

   
   

    @Test
    void testProcessPublishedPages() throws Exception {
        // Given
        setupQueryMocks();
        when(rowIterator.hasNext()).thenReturn(true, false);
        when(rowIterator.nextRow()).thenReturn(row);
        when(row.getNode()).thenReturn(pageNode);
        when(pageNode.getPath()).thenReturn("/content/test-page");
        when(pageNode.hasProperty("processedDate")).thenReturn(false);

        // When
        service.run();

        // Then
        verify(queryManager).createQuery(anyString(), eq(Query.JCR_SQL2));
        verify(pageNode).setProperty(eq("processedDate"), any(Calendar.class));
        verify(session).save();
        verify(resourceResolver).close();
    }

    @Test
    void testSkipRecentlyProcessedPage() throws Exception {
        // Given
        Calendar recentTime = Calendar.getInstance();
        recentTime.add(Calendar.MINUTE, -1); // 1 minute ago
        setupQueryMocks();
        when(rowIterator.hasNext()).thenReturn(true, false);
        when(rowIterator.nextRow()).thenReturn(row);
        when(row.getNode()).thenReturn(pageNode);
        when(pageNode.hasProperty("processedDate")).thenReturn(true);
        when(pageNode.getProperty("processedDate")).thenReturn(processedDateProperty);
        when(processedDateProperty.getDate()).thenReturn(recentTime);

        // When
        service.run();

        // Then
        verify(pageNode, never()).setProperty(eq("processedDate"), any(Calendar.class));
        verify(session, never()).save();
        verify(resourceResolver).close();
    }

    
    

    @Test
    void testProcessPublishedPagesWithNodeException() throws Exception {
        // Given
        setupQueryMocks();
        when(rowIterator.hasNext()).thenReturn(true, false);
        when(rowIterator.nextRow()).thenReturn(row);
        when(row.getNode()).thenReturn(pageNode);
        when(pageNode.getPath()).thenReturn("/content/test-page");
        when(pageNode.hasProperty("processedDate")).thenReturn(false);
        doThrow(new RepositoryException("Node error")).when(pageNode).setProperty(anyString(), any(Calendar.class));

        // When
        service.run();

        // Then
        verify(queryManager).createQuery(anyString(), eq(Query.JCR_SQL2));
        verify(pageNode).setProperty(eq("processedDate"), any(Calendar.class));
        verify(session, never()).save(); // Save not called due to exception
        verify(resourceResolver).close();
    }

    private void setupQueryMocks() throws Exception {
        when(resourceResolverFactory.getServiceResourceResolver(anyMap())).thenReturn(resourceResolver);
        when(resourceResolver.adaptTo(Session.class)).thenReturn(session);
        when(session.getWorkspace()).thenReturn(mock(Workspace.class));
        when(session.getWorkspace().getQueryManager()).thenReturn(queryManager);
        when(queryManager.createQuery(anyString(), anyString())).thenReturn(query);
        when(query.execute()).thenReturn(queryResult);
        when(queryResult.getRows()).thenReturn(rowIterator);
        when(resourceResolver.isLive()).thenReturn(true);
    }
}
