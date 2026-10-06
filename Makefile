# Atalhos de desenvolvimento. Uso: make run | make run PORT=9090 | make help
#
# O projeto exige JDK 21. Se o JDK abaixo existir, ele é usado no lugar do JAVA_HOME do ambiente.
# As variáveis do arquivo .env (se existir) são carregadas pela própria aplicação; as do ambiente
# do terminal têm precedência.

PORT ?= 8080
JDK21 ?= C:/Program Files/Java/jdk-21.0.11

empty :=
space := $(empty) $(empty)
JDK21_ESCAPED := $(subst $(space),\ ,$(JDK21))
ifneq ($(wildcard $(JDK21_ESCAPED)/bin/java.exe),)
export JAVA_HOME := $(JDK21)
endif

export SERVER_PORT := $(PORT)
export SPRING_CONFIG_IMPORT := optional:file:.env[.properties]

ifeq ($(OS),Windows_NT)
MVN := mvnw.cmd
else
MVN := ./mvnw
endif

.PHONY: help run build test smoke

help:
	@echo "make run [PORT=8080]  sobe a API na porta indicada (padrao 8080)"
	@echo "make build            gera o jar sem rodar os testes"
	@echo "make test             roda os testes e a cobertura (H2)"
	@echo "make smoke            valida por HTTP contra um PostgreSQL descartavel (precisa de bash)"

run:
	@echo "API em http://localhost:$(PORT)"
	$(MVN) -B -ntp spring-boot:run

build:
	$(MVN) -B -ntp -DskipTests package

test:
	$(MVN) -B -ntp verify

smoke:
	bash scripts/local-validation/smoke.sh --build
