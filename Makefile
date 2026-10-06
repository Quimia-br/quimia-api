# Atalhos de desenvolvimento. Uso: make run | make run PORT=9090 | make help
#
# O projeto exige JDK 21. Se o JDK abaixo existir, ele é usado no lugar do JAVA_HOME do ambiente.
# `make run` usa o banco configurado (.env ou SPRING_DATASOURCE_*); `make run-local` usa um banco
# descartável e não precisa de credenciais.
# O .env (se existir) é carregado pela própria aplicação (formato .properties: aspas valem como
# texto); as variáveis já definidas no terminal têm precedência sobre ele.

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

# ./mvnw (e não mvnw.cmd): o cmd.exe não lida com o "&" de "Instituto J&F" no caminho do projeto.
MVN := ./mvnw

.PHONY: help run run-local build test smoke

help:
	@echo "make run [PORT=8080]  sobe a API na porta indicada (padrao 8080)"
	@echo "make run-local     sobe a API num PostgreSQL descartavel (schema real + dados de exemplo)"
	@echo "make build            gera o jar sem rodar os testes"
	@echo "make test             roda os testes e a cobertura (H2)"
	@echo "make smoke            valida por HTTP contra um PostgreSQL descartavel (precisa de bash)"

run:
	@echo "API em http://localhost:$(PORT)"
	$(MVN) -B -ntp spring-boot:run

run-local:
	bash scripts/local-validation/run-local.sh

build:
	$(MVN) -B -ntp -DskipTests package

test:
	$(MVN) -B -ntp verify

smoke:
	bash scripts/local-validation/smoke.sh --build
