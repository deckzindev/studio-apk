package com.example.util

object WorkflowGenerator {

    const val DEFAULT_WORKFLOW_PATH = ".github/workflows/build-apk.yml"

    fun generateAndroidBuildWorkflow(): String {
        val d = "$"
        return """
name: Android APK Builder (GitBuilder)

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

concurrency:
  group: gitbuilder-""" + d + """{{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    name: Compile Android APK
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - name: Checkout Source Code
        uses: actions/checkout@v4

      - name: Setup JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
          cache: gradle

      - name: Prepare Gradle Environment
        run: |
          echo "Verificando gradlew e permissões..."
          if [ ! -f "gradlew" ]; then
            echo "Aviso: gradlew não encontrado na raiz. Gerando wrapper..."
            gradle wrapper --gradle-version 8.9 || gradle wrapper
          fi
          chmod +x gradlew
          ls -la

      - name: Prepare Signing Keystore
        run: |
          echo "Preparando chave de assinatura para versão Release..."
          if [ ! -f "my-upload-key.jks" ]; then
            keytool -genkeypair -v \
              -keystore my-upload-key.jks \
              -alias upload \
              -keyalg RSA \
              -keysize 2048 \
              -validity 10000 \
              -storepass "gitbuilder123" \
              -keypass "gitbuilder123" \
              -dname "CN=GitBuilder, OU=Android, O=GitBuilder, L=App, S=State, C=US"
            echo "Chave de assinatura local gerada com sucesso."
          fi

      - name: Compile Optimized Release APK
        env:
          KEYSTORE_PATH: """ + d + """{{ github.workspace }}/my-upload-key.jks
          STORE_PASSWORD: "gitbuilder123"
          KEY_PASSWORD: "gitbuilder123"
        run: |
          echo "Iniciando compilação do APK Release minificado com R8..."
          if ./gradlew assembleRelease --stacktrace --no-daemon; then
            echo "Compilação de Release finalizada com sucesso."
          else
            echo "Aviso: assembleRelease falhou neste projeto. Executando fallback com assembleDebug..."
            ./gradlew assembleDebug --stacktrace --no-daemon
          fi

      - name: Sign Unsigned APKs if needed
        if: always()
        run: |
          echo "Verificando se há arquivos APK sem assinatura..."
          find . -name "*unsigned*.apk" | while read -r unsigned_apk; do
            signed_apk=""" + d + """{unsigned_apk%-unsigned.apk}-signed.apk
            echo "Assinando """ + d + """unsigned_apk -> """ + d + """signed_apk..."
            if command -v apksigner >/dev/null 2>&1; then
              apksigner sign --ks my-upload-key.jks --ks-key-alias upload --ks-pass pass:gitbuilder123 --key-pass pass:gitbuilder123 --out """ + d + """signed_apk """ + d + """unsigned_apk
            else
              jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore my-upload-key.jks -storepass "gitbuilder123" -keypass "gitbuilder123" """ + d + """unsigned_apk upload
              mv """ + d + """unsigned_apk """ + d + """signed_apk
            fi
          done

      - name: Inspect Generated APKs
        if: always()
        run: |
          echo "Localizando e medindo arquivos APK gerados (Versão Release / Otimizada):"
          find . -name "*.apk" -exec ls -lh {} + || true

      - name: Upload APK Artifact
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: app-release-apk
          path: |
            **/build/outputs/apk/release/*-release*.apk
            **/build/outputs/apk/release/*-signed*.apk
            **/build/outputs/apk/release/*.apk
            **/build/outputs/apk/*release*/*.apk
            **/build/outputs/apk/debug/*.apk
            **/build/outputs/apk/*debug*/*.apk
          if-no-files-found: warn
          retention-days: 7
""".trimIndent()
    }
}
