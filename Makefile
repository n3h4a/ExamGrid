# One-command entry points for ExamGrid. Run `make` to list them.
# Needs Docker for the stack and the integration tests; Java 21, Maven and Node 22 for local work.

.DEFAULT_GOAL := help
.PHONY: help up down reset logs infra api web test test-java test-web solve bench loadtest screenshots brand

FULL := docker compose -f docker-compose.full.yml
SOLVER_JAR := solver/target/examgrid-solver-1.0.0-SNAPSHOT.jar

help: ## List the available commands
	@awk 'BEGIN {FS = ":.*## "} /^[a-z-]+:.*## / {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

# ---------------------------------------------------------------- run the whole stack

up: ## Build and start Postgres, Redis, API and web app with demo data on :8088
	$(FULL) up -d --build
	@echo "ExamGrid is starting on http://localhost:8088 (admin / admin12345)"

down: ## Stop the stack, keeping its data
	$(FULL) down

reset: ## Stop the stack and delete its data (next `make up` starts fresh)
	$(FULL) down -v

logs: ## Follow the API logs
	$(FULL) logs -f api

# ---------------------------------------------------------------- local development

infra: ## Start only Postgres and Redis for local development
	docker compose up -d

api: ## Run the API on :8080 with demo data and Swagger UI (needs `make infra`)
	mvn -q -pl solver -am install -DskipTests
	SPRING_PROFILES_ACTIVE=dev mvn -pl backend spring-boot:run

web: ## Run the web app on :5173 with hot reload, proxying /api to :8080
	cd frontend && { [ -d node_modules ] || npm ci; } && npm run dev

# ---------------------------------------------------------------- quality

test: test-java test-web ## Run every check CI runs

test-java: ## Solver and backend: unit, property and Testcontainers tests with coverage
	mvn -B verify

test-web: ## Frontend: lint, typecheck, tests with coverage, production build
	cd frontend && { [ -d node_modules ] || npm ci; } \
		&& npm run lint && npm run typecheck && npm run test:coverage && npm run build

# ---------------------------------------------------------------- engine and performance

solve: ## Solve a synthetic university (180 courses, 2,400 students) from the CLI
	mvn -q -pl solver package -DskipTests
	java -jar $(SOLVER_JAR) --synthetic 180:2400

bench: ## Re-measure solver scaling into docs/benchmarks/scaling.csv
	mvn -q -pl solver,benchmarks package -DskipTests
	java -cp benchmarks/target/examgrid-benchmarks.jar com.examgrid.benchmarks.ScalingReport docs/benchmarks

loadtest: ## k6 result-day test against the running stack (needs k6 and a published plan)
	@command -v k6 >/dev/null || { echo "k6 is not installed: https://grafana.com/docs/k6/latest/set-up/install-k6/"; exit 1; }
	$(FULL) exec -T postgres psql -U examgrid -t -A -c "select roll from student" > loadtest/rolls.txt
	cd loadtest && k6 run -e ROLLS=rolls.txt seat-search.js

# ---------------------------------------------------------------- documentation assets

screenshots: ## Recreate docs/screenshots from a fresh demo stack (needs Playwright)
	$(FULL) down -v
	$(FULL) up -d --build --wait
	node tools/screenshots/capture.cjs

brand: ## Re-render the README banner and social preview (needs Playwright)
	node tools/brand/render.cjs
