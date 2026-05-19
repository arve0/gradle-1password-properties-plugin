FROM gradle:9-jdk17

USER root

RUN apt update \
 && apt install -y --no-install-recommends ripgrep fd-find ca-certificates curl file binutils \
 && ln -s /usr/bin/fdfind /usr/bin/fd \
 && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL https://git.io/shellspec | sh -s -- --yes --prefix /usr/local

USER gradle
WORKDIR /workspace

COPY --chown=gradle:gradle gradlew gradlew
COPY --chown=gradle:gradle gradle/ gradle/
RUN ./gradlew --version
