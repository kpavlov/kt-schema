.SHELLFLAGS := -e -c

KOTLIN_TARGETS ?= full
PAGEFIND_VERSION ?= 1.6.0
MERMAID_VERSION ?= 12.2.0
SITE_DIR ?= docs/public
# Shown at the bottom of the site menu; Hugo reads HUGO_PARAMS_* as site.Params.
VERSION := $(shell sed -n "s/^version=//p" gradle.properties)
VERSION_SUFFIX := $(shell sed -n "s/^versionSuffix=//p" gradle.properties)
HUGO := HUGO_PARAMS_VERSION=$(VERSION)$(if $(VERSION_SUFFIX),-$(VERSION_SUFFIX)) hugo

.PHONY: all
all:clean lint build publish examples-snapshot knit

.PHONY: ci
ci:lint build knit apidocs publish examples-snapshot

.PHONY: build
build:
	@echo "🔨 Building..."
	@./gradlew -PkotlinTargets=$(KOTLIN_TARGETS) \
		kotlinUpgradePackageLock kotlinWasmUpgradePackageLock build
	@echo "☂️ Coverage reports..."
	@./gradlew -PkotlinTargets=$(KOTLIN_TARGETS) koverLog koverXmlReport koverHtmlReport
	@echo "✅ Build complete!"

.PHONY: test
test:
	@echo "🧪 Running tests..."
	@./gradlew -PkotlinTargets=$(KOTLIN_TARGETS) kotlinWasmUpgradePackageLock check --rerun-tasks
	@echo "✅ Tests complete!"

.PHONY: scan
scan:
	@echo "🔎 Running build with scan..."
	@./gradlew clean kotlinWasmUpgradePackageLock kotlinUpgradePackageLock build --scan --rerun-tasks
	@echo "✅ Build with scan is complete!"

.PHONY: apidump
apidump:
	@echo "🪏 API dump..."
	@./gradlew updateLegacyAbi

.PHONY: apidocs
apidocs:
	@echo "📚 Generating API documentation..."
	@rm -rf docs/public/apidocs
	@./gradlew :docs:dokkaGenerate
	@echo "✅ API docs generated!"

.PHONY: knit
knit:
	@echo "🪡🧶 Running Knit check ..."
	@./gradlew knit knitCheck --no-configuration-cache
	@./gradlew :docs:test --rerun-tasks
	@echo "✅ Knit check completed!"

# Depends on `site` so the search index exists; edits made while serving are not re-indexed until restart.
.PHONY: site-dev
site-dev: site
	@echo "🌐 Serving site at http://localhost:1313/kt-schema/ ..."
	@$(HUGO) server --source docs --environment development --openBrowser

.PHONY: site
site:
	@echo "📦 Building site bundle ($(SITE_DIR)) ..."
	@$(HUGO) --source docs --environment production --gc --minify --panicOnWarning --destination $(abspath $(SITE_DIR))
	@echo "🔎 Indexing site for search ..."
	@npx --yes pagefind@$(PAGEFIND_VERSION) --site $(SITE_DIR)
	@echo "🧜 Bundling Mermaid ..."
	@rm -rf $(SITE_DIR)/mermaid && mkdir -p $(SITE_DIR)/mermaid
	@npm pack mermaid@$(MERMAID_VERSION) --silent --pack-destination $(SITE_DIR)/mermaid > /dev/null
	@tar -xzf $(SITE_DIR)/mermaid/mermaid-$(MERMAID_VERSION).tgz -C $(SITE_DIR)/mermaid --strip-components=2 --exclude='*.map' \
		package/dist/mermaid.esm.min.mjs package/dist/chunks/mermaid.esm.min
	@rm $(SITE_DIR)/mermaid/mermaid-$(MERMAID_VERSION).tgz
	@echo "✅ Site bundle is ready in $(SITE_DIR)!"

# Builds into its own directory, so it never fights `make site-dev` over docs/public.
# The Dokka output (`make apidocs`) is linked in when present, so the API docs tests run too.
.PHONY: site-test
site-test:
	@rm -rf docs/build/site-test
	@$(MAKE) site SITE_DIR=docs/build/site-test
	@[ ! -d docs/public/api ] || ln -sfn $(abspath docs/public/api) docs/build/site-test/api
	@echo "🧭 Testing site in the browser ..."
	@SITE_DIR=$(abspath docs/build/site-test) node --test --test-timeout=60000 docs/e2e/site.test.mjs
	@echo "✅ Site tests passed!"

.PHONY: clean
clean:
	@echo "🧹 Cleaning build artifacts..."
	@./gradlew --stop
	@rm -rf ~/.m2/repository/me/kpavlov/kt/schema
	@rm -rf **/kotlin-js-store **/build **/.gradle/configuration-cache
	@echo "✅ Clean complete!"

.PHONY: lint
lint:
	@echo "🕵️‍♀️ Inspecting code..."
	@./gradlew detekt checkLegacyAbi
	@echo "✅ Code inspection complete!"

.PHONY: publish
publish:
	@echo "📰📦 Publishing to project repository (build/project-repo)..."
	@rm -rf build/project-repo
	@./gradlew publishAllPublicationsToProjectRepository -Pversion=1-SNAPSHOT
	@echo "✅ Version '1-SNAPSHOT' was published to build/project-repo! (1-SNAPSHOT)"

.PHONY: sync
sync:
	git submodule update --init --recursive --depth=1

.PHONY: examples
examples:
	@echo "📖 Running examples..."
	@(cd examples/gradle-google-ksp && rm -rf kotlin-js-store && ./gradlew clean build --no-daemon --rerun-tasks)
	@(cd examples/maven-java && rm -rf target && mvn package --no-transfer-progress)
	@(cd examples/maven-ksp && rm -rf target && mvn package --no-transfer-progress)
	@echo "✅ Examples complete!"

.PHONY: examples-snapshot
examples-snapshot:
	@echo "🧫 Running examples..."
	@(cd examples/gradle-google-ksp && rm -rf kotlin-js-store && ./gradlew clean build -PktSchemaVersion=1-SNAPSHOT --no-daemon --rerun-tasks)
	@(cd examples/maven-ksp && rm -rf target && mvn test -U -Plocal --no-transfer-progress)
	@echo "✅ Examples complete!"