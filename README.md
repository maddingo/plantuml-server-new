# PlantUML Server

https://github.com/maddingo/plantuml-server-new/actions/workflows/maven/badge.svg

This is a new implementation of the plantuml-sever from https://github.com/plantuml/plantuml-server.

It aims to use more modern APIs and re-enable the unit tests rom the original.

The image is available on docker hub with

```bash
docker run -p 8080:8080 maddingo/plantuml-new:2.1.0-SNAPSHOT
```

Then go to http://localhost:8080 to see the plantuml server.