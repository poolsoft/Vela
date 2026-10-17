# Installed regions without a connection

The downloaded section previously intersected local routing IDs with a network-only catalog. With no catalog response, installed files disappeared from the list. Basemap-only and POI-only restores were also omitted.

The catalog now exposes installed regions from local store inventories, before network work begins. Successful manifests are atomically cached per source URL and reused on failures. Restores with no catalog cache still show a readable local ID. Download completion actions require a catalog URL, and offline-only mode disables them. Deletion remains available for installed packages. No map files are changed by listing.

Validation: four InstalledRegionCatalogTest cases passed and the car release APK assembled successfully. The APK was installed on emulator-5554 without clearing data. With Wi-Fi and mobile data both disabled and no region_catalog directory, Settings displayed tr guney as installed map/place data and world as a map-only package. Screenshot and UI evidence remain local under .logs/offline-inventory-emulator. No installed files were deleted during testing.
