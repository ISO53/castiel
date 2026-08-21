import { computed, getCurrentScope, inject, onMounted, onScopeDispose, provide, shallowRef, watch } from 'vue';
// -----------------------------------------------------------------------------
// Constants
// -----------------------------------------------------------------------------
const DEFAULT_SCROLL_EDGE_THRESHOLD = 8;
const DEFAULT_SCROLL_PREVIOUS_ITEM_PEEK = 64;
const DEFAULT_SCROLL_MARGIN = 0;
const SCROLL_EPSILON = 0.5;
const AUTOSCROLLING_TIMEOUT = 180;
const SCROLL_KEYS = new Set([
    'ArrowDown',
    'ArrowUp',
    'End',
    'Home',
    'PageDown',
    'PageUp',
    ' ',
]);
const EMPTY_SCROLLABLE = { start: false, end: false };
const EMPTY_VISIBLE_IDS = [];
const EMPTY_VISIBILITY = {
    currentAnchorId: null,
    visibleMessageIds: EMPTY_VISIBLE_IDS,
};
function scrollableEqual(a, b) {
    return a.start === b.start && a.end === b.end;
}
function visibilityEqual(a, b) {
    if (a.currentAnchorId !== b.currentAnchorId
        || a.visibleMessageIds.length !== b.visibleMessageIds.length) {
        return false;
    }
    return a.visibleMessageIds.every((id, index) => id === b.visibleMessageIds[index]);
}
// -----------------------------------------------------------------------------
// DOM measurement helpers
// -----------------------------------------------------------------------------
function parseNumber(value) {
    if (!value)
        return 0;
    const parsed = Number.parseFloat(value);
    return Number.isFinite(parsed) ? parsed : 0;
}
function getPadding(element) {
    const style = window.getComputedStyle(element);
    return {
        end: parseNumber(style.paddingBlockEnd || style.paddingBottom),
        start: parseNumber(style.paddingBlockStart || style.paddingTop),
    };
}
function getContentPadding(spacer) {
    const parent = spacer?.parentElement;
    return parent ? getPadding(parent) : { end: 0, start: 0 };
}
function getRowGap(element) {
    if (!element)
        return 0;
    const style = window.getComputedStyle(element);
    const gap = style.rowGap === 'normal' ? style.gap : style.rowGap;
    return parseNumber(gap);
}
function getMessageChildren(content, spacer) {
    return Array.from(content.children).filter((child) => child instanceof HTMLElement && child !== spacer);
}
function getElementOffsetTop(element, viewport) {
    const elementRect = element.getBoundingClientRect();
    const viewportRect = viewport.getBoundingClientRect();
    return elementRect.top - viewportRect.top + viewport.scrollTop;
}
function getRelativeTop(element, viewport) {
    return element.getBoundingClientRect().top - viewport.getBoundingClientRect().top;
}
function measureContentHeight({ content, spacer, viewport, }) {
    const children = getMessageChildren(content, spacer);
    const padding = getPadding(content);
    const viewportRect = viewport.getBoundingClientRect();
    const scrollTop = viewport.scrollTop;
    let height = padding.start + padding.end;
    for (const child of children) {
        const rect = child.getBoundingClientRect();
        height = Math.max(height, rect.bottom - viewportRect.top + scrollTop + padding.end);
    }
    return height;
}
function maxScrollTop(element) {
    return Math.max(0, element.scrollHeight - element.clientHeight);
}
function computeSpacerHeight({ content, scrollTop, spacer, viewport, }) {
    const contentHeight = measureContentHeight({ content, spacer, viewport });
    return scrollTop + viewport.clientHeight - contentHeight;
}
function computeScrollTopForElement({ align, element, scrollMargin, spacer, viewport, }) {
    const offsetTop = getElementOffsetTop(element, viewport);
    const height = element.getBoundingClientRect().height;
    const padding = getContentPadding(spacer);
    if (align === 'center') {
        const available = Math.max(0, viewport.clientHeight - padding.start - padding.end);
        return offsetTop - padding.start - (available - height) / 2 - scrollMargin;
    }
    if (align === 'end')
        return offsetTop - viewport.clientHeight + height + padding.end + scrollMargin;
    if (align === 'nearest') {
        const bottom = offsetTop + height;
        const visibleTop = viewport.scrollTop + padding.start;
        const visibleBottom = viewport.scrollTop + viewport.clientHeight - padding.end;
        if (offsetTop >= visibleTop && bottom <= visibleBottom)
            return viewport.scrollTop;
        return offsetTop < visibleTop
            ? offsetTop - padding.start - scrollMargin
            : bottom - viewport.clientHeight + padding.end + scrollMargin;
    }
    return offsetTop - padding.start - scrollMargin;
}
function computeScrollable({ content, scrollEdgeThreshold, spacer, viewport, }) {
    if (!viewport || !content)
        return EMPTY_SCROLLABLE;
    const contentHeight = measureContentHeight({ content, spacer, viewport });
    return {
        start: viewport.scrollTop > scrollEdgeThreshold,
        end: contentHeight - viewport.scrollTop - viewport.clientHeight > scrollEdgeThreshold,
    };
}
function computeVisibility({ content, scrollMargin, scrollPreviousItemPeek, spacer, viewport, visibleMessageIds, }) {
    if (!content || !viewport)
        return EMPTY_VISIBILITY;
    const viewportRect = viewport.getBoundingClientRect();
    const anchorLine = viewportRect.top + scrollMargin + scrollPreviousItemPeek;
    const noIntersectionObserver = typeof IntersectionObserver === 'undefined';
    const visible = [];
    let currentAnchorId = null;
    for (const child of getMessageChildren(content, spacer)) {
        const messageId = child.dataset.messageId;
        if (!messageId)
            continue;
        const isAnchor = child.dataset.scrollAnchor === 'true';
        const rect = isAnchor || noIntersectionObserver ? child.getBoundingClientRect() : null;
        const isVisible = noIntersectionObserver && rect
            ? rect.bottom > anchorLine && rect.top < viewportRect.bottom
            : visibleMessageIds.has(messageId);
        if (isVisible)
            visible.push(messageId);
        if (isAnchor && rect && rect.top <= anchorLine + SCROLL_EPSILON)
            currentAnchorId = messageId;
    }
    return visible.length === 0 && currentAnchorId === null
        ? EMPTY_VISIBILITY
        : { currentAnchorId, visibleMessageIds: visible };
}
function findFirstAnchorFrom(elements, startIndex) {
    for (let i = startIndex; i < elements.length; i++) {
        const element = elements[i];
        if (element?.dataset.scrollAnchor === 'true')
            return element;
    }
    return null;
}
function findFirstUnhandledAnchor(elements, handled) {
    for (const element of elements) {
        if (element.dataset.scrollAnchor === 'true' && !handled.has(element))
            return element;
    }
    return null;
}
function hasMultipleAnchorsFrom(elements, startIndex) {
    let count = 0;
    for (let i = startIndex; i < elements.length; i++) {
        if (elements[i]?.dataset.scrollAnchor === 'true') {
            count += 1;
            if (count > 1)
                return true;
        }
    }
    return false;
}
function findLastAnchor(elements) {
    for (let i = elements.length - 1; i >= 0; i--) {
        const element = elements[i];
        if (element?.dataset.scrollAnchor === 'true')
            return element;
    }
    return null;
}
function findFirstVisibleMessage({ content, spacer, viewport, }) {
    const viewportRect = viewport.getBoundingClientRect();
    for (const child of getMessageChildren(content, spacer)) {
        if (!child.dataset.messageId)
            continue;
        const rect = child.getBoundingClientRect();
        if (rect.bottom > viewportRect.top && rect.top < viewportRect.bottom)
            return child;
    }
    return null;
}
const CONTEXT_KEY = Symbol('MessageScrollerContext');
const REGISTER_KEY = Symbol('MessageScrollerRegister');
function createEngine(props) {
    const autoScroll = () => props.autoScroll ?? false;
    const defaultScrollPosition = () => props.defaultScrollPosition ?? 'end';
    const scrollEdgeThreshold = () => props.scrollEdgeThreshold ?? DEFAULT_SCROLL_EDGE_THRESHOLD;
    const scrollPreviousItemPeek = () => props.scrollPreviousItemPeek ?? DEFAULT_SCROLL_PREVIOUS_ITEM_PEEK;
    const scrollMargin = () => props.scrollMargin ?? DEFAULT_SCROLL_MARGIN;
    let viewport = null;
    let content = null;
    let spacer = null;
    let spacerGap = 0;
    let spacerHeight = 0;
    let mode = autoScroll() ? 'following-bottom' : 'free-scrolling';
    let streamingTurn = null;
    let firstItem = null;
    let itemCount = 0;
    let lastScrollTop = 0;
    let defaultScrollPositionApplied = false;
    let preserveScrollOnPrepend = true;
    let prependRestore = null;
    let pendingScrollToMessage = null;
    let stateFrame = null;
    let visibilityFrame = null;
    let pendingScrollFrame = null;
    let autoscrollingTimeout = null;
    let visibilityObserver = null;
    let visibilityConsumers = 0;
    const messageElements = new Map();
    const visibleMessageIds = new Set();
    const handledScrollAnchors = new WeakSet();
    const autoscrolling = shallowRef(false);
    const scrollable = shallowRef(EMPTY_SCROLLABLE);
    const visibility = shallowRef(EMPTY_VISIBILITY);
    const scrollableAttr = computed(() => {
        const attr = [scrollable.value.start && 'start', scrollable.value.end && 'end']
            .filter(Boolean)
            .join(' ');
        return attr || undefined;
    });
    // --- scroll-state commit / visibility scheduling ---------------------------
    function updateModeFromScroll(next) {
        const scrollTop = viewport?.scrollTop ?? 0;
        const scrolledUp = scrollTop < lastScrollTop - SCROLL_EPSILON;
        lastScrollTop = scrollTop;
        if (autoScroll()
            && !next.end
            && mode !== 'settling-jump'
            && mode !== 'anchored-to-message') {
            mode = 'following-bottom';
        }
        else if (mode === 'following-bottom'
            && next.end
            && scrolledUp
            && !autoscrolling.value) {
            mode = 'free-scrolling';
        }
    }
    function commitScrollState() {
        const measured = computeScrollable({
            content,
            scrollEdgeThreshold: scrollEdgeThreshold(),
            spacer,
            viewport,
        });
        updateModeFromScroll(measured);
        const next = mode === 'following-bottom'
            ? { ...measured, end: false }
            : measured;
        if (!scrollableEqual(scrollable.value, next))
            scrollable.value = next;
    }
    function scheduleStateCommit() {
        if (stateFrame === null) {
            stateFrame = window.requestAnimationFrame(() => {
                stateFrame = null;
                commitScrollState();
            });
        }
    }
    function scheduleVisibilitySync() {
        if (visibilityConsumers === 0)
            return;
        if (visibilityFrame === null) {
            visibilityFrame = window.requestAnimationFrame(() => {
                visibilityFrame = null;
                if (visibilityConsumers > 0) {
                    const next = computeVisibility({
                        content,
                        scrollMargin: scrollMargin(),
                        scrollPreviousItemPeek: scrollPreviousItemPeek(),
                        spacer,
                        viewport,
                        visibleMessageIds,
                    });
                    if (!visibilityEqual(visibility.value, next))
                        visibility.value = next;
                }
            });
        }
    }
    // --- imperative scroll primitives ------------------------------------------
    function setAutoscrolling(active) {
        if (autoscrollingTimeout !== null) {
            window.clearTimeout(autoscrollingTimeout);
            autoscrollingTimeout = null;
        }
        if (autoscrolling.value !== active) {
            autoscrolling.value = active;
            commitScrollState();
        }
        if (active) {
            autoscrollingTimeout = window.setTimeout(() => {
                autoscrollingTimeout = null;
                autoscrolling.value = false;
                commitScrollState();
            }, AUTOSCROLLING_TIMEOUT);
        }
    }
    function setSpacerHeight(height) {
        if (!spacer)
            return;
        const next = Math.max(0, Math.ceil(height));
        if (spacerHeight !== next) {
            spacerHeight = next;
            spacer.hidden = next === 0;
            spacer.style.height = `${next}px`;
            spacer.style.marginTop = next > 0 ? `${-spacerGap}px` : '';
        }
    }
    function scrollTo(top, { behavior = 'auto', autoscrolling: isAutoscrolling = false } = {}) {
        if (!viewport)
            return;
        const target = Math.max(0, top);
        if (Math.abs(viewport.scrollTop - target) <= SCROLL_EPSILON) {
            viewport.scrollTop = target;
            commitScrollState();
            return;
        }
        if (isAutoscrolling)
            setAutoscrolling(true);
        viewport.scrollTo({ top: target, behavior });
        scheduleStateCommit();
    }
    function scrollToStart({ behavior = 'auto' } = {}) {
        if (!viewport)
            return false;
        setSpacerHeight(0);
        streamingTurn = null;
        mode = 'free-scrolling';
        scrollTo(0, { behavior });
        scheduleVisibilitySync();
        return true;
    }
    function scrollToEnd({ behavior = 'auto' } = {}) {
        if (!viewport)
            return false;
        setSpacerHeight(0);
        streamingTurn = null;
        mode = autoScroll() ? 'following-bottom' : 'free-scrolling';
        scrollTo(maxScrollTop(viewport), { autoscrolling: true, behavior });
        scheduleVisibilitySync();
        return true;
    }
    function scrollToElement(element, { align = 'start', behavior = 'auto', scrollMargin: margin = scrollMargin(), } = {}, { keepPreviousPeek = false } = {}) {
        if (!content || !viewport || !content.contains(element))
            return false;
        const targetScrollTop = computeScrollTopForElement({
            align,
            element,
            scrollMargin: keepPreviousPeek ? margin + scrollPreviousItemPeek() : margin,
            spacer,
            viewport,
        });
        setSpacerHeight(computeSpacerHeight({
            content,
            scrollTop: targetScrollTop,
            spacer,
            viewport,
        }));
        prependRestore = { element, viewportTop: getRelativeTop(element, viewport) };
        mode = keepPreviousPeek ? 'anchored-to-message' : 'settling-jump';
        streamingTurn = keepPreviousPeek ? element : null;
        scrollTo(targetScrollTop, { behavior });
        scheduleVisibilitySync();
        return true;
    }
    function reanchorToAnchoredMessage() {
        if (!streamingTurn || !streamingTurn.isConnected || mode !== 'anchored-to-message')
            return false;
        return scrollToElement(streamingTurn, { align: 'start' }, { keepPreviousPeek: true });
    }
    function scrollToMessage(messageId, options) {
        const element = messageElements.get(messageId);
        if (element) {
            defaultScrollPositionApplied = true;
            if (scrollToElement(element, options)) {
                pendingScrollToMessage = null;
                return true;
            }
            pendingScrollToMessage = { messageId, options };
            return true;
        }
        if (itemCount === 0) {
            pendingScrollToMessage = { messageId, options };
            defaultScrollPositionApplied = true;
            return true;
        }
        return false;
    }
    function flushPendingScrollToMessage() {
        if (!pendingScrollToMessage)
            return false;
        const element = messageElements.get(pendingScrollToMessage.messageId);
        if (!element || !scrollToElement(element, pendingScrollToMessage.options))
            return false;
        pendingScrollToMessage = null;
        defaultScrollPositionApplied = true;
        return true;
    }
    // --- prepend preservation --------------------------------------------------
    function applyPrependRestore() {
        if (!prependRestore || !viewport || !prependRestore.element.isConnected)
            return false;
        const delta = getRelativeTop(prependRestore.element, viewport) - prependRestore.viewportTop;
        if (Math.abs(delta) <= SCROLL_EPSILON)
            return false;
        viewport.scrollTop += delta;
        prependRestore.viewportTop = getRelativeTop(prependRestore.element, viewport);
        scheduleStateCommit();
        scheduleVisibilitySync();
        return true;
    }
    function capturePrependAnchor() {
        if (!content || !viewport) {
            prependRestore = null;
            return;
        }
        const element = findFirstVisibleMessage({ content, spacer, viewport });
        prependRestore = element
            ? { element, viewportTop: getRelativeTop(element, viewport) }
            : null;
    }
    function schedulePrependFlush() {
        if (pendingScrollFrame === null) {
            pendingScrollFrame = window.requestAnimationFrame(() => {
                pendingScrollFrame = null;
                if (flushPendingScrollToMessage())
                    capturePrependAnchor();
            });
        }
    }
    // --- default scroll position -----------------------------------------------
    function applyDefaultScrollPosition() {
        if (defaultScrollPositionApplied || itemCount === 0)
            return false;
        const position = defaultScrollPosition();
        let applied = false;
        if (position === 'last-anchor') {
            const lastAnchor = content && viewport
                ? findLastAnchor(getMessageChildren(content, spacer))
                : null;
            if (!content || !viewport || !lastAnchor) {
                applied = scrollToEnd({ behavior: 'auto' });
            }
            else {
                const anchorOffset = getElementOffsetTop(lastAnchor, viewport);
                const contentHeight = measureContentHeight({ content, spacer, viewport });
                applied = contentHeight - anchorOffset <= viewport.clientHeight
                    ? scrollToEnd({ behavior: 'auto' })
                    : scrollToElement(lastAnchor, { align: 'start' }, { keepPreviousPeek: true });
            }
        }
        else {
            applied = position === 'end'
                ? scrollToEnd({ behavior: 'auto' })
                : scrollToStart({ behavior: 'auto' });
        }
        if (applied) {
            defaultScrollPositionApplied = true;
            return true;
        }
        return false;
    }
    // --- content / resize handling ---------------------------------------------
    function applyContentChange(children, previousCount, previousFirst) {
        if (flushPendingScrollToMessage())
            return;
        if (previousCount === 0) {
            if (applyDefaultScrollPosition()
                || (children.length > 0 && autoScroll() && scrollToEnd({ behavior: 'auto' }))) {
                return;
            }
            commitScrollState();
            scheduleVisibilitySync();
            return;
        }
        const previousIndex = previousFirst ? children.indexOf(previousFirst) : -1;
        if (preserveScrollOnPrepend && previousIndex > 0) {
            applyPrependRestore();
            return;
        }
        if (children.length > previousCount) {
            const anchor = findFirstAnchorFrom(children, previousCount);
            if (anchor) {
                if (autoScroll()
                    && mode === 'following-bottom'
                    && hasMultipleAnchorsFrom(children, previousCount)) {
                    scrollToEnd({ behavior: 'auto' });
                    return;
                }
                scrollToElement(anchor, { align: 'start' }, { keepPreviousPeek: true });
                handledScrollAnchors.add(anchor);
                return;
            }
        }
        if (children.length === previousCount) {
            const anchor = findFirstUnhandledAnchor(children, handledScrollAnchors);
            if (anchor) {
                scrollToElement(anchor, { align: 'start' }, { keepPreviousPeek: true });
                handledScrollAnchors.add(anchor);
                return;
            }
        }
        if (mode === 'following-bottom' && autoScroll()) {
            scrollToEnd({ behavior: 'auto' });
        }
        else {
            commitScrollState();
            scheduleVisibilitySync();
        }
    }
    function handleContentChange() {
        if (!content)
            return;
        const children = getMessageChildren(content, spacer);
        const previousCount = itemCount;
        const previousFirst = firstItem;
        itemCount = children.length;
        firstItem = children[0] ?? null;
        applyContentChange(children, previousCount, previousFirst);
        capturePrependAnchor();
    }
    function handleResize() {
        if (mode === 'following-bottom' && autoScroll()) {
            scrollToEnd({ behavior: 'auto' });
            return;
        }
        const previousSpacerHeight = spacerHeight;
        if (reanchorToAnchoredMessage()) {
            // The reply streaming below the anchor consumes the tail spacer as it
            // grows. Once the last of it is gone the reply has filled the viewport
            // and the reader is genuinely at the live edge, so autoScroll hands off
            // from the anchor hold to following the bottom. Requiring the >0 → 0
            // transition keeps a turn taller than the viewport (placed with no
            // spacer) held instead of yanked to the end.
            if (autoScroll() && previousSpacerHeight > 0 && spacerHeight === 0)
                scrollToEnd({ behavior: 'auto' });
            return;
        }
        scheduleStateCommit();
        scheduleVisibilitySync();
    }
    // --- visibility observation ------------------------------------------------
    function observeVisibility() {
        if (!viewport || visibilityConsumers === 0)
            return;
        if (typeof IntersectionObserver === 'undefined') {
            scheduleVisibilitySync();
            return;
        }
        if (!visibilityObserver) {
            visibilityObserver = new IntersectionObserver((entries) => {
                for (const entry of entries) {
                    const messageId = entry.target.dataset.messageId;
                    if (!messageId)
                        continue;
                    if (entry.isIntersecting)
                        visibleMessageIds.add(messageId);
                    else
                        visibleMessageIds.delete(messageId);
                }
                scheduleVisibilitySync();
            }, {
                root: viewport,
                rootMargin: `${-(scrollMargin() + scrollPreviousItemPeek())}px 0px 0px 0px`,
                threshold: [0, 0.01, 0.5, 1],
            });
        }
        messageElements.forEach((element) => {
            visibilityObserver?.observe(element);
        });
        scheduleVisibilitySync();
    }
    function unobserveVisibility() {
        if (visibilityFrame !== null) {
            window.cancelAnimationFrame(visibilityFrame);
            visibilityFrame = null;
        }
        visibilityObserver?.disconnect();
        visibilityObserver = null;
        visibleMessageIds.clear();
        if (!visibilityEqual(visibility.value, EMPTY_VISIBILITY))
            visibility.value = EMPTY_VISIBILITY;
    }
    function acquireVisibility() {
        visibilityConsumers += 1;
        if (visibilityConsumers === 1)
            observeVisibility();
    }
    function releaseVisibility() {
        visibilityConsumers -= 1;
        if (visibilityConsumers === 0)
            unobserveVisibility();
    }
    const registerMessage = (messageId, element, previousElement) => {
        if (element) {
            messageElements.set(messageId, element);
            visibilityObserver?.observe(element);
            scheduleVisibilitySync();
            if (pendingScrollToMessage?.messageId === messageId)
                schedulePrependFlush();
            return;
        }
        if (previousElement && messageElements.get(messageId) === previousElement) {
            messageElements.delete(messageId);
            visibleMessageIds.delete(messageId);
            visibilityObserver?.unobserve(previousElement);
            scheduleVisibilitySync();
        }
    };
    // --- user intent + element setters -----------------------------------------
    function userScrollIntent() {
        if (mode === 'following-bottom'
            || mode === 'anchored-to-message'
            || mode === 'settling-jump') {
            streamingTurn = null;
            mode = 'free-scrolling';
        }
    }
    function setViewportElement(element) {
        viewport = element;
        // A visibility consumer may have subscribed before the viewport mounted,
        // in which case observeVisibility() bailed out. Retry now it exists.
        if (element)
            observeVisibility();
    }
    function setContentElement(element) {
        content = element;
    }
    function setSpacerElement(element) {
        spacer = element;
        spacerGap = getRowGap(element?.parentElement ?? null);
    }
    function setPreserveScrollOnPrepend(value) {
        preserveScrollOnPrepend = value;
    }
    function syncAfterScroll() {
        commitScrollState();
        scheduleVisibilitySync();
        capturePrependAnchor();
    }
    function onAutoScrollChange() {
        if (autoScroll() && mode === 'following-bottom' && itemCount > 0) {
            scrollToEnd({ behavior: 'auto' });
            return;
        }
        commitScrollState();
    }
    function destroy() {
        if (stateFrame !== null) {
            window.cancelAnimationFrame(stateFrame);
            stateFrame = null;
        }
        if (visibilityFrame !== null) {
            window.cancelAnimationFrame(visibilityFrame);
            visibilityFrame = null;
        }
        if (autoscrollingTimeout !== null) {
            window.clearTimeout(autoscrollingTimeout);
            autoscrollingTimeout = null;
        }
        if (pendingScrollFrame !== null) {
            window.cancelAnimationFrame(pendingScrollFrame);
            pendingScrollFrame = null;
        }
        visibilityObserver?.disconnect();
        visibilityObserver = null;
    }
    const context = {
        autoscrolling,
        scrollable,
        scrollableAttr,
        visibility,
        acquireVisibility,
        releaseVisibility,
        handleContentChange,
        handleResize,
        scrollToEnd,
        scrollToMessage,
        scrollToStart,
        setContentElement,
        setSpacerElement,
        setViewportElement,
        setPreserveScrollOnPrepend,
        syncAfterScroll,
        userScrollIntent,
    };
    return {
        context,
        registerMessage,
        applyDefaultScrollPosition,
        onAutoScrollChange,
        destroy,
    };
}
// -----------------------------------------------------------------------------
// Provider wiring (provide/inject)
// -----------------------------------------------------------------------------
export function provideMessageScroller(props) {
    const engine = createEngine(props);
    provide(CONTEXT_KEY, engine.context);
    provide(REGISTER_KEY, engine.registerMessage);
    watch(() => props.autoScroll ?? false, () => engine.onAutoScrollChange());
    onMounted(() => {
        engine.applyDefaultScrollPosition();
        // The viewport element attaches in MessageScrollerViewport's onMounted,
        // after MessageScrollerContent ran its initial handleContentChange without
        // it. Re-sync now that every element is wired up.
        engine.context.syncAfterScroll();
    });
    onScopeDispose(() => engine.destroy());
    return engine;
}
export function useMessageScrollerContext() {
    const context = inject(CONTEXT_KEY, null);
    if (!context)
        throw new Error('useMessageScroller must be used within a MessageScroller.');
    return context;
}
export function useMessageScrollerRegister() {
    const register = inject(REGISTER_KEY, null);
    if (!register)
        throw new Error('MessageScrollerItem must be used within a MessageScroller.');
    return register;
}
// -----------------------------------------------------------------------------
// Public composables
// -----------------------------------------------------------------------------
export function useMessageScroller() {
    const { scrollToEnd, scrollToMessage, scrollToStart } = useMessageScrollerContext();
    return { scrollToEnd, scrollToMessage, scrollToStart };
}
export function useMessageScrollerScrollable() {
    const { scrollable } = useMessageScrollerContext();
    return computed(() => scrollable.value);
}
export function useMessageScrollerVisibility() {
    const { acquireVisibility, releaseVisibility, visibility } = useMessageScrollerContext();
    acquireVisibility();
    if (getCurrentScope())
        onScopeDispose(releaseVisibility);
    return computed(() => visibility.value);
}
export { SCROLL_KEYS };
