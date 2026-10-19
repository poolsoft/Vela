# Music scanning exclusions

Music scanning shares messaging-folder and system-sound exclusions across MediaStore, direct file crawling and cached-library restoration. MediaStore ringtone/alarm/notification flags exclude audio regardless of folder, and excluded file identities prevent the crawler from re-adding those rows. The crawler prunes excluded folders and .nomedia directories before metadata reads. Matching uses folder boundaries and Locale.ROOT so ordinary album names remain eligible; IS_MUSIC is not required for head-unit USB compatibility.

Excluded messaging folders include WhatsApp/Business, Telegram, Signal, Viber, Messenger and WeChat, with known application-package paths under Android media storage. Ringtone, alarm, notification, voice-note and recording folders are excluded on internal and removable storage. The same path policy filters previously cached entries at library restoration, so old scans cannot reintroduce those paths.

The MediaStore query still accepts normal songs based on the existing duration criterion rather than IS_MUSIC. It additionally reads flagged system sounds, including short ones, to reserve their file identities before direct crawling. If duplicate MediaStore rows disagree on sound flags, the excluded identity wins before the final library is published and cached. Files and user-created playlist records are not deleted.

Validation covers legacy/scoped messaging paths, USB sound directories, ordinary album-name boundary cases, mixed-case paths, path separators, explicit sound flags and restoration of old excluded cache entries. Manual rescan refreshes the saved index with the new rules; startup remains cache-only without automatic scanning. Physical MediaStore/OEM acceptance is still required.
