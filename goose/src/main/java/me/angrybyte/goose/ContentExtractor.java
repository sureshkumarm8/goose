/**
 * Licensed to Gravity.com under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  Gravity.com licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package me.angrybyte.goose;

import android.util.Log;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.select.Selector;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import cz.msebera.android.httpclient.client.HttpClient;
import me.angrybyte.goose.apache.HashUtils;
import me.angrybyte.goose.apache.StringEscapeUtils;
import me.angrybyte.goose.cleaners.DefaultDocumentCleaner;
import me.angrybyte.goose.cleaners.DocumentCleaner;
import me.angrybyte.goose.images.BestImageGuesser;
import me.angrybyte.goose.images.ImageExtractor;
import me.angrybyte.goose.network.HtmlFetcher;
import me.angrybyte.goose.network.MaxBytesException;
import me.angrybyte.goose.network.NotHtmlException;
import me.angrybyte.goose.outputformatters.DefaultOutputFormatter;
import me.angrybyte.goose.outputformatters.OutputFormatter;
import me.angrybyte.goose.texthelpers.ReplaceSequence;
import me.angrybyte.goose.texthelpers.StopWords;
import me.angrybyte.goose.texthelpers.StringReplacement;
import me.angrybyte.goose.texthelpers.StringSplitter;
import me.angrybyte.goose.texthelpers.WordStats;
import me.angrybyte.goose.texthelpers.string;

/**
 * User: jim plush Date: 12/16/10 a lot of work in this class is based on Arc90's readability code that does content extraction in JS I wasn't able to find a
 * good server side codebase to acheive the same so I started with their base ideas and then built additional metrics on top of it such as looking for clusters
 * of english stopwords. Gravity was doing 30+ million links per day with this codebase across a series of crawling servers for a project and it held up well.
 * Our current port is slightly different than this one but I'm working to align them so the goose project gets the love as we continue to move forward.
 * <p/>
 * Cougar: God dammit, Mustang! This is Ghost Rider 117. This bogey is all over me. He's got missile lock on me. Do I have permission to fire? Stinger: Do not
 * fire until fired upon...
 */

public class ContentExtractor {

    private static final String TAG = ContentExtractor.class.getSimpleName();

    private static final StringReplacement MOTLEY_REPLACEMENT = StringReplacement.compile("&#65533;", string.empty);

    private static final StringReplacement ESCAPED_FRAGMENT_REPLACEMENT = StringReplacement.compile("#!", "?_escaped_fragment_=");

    private static final ReplaceSequence TITLE_REPLACEMENTS = ReplaceSequence.create("&raquo;").append("»");
    private static final StringSplitter PIPE_SPLITTER = new StringSplitter("\\|");
    private static final StringSplitter DASH_SPLITTER = new StringSplitter(" - ");
    private static final StringSplitter ARROWS_SPLITTER = new StringSplitter("»");
    private static final StringSplitter COLON_SPLITTER = new StringSplitter(":");
    private static final StringSplitter SPACE_SPLITTER = new StringSplitter(" ");

    private static final Set<String> NO_STRINGS = new HashSet<>(0);
    private static final String A_REL_TAG_SELECTOR = "a[rel=tag], a[href*=/tag/]";

    private Configuration config;

    // sets the default cleaner class to prep the HTML for parsing
    private DocumentCleaner documentCleaner;
    // the MD5 of the URL we're currently parsing, used to references the images we download to the url so we
    // can more easily clean up resources when we're done with the page.
    private String linkHash;
    // once we have our topNode then we want to format that guy for output to the user
    private OutputFormatter outputFormatter;
    private ImageExtractor imageExtractor;

    /**
     * overloaded to accept a custom configuration object
     */
    public ContentExtractor(Configuration config) {
        this.config = config;
    }

    /**
     * @param urlToCrawl The url you want to extract the text from
     * @param html If you already have the raw html handy you can pass it here to avoid a network call
     */
    public Article extractContent(String urlToCrawl, String html) {
        return performExtraction(urlToCrawl, html);
    }

    /**
     * @param urlToCrawl The url you want to extract the text from, makes a network call
     */
    public Article extractContent(String urlToCrawl) {
        return performExtraction(urlToCrawl, null);
    }

    private Article performExtraction(String urlToCrawl, String rawHtml) {
        urlToCrawl = getUrlToCrawl(urlToCrawl);
        try {
            new URL(urlToCrawl);
            linkHash = HashUtils.md5(urlToCrawl);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL Passed in: " + urlToCrawl, e);
        }

        ParseWrapper parseWrapper = new ParseWrapper();
        Article article = null;
        try {
            if (rawHtml == null) {
                rawHtml = HtmlFetcher.getHtml(urlToCrawl);
            }

            article = new Article();

            article.setRawHtml(rawHtml);

            Document doc = parseWrapper.parse(rawHtml, urlToCrawl);

            // before we cleanse, provide consumers with an opportunity to extract the publish date
            article.setPublishDate(config.getPublishDateExtractor().extract(doc));

            // now allow for any additional data to be extracted
            article.setAdditionalData(config.getAdditionalDataExtractor().extract(doc));

            // grab the text nodes of any <a ... rel="tag">Tag Name</a> elements
            article.setTags(extractTags(doc));

            // now perform a nice deep cleansing
            DocumentCleaner documentCleaner = getDocCleaner();
            doc = documentCleaner.clean(doc);

            article.setTitle(getTitle(doc));
            article.setMetaDescription(getMetaDescription(doc));
            article.setMetaKeywords(getMetaKeywords(doc));
            article.setCanonicalLink(getCanonicalLink(doc, urlToCrawl));
            article.setDomain(article.getCanonicalLink());

            // extract the content of the article
            article.setTopNode(calculateBestNodeBasedOnClustering(doc));

            if (article.getTopNode() != null) {

                // extract any movie embeds out from our main article content
                article.setMovies(extractVideos(article.getTopNode()));

                if (config.isEnableImageFetching()) {
                    HttpClient httpClient = HtmlFetcher.getHttpClient();
                    imageExtractor = getImageExtractor(httpClient, urlToCrawl);
                    article.setTopImage(imageExtractor.getBestImage(doc, article.getTopNode()));
                }

                // grab siblings and remove high link density elements
                cleanupNode(article.getTopNode());
                outputFormatter = getOutputFormatter();
                article.setCleanedArticleText(outputFormatter.getFormattedText(article.getTopNode()));
            }

            // cleans up all the temp images that we've downloaded
            releaseResources();
        } catch (MaxBytesException e) {
            Log.e(TAG, e.toString(), e);
        } catch (NotHtmlException e) {
            Log.e(TAG, "URL: " + urlToCrawl + " did not contain valid HTML to parse, exiting. " + e.toString());
        } catch (Exception e) {
            Log.e(TAG, "General Exception occurred on url: " + urlToCrawl + " " + e.toString());
        }

        return article;
    }

    private Set<String> extractTags(Element node) {
        if (node.children().size() == 0) return NO_STRINGS;

        Elements elements = Selector.select(A_REL_TAG_SELECTOR, node);
        if (elements.size() == 0) return NO_STRINGS;

        Set<String> tags = new HashSet<String>(elements.size());
        for (Element el : elements) {
            String tag = el.text();
            if (!string.isNullOrEmpty(tag)) tags.add(tag);
        }

        return tags;
    }

    // used for gawker type ajax sites with pound sites
    private String getUrlToCrawl(String urlToCrawl) {
        String finalURL;
        if (urlToCrawl.contains("#!")) {
            finalURL = ESCAPED_FRAGMENT_REPLACEMENT.replaceAll(urlToCrawl);
        } else {
            finalURL = urlToCrawl;
        }

        Log.d(TAG, "Goose Extraction: " + finalURL);
        return finalURL;
    }

    private OutputFormatter getOutputFormatter() {
        if (outputFormatter == null) {
            return new DefaultOutputFormatter();
        } else {
            return outputFormatter;
        }

    }

    private ImageExtractor getImageExtractor(HttpClient httpClient, String urlToCrawl) {
        if (imageExtractor == null) {
            return new BestImageGuesser(config, httpClient, urlToCrawl);
        } else {
            return imageExtractor;
        }

    }

    private DocumentCleaner getDocCleaner() {
        if (this.documentCleaner == null) {
            this.documentCleaner = new DefaultDocumentCleaner();
        }
        return this.documentCleaner;
    }

    /**
     * Attempts to grab titles from the html pages, lots of sites use different delimiters for titles so we'll try and do our best guess.
     */
    private String getTitle(Document doc) {
        String title = string.empty;

        try {

            Elements titleElem = doc.getElementsByTag("title");
            if (titleElem == null || titleElem.isEmpty()) return string.empty;

            String titleText = titleElem.first().text();

            if (string.isNullOrEmpty(titleText)) return string.empty;

            boolean usedDelimeter = false;

            if (titleText.contains("|")) {
                titleText = doTitleSplits(titleText, PIPE_SPLITTER);
                usedDelimeter = true;
            }

            if (!usedDelimeter && titleText.contains("-")) {
                titleText = doTitleSplits(titleText, DASH_SPLITTER);
                usedDelimeter = true;
            }
            if (!usedDelimeter && titleText.contains("»")) {
                titleText = doTitleSplits(titleText, ARROWS_SPLITTER);
                usedDelimeter = true;
            }

            if (!usedDelimeter && titleText.contains(":")) {
                titleText = doTitleSplits(titleText, COLON_SPLITTER);
            }

            // encode unicode charz
            title = StringEscapeUtils.escapeHtml(titleText);

            // todo this is a hack until I can fix this.. weird motely crue error with
            // http://money.cnn.com/2010/10/25/news/companies/motley_crue_bp.fortune/index.htm?section=money_latest
            title = MOTLEY_REPLACEMENT.replaceAll(title);
        } catch (NullPointerException e) {
            Log.e(TAG, e.toString());
        }
        return title;

    }

    /**
     * Based on a delimiter in the title take the longest piece or do some custom logic based on the site
     */
    private String doTitleSplits(String title, StringSplitter splitter) {
        int largetTextLen = 0;
        int largeTextIndex = 0;

        String[] titlePieces = splitter.split(title);

        // take the largest split
        for (int i = 0; i < titlePieces.length; i++) {
            String current = titlePieces[i];
            if (current.length() > largetTextLen) {
                largetTextLen = current.length();
                largeTextIndex = i;
            }
        }

        return TITLE_REPLACEMENTS.replaceAll(titlePieces[largeTextIndex]).trim();
    }

    private String getMetaContent(Document doc, String metaName) {
        Elements meta = doc.select(metaName);
        if (meta.size() > 0) {
            String content = meta.first().attr("content");
            return string.isNullOrEmpty(content) ? string.empty : content.trim();
        }
        return string.empty;
    }

    /**
     * If the article has meta description set in the source, use that
     */
    private String getMetaDescription(Document doc) {
        return getMetaContent(doc, "meta[name=description]");
    }

    /**
     * If the article has meta keywords set in the source, use that
     */
    private String getMetaKeywords(Document doc) {
        return getMetaContent(doc, "meta[name=keywords]");
    }

    /**
     * If the article has meta canonical link set in the url
     */
    private String getCanonicalLink(Document doc, String baseUrl) {
        Elements meta = doc.select("link[rel=canonical]");
        if (meta.size() > 0) {
            String href = meta.first().attr("href");
            return string.isNullOrEmpty(href) ? string.empty : href.trim();
        } else {
            return baseUrl;
        }
    }

    private String getDomain(String canonicalLink) {
        try {
            return new URL(canonicalLink).getHost();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * We're going to start looking for where the clusters of paragraphs are. We'll score a cluster based on the number of stopwords and the number of
     * consecutive paragraphs together, which should form the cluster of text that this node is around also store on how high up the paragraphs are, comments
     * are usually at the bottom and should get a lower score
     */
    private Element calculateBestNodeBasedOnClustering(Document doc) {
        Element topNode = null;

        // grab all the paragraph elements on the page to start to inspect the likely hood of them being good peeps
        ArrayList<Element> nodesToCheck = getNodesToCheck(doc);

        double startingBoost = 1.0;
        int cnt = 0;
        int i = 0;

        // holds all the parents of the nodes we're checking
        Set<Element> parentNodes = new HashSet<Element>();

        ArrayList<Element> nodesWithText = new ArrayList<Element>();

        for (Element node : nodesToCheck) {

            String nodeText = node.text();
            WordStats wordStats = StopWords.getStopWordCount(nodeText);
            boolean highLinkDensity = isHighLinkDensity(node);

            if (wordStats.getStopWordCount() > 2 && !highLinkDensity) {

                nodesWithText.add(node);
            }

        }

        int numberOfNodes = nodesWithText.size();
        int negativeScoring = 0; // we shouldn't give more negatives than positives
        // we want to give the last 20% of nodes negative scores in case they're comments
        double bottomNodesForNegativeScore = (float) numberOfNodes * 0.25;

        Log.d(TAG, "About to inspect num of nodes with text: " + numberOfNodes);

        for (Element node : nodesWithText) {

            // add parents and grandparents to scoring
            // only add boost to the middle paragraphs, top and bottom is usually jankz city
            // so basically what we're doing is giving boost scores to paragraphs that appear higher up in the dom
            // and giving lower, even negative scores to those who appear lower which could be commenty stuff

            float boostScore = 0;

            if (isOkToBoost(node)) {
                if (cnt >= 0) {
                    boostScore = (float) ((1.0 / startingBoost) * 50);
                    startingBoost++;
                }
            }

            // check for negative node values
            if (numberOfNodes > 15) {
                if ((numberOfNodes - i) <= bottomNodesForNegativeScore) {
                    float booster = (float) bottomNodesForNegativeScore - (float) (numberOfNodes - i);
                    boostScore = -(float) Math.pow(booster, (float) 2);

                    // we don't want to score too highly on the negative side.
                    float negscore = Math.abs(boostScore) + negativeScoring;
                    if (negscore > 40) {
                        boostScore = 5;
                    }
                }
            }

            Log.d(TAG, "Location Boost Score: " + boostScore + " on interation: " + i + "' id='" + node.parent().id() + "' class='" + node.parent());

            String nodeText = node.text();
            WordStats wordStats = StopWords.getStopWordCount(nodeText);
            int upscore = (int) (wordStats.getStopWordCount() + boostScore);
            updateScore(node.parent(), upscore);
            updateScore(node.parent().parent(), upscore / 2);
            updateNodeCount(node.parent(), 1);
            updateNodeCount(node.parent().parent(), 1);

            if (!parentNodes.contains(node.parent())) {
                parentNodes.add(node.parent());
            }

            if (!parentNodes.contains(node.parent().parent())) {
                parentNodes.add(node.parent().parent());
            }

            cnt++;
            i++;
        }

        // now let's find the parent node who scored the highest

        int topNodeScore = 0;
        for (Element e : parentNodes) {

            Log.d(TAG, "ParentNode: score='" + e.attr("gravityScore") + "' nodeCount='" + e.attr("gravityNodes") + "' id='" + e.id() + "' class='" + e.attr
                    ("class") + "' ");

            int score = getScore(e);
            if (score > topNodeScore) {
                topNode = e;
                topNodeScore = score;
            }

            if (topNode == null) {
                topNode = e;
            }
        }

        // if (topNode == null) {
        //     Log.d(TAG, "ARTICLE NOT ABLE TO BE EXTRACTED!, WE HAZ FAILED YOU LORD VADAR");
        // } else {
        //     String logText;
        //     String targetText = "";
        //     Element topPara = topNode.getElementsByTag("p").first();
        //     if (topPara == null) {
        //         topNode.text();
        //     } else {
        //         topPara.text();
        //     }
        //
        //     if (targetText.length() >= 51) {
        //         logText = targetText.substring(0, 50);
        //     } else {
        //         logText = targetText;
        //     }
        //     Log.d(TAG, "TOPNODE TEXT: " + logText.trim());
        //     Log.d(TAG, "Our TOPNODE: score='" + topNode.attr("gravityScore") + "' nodeCount='" + topNode.attr("gravityNodes") + "' id='" + topNode.id() +
        //             "' class='" + topNode.attr("class") + "' ");
        // }

        return topNode;

    }

    /**
     * Returns a list of nodes we want to search on like paragraphs and tables
     */
    private ArrayList<Element> getNodesToCheck(Document doc) {
        ArrayList<Element> nodesToCheck = new ArrayList<Element>();

        nodesToCheck.addAll(doc.getElementsByTag("p"));
        nodesToCheck.addAll(doc.getElementsByTag("pre"));
        nodesToCheck.addAll(doc.getElementsByTag("td"));
        return nodesToCheck;

    }

    /**
     * Checks the density of links within a node, is there not much text and most of it contains linky shit? if so it's no good
     */
    private static boolean isHighLinkDensity(Element e) {

        Elements links = e.getElementsByTag("a");

        if (links.size() == 0) {
            return false;
        }

        String text = e.text().trim();
        String[] words = SPACE_SPLITTER.split(text);
        float numberOfWords = words.length;

        // let's loop through all the links and calculate the number of words that make up the links
        StringBuilder sb = new StringBuilder();
        for (Element link : links) {
            sb.append(link.text());
        }
        String linkText = sb.toString();
        String[] linkWords = SPACE_SPLITTER.split(linkText);
        float numberOfLinkWords = linkWords.length;

        float numberOfLinks = links.size();

        float linkDivisor = numberOfLinkWords / numberOfWords;
        float score = linkDivisor * numberOfLinks;

        return score > 1;
    }

    /**
     * alot of times the first paragraph might be the caption under an image so we'll want to make sure if we're going to boost a parent node that it should be
     * connected to other paragraphs, at least for the first n paragraphs so we'll want to make sure that the next sibling is a paragraph and has at least some
     * substatial weight to it
     *
     * @param node
     *
     * @return
     */
    private boolean isOkToBoost(Element node) {

        int stepsAway = 0;

        Element sibling = node.nextElementSibling();
        while (sibling != null) {

            if (sibling.tagName().equals("p")) {
                if (stepsAway >= 3) {
                    Log.d(TAG, "Next paragraph is too far away, not boosting");
                    return false;
                }

                String paraText = sibling.text();
                WordStats wordStats = StopWords.getStopWordCount(paraText);
                if (wordStats.getStopWordCount() > 5) {
                    Log.d(TAG, "We're gonna boost this node, seems contenty");
                    return true;
                }

            }

            // increase how far away the next paragraph is from this node
            stepsAway++;

            sibling = sibling.nextElementSibling();
        }

        return false;
    }

    /**
     * Adds a score to the gravityScore Attribute we put on divs we'll get the current score then add the score we're passing in to the current
     *
     * @param addToScore - the score to add to the node
     */
    private void updateScore(Element node, int addToScore) {
        int currentScore;
        try {
            String scoreString = node.attr("gravityScore");
            currentScore = string.isNullOrEmpty(scoreString) ? 0 : Integer.parseInt(scoreString);
        } catch (NumberFormatException e) {
            currentScore = 0;
        }
        int newScore = currentScore + addToScore;
        node.attr("gravityScore", Integer.toString(newScore));

    }

    /**
     * Stores how many decent nodes are under a parent node
     */
    private void updateNodeCount(Element node, int addToCount) {
        int currentScore;
        try {
            String countString = node.attr("gravityNodes");
            currentScore = string.isNullOrEmpty(countString) ? 0 : Integer.parseInt(countString);
        } catch (NumberFormatException e) {
            currentScore = 0;
        }
        int newScore = currentScore + addToCount;
        node.attr("gravityNodes", Integer.toString(newScore));

    }

    /**
     * Returns the gravityScore as an integer from this node
     */
    private int getScore(Element node) {
        if (node == null) return 0;
        try {
            String grvScoreString = node.attr("gravityScore");
            if (string.isNullOrEmpty(grvScoreString)) return 0;
            return Integer.parseInt(grvScoreString);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Pulls out videos we like
     */
    private ArrayList<Element> extractVideos(Element node) {
        ArrayList<Element> candidates = new ArrayList<Element>();
        ArrayList<Element> goodMovies = new ArrayList<Element>();
        try {

            Elements embeds = node.parent().getElementsByTag("embed");
            for (Element el : embeds) {
                candidates.add(el);
            }
            Elements objects = node.parent().getElementsByTag("object");
            for (Element el : objects) {
                candidates.add(el);
            }
            Log.d(TAG, "extractVideos: Starting to extract videos. Found: " + candidates.size());

            for (Element el : candidates) {

                Attributes attrs = el.attributes();

                for (Attribute a : attrs) {
                    try {
                        Log.d(TAG, a.getKey() + " : " + a.getValue());
                        if ((a.getValue().contains("youtube") || a.getValue().contains("vimeo")) && a.getKey().equals("src")) {
                            Log.d(TAG, "Found video... setting");
                            Log.d(TAG, "This page has a video!: " + a.getValue());
                            goodMovies.add(el);

                        }
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                        e.printStackTrace();
                    }
                }

            }
        } catch (Exception e) {
            Log.e(TAG, e.toString(), e);
        }

        Log.d(TAG, "extractVideos: done looking for videos");
        return goodMovies;
    }

    /**
     * Remove any divs that looks like non-content, clusters of links, or paras with no gusto
     */
    private Element cleanupNode(Element node) {
        Log.d(TAG, "Starting cleanup Node");

        node = addSiblings(node);

        Elements nodes = node.children();
        for (Element e : nodes) {
            if (e.tagName().equals("p")) {
                continue;
            }
            Log.d(TAG, "CLEANUP  NODE: " + e.id() + " class: " + e.attr("class"));
            boolean highLinkDensity = isHighLinkDensity(e);
            if (highLinkDensity) {
                Log.d(TAG, "REMOVING  NODE FOR LINK DENSITY: " + e.id() + " class: " + e.attr("class"));
                e.remove();
                continue;
            }

            // now check for word density
            // grab all the paragraphs in the children and remove ones that are too small to matter
            Elements subParagraphs = e.getElementsByTag("p");

            for (Element p : subParagraphs) {
                if (p.text().length() < 25) {
                    p.remove();
                }
            }

            // now that we've removed shorty paragraphs let's make sure to exclude any first paragraphs that don't have paras as
            // their next siblings to avoid getting img bylines
            // first let's remove any element that now doesn't have any p tags at all
            Elements subParagraphs2 = e.getElementsByTag("p");
            if (subParagraphs2.size() == 0 && !e.tagName().equals("td")) {
                Log.d(TAG, "Removing node because it doesn't have any paragraphs");
                e.remove();
                continue;
            }

            //if this node has a decent enough gravityScore we should keep it as well, might be content
            int topNodeScore = getScore(node);
            int currentNodeScore = getScore(e);
            float thresholdScore = (float) (topNodeScore * .08);
            Log.d(TAG, "topNodeScore: " + topNodeScore + " currentNodeScore: " + currentNodeScore + " threshold: " + thresholdScore);
            if (currentNodeScore < thresholdScore) {
                if (!e.tagName().equals("td")) {
                    Log.d(TAG, "Removing node due to low threshold score");
                    e.remove();
                } else {
                    Log.d(TAG, "Not removing TD node");
                }
            }

        }

        return node;
    }

    /**
     * Adds any siblings that may have a decent score to this node
     */
    private Element addSiblings(Element node) {
        Log.d(TAG, "Starting to add siblings");
        int baselineScoreForSiblingParagraphs = getBaselineScoreForSiblings(node);

        Element currentSibling = node.previousElementSibling();
        while (currentSibling != null) {
            Log.d(TAG, "SIBLING CHECK: " + debugNode(currentSibling));

            if (currentSibling.tagName().equals("p")) {

                node.child(0).before(currentSibling.outerHtml());
                currentSibling = currentSibling.previousElementSibling();
                continue;
            }

            // check for a paragraph embedded in a containing element
            int insertedSiblings = 0;
            Elements potentialParagraphs = currentSibling.getElementsByTag("p");
            if (potentialParagraphs.first() == null) {
                currentSibling = currentSibling.previousElementSibling();
                continue;
            }
            for (Element firstParagraph : potentialParagraphs) {
                WordStats wordStats = StopWords.getStopWordCount(firstParagraph.text());

                int paragraphScore = wordStats.getStopWordCount();

                if ((float) (baselineScoreForSiblingParagraphs * .30) < paragraphScore) {
                    Log.d(TAG, "This node looks like a good sibling, adding it");
                    node.child(insertedSiblings).before("<p>" + firstParagraph.text() + "<p>");
                    insertedSiblings++;
                }

            }

            currentSibling = currentSibling.previousElementSibling();
        }
        return node;

    }

    /**
     * We could have long articles that have tons of paragraphs so if we tried to calculate the base score against the total text score of those paragraphs it
     * would be unfair. So we need to normalize the score based on the average scoring of the paragraphs within the top node. For example if our total score of
     * 10 paragraphs was 1000 but each had an average value of 100 then 100 should be our base.
     */
    private int getBaselineScoreForSiblings(Element topNode) {
        int base = 100000;
        int numberOfParagraphs = 0;
        int scoreOfParagraphs = 0;

        Elements nodesToCheck = topNode.getElementsByTag("p");
        for (Element node : nodesToCheck) {
            String nodeText = node.text();
            WordStats wordStats = StopWords.getStopWordCount(nodeText);
            boolean highLinkDensity = isHighLinkDensity(node);

            if (wordStats.getStopWordCount() > 2 && !highLinkDensity) {
                numberOfParagraphs++;
                scoreOfParagraphs += wordStats.getStopWordCount();
            }
        }

        if (numberOfParagraphs > 0) {
            base = scoreOfParagraphs / numberOfParagraphs;
            Log.d(TAG, "The base score for siblings to beat is: " + base + " NumOfParas: " + numberOfParagraphs + " scoreOfAll: " + scoreOfParagraphs);
        }

        return base;
    }

    private String debugNode(Element e) {
        return "GravityScore: '" +
                e.attr("gravityScore") +
                "' paraNodeCount: '" +
                e.attr("gravityNodes") +
                "' nodeId: '" +
                e.id() +
                "' className: '" +
                e.attr("class");
    }

    /**
     * Cleans up any temp files we have laying around like temp images removes any image in the temp dir that starts with the linkHash of the url we parsed
     */
    public void releaseResources() {
        Log.d(TAG, "STARTING TO RELEASE ALL RESOURCES");
        File dir = new File(config.getCacheDirectory());
        String[] children = dir.list();

        if (children == null) {
            Log.d(TAG, "No Temp images found for linkHash: " + this.linkHash);
        } else {
            for (String filename : children) {
                if (filename.startsWith(this.linkHash)) {
                    File f = new File(dir.getAbsolutePath() + "/" + filename);
                    if (!f.delete()) {
                        Log.e(TAG, "Unable to remove temp file: " + filename);
                    }
                }
            }
        }
    }

}
