FROM mcr.microsoft.com/devcontainers/java:1-21-bookworm

# Yarn is unused here; its outdated signing key blocks feature installation.
RUN rm -f /etc/apt/sources.list.d/yarn.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*
