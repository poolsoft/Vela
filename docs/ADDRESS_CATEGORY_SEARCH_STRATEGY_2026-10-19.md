# Separate address search and POI category browsing

Status: implemented. Category keys, local filter text, captured search center, radius and the loaded POI pool are separate from address text. The initial radius is 10 km; the explicit expansion action is capped at 20 km.

## Current coupling

Category chips call quickSearch, which puts their query into MapUiState.query and starts the same search/suggestion pipeline as typed text. Search focus opens the general search entry page. In the no-Google source that pipeline uses Photon name/address geocoding. Turkish result filters and result-count strings also contain untranslated text. These are separate search-state and localization defects.

## Proposed behavior

- ADDRESS mode is the default. The input searches addresses and does not infer a POI category from typed words.
- A category button selects a stable category ID and enters CATEGORY mode. Its translated name appears as a selected badge/list title, not as editable input text. The input is empty with a localized placeholder such as Search within restaurants.
- CATEGORY mode uses exact canonical POI tags from downloaded data first. If online data is needed, an explicit OSM category query runs around a captured map/GPS anchor. Photon address geocoding is never used as a category fallback. A general Google keyword query is not treated as proof of category membership.
- Text typed in CATEGORY mode filters the loaded POI pool by name and address. It does not send address suggestions or global text-search requests. A zero-match filter displays an explanation and preserves the selected category.
- Deleting filter text with Backspace restores the category list. Pressing the input X clears text, category, POI results and associated pins, cancels pending work, and returns to ADDRESS mode. Clearing search must not cancel an active route; existing along-route destination restoration remains intact.
- Selecting a different category clears the previous text filter and loads that category. Tapping the currently selected category again removes its text filter and restores its unfiltered list.
- Panning does not silently reload or relocate the category list. An explicit Search this area action captures a new anchor, refetches the category, and reapplies its text filter. Typing and GPS ticks never refetch the list.
- The initial nearby radius may be 10 km, with an explicit expansion action when empty. It must not silently widen to distant cities or countries. The current typed-address search must still support an explicitly entered distant city/address.

## State and responsibilities

Maintain addressText, selectedCategoryId, categoryFilterText, categoryAnchor, categoryRadius and the full loaded category POI pool separately. The view model derives visible results after filtering and sorting; it must not overwrite or progressively filter the source pool. Each category load has a request generation so a stale response cannot repopulate results after X, another category, or an area change. Suggestions, status, pins and list titles derive from the active mode. Opening the keyboard in CATEGORY mode must keep category results visible rather than replacing them with the address entry page.

The offline bounding-box query selects exact canonical category columns instead of name/address FTS ranking, then computes radial distances and keeps at most 500 nearest loaded POIs. Local filtering uses Turkish-aware case handling and accent-tolerant matching. Filtering runs locally on the bounded pool without network work. At most one category request runs at a time on a head unit.

## Delivery order

1. Separate category and address state, clear/reset semantics, keyboard/focus behavior and cancellation.
2. Implement exact offline category lookup, explicit online category fallback and bounded distance ordering.
3. Implement local filtering, selected category badge, localized title/placeholders and area refresh.
4. Complete Turkish result/filter/count/status translations.
5. Verify with downloaded data offline, online category data, rapid typing, stale responses and a slow-device acceptance run.

## Required cases

Restaurants -> shows only restaurant POIs. Type a venue name -> filters those restaurants. Delete text -> restores all loaded restaurants. X -> ADDRESS mode and no category pins. Type a street address -> address results. An empty filtered list never becomes a worldwide address list. Change category during a request -> old results cannot reappear. Pan -> no request until Search this area. Clear while navigating -> route remains active.

## Localization inventory

See TURKISH_LOCALIZATION_AUDIT_2026-10-19.md for the resource comparison. The initial inventory found 39 absent Turkish declarations and 284 identical-to-English candidates. The completion pass supplies all declarations and translates the reviewed UI text; the only remaining audit candidate is the provider label AllThePlaces. Navigation and destructive offline-data messages take priority, followed by search/place details and voice/diagnostic settings.
