# GitHub → ServiceNow DevOps Change Velocity onboarding runbook

Order of operations for onboarding a GitHub Actions pipeline to DevOps Change Velocity in a customer instance, **after** the GitHub App (JWT) credential setup is complete. Based on a PDI build of the DevOps Change Velocity demo (Australia).

**Who does what**

| Tag | Owner |
|---|---|
| **SN** | ServiceNow admin (admin, sn_devops.admin) |
| **GH-ADMIN** | GitHub org owner / GitHub App manager |
| **REPO** | Repository owner (admin on the pilot repo) |

**Precondition (from JWT setup):** the OAuth 2.0 credential's **Get OAuth Token** succeeds (Outbound HTTP Log shows **201** on `api.github.com/app/installations/<id>/access_tokens`), and the `OAuthDevOpsGitHubJWTHandler` module access policy exists with **Result = Track** (it's auto-generated on the first token request).

---

## Phase 1: ServiceNow platform prep

### 1. Disable change models (only if the customer uses type-based changes) · SN
Run in **Global** scope (application picker and update set).

| Setting | Value |
|---|---|
| `com.snc.change_management.change_model.type_compatibility` | `true` |
| `com.snc.change_management.change_model.hide` | `true` (create it if missing; it only exists on upgraded instances) |
| All active `chg_model` records | `active = false` |

Script: `pdi_disable_change_models.js` (dry run first). Verify:
- `sys_properties_list.do?sysparm_query=nameSTARTSWITHcom.snc.change_management.change_model`
- `chg_model_list.do?sysparm_query=active=true` returns nothing

### 2. Application services per environment · SN
Confirm or create a CMDB application service for each environment (Development, Test, Production). The step Service/CI drives incident and outage policy inputs.

### 3. DevOps application · SN
**DevOps Change Workspace > Applications**: create the application, named for the business app/service rather than the repo.

---

## Phase 2: GitHub App readiness

### 4. Verify GitHub App permissions and events · GH-ADMIN
**GitHub App settings > Permissions & events**

| Repository permission | Access |
|---|---|
| Actions, Checks, Contents, Environments, Metadata, Pull requests, Secrets | Read-only |
| Deployments, Webhooks | Read and write |

- Subscribe to event: **Deployment protection rule**
- If any permission changed: **Install App > Configure > Review request > Accept new permissions**
- Installation repository access includes the pilot repo(s)

> One GitHub App ↔ one org ↔ one ServiceNow tool.

---

## Phase 3: Connect and discover (ServiceNow)

### 5. Connect GitHub from the Applications module · SN
**DevOps Change Workspace > Applications > (app) > Connect a tool > GitHub**

> Connect from **Applications**. The Homepage and Tools module entry points don't discover repos, plans, or pipelines.

1. Tool name: e.g. `GitHub - <org>`. URL: `https://api.github.com` (GitHub.com / Enterprise Cloud)
2. Credential type **OAuth 2.0 with JWT** → **Use an existing JWT credential record**
3. **GitHub app slug name** (runs the permission check) → **Connect** → **Next**
4. Tool access (Maintained by groups) → **Assign**
5. **Configure webhooks**: the repo list may be empty until discovery completes. Select **Skip** and configure from the repo record (step 8).
6. **Track plans** → **Track repositories** → optionally import history (≤ 90 days)
7. **Track pipelines**: the pipeline only appears after its first run (step 13). You can finish the playbook now and track it afterwards.

**If discovery hangs** ("discovery already running", no new `api.github.com` calls): delete the stuck **Requested** import request (capability *code*) and select **Discover** again (KB1759355).

### 6. Point the GitHub App webhook at the tool · SN
Tool record → **Configure GitHub App > Auto configure with existing token**. This sets the App webhook URL to `.../api/sn_devops/v2/devops/tool/apps?toolId=<tool sys_id>`. If it does nothing (KB2995978), paste the URL into the GitHub App manually (GH-ADMIN).

### 7. Verify test type mapping · SN
**DevOps > Integrations > Test Type Mappings**: **JUnit ↔ GitHub** must exist (base system). No separate test tool is needed; the test report action posts through the GitHub orchestration tool. TestNG is also sent as JUnit (point the action at `testng-results.xml`, not the directory).

### 8. Track and configure the repository · SN
Open the repository record (`sn_devops_repository`):
- **Track** ✔ (required for commit events). **Track file changes** is optional (per-file change lists).
- **Configure** → webhooks. **Configure status** must read **Configured**.
- REPO verifies **repo Settings > Webhooks** shows the ServiceNow webhook (`push`, `workflow_job`, `issues`).

### 9. Collect values for GitHub · SN → REPO
From the tool record (Classic UI, `sn_devops_tool`):
- Tool **sys_id**
- Integration token via **Copy token**

---

## Phase 4: Repository setup (GitHub)

### 10. Workflow requirements · REPO
- File at `.github/workflows/<name>.yml`. The workflow **`name` must equal the file name**.
- Every job has a **unique display `name`**. Each ServiceNow action's `job-name` must equal the display name of the job it runs in.
- Jobs under change control run **sequentially** (parallel jobs aren't supported for change automation).
- **Register Package** runs in a job **before** the change job.
- Reusable/composite workflows: `job-name: '<parent-job-name> / <child-job-name>'`.
- Reference work items in commit messages (`Fixes #12`) so commits link to issues.

Reference pipeline: `.github/workflows/build-and-deploy.yml`

| Job | ServiceNow actions |
|---|---|
| Build | Test report (JUnit), Register artifact |
| Deploy DEV / Deploy TEST | Change action (receipt), Test report (smoke) |
| Register Package | Register package |
| ServiceNow Change | Change action with `deployment-gate` → `{"environment":"production","jobName":"Deploy PROD"}` |
| Deploy PROD | `environment: production` (held by the gate) |

Actions pinned at `v7.1.0`: `servicenow-devops-change`, `-test-report`, `-register-artifact`, `-register-package`.

### 11. Repository secrets · REPO
**Settings > Secrets and variables > Actions > Secrets**

| Secret | Value |
|---|---|
| `SN_INSTANCE_URL` | `https://<instance>.service-now.com` (no trailing slash) |
| `SN_ORCHESTRATION_TOOL_ID` | Tool sys_id |
| `SN_DEVOPS_INTEGRATION_TOKEN` | Token from **Copy token** |

Assignment group, change type, and CI come from the ServiceNow step records. Don't pass `assignment_group` or `type` from the pipeline, because pipeline values override the Step form.

### 12. GitHub environments · REPO
**Settings > Environments**. Delete stale or unused environments first (rules from old apps don't enforce anything).

| Environment | Configuration |
|---|---|
| `development` | Defaults |
| `test` | Defaults |
| `production` | **Deployment protection rules** → select the ServiceNow GitHub App → **Save protection rules**. **Deployment branches** → `main`. No required reviewers. |

- Names are lowercase and must match `environment:` in the workflow and the `deployment-gate` JSON.
- Private repos need GitHub Enterprise Cloud for environments. If the App isn't listed under protection rules, check plan/visibility, installation repo access, and the Deployments permission/event (step 4).

### 13. First run · REPO
Push to `main` or **Run workflow**. This creates the pipeline, steps, and task executions in ServiceNow. Go back to the playbook (or the pipeline record) and **track the pipeline**.

---

## Phase 5: Pipeline step configuration (ServiceNow)

### 14. Configure steps · SN
Pipeline record → Steps (`sn_devops_step`). With a deployment gate, **the change attaches to the gated job's step (`jobName`), not the job that runs the action.**

| Step | Type | Change control | Change receipt | Change type | Assignment group | CI / Service |
|---|---|---|---|---|---|---|
| Build | Build and Test | ✘ | ✘ | — | — | — |
| Deploy DEV | Deploy | ✔ | ✔ | Normal | App team group | App – Development |
| Deploy TEST | Deploy | ✔ | ✔ | Normal | App team group | App – Test |
| Register Package | (blank) | ✘ | ✘ | — | — | — |
| ServiceNow Change | Manual | ✘ | ✘ | — | — | — |
| Deploy PROD | **Prod Deploy** | ✔ | ✘ | Normal | App team group | App – Production |

- **Prod Deploy** only on the step that actually deploys to production (DORA uses it).
- Change model blank everywhere (type-based).
- Change controlled branches: `*` or the release branch(es).
- CI and assignment group only appear on the form when Change control is checked.

---

## Phase 6: Verify

### 15. End-to-end run · REPO + SN
| Check | Expected |
|---|---|
| GitHub run graph | **Deploy PROD** shows **Waiting** until ServiceNow releases it |
| GitHub App > Advanced > Recent Deliveries | `deployment_protection_rule` delivered to the instance |
| Pipeline execution → Step Executions | CHG on Deploy DEV, Deploy TEST (receipts) and **Deploy PROD** |
| Change receipts | Created without approval; pipeline continues after about one poll interval (100 s minimum in v7.1.0) |
| PROD change | Normal, Production CI, correct assignment group; releases Deploy PROD at Implement |
| Test Summaries | `Unit Tests`, `Smoke Tests DEV`, `Smoke Tests TEST` per version |
| Artifacts / Packages | `<app>.jar` version registered; package `<app>-<version>` linked to the PROD change |
| Commits / Work items | Commits referencing `#N` linked to work items |

---

## Open items (carry into M3)
- **Receipts remain in Implement** even though `sn_devops.enable_change_receipt_state_transition = true`. Investigate the receipt state transition before building custom flow changes.
- **PROD change auto-proceeded:** "No approvals were generated from matched Decisions." Approval behavior comes from the active DevOps approval flow and policy (Manual / Minimal / Advanced). Define the policy rules with Change Management in M3.
- Validate DORA metrics population in DevOps Insights after several runs.

## Troubleshooting reference
| Symptom | Cause / fix | Source |
|---|---|---|
| Module access policy for `OAuthDevOpsGitHubJWTHandler` doesn't exist | Auto-generated on first token request; set Result = Track | KB1704422 |
| 404 on `access_tokens` | `iss` claim must be App ID or Client ID (never installation ID); Token URL uses installation ID | KB2984994 |
| Client ID has no dot (`Iv23…`) | Newer GitHub App format; use as-is | KB2984994 |
| `keytool`: "tampered with, or password incorrect" | Existing `.jks` in folder, or OpenSSL 3 vs older Java (re-export `.p12` with `-legacy`) | — |
| Connect times out, no GitHub calls in Outbound HTTP Log | Capture `sys_flow_context`, DevOps Error Logs, and crypto access-denied logs before deleting anything | — |
| Webhook step shows 0 repos | Discovery still running / stuck import request | KB1759355 |
| Repo **Configure status: Unconfigured** | Configure webhooks from repo record | KB2774012 |
| Deploy PROD not held | Stale/missing `production` protection rule, private repo without Enterprise Cloud, App webhook missing `toolId` | GitHub Actions configurations (limitations) |
| Test report fails `ENOENT target/surefire-reports` | Build never ran tests (e.g., missing `pom.xml` at repo root) | — |
| "Invalid token and toolid" | Repo secrets missing or wrong | — |

## Sources (ServiceNow docs, Australia)
- [OAuth 2.0 credentials for GitHub Apps – JWT](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/dev-ops-github-apps-oath-jwt.md)
- [Onboard GitHub to DevOps Change Velocity — Workspace](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/playbook-enter-github-instance-details.md)
- [GitHub integration with DevOps Change Velocity](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/github-integration-dev-ops.md)
- [GitHub Actions configurations](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/github-actions-integration-with-devops.md)
- [GitHub Deployment Gates for ServiceNow DevOps Change](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/github-deployment-gate-for-servicenow-devops-change.md)
- [ServiceNow DevOps custom actions from GitHub marketplace](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/servicenow-devops-custom-actions-from-github-marketplace.md)
- [Accelerating your DevOps change process](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/dev-ops-change-acceleration.md)
- [DevOps change models](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/devops-change-multimodel.md)
- [Change Models properties](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/change-management/change-models-properties.md)
- [DevOps test tool integration](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/dev-ops-test-tool-integration.md)
- [Artifacts and packages](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/using-dev-ops-release-change.md)
- [Associating work items to a commit](https://github.com/rapdev-io/frame/blob/main/knowledge/platforms/servicenow/docs/australia/markdown/it-service-management/devops-change-velocity/associating-multiple-work-items-to-a-commit-in-devops.md)
- KBs: [KB1704422](https://support.servicenow.com/kb?id=kb_article_view&sysparm_article=KB1704422), [KB2984994](https://support.servicenow.com/kb?id=kb_article_view&sysparm_article=KB2984994), [KB1759355](https://support.servicenow.com/kb?id=kb_article_view&sysparm_article=KB1759355), [KB2995978](https://support.servicenow.com/kb?id=kb_article_view&sysparm_article=KB2995978), [KB2774012](https://support.servicenow.com/kb?id=kb_article_view&sysparm_article=KB2774012), [KB1329217](https://support.servicenow.com/kb?id=kb_article_view&sysparm_article=KB1329217), [KB3123760](https://support.servicenow.com/kb?id=kb_article_view&sysparm_article=KB3123760)
