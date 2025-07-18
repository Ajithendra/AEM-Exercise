package com.mysite.core.services;


import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

@Component(service = PublishedPagesProcessorService.class, immediate = true)
@Designate(ocd = PublishedPagesProcessorService.Config.class)
public class PublishedPagesProcessorService implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(PublishedPagesProcessorService.class);
    private static final String PROCESSED_DATE_PROPERTY = "processedDate";
  
    @Reference
    public ResourceResolverFactory resourceResolverFactory;

    private Config config;

    @ObjectClassDefinition(name = "Published Pages Processor Service", description = "Service to process published pages")
    public @interface Config {
        @AttributeDefinition(name = "Enabled", description = "Enable the service")
        boolean enabled() default true;

        @AttributeDefinition(name = "Cron Expression", description = "Cron expression for scheduling (default: every 2 minutes)")
        String cronExpression() default "0 */2 * * * ?";

        @AttributeDefinition(name = "Content Path", description = "Path to search for pages")
        String contentPath() default "/content";
    }

    @Activate
    protected void activate(Config config) {
        this.config = config;
        LOG.info("PublishedPagesProcessorService activated with cron: {}", config.cronExpression());
    }

    @Override
    public void run() {
        if (!config.enabled()) {
            LOG.debug("Service is disabled");
            return;
        }

        LOG.info("Starting published pages processing");
        
        ResourceResolver resourceResolver = null;
        try {
            Map<String, Object> authInfo = new HashMap<>();
            authInfo.put(ResourceResolverFactory.SUBSERVICE, "publish-processor");
            resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo);

            if (resourceResolver == null) {
                LOG.error("Failed to get resource resolver");
                return;
            }

            Session session = resourceResolver.adaptTo(Session.class);
            if (session == null) {
                LOG.error("Failed to get JCR session");
                return;
            }

            processPublishedPages(session);
            
        } catch (Exception e) {
            LOG.error("Error processing published pages", e);
        } finally {
            if (resourceResolver != null && resourceResolver.isLive()) {
                resourceResolver.close();
            }
        }
    }

    private void processPublishedPages(Session session) throws RepositoryException {
        QueryManager queryManager = session.getWorkspace().getQueryManager();
        
        // Query to find pages that have been published (have cq:lastReplicated property)
        String query = "SELECT * FROM [cq:Page] AS page WHERE ISDESCENDANTNODE(page, '" + 
                      config.contentPath() + "') AND page.[cq:lastReplicated] IS NOT NULL";
        
        Query jcrQuery = queryManager.createQuery(query, Query.JCR_SQL2);
        QueryResult result = jcrQuery.execute();
        RowIterator rows = result.getRows();
        
        int processedCount = 0;
        Calendar currentTime = Calendar.getInstance();
        
        while (rows.hasNext()) {
            Row row = rows.nextRow();
            Node pageNode = row.getNode();
            
            try {
                // Check if the page has been processed recently (within last 2 minutes)
                if (pageNode.hasProperty(PROCESSED_DATE_PROPERTY)) {
                    Calendar lastProcessed = pageNode.getProperty(PROCESSED_DATE_PROPERTY).getDate();
                    long timeDiff = currentTime.getTimeInMillis() - lastProcessed.getTimeInMillis();
                    
                    // Skip if processed within last 2 minutes
                    if (timeDiff < 120000) {
                        continue;
                    }
                }
                
                // Set the processedDate property
                pageNode.setProperty(PROCESSED_DATE_PROPERTY, currentTime);
                processedCount++;
                
                LOG.debug("Updated processedDate for page: {}", pageNode.getPath());
                
            } catch (RepositoryException e) {
                LOG.error("Error processing page: {}", pageNode.getPath(), e);
            }
        }
        
        if (processedCount > 0) {
            session.save();
            LOG.info("Successfully processed {} published pages", processedCount);
        } else {
            LOG.debug("No pages needed processing");
        }
    }
} 