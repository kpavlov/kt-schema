.SHELLFLAGS := -e -c

KOTLIN_TARGETS ?= full

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
	@(cd examples/maven-ksp && rm -rf target && mvn package --no-transfer-progress)
	@echo "✅ Examples complete!"

.PHONY: examples-snapshot
examples-snapshot:
	@echo "🧫 Running examples..."
	@(cd examples/gradle-google-ksp && rm -rf kotlin-js-store && ./gradlew clean build -PktSchemaVersion=1-SNAPSHOT --no-daemon --rerun-tasks)
	@(cd examples/maven-ksp && rm -rf target && mvn test -U -Plocal --no-transfer-progress)
	@echo "✅ Examples complete!"