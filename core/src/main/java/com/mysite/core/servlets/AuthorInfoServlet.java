package com.mysite.core.servlets;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component(service = SlingSafeMethodsServlet.class,
        property = {
                "sling.servlet.methods=" + HttpConstants.METHOD_GET,
                "sling.servlet.resourceTypes=sling/servlet/default",
                "sling.servlet.selectors=authorinfo",
                "sling.servlet.extensions=json,xml"
        })
public class AuthorInfoServlet extends SlingSafeMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorInfoServlet.class);
    private static final String JCR_LAST_MODIFIED_BY = "jcr:lastModifiedBy";

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) 
            throws ServletException, IOException {
        
        try {
            String path = request.getParameter("path");
            if (path == null || path.isEmpty()) {
                response.sendError(400, "Path parameter is required");
                return;
            }

            ResourceResolver resourceResolver = request.getResourceResolver();
            Resource resource = resourceResolver.getResource(path);
            
            if (resource == null) {
                response.sendError(404, "Resource not found: " + path);
                return;
            }

            PageManager pageManager = resourceResolver.adaptTo(PageManager.class);
            Page page = pageManager.getPage(path);
            
            if (page == null) {
                response.sendError(404, "Page not found: " + path);
                return;
            }

            AuthorInfo authorInfo = getAuthorInfo(page, resourceResolver);
            
            String extension = request.getRequestPathInfo().getExtension();
            if ("xml".equals(extension)) {
                sendXmlResponse(response, authorInfo);
            } else {
                sendJsonResponse(response, authorInfo);
            }

        } catch (Exception e) {
            LOG.error("Error processing author info request", e);
            response.sendError(500, "Internal server error");
        }
    }

    private AuthorInfo getAuthorInfo(Page page, ResourceResolver resourceResolver) throws RepositoryException {
        Session session = resourceResolver.adaptTo(Session.class);
        Node pageNode = session.getNode(page.getPath());
        
        String lastModifiedBy = pageNode.hasProperty(JCR_LAST_MODIFIED_BY) ? 
                pageNode.getProperty(JCR_LAST_MODIFIED_BY).getString() : null;
        
        if (lastModifiedBy == null) {
            return new AuthorInfo("Unknown", "Unknown", new ArrayList<>());
        }

        // Get author details from user profile
        String authorFirstName = getAuthorFirstName(lastModifiedBy, resourceResolver);
        String authorLastName = getAuthorLastName(lastModifiedBy, resourceResolver);
        
        // Find child pages modified by the same author
        List<ChildPageInfo> childPages = findChildPagesModifiedByAuthor(page, lastModifiedBy, resourceResolver);
        
        return new AuthorInfo(authorFirstName, authorLastName, childPages);
    }

    private String getAuthorFirstName(String userId, ResourceResolver resourceResolver) {
        try {
            Resource userResource = resourceResolver.getResource("/home/users/" + userId.charAt(0) + "/" + userId);
            if (userResource != null) {
                Node userNode = userResource.adaptTo(Node.class);
                if (userNode != null && userNode.hasProperty("profile/givenName")) {
                    return userNode.getProperty("profile/givenName").getString();
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not get first name for user: {}", userId, e);
        }
        return userId;
    }

    private String getAuthorLastName(String userId, ResourceResolver resourceResolver) {
        try {
            Resource userResource = resourceResolver.getResource("/home/users/" + userId.charAt(0) + "/" + userId);
            if (userResource != null) {
                Node userNode = userResource.adaptTo(Node.class);
                if (userNode != null && userNode.hasProperty("profile/familyName")) {
                    return userNode.getProperty("profile/familyName").getString();
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not get last name for user: {}", userId, e);
        }
        return "";
    }

    private List<ChildPageInfo> findChildPagesModifiedByAuthor(Page parentPage, String authorId, ResourceResolver resourceResolver) {
        List<ChildPageInfo> childPages = new ArrayList<>();
        
        try {
            Session session = resourceResolver.adaptTo(Session.class);
            
            Iterator<Page> children = parentPage.listChildren();
            while (children.hasNext()) {
                Page childPage = children.next();
                Node childNode = session.getNode(childPage.getPath());
                
                if (childNode.hasProperty(JCR_LAST_MODIFIED_BY)) {
                    String childAuthor = childNode.getProperty(JCR_LAST_MODIFIED_BY).getString();
                    if (authorId.equals(childAuthor)) {
                        childPages.add(new ChildPageInfo(
                                childPage.getTitle(),
                                childPage.getPath(),
                                childPage.getLastModified()
                        ));
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error finding child pages modified by author", e);
        }
        
        return childPages;
    }

    private void sendJsonResponse(SlingHttpServletResponse response, AuthorInfo authorInfo) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(response.getWriter(), authorInfo);
    }

    private void sendXmlResponse(SlingHttpServletResponse response, AuthorInfo authorInfo) throws IOException {
        response.setContentType("application/xml");
        response.setCharacterEncoding("UTF-8");
        
        XmlMapper mapper = new XmlMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(response.getWriter(), authorInfo);
    }

    public static class AuthorInfo {
        private String firstName;
        private String lastName;
        private List<ChildPageInfo> childPages;

        public AuthorInfo(String firstName, String lastName, List<ChildPageInfo> childPages) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.childPages = childPages;
        }

        // Getters for Jackson serialization
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public List<ChildPageInfo> getChildPages() { return childPages; }
    }

    public static class ChildPageInfo {
        private String title;
        private String path;
        private java.util.Calendar lastModified;

        public ChildPageInfo(String title, String path, java.util.Calendar lastModified) {
            this.title = title;
            this.path = path;
            this.lastModified = lastModified;
        }

        // Getters for Jackson serialization
        public String getTitle() { return title; }
        public String getPath() { return path; }
        public java.util.Calendar getLastModified() { return lastModified; }
    }
} 