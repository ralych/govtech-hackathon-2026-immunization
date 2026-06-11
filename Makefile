# Handy compose shortcuts. Run `make` or `make help` for the list.

.PHONY: help restart log

help:
	@echo "Usage: make <target>"
	@echo ""
	@echo "Targets:"
	@echo "  restart   docker compose down + build + up --wait"
	@echo "  log       follow logs from all services (docker compose logs -f)"
	@echo "  help      show this message"

.DEFAULT_GOAL := help

restart:
	docker compose down && docker compose build && docker compose up --wait

log:
	docker compose logs -f
