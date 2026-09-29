# PDI + GitHub setup — ServiceNow DevOps Change Velocity demo

Setup for the `build-and-deploy` pipeline in this repo against a PDI on **Australia**, with **change models disabled** (type-based changes) and GitHub connected with **OAuth 2.0 GitHub App – JWT**.

---

## 0. Prerequisites

| Item | Notes |
|---|---|
| PDI with DevOps Change Velocity installed | Australia |
| Change models disabled | `com.snc.change_management.change_model.type_compatibility = true`, `com.snc.change_management.change_model.hide = true`, change models inactive (see `pdi_disable_change_models.js`) |
| GitHub repo | Push the contents of this folder to it |
| Repo visibility | ServiceNow's GitHub Actions limitations note that GitHub environments are available to **private** repos only on GitHub Enterprise Cloud. Use a **public** repo (or Enterprise Cloud) so the `production` environment protection rule works. |

## 1. Connect GitHub to the PDI (JWT)

Follow the official doc end to end: **OAuth 2.0 credentials for GitHub Apps – JWT**. Summary of the steps it covers:

1. Create a custom GitHub App.
2. Generate a Java KeyStore (JKS) certificate.
3. Attach the JKS certificate to the instance.
4. Create a JWT signing key.
5. Add a JWT provider for GitHub.
6. Register GitHub as an OAuth provider (JWT), and set the Module Access Policy for `OAuthDevOpsGitHubJWTHandler` to **Track**.
7. Create the credential record.

Then onboard the tool (DevOps Change Workspace playbook is the guided option) and **discover + configure** the repo. Configuring creates the `push`, `issues` and `workflow_job` webhooks.

Constraints from the docs:
- One GitHub App ↔ one GitHub org ↔ one GitHub tool. Additional orgs need separate tools/apps.
- Don't configure the same repo in more than one tool.
- JWT is what enables **GitHub environments + deployment gates** (not supported with basic auth).

## 2. Collect values from the GitHub tool record

**All > Tools > Orchestration Tools** → open the GitHub tool:

| Value | Where |
|---|---|
| Tool sys_id | Copy sys_id from the record |
| Integration token | **Copy token** (Classic UI) |

## 3. GitHub repo secrets and variables

**Settings > Secrets and variables > Actions**

Secrets (names match the ServiceNow docs):

| Secret | Value |
|---|---|
| `SN_INSTANCE_URL` | `https://<pdi>.service-now.com` |
| `SN_ORCHESTRATION_TOOL_ID` | GitHub tool sys_id |
| `SN_DEVOPS_INTEGRATION_TOKEN` | Token from the tool record |

Change assignment group is set on the **ServiceNow Change** pipeline step record in ServiceNow, not passed from the workflow. (Precedence per the DevOps change models doc: record preset > value passed from the pipeline > Step form, so the pipeline must not send `assignment_group` or it would override the step.)

## 3a. Test results — no separate test tool needed

The `servicenow-devops-test-report` action posts to `/api/sn_devops/v2/devops/tool/test?toolId=<SN_ORCHESTRATION_TOOL_ID>&testType=JUnit` (from the v7.1.0 action source). It reports **through the GitHub orchestration tool**, so you don't create a separate test tool record.

What you do need is a **test type mapping** between JUnit and the GitHub tool. The docs require the test type to be mapped to the orchestration tool:

1. **DevOps > Integrations > Test Types** (or DevOps Change Workspace > Administration > Integrations > Test types): confirm **JUnit** exists.
2. **DevOps > Integrations > Test Type Mappings**: confirm a mapping of **JUnit ↔ GitHub** exists. The base system tool mappings list includes `GitHub - JUnit`. If it's missing, create one: **Test type = JUnit**, **Tool integration = GitHub**.

Note: the action sends every JUnit/Surefire XML as `testType=JUnit` (Unit category), including the DEV/TEST smoke results. They show up as separate test summaries (named `Smoke Tests DEV/TEST - …`) but are still categorized as JUnit.

## 4. GitHub environments

**Settings > Environments** — create three:

| Environment | Configuration |
|---|---|
| `development` | none |
| `test` | none |
| `production` | **Deployment protection rules** → select the installed ServiceNow GitHub App → **Save protection rules**. Recommended: **Deployment branches and tags** → `main` only. |

Per the docs, the user who created the GitHub tool in ServiceNow must be a reviewer to approve workflows for GitHub environments.

## 5. First run

Push to `main` (or **Actions > build-and-deploy > Run workflow**). Expected flow:

```
Build → Deploy DEV → Deploy TEST → Register Package → ServiceNow Change → Deploy PROD (waiting)
```

`ServiceNow Change` creates the change and finishes immediately (with `deployment-gate` set, the action does not poll). `Deploy PROD` shows **Waiting** on the `production` protection rule until ServiceNow releases it.

## 6. Configure the pipeline in ServiceNow

After the first run the pipeline and its steps are created from the `workflow_job` events.

On the pipeline step for **ServiceNow Change**:
- Leave **Change model** empty. With type compatibility = true, a type-based change is created when the pipeline passes a type. The workflow passes `"type": "normal"` in `change-request.attributes`, so the step does not need a model.
- Optionally set **Change type = Normal** on the step as well.

Approval behavior for type-based DevOps changes comes from whichever of these flows is active: **DevOps Change Request Manual Approval**, **Minimal Automation Approval**, or **Advanced Automation Approval**. The change policy and its inputs are an M3 build item; for the demo the default manual approval is fine.

Approve the change and move it to **Implement** → the deployment gate releases **Deploy PROD**.

## 7. Where the demo data shows up

| Data | Navigation |
|---|---|
| Unit + smoke test summaries | **DevOps > Test Results > Test Summaries** (also on the Task Execution record) |
| Artifacts | **DevOps > Artifact > Artifacts** |
| Packages | **DevOps > Artifact > Packages** |
| Pipeline change requests | **DevOps > Orchestrate > Pipeline Change Requests** |
| Artifact/package staging issues | `sn_devops_artifact_staging` (Description explains what's missing) |

Test summaries created per run:

| Job | Summary name |
|---|---|
| Build | `Unit Tests - 1.0.<run>` |
| Deploy DEV | `Smoke Tests DEV - 1.0.<run>` |
| Deploy TEST | `Smoke Tests TEST - 1.0.<run>` |

## 8. Link commits to work items

The base system commit parser supports the hash pattern, so a GitHub issue reference in the commit message links the commit to the work item:

```
git commit -m "Add greeting length validation. Fixes #12"
```

The PR template prompts for this. Note: squash-merge titles end with `(#<PR number>)`, which also matches the hash pattern — reference the issue explicitly.

## 9. Demo scenarios

| Scenario | How |
|---|---|
| Happy path | Push to `main`, approve the change, watch PROD deploy |
| Failing unit test | Break an assertion in `GreetingServiceTest` → Build fails; the failed summary still posts to ServiceNow (`if: always()`) |
| Rejected change | Reject the change → Deploy PROD does not run |
| Multiple CI builds before a release | Several pushes; the package pulls in all artifact versions/commits since the last prod deploy |

## 10. Industry-standard choices in the workflow

- Least-privilege `permissions: contents: read`
- Concurrency: cancels superseded PR runs, never cancels an in-flight deployment on `main`
- Build once, promote the same jar through DEV → TEST → PROD (`upload-artifact` / `download-artifact`)
- Semantic version per run (`1.0.<run_number>`) via Maven CI-friendly `${revision}`
- Unit tests in CI, smoke tests post-deploy (JUnit 5 tags)
- GitHub environments with a protection rule for production
- Job timeouts; Dependabot for Maven and Actions
- Hardening option: pin actions to full commit SHAs instead of tags

## Sources

- [DevOps change models (Australia)](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/devops-change-multimodel.md)
- [GitHub integration with DevOps Change Velocity](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/github-integration-dev-ops.md)
- [OAuth 2.0 credentials for GitHub Apps – JWT](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/dev-ops-github-apps-oath-jwt.md)
- [GitHub Actions configurations](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/github-actions-integration-with-devops.md)
- [GitHub Deployment Gates for ServiceNow DevOps Change](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/github-deployment-gate-for-servicenow-devops-change.md)
- [ServiceNow DevOps custom actions from GitHub marketplace](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/servicenow-devops-custom-actions-from-github-marketplace.md)
- [Artifacts and packages](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/using-dev-ops-release-change.md)
- [DevOps test tool integration](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/dev-ops-test-tool-integration.md)
- [Add test results to change requests using test API](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/test-api-tools.md)
- [Associating multiple work items to a commit](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/associating-multiple-work-items-to-a-commit-in-devops.md)
- Action READMEs/source (v7.1.0): [change](https://github.com/ServiceNow/servicenow-devops-change), [test-report](https://github.com/ServiceNow/servicenow-devops-test-report), [register-artifact](https://github.com/ServiceNow/servicenow-devops-register-artifact), [register-package](https://github.com/ServiceNow/servicenow-devops-register-package)
