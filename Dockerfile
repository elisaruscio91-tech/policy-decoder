# Build multi-stage: compila frontend (ScalaJS) + backend (fat jar via
# sbt-assembly) in un'immagine con sbt, poi copia solo il risultato in
# un'immagine runtime minimale con la sola JRE.
#
# Immagine di build non compiler-verificata (nessun toolchain locale
# disponibile durante la scrittura): se il tag sbtscala/scala-sbt sotto non
# esiste più con questa combinazione JDK/sbt/Scala, è il primo posto da
# controllare — in alternativa usare un'immagine eclipse-temurin e installare
# sbt manualmente (https://www.scala-sbt.org/download).
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.5_11_1.10.6_3.3.4 AS build
WORKDIR /app

# Cache delle dipendenze: copiamo prima solo i file di definizione build.
COPY project/ project/
COPY build.sbt ./
RUN sbt update

COPY modules/ modules/

# ScalaJS: link di produzione, poi individuiamo la cartella di output senza
# assumere il nome esatto (dipende da `name` del progetto frontend) e la
# copiamo in static/ (main.js + eventuali chunk splittati dal linker).
RUN sbt frontend/fullLinkJS && \
    OUT_DIR=$(dirname "$(find modules/frontend/target -type f -name main.js -path '*opt*' | head -n1)") && \
    cp -r "$OUT_DIR"/. modules/backend/src/main/resources/static/

# Backend: fat jar via sbt-assembly, di nuovo localizzato con find invece di
# assumere name+version esatti nel path generato.
RUN sbt backend/assembly && \
    find modules/backend/target -type f -name "*-assembly-*.jar" -exec cp {} /app/app.jar \;

# --- Runtime ---------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar

# curl serve solo all'HEALTHCHECK sotto: eclipse-temurin:21-jre non lo include
# di default.
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
