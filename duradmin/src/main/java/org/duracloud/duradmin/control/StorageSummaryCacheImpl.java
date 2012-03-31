/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.duradmin.control;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.duracloud.client.report.StorageReportManager;
import org.duracloud.client.report.error.NotFoundException;
import org.duracloud.client.report.error.ReportException;
import org.duracloud.common.error.DuraCloudRuntimeException;
import org.duracloud.reportdata.storage.StorageReport;
import org.duracloud.reportdata.storage.metrics.SpaceMetrics;
import org.duracloud.reportdata.storage.metrics.StorageProviderMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This class creates an in-memory cache of storage summary lists indexed by
 * store[/spaceId] to provide a quick way for the client to display the storage
 * statistics through time for a given store or space. On construction the 
 * cache fires off a thread to start building the cache immediately.  It also 
 * schedules the cache to be refreshed every 24 hours.
 * @author Daniel Bernstein
 * 
 */
@Component
public class StorageSummaryCacheImpl implements StorageSummaryCache {
    private Logger log = LoggerFactory.getLogger(StorageSummaryCacheImpl.class);
    
    private StorageReportManager storageReportManager;

    private Map<String, List<StorageSummary>> summaryListCache =
        new HashMap<String, List<StorageSummary>>();

    private static final DateFormat REPORT_ID_DATE_FORMAT =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    private Timer timer = null;
    
    @Autowired
    public StorageSummaryCacheImpl(StorageReportManager storageReportManager) {
        if (storageReportManager == null) {
            throw new IllegalArgumentException("The storageReportManager must be non-null");
        }
        this.storageReportManager = storageReportManager;
    }

    public void init(){
        if(timer != null){
            this.timer.cancel();
        }
        this.timer = new Timer();
        
        class LoadTimerTask extends TimerTask {
            @Override
            public void run() {
                try {
                    log.info("loading storage summary cache...");
                    loadCache();
                    log.info("loaded storage summary cache.");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };

        Calendar c = Calendar.getInstance();
        c.add(Calendar.DATE, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        Date midnight = new Date(c.getTimeInMillis());
        // run immediately
        timer.schedule(new LoadTimerTask(), new Date());
        // and run
        timer.schedule(new LoadTimerTask(), midnight, 24 * 60 * 60 * 1000);
    }
    
    private Long parseDateFromReportId(String reportId) {
        // report/storage-report-2012-03-26T23:54:58.xml
        String dateString =
            reportId.replace("report/storage-report-", "").replace(".xml", "");
        try {
            return REPORT_ID_DATE_FORMAT.parse(dateString).getTime();
        } catch (ParseException e) {
            throw new DuraCloudRuntimeException(e);
        }

    }

    private void loadCache() throws Exception {
        log.info("retrieving report list...");
        List<String> list = this.storageReportManager.getStorageReportList();
        
        log.info("approximately " + list.size() + " reports in list");
        this.summaryListCache.clear();
        for (String reportId : list) {
            if (reportId.endsWith("xml")) {
                appendSummaries(reportId);
            }
        }
    }

    private void appendSummaries(String reportId)
        throws NotFoundException,
            ReportException {
        Long dateInMs = parseDateFromReportId(reportId);

        StorageReport report =
            this.storageReportManager.getStorageReport(reportId);
        for (StorageProviderMetrics spm : report.getStorageMetrics()
                                                .getStorageProviderMetrics()) {
            String storeId = spm.getStorageProviderId();
            StorageSummary sps =
                new StorageSummary(dateInMs,
                                   spm.getTotalSize(),
                                   spm.getTotalItems(),
                                   reportId);

            appendToSummaryList(storeId, sps);
            for (SpaceMetrics spaceMetrics : spm.getSpaceMetrics()) {
                StorageSummary spaceSummary =
                    new StorageSummary(dateInMs,
                                       spaceMetrics.getTotalSize(),
                                       spaceMetrics.getTotalItems(),
                                       reportId);
                appendToSummaryList(storeId,
                                    spaceMetrics.getSpaceName(),
                                    spaceSummary);
            }
        }
        
        log.info("added storage summaries extracted from " + reportId);
    }

    private void appendToSummaryList(String storeId, StorageSummary summary) {
        appendToSummaryList(storeId, null, summary);
    }

    private void appendToSummaryList(String storeId,
                                     String spaceId,
                                     StorageSummary summary) {
        List<StorageSummary> list = getSummaryList(storeId, spaceId);
        list.add(summary);
    }

    private List<StorageSummary> getSummaryList(String storeId, String spaceId) {
        String key = formatKey(storeId, spaceId);
        List<StorageSummary> summaryList = summaryListCache.get(key);
        if (summaryList == null) {
            summaryList = new LinkedList<StorageSummary>();
            summaryListCache.put(key, summaryList);
        }

        return summaryList;
    }

    public List<StorageSummary> getSummaries(String storeId, String spaceId) {
        if (storeId == null) {
            throw new IllegalArgumentException("storeId must be non-null");
        }

        return getSummaryList(storeId, spaceId);
    }

    private String formatKey(String storeId, String spaceId) {
        return storeId + (spaceId != null ? "/" + spaceId : "");
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if(timer != null){
            timer.cancel();
        }
    }
}
