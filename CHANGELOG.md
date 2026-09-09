# Changelog

## [0.5.0](https://github.com/iNAYATechLab/File-Manager-Pro/compare/v0.4.0...v0.5.0) (2026-09-09)


### Features

* **about:** company/About page + real-emulator screenshot capture pipeline ([a31885d](https://github.com/iNAYATechLab/File-Manager-Pro/commit/a31885d1dd2742bec8cbb6d678d5932693761749))
* **core:** app-managed Trash (recycle bin) with restore & empty ([ff579f5](https://github.com/iNAYATechLab/File-Manager-Pro/commit/ff579f5654cfcb25fa21e47823ea1b7b81d29afa))
* **core:** library quick access (favorites/recents/downloads) + in-app text viewer ([48e4306](https://github.com/iNAYATechLab/File-Manager-Pro/commit/48e4306ac9a20cdf826503e76fa9f023efa4681d))
* **core:** select by type, open with chooser, extended sorting, home quick access ([a52cbd9](https://github.com/iNAYATechLab/File-Manager-Pro/commit/a52cbd99ccf0b8effd4fb4d2c0853a49d9e8a15e))
* **public:** README with company info & real emulator screenshots ([#37](https://github.com/iNAYATechLab/File-Manager-Pro/issues/37)) ([d175281](https://github.com/iNAYATechLab/File-Manager-Pro/commit/d17528114c506058298dd08381172908341e55b1))


### Bug Fixes

* **test:** rename wait() to sleepMs (Object.wait JVM clash) ([2626fe8](https://github.com/iNAYATechLab/File-Manager-Pro/commit/2626fe8b8e4641cb7a3bb54dac3f987eff8fa243))
* **trash:** unique id per trash event (same-ms double trash of one path) ([c05edd2](https://github.com/iNAYATechLab/File-Manager-Pro/commit/c05edd2f6cde1bb861d57b33b350d9845f5340b1))
* **viewer:** strip BOM char from decoded text; fix legacy-encoding test bytes ([719589a](https://github.com/iNAYATechLab/File-Manager-Pro/commit/719589a041da394c53ecd3844f6dae0f27d224af))
* **viewer:** use trash move failure snackbar (no OpResult errors field) ([1e54d60](https://github.com/iNAYATechLab/File-Manager-Pro/commit/1e54d607a9018d60baa9902e0bbc5d3dd14ba74a))

## [0.4.0](https://github.com/iNAYATechLab/File-Manager-Pro/compare/v0.3.0...v0.4.0) (2026-09-09)


### Features

* **build:** 1.0.0 hardening — R8, splash, emulator tests, Play assets ([#22](https://github.com/iNAYATechLab/File-Manager-Pro/issues/22)) ([39f0dc5](https://github.com/iNAYATechLab/File-Manager-Pro/commit/39f0dc5881a30be81e498df6500797686c1f9a5a))

## [0.3.0](https://github.com/iNAYATechLab/File-Manager-Pro/compare/v0.2.0...v0.3.0) (2026-09-09)


### Features

* **saf:** browse protected folders via Storage Access Framework ([#21](https://github.com/iNAYATechLab/File-Manager-Pro/issues/21)) ([fbe146a](https://github.com/iNAYATechLab/File-Manager-Pro/commit/fbe146a35d2c65071d1e945a91acbb6ab3467851))
* **search/ops/preview:** search filters & scope, transfer progress+conflicts, multi-format extraction, zoomable viewer, Espresso smoke test ([de6a950](https://github.com/iNAYATechLab/File-Manager-Pro/commit/de6a950b98c22e7b9221cb7643a0d53ff43237a2)), closes [#15](https://github.com/iNAYATechLab/File-Manager-Pro/issues/15) [#16](https://github.com/iNAYATechLab/File-Manager-Pro/issues/16) [#17](https://github.com/iNAYATechLab/File-Manager-Pro/issues/17) [#18](https://github.com/iNAYATechLab/File-Manager-Pro/issues/18) [#19](https://github.com/iNAYATechLab/File-Manager-Pro/issues/19)
* Settings (sort, language, about) + hidden toggle + UI-text localization ([#12](https://github.com/iNAYATechLab/File-Manager-Pro/issues/12)–[#14](https://github.com/iNAYATechLab/File-Manager-Pro/issues/14)) ([#26](https://github.com/iNAYATechLab/File-Manager-Pro/issues/26)) ([7697870](https://github.com/iNAYATechLab/File-Manager-Pro/commit/76978707fb2b99abbbf6a1c951125a8cf8207324))
* **vault:** encrypted private vault with biometric unlock ([#20](https://github.com/iNAYATechLab/File-Manager-Pro/issues/20)) ([07e6ac5](https://github.com/iNAYATechLab/File-Manager-Pro/commit/07e6ac503912b2b931d5aa380d6f02c4293cd42f))

## [0.2.0](https://github.com/iNAYATechLab/File-Manager-Pro/compare/v0.1.3...v0.2.0) (2026-09-08)


### Features

* **browse:** storage home screen with volume cards (internal + SD) ([#24](https://github.com/iNAYATechLab/File-Manager-Pro/issues/24)) ([d3fbf90](https://github.com/iNAYATechLab/File-Manager-Pro/commit/d3fbf903f3227dea7f488ec7d6df87897d102ae9))

## [0.1.3](https://github.com/iNAYATechLab/File-Manager-Pro/compare/v0.1.2...v0.1.3) (2026-09-08)


### Bug Fixes

* **browse:** crash on app start (NullPointerException in reload) ([#9](https://github.com/iNAYATechLab/File-Manager-Pro/issues/9)) ([f26e176](https://github.com/iNAYATechLab/File-Manager-Pro/commit/f26e176df098cb08c5da61151581a8b2ab2afdab))

## [0.1.2](https://github.com/iNAYATechLab/File-Manager-Pro/compare/v0.1.1...v0.1.2) (2026-09-08)


### Bug Fixes

* **build:** ship properly signed release APKs (keystore from secrets) ([#7](https://github.com/iNAYATechLab/File-Manager-Pro/issues/7)) ([a441575](https://github.com/iNAYATechLab/File-Manager-Pro/commit/a4415752a2fca0a37815fc02459bc7583fce118e))

## [0.1.1](https://github.com/iNAYATechLab/File-Manager-Pro/compare/v0.1.0...v0.1.1) (2026-09-08)


### Bug Fixes

* **core:** resolve Kotlin compile errors found by CI ([#1](https://github.com/iNAYATechLab/File-Manager-Pro/issues/1)) ([90b57de](https://github.com/iNAYATechLab/File-Manager-Pro/commit/90b57dee07903bbd1e0e98d184950b6712d9779b))
