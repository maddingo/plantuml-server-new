# PlantUML Server

![Build Status](https://github.com/maddingo/plantuml-server-new/actions/workflows/maven.yml/badge.svg)

This is a re-implementation of the plantuml-sever from https://github.com/plantuml/plantuml-server in Spring Boot.

The image is available on docker hub with
```bash
docker run -p 8080:8080 maddingo/plantuml-new
```
Check the latest version on https://hub.docker.com/r/maddingo/plantuml-new/tags

Then go to http://localhost:8080 to see the plantuml server.

## Configuration
The application is configured like any other Spring Boot application, through either environment variables, command line arguments, application.properties of application.yml file.

An example can be found in the code repository [application.yml](plantuml-web/src/main/resources/application.yml)

## Proxy git ssh
You can proxy git ssh URLs `http://localhost:8080/proxy?src=git+ssh://git@github.com/my-org/my-repo?branch=main#path/to/file`

Make sure the `src` parameter is URL encoded, e.g. the URL above becomes `git%2Bssh%3A%2F%2Fgit%40github.com%2Fmy-org%2Fmy-repo%3Fbranch%3Dmain%23path%2Fto%2Ffile`.

This requires a private key in `application.yml` where the URL is stripped off `git+`.
