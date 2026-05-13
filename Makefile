SHELL := /usr/bin/env bash
.SHELLFLAGS := --noprofile --norc -eu -o pipefail -c

.SUFFIXES:
.NOTPARALLEL:

REPO_ROOT := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))

TOPOLOGY ?= $(REPO_ROOT)sample_topologies/width_4/variant_2/topology.json
TOPOLOGIES ?= $(REPO_ROOT)generated/topologies
FRONTIER ?= $(REPO_ROOT)pareto_frontier_topologies
SAMPLES ?= $(REPO_ROOT)sample_topologies
MIN_WIDTH ?= 1
MAX_WIDTH ?= 4
WORKERS ?= 1

MILL_JOBS ?= $(shell n="$$(getconf _NPROCESSORS_ONLN 2>/dev/null || nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 1)"; [ "$$n" -gt 4 ] && echo 4 || echo "$$n")
MILL ?= $(REPO_ROOT)mill
MILL_FLAGS ?= --no-daemon -j $(MILL_JOBS)

MILL_COMMAND := env PS1= "$(MILL)" $(MILL_FLAGS)
APP_COMMAND := $(MILL_COMMAND) --ticker false prefixTopologyLibrary.run
SCALAFMT_MODULES := {prefixTopologyLibrary,prefixTopologyLibrary.test}

HELP_INPUT_VARIABLES := TOPOLOGY
HELP_ROOT_VARIABLES := TOPOLOGIES FRONTIER SAMPLES
HELP_WIDTH_VARIABLES := MIN_WIDTH MAX_WIDTH
HELP_EXECUTION_VARIABLES := WORKERS MILL_JOBS
HELP_TOOL_VARIABLES := MILL MILL_FLAGS

define PRINT_HELP_VALUES
@{ $(foreach var,$(1),printf '%s\t%s\n' "$(var)=" "$($(var))";) } | \
awk 'BEGIN { FS = "\t" } { v[++n] = $$1; d[n] = $$2; if (length($$1) > m) m = length($$1) } END { for (i=1; i<=n; i++) printf "  %-" m "s %s\n", v[i], d[i] }'
endef

.DEFAULT_GOAL := help

.PHONY: help inspect topologies frontier samples check test lint format clean clean-all

help: ## Show targets and current defaults
	@echo "Usage:"
	@echo "  make <target> [VAR=value ...]"
	@echo ""
	@echo "Input:"
	$(call PRINT_HELP_VALUES,$(HELP_INPUT_VARIABLES))
	@echo ""
	@echo "Directories:"
	$(call PRINT_HELP_VALUES,$(HELP_ROOT_VARIABLES))
	@echo ""
	@echo "Widths:"
	$(call PRINT_HELP_VALUES,$(HELP_WIDTH_VARIABLES))
	@echo ""
	@echo "Execution:"
	$(call PRINT_HELP_VALUES,$(HELP_EXECUTION_VARIABLES))
	@echo ""
	@echo "Tools:"
	$(call PRINT_HELP_VALUES,$(HELP_TOOL_VARIABLES))
	@echo ""
	@echo "Targets:"
	@awk 'BEGIN {FS = ":.*##"} /^[a-zA-Z0-9_.-]+:.*##/ { t[++n] = $$1; d[n] = $$2; if (length($$1) > m) m = length($$1) } END { for (i=1; i<=n; i++) printf "  %-" m "s %s\n", t[i], d[i] }' $(MAKEFILE_LIST)

inspect: ## Print canonical topology JSON and cell counts from TOPOLOGY
	@$(APP_COMMAND) inspect "$(TOPOLOGY)"

topologies: ## Generate full width-first topology artifacts in TOPOLOGIES
	@$(APP_COMMAND) topologies \
		"$(TOPOLOGIES)" "$(MIN_WIDTH)" "$(MAX_WIDTH)" "$(WORKERS)"

frontier: ## Curate module-first Pareto frontier artifacts from TOPOLOGIES into FRONTIER
	@$(APP_COMMAND) frontier \
		"$(TOPOLOGIES)" "$(FRONTIER)" "$(MIN_WIDTH)" "$(MAX_WIDTH)"

samples: ## Refresh checked-in sample topology artifacts in SAMPLES
	@$(APP_COMMAND) topologies \
		"$(SAMPLES)" "$(MIN_WIDTH)" "$(MAX_WIDTH)" "$(WORKERS)"

check: lint test ## Run formatting checks and tests

test: ## Run tests
	$(MILL_COMMAND) prefixTopologyLibrary.test

lint: ## Check source formatting
	$(MILL_COMMAND) "$(SCALAFMT_MODULES).checkFormat"

format: ## Format source files
	$(MILL_COMMAND) "$(SCALAFMT_MODULES).reformat"

clean: ## Remove generated topology files
	rm -rf -- "$(REPO_ROOT)generated"

clean-all: clean ## Remove generated topology files and Mill state
	rm -rf -- "$(REPO_ROOT)out"
