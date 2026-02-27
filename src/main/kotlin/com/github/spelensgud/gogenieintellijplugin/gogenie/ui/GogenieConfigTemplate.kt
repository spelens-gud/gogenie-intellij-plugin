package com.github.spelensgud.gogenieintellijplugin.gogenie.ui

object GogenieConfigTemplate {
    fun fullTemplate(): String {
        return """
global:
  project:
    name: ""
    module: ""
    version: "1.0.0"
    author: ""
  output:
    base_path: ./
    backup: true
    overwrite: false
    format: true
  template:
    base_dir: .gogenie/templates
    suffix: .tmpl
  exclude_dirs:
    - vendor
    - testdata
    - .git
    - node_modules

commands:
  autowire:
    enabled: true
    search_path: ./
    output_path: ./cmd/internal
    package: internal
    init_types:
      - "*"
    enable_cache: true
    parallel: 0
    exclude_dirs:
      - vendor
      - testdata
      - .git
      - node_modules
    include_only: []
    watch: false
    watch_ignore:
      - "*.gen.go"
      - wire_gen.go

  db2struct:
    enabled: true
    database:
      type: postgresql
      host: localhost
      port: 5432
      user: postgres
      password: ""
      database: ""
      schema: public
      tables: []
      charset: utf8mb4
      ssl_mode: disable
    output:
      path: ./internal/table
      package: table
      gorm_annotation: true
      json_tag: snake
      sql_tag: gorm
    options:
      comment_outside: true
      sql_info: false
      type_map:
        interface{}: string
    generic_template: model_generic
    generic_map_types:
      - int
      - string
    generate_cast: true
    cast_template: model_cast

  enum:
    enabled: true
    scope: ./
    indent: enum
    output_path: ./internal/enum
    package: enum
    template: enum

  http:
    enabled: true
    indent: service
    client:
      scope: ./
      output_path: ./clients
      template: http_client_api
      base_template: http_client_base
    router:
      scope: ./
      output_path: ./apis
      template: http_router
    api:
      scope: ./
      output_path: ./apis
      template: http_api
    swagger:
      enabled: true
      path: docs/swagger.json
      mainApiPath: ./apis/root.go
      success: 200 {object} object{data={{ .Response }},ok=bool}
      failed: 400,500 {object} object{message=string,ok=bool,code=int} "failed"
      produceType: application/json
      template: http_swagger

  impl:
    enabled: true
    scope: ./
    indents:
      - service: service
        output_path: ./internal/svc_impls
        structName: Service
        pkgPrefix: svc
        template: impl
        is_grpc_service: false
        grpc_suffix: ""
        enable_rule: true
      - service: dao
        output_path: ./internal/dao_impls
        structName: DaoImpl
        pkgPrefix: dao
        template: impl
        is_grpc_service: false
        grpc_suffix: ""
        enable_rule: true
      - service: grpc
        output_path: ./internal/grpc_impls
        structName: GrpcImpl
        pkgPrefix: grpc
        template: impl
        is_grpc_service: true
        grpc_suffix: Server
        enable_rule: true
    proto_scope: ./
    proto_paths:
      - proto

  mount:
    enable: true
    scope: ./
    name: mount
    args: []
    tag: json

  template:
    enabled: true
    model_path: ./internal/table
    template_dir: .gogenie/templates
    output_prefix: ./
    templates:
      - name: service
        template: ""
        output_path: service/{{ .PackageName }}.go
        package: ""
        overwrite: false
        enabled: true
      - name: dao
        template: ""
        output_path: dao/{{ .PackageName }}.go
        package: ""
        overwrite: false
        enabled: true
      - name: service_impl
        template: ""
        output_path: internal/svc_impls/svc_{{.PackageName}}/{{ .PackageName }}.go
        package: ""
        overwrite: false
        enabled: true
      - name: dao_impl
        template: ""
        output_path: internal/dao_impls/dao_{{.PackageName}}/{{ .PackageName }}.go
        package: ""
        overwrite: false
        enabled: true

  rule:
    enabled: true
    scope: ./
    concurrency: 4
    max_retries: 3
    dry_run: false
    verbose: false
    llm:
      provider: openai
      base_url: https://api.openai.com/v1
      api_key: ""
      model: gpt-4o-mini
""".trimIndent() + "\n"
    }
}
