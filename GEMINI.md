# Development Instructions

The following guidelines govern all code development, refactoring, and problem-solving within this project. Please adhere to these principles rigorously.

## 1. Planning & Communication
* **Propose Before You Code**: Before diving into implementation for a new feature or complex refactoring, always outline a brief plan of action. We need to discuss and agree upon the architecture, libraries, and approach before you start modifying files.
* **Never Remove Features Without Asking**: Always explicitly ask for confirmation before removing existing features, UI elements (e.g., buttons), or user-facing functionality, even if you suspect they are obsolete or replaced by a new pattern.

## 2. Simplicity & Dependencies
* **Keep It Simple**: Prefer simple, readable, and maintainable code over overly clever or highly abstracted solutions.
* **Minimize Dependencies**: Do not introduce third-party libraries unless absolutely necessary. Rely on the standard library and native framework capabilities whenever possible. When a dependency is needed, justify its inclusion during the planning phase.

## 3. Code Correctness & Tooling
* **Zero Warnings Policy**: If the language toolchain supports linting, static analysis, or compilation warnings, use them. Code must compile and run without throwing warnings. Run `make verify` or the equivalent linting/testing commands to ensure structural correctness.
* **Maintain the Makefile**: Keep the project's `Makefile` up to date. Ensure it contains clear targets for building, cleaning, testing, and verifying the project (e.g., `make verify`). The `Makefile` should be the primary entry point for all common development tasks and CI/CD operations.

## 4. Idiomatic Code & Best Practices
* **Follow Official Guidelines**: Write idiomatic code that aligns with the official best practices for the language and framework (e.g., Google's App Architecture Guidelines for Android, standard Kotlin conventions).
* **Consult Documentation**: When unsure, consult the official documentation websites. Use the language's standard library source code as a benchmark for what good, idiomatic code looks like.

## 5. Performance & Efficiency
* **Monitor Resource Usage**: Actively consider the impact of new changes on performance, memory usage, and battery life.
* **Bulk Operations**: Always prioritize bulk database operations over iterative queries. Use the "Fetch-Merge-Upsert" pattern demonstrated in `EpisodeDao.syncNetworkEpisodes` to handle large datasets efficiently. For secondary data like Chapters, use bulk delete/insert transactions (e.g., `ChapterDao.updateChaptersBulk`).
* **Non-blocking Media Bridge**: Never use `runBlocking` within `MediaSession` callbacks. Utilize the `asListenableFuture` bridge to resolve media items asynchronously, ensuring the UI thread remains responsive.
* **Emulator Setup**: Use the `Makefile` commands `make avd-init` and `make emulator` to set up and run a performance-optimized Pixel 8 Pro environment for testing.
* **Test for Constraints**: Ensure that background tasks, media players, and network calls are optimized, properly bounded to the correct threads (using explicit Dispatchers), and cancelled cleanly when no longer needed.

## 6. Maintenance & Upgrades
* **Stateless Upgrades**: During rapid development, prefer a "Stateless Maintenance" strategy. Instead of complex SQL migrations, utilize the OPML (Subscriptions) and JSON (History) export/import features to preserve user state across destructive database schema updates.
* **Zero-Debt Schema**: If an experimental schema change is reverted, revert the database version number to match the stable version, avoiding unnecessary wipes for other developers.

## 7. Test-Driven Development (TDD)
* **Write Failing Tests First**: Always write a failing test before implementing the code to fix it. This proves the test works and defines the exact bounds of the implementation.
* **Maintain `TESTS.md`**: Whenever a new test is added or an existing test's behavior is changed, update `app/src/test/tests.md` (or the equivalent test documentation file). This file must explain what each test does in simple, human-readable English.
* **Beyond Unit Tests**: Do not rely solely on unit tests. Use integration tests for Background Workers and MediaSession callbacks.
* **Benchmark Verification**: Any change affecting list performance (e.g., the New Episodes feed) must be validated using the `benchmark` module to ensure it maintains target frame timings.

## 8. Documentation & Visuals
* **Keep Docs Synced**: Whenever making UI or layout changes, use an emulator or device to capture new screenshots of the updated screens. Ensure that the screenshots stored in `docs/images/` accurately reflect the current state of the application.
