package com.sdl.dxa.modelservice.service.api.navigation.dynamic;

import com.sdl.dxa.api.datamodel.model.SitemapItemModelData;
import com.sdl.dxa.api.datamodel.model.TaxonomyNodeModelData;
import com.sdl.dxa.common.dto.DepthCounter;
import com.sdl.dxa.common.dto.SitemapRequestDto;
import com.sdl.dxa.common.util.PathUtils;
import com.sdl.web.api.dynamic.taxonomies.WebTaxonomyFactory;
import com.sdl.webapp.common.api.navigation.NavigationFilter;
import com.sdl.webapp.common.api.navigation.TaxonomyUrisHolder;
import com.sdl.webapp.common.controller.exception.BadRequestException;
import com.sdl.webapp.common.util.TcmUtils;
import com.tridion.ItemTypes;
import com.tridion.broker.StorageException;
import com.tridion.meta.PageMeta;
import com.tridion.meta.PageMetaFactory;
import com.tridion.taxonomies.Keyword;
import com.tridion.taxonomies.TaxonomyRelationManager;
import com.tridion.taxonomies.filters.DepthFilter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Sets.difference;
import static com.google.common.collect.Sets.newHashSet;
import static com.sdl.dxa.common.util.PathUtils.isIndexPath;
import static com.sdl.dxa.common.util.PathUtils.isWithSequenceDigits;
import static com.sdl.dxa.common.util.PathUtils.removeSequenceFromPageTitle;
import static com.sdl.dxa.common.util.PathUtils.stripDefaultExtension;
import static com.sdl.webapp.common.api.navigation.TaxonomyUrisHolder.parse;
import static com.sdl.webapp.common.util.TcmUtils.Taxonomies.SitemapItemType.KEYWORD;
import static com.sdl.webapp.common.util.TcmUtils.Taxonomies.SitemapItemType.PAGE;
import static com.sdl.webapp.common.util.TcmUtils.Taxonomies.getTaxonomySitemapIdentifier;

@Slf4j
@Service
public class DynamicNavigationProviderImpl implements DynamicNavigationProvider, OnDemandNavigationProvider {

    private final WebTaxonomyFactory taxonomyFactory;

    private final TaxonomyRelationManager relationManager;

    @Value("${dxa.tridion.navigation.taxonomy.marker}")
    protected String taxonomyNavigationMarker;

    @Value("${dxa.tridion.navigation.taxonomy.type.taxonomyNode}")
    protected String sitemapItemTypeTaxonomyNode;

    @Value("${dxa.tridion.navigation.taxonomy.type.structureGroup}")
    protected String sitemapItemTypeStructureGroup;

    @Value("${dxa.tridion.navigation.taxonomy.type.page}")
    protected String sitemapItemTypePage;

    @Autowired
    public DynamicNavigationProviderImpl(WebTaxonomyFactory taxonomyFactory, TaxonomyRelationManager relationManager) {
        this.taxonomyFactory = taxonomyFactory;
        this.relationManager = relationManager;
    }

    @Override
    public Optional<SitemapItemModelData> getNavigationModel(@NotNull SitemapRequestDto requestDto) {
        List<Keyword> roots = getTaxonomyRoots(requestDto, keyword -> keyword.getKeywordName().contains(taxonomyNavigationMarker));
        if (roots.isEmpty()) {
            log.error("No Navigation Taxonomy Found in Localization [{}]. Ensure a Taxonomy with '{}' in its title is published",
                    requestDto.getLocalizationId(), taxonomyNavigationMarker);
            return Optional.empty();
        }

        Keyword rootTaxonomy = roots.get(0);
        log.debug("Resolved Navigation Taxonomy {} for request {}", rootTaxonomy, requestDto);

        return Optional.of(createTaxonomyNode(rootTaxonomy, requestDto));
    }

    @Override
    @NotNull
    public Collection<SitemapItemModelData> getNavigationSubtree(@NotNull SitemapRequestDto requestDto) {
        log.trace("SitemapRequestDto {}", requestDto);

        if (isNullOrEmpty(requestDto.getSitemapId())) {
            log.trace("Sitemap ID is empty, expanding all taxonomy roots");

            return getTaxonomyRoots(requestDto, keyword -> true).stream()
                    .map(keyword -> createTaxonomyNode(keyword, requestDto))
                    .collect(Collectors.toList());
        }

        TaxonomyUrisHolder info = parse(requestDto.getSitemapId(), requestDto.getLocalizationId());
        if (info == null) {
            throw new BadRequestException(String.format("SitemapID %s is wrong for Taxonomy navigation", requestDto.getSitemapId()));
        }

        log.debug("Sitemap ID is known: {}", info);

        if (requestDto.getNavigationFilter().isWithAncestors()) {
            log.trace("Filter with ancestors, expanding ancestors");
            Optional<SitemapItemModelData> taxonomy = taxonomyWithAncestors(info, requestDto);
            if (taxonomy.isPresent()) {
                log.debug("Found taxonomy {} for request {}", taxonomy.get(), requestDto);
                return Collections.singletonList(taxonomy.get());
            }
        }

        if (requestDto.getNavigationFilter().getDescendantLevels() != 0 && !info.isPage()) {
            log.trace("Filter with descendants, expanding descendants");
            return expandDescendants(info, requestDto);
        }

        log.trace("Filter is not specific, doing nothing");
        throw new BadRequestException(String.format("Request %s is not specific, doing nothing", requestDto));
    }

    @NotNull
    private List<Keyword> getTaxonomyRoots(SitemapRequestDto requestDto, Predicate<Keyword> filter) {
        NavigationFilter navigationFilter = requestDto.getNavigationFilter();
        final int maximumDepth = navigationFilter.getDescendantLevels() > 0 ?
                navigationFilter.getDescendantLevels() - 1 : navigationFilter.getDescendantLevels();
        DepthFilter depthFilter = new DepthFilter(maximumDepth, DepthFilter.FILTER_DOWN);

        return Arrays.stream(taxonomyFactory.getTaxonomies(TcmUtils.buildPublicationTcmUri(requestDto.getLocalizationId())))
                .distinct()
                .map(taxonomy -> taxonomyFactory.getTaxonomyKeywords(taxonomy, depthFilter))
                .filter(filter)
                .collect(Collectors.toList());
    }

    @NonNull
    private Optional<SitemapItemModelData> taxonomyWithAncestors(@NonNull TaxonomyUrisHolder uris, @NotNull SitemapRequestDto requestDto) {
        if (uris.isTaxonomyOnly()) {
            throw new IllegalArgumentException(String.format("URIs %s is not a page nor keyword, can't expand ancestors, request %s", uris, requestDto));
        }

        Optional<SitemapItemModelData> taxonomy = uris.isPage() ?
                expandAncestorsForPage(uris, requestDto) : expandAncestorsForKeyword(uris, requestDto);

        if (taxonomy.isPresent()) {
            if (requestDto.getNavigationFilter().getDescendantLevels() != 0) {
                addDescendantsToTaxonomy(taxonomy.get(), requestDto);
            }

            return taxonomy;
        }

        log.debug("Taxonomy was not found, uris {}, request {}", uris, requestDto);
        return Optional.empty();
    }

    private void addDescendantsToTaxonomy(@NonNull SitemapItemModelData taxonomy, @NotNull SitemapRequestDto requestDto) {
        taxonomy.getItems().stream()
                .filter(TaxonomyNodeModelData.class::isInstance)
                .forEach(child -> addDescendantsToTaxonomy(child, requestDto));

        Set<SitemapItemModelData> additionalChildren = new LinkedHashSet<>(
                expandDescendants(parse(taxonomy.getId(), requestDto.getLocalizationId()), requestDto));

        for (SitemapItemModelData child : difference(additionalChildren, newHashSet(taxonomy.getItems()))) {
            taxonomy.addItem(child);
        }
    }

    /**
     * Expands descendants for a given {@link SitemapItemModelData}.
     * We don't expect two equals items at the same level, so the method returns {@link Set}.
     *
     * @param uris       information about URI of current item
     * @param requestDto current request data
     * @return a set of descendants of item with passed URI
     */
    @NotNull
    private Set<SitemapItemModelData> expandDescendants(TaxonomyUrisHolder uris, @NotNull SitemapRequestDto requestDto) {
        if (uris.isPage()) {
            log.debug("Page cannot have descendants, return emptyList, uris = ", uris);
            return Collections.emptySet();
        }

        Keyword keyword = taxonomyFactory.getTaxonomyKeywords(uris.getTaxonomyUri(),
                new DepthFilter(requestDto.getNavigationFilter().getDescendantLevels(), DepthFilter.FILTER_DOWN), uris.getKeywordUri());

        if (keyword == null) {
            log.warn("Keyword '{}' in Taxonomy '{}' was not found.", uris.getKeywordUri(), uris.getTaxonomyUri());
            return Collections.emptySet();
        }

        return createTaxonomyNode(keyword, requestDto).getItems();
    }

    @NotNull
    private Optional<SitemapItemModelData> expandAncestorsForPage(@NotNull TaxonomyUrisHolder uris, @NotNull SitemapRequestDto requestDto) {
        List<SitemapItemModelData> nodes = collectAncestorsForPage(uris, requestDto);

        if (nodes.isEmpty()) {
            return Optional.empty();
        }

        Iterator<SitemapItemModelData> iterator = nodes.iterator();
        SitemapItemModelData mergedNode = iterator.next();
        while (iterator.hasNext()) {
            mergeSubtrees(iterator.next(), mergedNode);
        }

        return Optional.of(mergedNode);
    }

    private void mergeSubtrees(@NonNull SitemapItemModelData sourceTree, @NonNull SitemapItemModelData targetTree) {
        for (final SitemapItemModelData sourceLeaf : sourceTree.getItems()) {
            Optional<SitemapItemModelData> targetLeaf = targetTree.getItems().stream()
                    .filter(input -> Objects.equals(sourceLeaf.getId(), input.getId()))
                    .findFirst();

            if (!targetLeaf.isPresent()) {
                targetTree.addItem(sourceLeaf);
            } else {
                mergeSubtrees(sourceLeaf, targetLeaf.get());
            }
        }
    }

    /**
     * Ancestors for a page is a list of same ROOT node with different children.
     * Basically, these different ROOTs (with same ID, because we are still within one taxonomy) contain
     * different children for different paths your page may be in.
     * <p>Unless other methods for descendants, this method returns {@link List} because the root Taxonomies will be the same object
     * even if page is in multiple places.</p>
     *
     * @param uris       URIs of your current context taxonomy node
     * @param requestDto current request data
     * @return a list of roots of taxonomy with different paths for items
     */
    @NotNull
    private List<SitemapItemModelData> collectAncestorsForPage(@NotNull TaxonomyUrisHolder uris, @NotNull SitemapRequestDto requestDto) {
        if (!uris.isPage()) {
            throw new IllegalArgumentException(String.format("Method for pages was called for not a page! uris: %s, request: %s", uris, requestDto));
        }

        DepthFilter depthFilter = new DepthFilter(DepthFilter.UNLIMITED_DEPTH, DepthFilter.FILTER_UP);
        Keyword[] keywords = relationManager.getTaxonomyKeywords(uris.getTaxonomyUri(), uris.getPageUri(), null, depthFilter, ItemTypes.PAGE);

        if (keywords == null || keywords.length == 0) {
            log.debug("Page {} is not classified in taxonomy {}", uris.getPageUri(), uris.getTaxonomyUri());
            return Collections.emptyList();
        }

        return Arrays.stream(keywords)
                .map(keyword -> createTaxonomyNode(keyword, requestDto.toBuilder().expandLevels(DepthCounter.UNLIMITED_DEPTH).build()))
                .collect(Collectors.toList());
    }

    /**
     * One single ancestor for a given keyword. Although same keyword may be in few places, we don't expect it due to
     * technical limitation in CME. So basically we ignore the fact that keyword may be in many places (like page) and
     * expect only a single entry. Because of that we have only one taxonomy root for Keyword's ancestors.
     *
     * @param uris       URIs of your current context taxonomy node
     * @param requestDto current request data
     * @return root of a taxonomy
     */
    @NotNull
    private Optional<SitemapItemModelData> expandAncestorsForKeyword(TaxonomyUrisHolder uris, SitemapRequestDto requestDto) {
        if (!uris.isKeyword()) {
            throw new IllegalArgumentException(String.format("Method for keywords was called for not a keyword! uris: %s, request: %s", uris, requestDto));
        }

        DepthFilter depthFilter = new DepthFilter(DepthFilter.UNLIMITED_DEPTH, DepthFilter.FILTER_UP);
        Keyword taxonomyRoot = taxonomyFactory.getTaxonomyKeywords(uris.getTaxonomyUri(), depthFilter, uris.getKeywordUri());

        if (taxonomyRoot == null) {
            log.warn("Keyword {} in taxonomy {} wasn't found", uris.getKeywordUri(), uris.getTaxonomyUri());
            return Optional.empty();
        }

        return Optional.of(createTaxonomyNode(taxonomyRoot, requestDto.toBuilder().expandLevels(DepthCounter.UNLIMITED_DEPTH).build()));
    }

    private TaxonomyNodeModelData createTaxonomyNode(@NotNull Keyword keyword, @NotNull SitemapRequestDto requestDto) {
        log.debug("Creating taxonomy node for keyword {} and request {}", keyword.getTaxonomyURI(), requestDto);
        String taxonomyId = String.valueOf(TcmUtils.getItemId(keyword.getTaxonomyURI()));

        String taxonomyNodeUrl = null;

        List<SitemapItemModelData> children = new ArrayList<>();

        if (requestDto.getExpandLevels().isNotTooDeep()) {
            keyword.getKeywordChildren().forEach(child -> children.add(createTaxonomyNode(child, requestDto.nextExpandLevel())));

            if (keyword.getReferencedContentCount() > 0 && requestDto.getNavigationFilter().getDescendantLevels() != 0) {
                List<SitemapItemModelData> pageSitemapItems = getChildrenPages(keyword, taxonomyId, requestDto);

                taxonomyNodeUrl = findIndexPageUrl(pageSitemapItems).orElse(null);
                log.trace("taxonomyNodeUrl = {}", taxonomyNodeUrl);

                children.addAll(pageSitemapItems);
            }
        }

        children.forEach(child -> child.setTitle(removeSequenceFromPageTitle(child.getTitle())));

        return createTaxonomyNodeFromKeyword(keyword, taxonomyId, taxonomyNodeUrl, new LinkedHashSet<>(children));
    }

    private List<SitemapItemModelData> getChildrenPages(@NotNull Keyword keyword, @NotNull String taxonomyId, @NotNull SitemapRequestDto requestDto) {
        log.trace("Getting SitemapItems for all classified Pages (ordered by Page Title, including sequence prefix if any), " +
                "keyword {}, taxonomyId {}, localization {}", keyword, taxonomyId, requestDto.getLocalizationId());

        List<SitemapItemModelData> items = new ArrayList<>();

        try {
            PageMetaFactory pageMetaFactory = new PageMetaFactory(requestDto.getLocalizationId());
            PageMeta[] taxonomyPages = pageMetaFactory.getTaxonomyPages(keyword, false);
            items = Arrays.stream(taxonomyPages)
                    .map(page -> createSitemapItemFromPage(page, taxonomyId))
                    .collect(Collectors.toList());
        } catch (StorageException e) {
            log.error("Error loading taxonomy pages for taxonomyId = {}, localizationId = {} and keyword {}", taxonomyId, requestDto.getLocalizationId(), keyword, e);
        }

        return items;
    }

    private Optional<String> findIndexPageUrl(@NonNull List<SitemapItemModelData> pageSitemapItems) {
        return pageSitemapItems.stream()
                .filter(input -> isIndexPath(input.getUrl()))
                .findFirst()
                .map(SitemapItemModelData::getUrl)
                .map(PathUtils::stripIndexPath);
    }

    private SitemapItemModelData createSitemapItemFromPage(PageMeta page, String taxonomyId) {
        return new SitemapItemModelData()
                .setId(getTaxonomySitemapIdentifier(taxonomyId, PAGE, String.valueOf(page.getId())))
                .setType(sitemapItemTypePage)
                .setTitle(page.getTitle())
                .setUrl(stripDefaultExtension(page.getURLPath()))
                .setVisible(isVisibleItem(page.getTitle(), page.getURLPath()))
                .setPublishedDate(new DateTime(page.getLastPublicationDate()));
    }

    private TaxonomyNodeModelData createTaxonomyNodeFromKeyword(@NotNull Keyword keyword, String taxonomyId, String taxonomyNodeUrl, Set<SitemapItemModelData> children) {
        boolean isRoot = Objects.equals(keyword.getTaxonomyURI(), keyword.getKeywordURI());
        String keywordId = String.valueOf(TcmUtils.getItemId(keyword.getTaxonomyURI()));

        return (TaxonomyNodeModelData) new TaxonomyNodeModelData()
                .setWithChildren(keyword.hasKeywordChildren() || keyword.getReferencedContentCount() > 0)
                .setDescription(keyword.getKeywordDescription())
                .setTaxonomyAbstract(keyword.isKeywordAbstract())
                .setClassifiedItemsCount(keyword.getReferencedContentCount())
                .setKey(keyword.getKeywordKey())
                .setId(isRoot ? getTaxonomySitemapIdentifier(taxonomyId) : getTaxonomySitemapIdentifier(taxonomyId, KEYWORD, keywordId))
                .setType(sitemapItemTypeTaxonomyNode)
                .setUrl(stripDefaultExtension(taxonomyNodeUrl))
                .setTitle(keyword.getKeywordName())
                .setVisible(isVisibleItem(keyword.getKeywordName(), taxonomyNodeUrl))
                .setItems(children);
    }

    private boolean isVisibleItem(String pageName, String pageUrl) {
        return isWithSequenceDigits(pageName) && !isNullOrEmpty(pageUrl);
    }

}
