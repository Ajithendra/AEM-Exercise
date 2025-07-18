package com.mysite.core.models;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class SearchComponent {

    @SlingObject
    public Resource resource;

    @SlingObject
    public ResourceResolver resourceResolver;

    @ValueMapValue
    public String inputLabel;

    @ValueMapValue
    public String submitButtonText;
    
    @ValueMapValue
    public String searchTerm;

    private List<SearchResult> searchResults;
    private boolean hasResults;
    private String noResultsMessage = "No pages found matching your search term.";

    @PostConstruct
    protected void init() {
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            performSearch();
        }
    }

    private void performSearch() {
        searchResults = new ArrayList<>();
        
        try {
            Session session = resourceResolver.adaptTo(Session.class);
            if (session == null) {
                return;
            }

            QueryManager queryManager = session.getWorkspace().getQueryManager();
            
            // Search in title and description (jcr:title and jcr:description)
            String query = "SELECT * FROM [cq:Page] AS page WHERE " +
                          "ISDESCENDANTNODE(page, '/content') AND " +
                          "(page.[jcr:title] LIKE '%" + searchTerm + "%' OR " +
                          "page.[jcr:description] LIKE '%" + searchTerm + "%')";
            
            Query jcrQuery = queryManager.createQuery(query, Query.JCR_SQL2);
            QueryResult result = jcrQuery.execute();
            RowIterator rows = result.getRows();
            
            while (rows.hasNext()) {
                Row row = rows.nextRow();
                Node pageNode = row.getNode();
                
                SearchResult resultItem = createSearchResult(pageNode);
                if (resultItem != null) {
                    searchResults.add(resultItem);
                }
            }
            
            hasResults = !searchResults.isEmpty();
            
        } catch (RepositoryException e) {
            // Log error but don't fail the component
            hasResults = false;
        }
    }

    private SearchResult createSearchResult(Node pageNode) throws RepositoryException {
        try {
            String path = pageNode.getPath();
            String title = pageNode.hasProperty("jcr:title") ? 
                    pageNode.getProperty("jcr:title").getString() : "";
            String description = pageNode.hasProperty("jcr:description") ? 
                    pageNode.getProperty("jcr:description").getString() : "";
            
            // Get image path if available
            String imagePath = null;
            if (pageNode.hasNode("jcr:content")) {
                Node contentNode = pageNode.getNode("jcr:content");
                if (contentNode.hasProperty("fileReference")) {
                    imagePath = contentNode.getProperty("fileReference").getString();
                }
            }
            
            Calendar lastModified = pageNode.hasProperty("jcr:lastModified") ? 
                    pageNode.getProperty("jcr:lastModified").getDate() : null;
            
            return new SearchResult(title, description, imagePath, path, lastModified);
            
        } catch (RepositoryException e) {
            return null;
        }
    }

    // Getters
    public String getInputLabel() {
        return inputLabel != null ? inputLabel : "Search Pages";
    }

    public String getSubmitButtonText() {
        return submitButtonText != null ? submitButtonText : "Search";
    }

    public String getSearchTerm() {
        return searchTerm;
    }

    public List<SearchResult> getSearchResults() {
        return searchResults;
    }

    public boolean isHasResults() {
        return hasResults;
    }

    public String getNoResultsMessage() {
        return noResultsMessage;
    }

    public static class SearchResult {
        private String title;
        private String description;
        private String imagePath;
        private String pagePath;
        private Calendar lastModified;

        public SearchResult(String title, String description, String imagePath, String pagePath, Calendar lastModified) {
            this.title = title;
            this.description = description;
            this.imagePath = imagePath;
            this.pagePath = pagePath;
            this.lastModified = lastModified;
        }

        // Getters
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getImagePath() { return imagePath; }
        public String getPagePath() { return pagePath; }
        public Calendar getLastModified() { return lastModified; }
    }
} 