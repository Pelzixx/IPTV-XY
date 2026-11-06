# Changelog

## Unreleased
- Sign-in with built-in Xtream servers (secret PRESET_SERVERS): only user name and password, server choice when there are several; "Other source" keeps the free input
- Several own XMLTV EPG sources in Settings > Live TV, plus built-in EPG sources (secret PRESET_EPG)

## 1.0 - IPTV-XY
- Renamed to IPTV-XY with its own logo and app ID (com.pelzixx.iptvxy), based on IPVIO Stream 1.0
- Releases via GitHub Actions (workflow "Release"): signed APK plus update.json
- In-app update points to the latest GitHub release by default
- Prebuilt APK removed from the repository; builds come from GitHub Actions

## 1.0 - first open-source release
- Android TV / Google TV / Fire TV IPTV player for Xtream Codes and M3U playlists
- Home, Movies, Series, Live TV with TV guide and catch-up
- Cinema and grid browse views, filters, search with typo tolerance
- Player with audio/subtitle selection, FFmpeg audio decoder, skip intro/credits, source fallback
- Profiles, parental controls, history, my list, favourites, backup and restore
- 9 languages, 12 colour themes
- Own API keys for TMDB and OMDb in Settings > API keys
- Without keys: description, cover, rating, genres, running time and trailer straight from the Xtream provider
- OMDb ratings also without TMDB (lookup by title and year)
- Rating order for sorting and "Top rated": IMDb, Rotten Tomatoes, TMDB, provider
- Licensed under GPL-3.0
