FROM eu.gcr.io/cerno-245411/scalabuilder:latest AS build-env

ARG ARTIFACTORY_USERNAME
ARG ARTIFACTORY_PASSWORD

WORKDIR /

COPY . /

RUN sbt publish
