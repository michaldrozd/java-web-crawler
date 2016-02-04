// Copyright (C) 2010 Michal Drozd
// All Rights Reserved

// The use of this software is subject to the laws and regulations of the country in which it is being used.
// The developer of this software is not responsible for any illegal or unauthorized use of the program.
// By using this software, you agree to use it in compliance with all applicable laws and regulations!

package com.web.crawler;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Michal Drozd
 */
@Slf4j
public class App {

    private static final int MAX_PAGES_TO_SEARCH = 100000;
    private static final int URL_TIMEOUT_MS = 200;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.121 Safari/537.36";
    private final ConcurrentHashMap<String, Boolean> pagesVisited = new ConcurrentHashMap<>();
    private final BlockingQueue<String> pagesToVisit = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(12);
    private final AtomicInteger processedUrlsCounter = new AtomicInteger(0);

    @SuppressWarnings("java:S899")
    public void search(String url) {
        pagesToVisit.offer(url);
        while (processedUrlsCounter.get() < MAX_PAGES_TO_SEARCH) {
            String currentUrl;
            try {
                currentUrl = pagesToVisit.take();
                if (pagesVisited.containsKey(currentUrl)) {
                    continue;
                }
            } catch (InterruptedException e) {
                log.error("Error occurred while getting the next URL to visit", e);
                continue;
            }
            String finalCurrentUrl = currentUrl;
            executor.execute(() -> {
                if (processedUrlsCounter.get() > MAX_PAGES_TO_SEARCH) {
                    executor.shutdown();
                    return;
                }
                try {
                    Document doc = Jsoup.connect(finalCurrentUrl).timeout(URL_TIMEOUT_MS).userAgent(USER_AGENT).get();
                    Elements linksOnPage = doc.select("a[href]");
                    log.info("Found (" + linksOnPage.size() + ") links on " + finalCurrentUrl);
                    for (Element link : linksOnPage) {
                        pagesToVisit.offer(link.absUrl("href"));
                    }
                } catch (IOException e) {
                    log.error("Error occurred while connecting to the page: " + finalCurrentUrl);
                } finally {
                    pagesVisited.put(finalCurrentUrl, true);
                    processedUrlsCounter.incrementAndGet();
                }
            });
        }
        if (!executor.isShutdown()) {
            executor.shutdown();
        }
        try {
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Error occurred while waiting for the threads to finish", e);
        }
        log.info("\nDone Visited " + pagesVisited.size() + " web page(s)");
    }

    public static void main(String[] args) {
        String startingUrl = "https://www.sme.sk/";
        App crawler = new App();
        crawler.search(startingUrl);
    }
}