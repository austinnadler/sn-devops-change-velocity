# austin-devops-demo

Sample Spring Boot service used to demo **ServiceNow DevOps Change Velocity** with **GitHub Actions** — test results, artifacts, packages, and a change-gated production deployment.

## Pipeline

`.github/workflows/build-and-deploy.yml`

```
Build ──► Deploy DEV ──► Deploy TEST ──► Register Package ──► ServiceNow Change ──► Deploy PROD
```

| Job | What it sends to ServiceNow |
|---|---|
| Build | Unit test summary (JUnit), artifact version `austin-devops-demo.jar` `1.0.<run>` |
| Deploy DEV / Deploy TEST | Smoke test summary per environment |
| Register Package | Package `austin-devops-demo-1.0.<run>` |
| ServiceNow Change | Type-based Normal change with deployment gate on `production` |
| Deploy PROD | Released by the ServiceNow deployment protection rule |

Pull requests run **Build** only and skip ServiceNow reporting.

## Local

```bash
mvn verify                                   # build + unit tests
java -jar target/austin-devops-demo.jar       # http://localhost:8080/api/greeting?name=Austin
mvn test -Psmoke -Dsmoke.baseUrl=http://localhost:8080
```

## Setup

See [docs/SETUP.md](docs/SETUP.md).
