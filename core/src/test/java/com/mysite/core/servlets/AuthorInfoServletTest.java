package com.mysite.core.servlets;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;

import com.mysite.core.servlets.AuthorInfoServlet.ChildPageInfo;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.*;
import org.apache.sling.api.request.RequestPathInfo;
import org.junit.jupiter.api.*;
import org.mockito.*;

import javax.jcr.Node;
import javax.jcr.Session;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthorInfoServletTest {

    @InjectMocks
    private AuthorInfoServlet servlet;

    @Mock
    private SlingHttpServletRequest request;
    @Mock
    private SlingHttpServletResponse response;
    @Mock
    private ResourceResolver resourceResolver;
    @Mock
    private Resource resource;
    @Mock
    private PageManager pageManager;
    @Mock
    private Page page;
    @Mock
    private Session session;
    @Mock
    private Node pageNode;
    @Mock
    private Node userNode;
    @Mock
    private Resource userResource;
    @Mock
    private Iterator<Page> childIterator;
    @Mock
    private Page childPage;
    @Mock
    private Node childNode;
    @Mock
    private RequestPathInfo requestPathInfo;

    private AutoCloseable closeable;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        closeable = MockitoAnnotations.openMocks(this);
        when(request.getResourceResolver()).thenReturn(resourceResolver);
        when(resourceResolver.adaptTo(PageManager.class)).thenReturn(pageManager);
        when(resourceResolver.adaptTo(Session.class)).thenReturn(session);
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter)); // Ensure response writer is mocked
        when(request.getRequestPathInfo()).thenReturn(requestPathInfo); // Mock request path info
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void testDoGet_NoPathParam() throws Exception {
        when(request.getParameter("path")).thenReturn(null);

        servlet.doGet(request, response);

        verify(response).sendError(400, "Path parameter is required");
    }

    @Test
    void testDoGet_ResourceNotFound() throws Exception {
        when(request.getParameter("path")).thenReturn("/invalid/path");
        when(resourceResolver.getResource("/invalid/path")).thenReturn(null);

        servlet.doGet(request, response);

        verify(response).sendError(404, "Resource not found: /invalid/path");
    }

    @Test
    void testDoGet_PageNotFound() throws Exception {
        when(request.getParameter("path")).thenReturn("/content/mysite/home");
        when(resourceResolver.getResource(any())).thenReturn(resource);
        when(pageManager.getPage(any())).thenReturn(null);

        servlet.doGet(request, response);

        verify(response).sendError(404, "Page not found: /content/mysite/home");
    }

    @Test
    void testDoGet_LastModifiedByMissing() throws Exception {
        when(request.getParameter("path")).thenReturn("/content/mysite/home");
        when(requestPathInfo.getExtension()).thenReturn("json");

        when(resourceResolver.getResource(any())).thenReturn(resource);
        when(pageManager.getPage(any())).thenReturn(page);
        when(session.getNode(any())).thenReturn(pageNode);
        when(pageNode.hasProperty("jcr:lastModifiedBy")).thenReturn(false);

        servlet.doGet(request, response);

        verify(response).setContentType("application/json");
        assertTrue(responseWriter.toString().contains("Unknown"));
    }

    @Test
    void testDoGet_ExceptionHandling() throws Exception {
        when(request.getParameter("path")).thenReturn("/error/path");
        when(resourceResolver.getResource(any())).thenThrow(new RuntimeException("Simulated Exception"));

        servlet.doGet(request, response);

        verify(response).sendError(eq(500), contains("Internal server error"));
    }

    @Test
    void testDoGet_WithValidJsonResponse() throws Exception {
        // Arrange and mock setup

        // Act and assert response

    }

    @Test
    void testDoGet_WithValidXmlResponse() throws Exception {
        // Arrange and mock setup

        // Act and assert response
    }

    // Test for ChildPageInfo constructor (Improving coverage)
    @Test
    void testChildPageInfoConstructor() {
        // Arrange: Prepare test data
        String title = "Test Page";
        String path = "/content/mysite/test";
        Calendar lastModified = Calendar.getInstance();
        lastModified.set(2023, Calendar.JUNE, 15); // Set a specific date for lastModified

        // Act: Create ChildPageInfo object
        ChildPageInfo childPageInfo = new ChildPageInfo(title, path, lastModified);

        // Assert: Validate the object fields
        assertNotNull(childPageInfo, "ChildPageInfo should be created successfully");
        assertEquals(title, childPageInfo.getTitle(), "Title should be correctly set");
        assertEquals(path, childPageInfo.getPath(), "Path should be correctly set");
        assertEquals(lastModified, childPageInfo.getLastModified(), "Last modified date should be correctly set");
    }

}

