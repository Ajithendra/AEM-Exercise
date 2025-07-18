package com.mysite.core.models;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.jcr.*;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({AemContextExtension.class})
class SearchComponentTest {

    private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);
    private SearchComponent searchComponent;
    private Resource resource;

    @BeforeEach
    void setUp() {
        context.create().page("/content/test-page/child1", "jcr:content",
            "jcr:title", "Test Page 1",
            "jcr:description", "Description for test page 1",
            "fileReference", "/content/dam/test.jpg");

        context.create().page("/content/test-page/child2", "jcr:content",
            "jcr:title", "Test Page 2",
            "jcr:description", "Another test description");

        resource = context.create().resource("/content/test-page/jcr:content/search-component",
            "inputLabel", "Custom Search",
            "submitButtonText", "Go",
            "searchTerm", "test");

        searchComponent = resource.adaptTo(SearchComponent.class);
    }

    @Test
    void testInitWithSearchTerm() {
        assertNotNull(searchComponent);
        assertEquals("Custom Search", searchComponent.getInputLabel());
        assertEquals("Go", searchComponent.getSubmitButtonText());
        assertEquals("test", searchComponent.getSearchTerm());

        List<SearchComponent.SearchResult> results = searchComponent.getSearchResults();
        assertTrue(searchComponent.isHasResults());
        assertEquals(2, results.size());
    }

    @Test
    void testInitWithEmptySearchTerm() {
        resource = context.create().resource("/content/test-page/jcr:content/search-component-empty",
            "searchTerm", "");
        searchComponent = resource.adaptTo(SearchComponent.class);

        assertNotNull(searchComponent);
        assertFalse(searchComponent.isHasResults());
        assertNull(searchComponent.getSearchResults());
    }

    @Test
    void testDefaultValues() {
        resource = context.create().resource("/content/test-page/jcr:content/default-component");
        searchComponent = resource.adaptTo(SearchComponent.class);

        assertNotNull(searchComponent);
        assertEquals("Search Pages", searchComponent.getInputLabel());
        assertEquals("Search", searchComponent.getSubmitButtonText());
    }

    @Test
    void testSearchWithNoResults() {
        resource = context.create().resource("/content/test-page/jcr:content/search-component-noresults",
            "searchTerm", "nonexistent");
        searchComponent = resource.adaptTo(SearchComponent.class);

        assertNotNull(searchComponent);
        assertTrue(searchComponent.getSearchResults().isEmpty());
        assertFalse(searchComponent.isHasResults());
        assertEquals("No pages found matching your search term.", searchComponent.getNoResultsMessage());
    }

    // ✅ TEST: createSearchResult – all fields present
    @Test
    void testCreateSearchResult_AllFieldsPresent() throws Exception {
        Node mockPageNode = mock(Node.class);
        Node mockContentNode = mock(Node.class);
        Property titleProp = mock(Property.class);
        Property descProp = mock(Property.class);
        Property fileRefProp = mock(Property.class);
        Property modProp = mock(Property.class);
        Calendar calendar = Calendar.getInstance();

        when(mockPageNode.getPath()).thenReturn("/content/sample");
        when(mockPageNode.hasProperty("jcr:title")).thenReturn(true);
        when(mockPageNode.getProperty("jcr:title")).thenReturn(titleProp);
        when(titleProp.getString()).thenReturn("Sample Title");

        when(mockPageNode.hasProperty("jcr:description")).thenReturn(true);
        when(mockPageNode.getProperty("jcr:description")).thenReturn(descProp);
        when(descProp.getString()).thenReturn("Sample Description");

        when(mockPageNode.hasNode("jcr:content")).thenReturn(true);
        when(mockPageNode.getNode("jcr:content")).thenReturn(mockContentNode);
        when(mockContentNode.hasProperty("fileReference")).thenReturn(true);
        when(mockContentNode.getProperty("fileReference")).thenReturn(fileRefProp);
        when(fileRefProp.getString()).thenReturn("/content/dam/sample.jpg");

        when(mockPageNode.hasProperty("jcr:lastModified")).thenReturn(true);
        when(mockPageNode.getProperty("jcr:lastModified")).thenReturn(modProp);
        when(modProp.getDate()).thenReturn(calendar);

        Method method = SearchComponent.class.getDeclaredMethod("createSearchResult", Node.class);
        method.setAccessible(true);
        SearchComponent result = new SearchComponent();
        SearchComponent.SearchResult searchResult = (SearchComponent.SearchResult) method.invoke(result, mockPageNode);

        assertNotNull(searchResult);
        assertEquals("Sample Title", searchResult.getTitle());
        assertEquals("Sample Description", searchResult.getDescription());
        assertEquals("/content/dam/sample.jpg", searchResult.getImagePath());
        assertEquals("/content/sample", searchResult.getPagePath());
        assertEquals(calendar, searchResult.getLastModified());
    }

    // ✅ TEST: createSearchResult – missing fields
    @Test
    void testCreateSearchResult_MissingOptionalFields() throws Exception {
        Node mockPageNode = mock(Node.class);

        when(mockPageNode.getPath()).thenReturn("/content/partial");
        when(mockPageNode.hasProperty(anyString())).thenReturn(false);
        when(mockPageNode.hasNode("jcr:content")).thenReturn(false);

        Method method = SearchComponent.class.getDeclaredMethod("createSearchResult", Node.class);
        method.setAccessible(true);
        SearchComponent result = new SearchComponent();
        SearchComponent.SearchResult searchResult = (SearchComponent.SearchResult) method.invoke(result, mockPageNode);

        assertNotNull(searchResult);
        assertEquals("", searchResult.getTitle());
        assertEquals("", searchResult.getDescription());
        assertNull(searchResult.getImagePath());
        assertEquals("/content/partial", searchResult.getPagePath());
        assertNull(searchResult.getLastModified());
    }

    // ✅ TEST: createSearchResult – exception thrown
    @Test
    void testCreateSearchResult_ThrowsRepositoryException() throws Exception {
        Node mockPageNode = mock(Node.class);
        when(mockPageNode.getPath()).thenThrow(new RepositoryException("Forced exception"));

        Method method = SearchComponent.class.getDeclaredMethod("createSearchResult", Node.class);
        method.setAccessible(true);
        SearchComponent result = new SearchComponent();
        SearchComponent.SearchResult searchResult = (SearchComponent.SearchResult) method.invoke(result, mockPageNode);

        assertNull(searchResult); // should return null when exception occurs
    }

    // ✅ TEST: null session case
    @Test
    void testPerformSearchWithNullSession() {
        resource = context.create().resource("/content/test-page/jcr:content/null-session",
            "searchTerm", "test");

        ResourceResolver mockResolver = mock(ResourceResolver.class);
        when(mockResolver.adaptTo(Session.class)).thenReturn(null);

        searchComponent = resource.adaptTo(SearchComponent.class);
        searchComponent.resourceResolver = mockResolver;
        searchComponent.init();

        assertFalse(searchComponent.isHasResults());
        assertTrue(searchComponent.getSearchResults().isEmpty());
    }

    // ✅ TEST: RepositoryException in QueryManager
    @Test
    void testPerformSearchWithRepositoryException() throws RepositoryException {
        resource = context.create().resource("/content/test-page/jcr:content/error-component",
            "searchTerm", "test");

        ResourceResolver mockResolver = mock(ResourceResolver.class);
        Session mockSession = mock(Session.class);
        Workspace mockWorkspace = mock(Workspace.class);
        when(mockResolver.adaptTo(Session.class)).thenReturn(mockSession);
        when(mockSession.getWorkspace()).thenReturn(mockWorkspace);
        when(mockWorkspace.getQueryManager()).thenThrow(new RepositoryException("Query manager error"));

        searchComponent = resource.adaptTo(SearchComponent.class);
        searchComponent.resourceResolver = mockResolver;
        searchComponent.init();

        assertFalse(searchComponent.isHasResults());
        assertTrue(searchComponent.getSearchResults().isEmpty());
    }
}
