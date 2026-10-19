# Turkish localization audit and completion, 2026-10-19

The initial car-resource audit found 39 missing Turkish declarations and 284 identical-to-English candidates after excluding reviewed brands and units. The completion pass translates 315 reviewed string entries and adds four missing plural resources. Earlier search-result translations are included in this delivery.

| Resource scope | Translatable base entries | Turkish declarations | Missing | Format/XML defects |
|---|---:|---:|---:|---:|
| App main | 1074 | 1074 | 0 | 0 |
| App car (main plus overlay) | 1359 | 1359 | 0 | 0 |
| App standard | 1074 | 1074 | 0 | 0 |
| Core | 19 | 19 | 0 | 0 |

Counts overlap between app scopes and must not be added together. AllThePlaces (%1$s) remains identical as a provider name. Normal, Modern, Smart Focus, Street View, units and other reviewed technical identifiers remain unchanged where appropriate. The base music-scan label is English and has an explicit Turkish translation.

Covered areas include search/results, place details, reviews, directions, notification actions, offline-data deletion/downloads, voice settings, diagnostics, recorded trips/sharing, updates and first-run screens. Format arguments are compared by argument number and conversion type, allowing Turkish word order to differ without changing argument contracts. XML, required plural other forms and array lengths are checked.

Reproduce the resource audit with `python scripts/audit_turkish_resources.py`. Detailed TSV and JSON output goes to `.logs/turkish-locale-audit/`. This resource audit does not claim that every hard-coded UI string or remote provider label is translated.
