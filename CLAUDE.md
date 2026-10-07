# CLAUDE.md — Project Management Program (Project SA)

Functional spec + build guide for the app. Sources: `Business Process.drawio.xml` (pages *Clean
To-Be*, *Use-Case*, *Copy of Use-Case Narrative*, *ER Diagram*, *CRUD Table*, *State Diagram*) and
the team's `SA Project Note.txt`, plus decisions made with the user (§8).

- Read this before writing code. Re-check code against **§9 Bug-check checklist** when hunting bugs.
- Diagrams disagree in places → follow **§8**. Anything not covered here → **ask the user**, don't invent.
- Any item marked **OPEN** in §8 must be answered before implementing that part.

---

## 0. Tech stack & how to work

| Layer | Choice |
|---|---|
| Language | **Java 21** |
| Framework | **Spring Boot 4.1** (webmvc, Security 7, Data JPA / Hibernate 7, Validation, Thymeleaf, Flyway, DevTools). Boot 4 moved test classes, e.g. `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` |
| Build | **Maven** via wrapper (`mvnw` / `mvnw.cmd`) |
| UI | Server-rendered **Thymeleaf** + `thymeleaf-extras-springsecurity6` + **Bootstrap 5.3 + Bootstrap Icons** served as WebJars (`/webjars/bootstrap/dist/...`, works offline); small plain JS in `static/js/` |
| Database | **H2 only**, a local file DB: `jdbc:h2:file:./data/projectsa` (embedded in the app, nothing to install). **No PostgreSQL** or any other DB server — decided (§8); do not add PostgreSQL drivers, profiles, Docker or Testcontainers back |
| Migrations | **Flyway** (`src/main/resources/db/migration/V1__init.sql`, …) — schema lives only here, `ddl-auto=validate` |
| Auth | Spring Security form login, username = email, **BCrypt** password hashes |
| Files | Stored **on disk** under `./uploads/` (PDFs, attachments, profile pictures); DB stores path + metadata only |
| Tests | JUnit 5, Spring Boot Test, MockMvc + `spring-security-test` (`@WithMockUser`/`user()`); in-memory H2 |
| Language of UI | **English only** (all labels, messages, statuses) |

**Commands** (repo root, Windows PowerShell):
- Run: `.\mvnw spring-boot:run` → http://localhost:8080 (H2 console at `/h2-console`: URL `jdbc:h2:file:./data/projectsa`, user `sa`, no password)
- Test: `.\mvnw test` (uses an in-memory DB; never touches the demo data)

**Demo data (how the app is shown):** the app runs on one laptop for the demo. All data lives in
`./data/` (database) and `./uploads/` (files). **Both are committed to Git** (decided by the user
2026-10-07), so the same demo data moves to another laptop with `git push` / `git pull`. Always **stop
the app before `git add`/`commit`/`pull`** (the running app locks `data/projectsa.mv.db`), and change
data on one laptop at a time: the DB is a binary file, so two changed copies can't be merged — one side
wins. Prepare demo data once by using the app; it survives restarts. Reset = stop the app and delete
`data/` and `uploads/`. Schema changes must be new Flyway files (`V2__…`) so existing demo data is kept.

**Code layout** (package by feature, base package `com.projectsa`):
```
auth/          login, register (UC00), security config
employee/      Employee, EngineerSkill, profile (F4), engineer list for UC04
project/       Project, ProjectStatus (§6), list/search (F3), create (UC01), detail (UC03), assign (UC04)
solution/      UC02
testschedule/  UC05
requirement/   UC06, UC07
document/      file storage + download (PDF/attachments/avatars)
notification/  Notification, bell (F2)
dashboard/     F1
common/        exceptions, validation helpers
```
Layering: Controller (HTTP + form binding) → Service (**all** role/ownership checks, state
transitions, validation) → Repository. Never put business rules only in templates or JS.

**Conventions already in the code** (follow them):
- Page templates in `templates/<feature>/`, shared `head`/`navbar`/`scripts` fragments in `templates/fragments/layout.html`.
- Business-rule failures: the service collects **every** failing rule into a `List<FieldMessage>` and throws
  `common.FieldValidationException` once (`throwIfAny`; `collect(...)` wraps a check that throws, e.g.
  `FileStorage.check`). The controller calls `e.rejectInto(bindingResult)` so each message shows under its field.
- All errors in one round: each form's service has a `validate(...)` with the business rules only. When
  `bindingResult.hasErrors()`, the controller calls `validate(...)` instead of saving, so annotation and business-rule
  errors show together (`rejectInto` skips fields that already have an error).
- Values that can't be converted (letters in a number, tampered dropdown) get plain messages from
  `messages.properties` (`typeMismatch.<field>` / `typeMismatch.<type>`). No apostrophes there (MessageFormat).
- Form setters for emails trim the value, so `@Email` accepts `" a@b.com "`.
- Logged-in user: `@AuthenticationPrincipal CurrentUser` (id, name, role); authority `ROLE_<ROLE>`.
- Emails always stored via `Emails.normalize` (trimmed, lower-case).
- Enums in entities: `@Enumerated(STRING)` + `@JdbcTypeCode(SqlTypes.VARCHAR)` (matches VARCHAR + CHECK columns).
- Timestamps: `LocalDateTime`. Status changes only via `Project.changeStatus(...)` (which calls `ProjectStatus.moveTo`).
- Role checks: `@PreAuthorize("hasRole('X')")` on the controller **and** a check in the service
  (`ProjectService.requireRole` → `AccessDeniedException` → 403 page). "In charge" checks go in the service.
- After a successful POST: redirect + `RedirectAttributes.addFlashAttribute("success", ...)`; pages show it with
  the `flash` fragment from `layout.html`.
- Error pages: `templates/error/403.html`, `404.html`, `413.html`, and `templates/error.html` for every other status
  (400, 405, 500, ...). Browsers get HTML; API clients like curl get JSON. Stack traces and exception messages are
  off via `spring.web.error.include-*=never` (Boot 4 key; the old `server.error.*` keys are ignored, and DevTools
  turns both on by default).
- Not found → throw `common.NotFoundException` (404 page). Wrong project state / "not allowed right now" →
  throw `common.ActionNotAllowedException`; `WebExceptionHandler` redirects to the dashboard with a red message.
- Uploads: `document.FileStorage` — `check(file, allowedKinds, maxBytes, field)` detects the real type from the
  first bytes (`FileKind` PDF/PNG/JPG), `save(...)` stores `<folder>/<uuid><ext>` under `app.upload-dir`,
  `load(...)` refuses paths outside it. Metadata row in `ProjectDocument`. Downloads only via `/documents/{id}`
  (`DocumentService.forDownload` decides who may download; requirement attachments: `mayDownloadAttachments`, §8).
  Display/download names via `FileStorage.originalName`. If saving the DB row fails, delete the stored files.
- Detail-page loads: use `ProjectRepository.findDetailById` / `search` (join fetch), because
  `open-in-view=false` means lazy relations can't be read in templates.
- Status badge: `th:replace="~{fragments/layout :: status(${project.status})}"`.
- Profile picture: `th:replace="~{fragments/layout :: avatar(${employee})}"` (takes an `Employee` or the logged-in
  `CurrentUser`). Shows `/employees/{id}/avatar?v=<file name>` (login required, served by `DocumentController`) or the
  default icon when there is no picture.
- After changing your own profile (F4), `auth.CurrentUserSession.refresh(...)` replaces the logged-in `CurrentUser`, so
  the navbar shows the new name/picture without logging in again.
- Action buttons are shown by static `canX(project, user)` helpers next to the rule in each service
  (`SolutionService.canStoreSolution`, `AssignEngineerService.canAssign`, `TestScheduleService.canSetTestDate`,
  `RequirementService.canAddRequirement`, `ApprovalService.canDecide`, `CompleteProjectService.canComplete`). Use them
  for every new button (project page, F3 list, dashboard) so buttons and service checks never disagree.
- Notifications: `notification.NotificationService.notify(recipient, project, type, message)` inside the action's
  transaction (`Propagation.MANDATORY`), so they roll back with it. Add new `NotificationType` values per UC (with a
  label + Bootstrap icon for the F2 list). Reading (F2) only via `NotificationService` queries that filter on the
  recipient **and** `send_at <= now`; a notification row is shown with `th:replace="~{fragments/layout :: notification(${n})}"`
  inside a `.list-group` (put `th:each` on a wrapping `th:block`, because `th:replace` runs before `th:each`).
- Engineers on a project: `project.ProjectEngineer` (composite key, `Persistable`, so `save()` always INSERTs and a
  duplicate fails instead of merging). Read with `ProjectEngineerRepository.findForProject` (fetches skills too).
- Two people saving the same project at once: `@Version` → `ObjectOptimisticLockingFailureException` →
  `WebExceptionHandler` sends the second one to the dashboard with a red message.
- Tests: in-memory H2 config in `src/test/resources/application.properties`; `@SpringBootTest @Transactional`;
  MockMvc tests use `csrf()` for POSTs and `user(new CurrentUser(employee))` to log in as a role;
  shared helpers in `src/test/java/com/projectsa/TestData.java`.

**Git ignore:** `target/`, H2 temp files (`data/*.trace.db`, `*.lock.db`, `*.temp.db`), `.idea/`, `*.iml`, `.vscode/`.
`data/` and `uploads/` are **not** ignored (demo data travels with the repo).

### Progress
Last check (2026-10-06): 199 automated tests pass (`.\mvnw test`). The running app was also tested over HTTP as every
role (about 365 checks, including two people acting on the same project at once); no functional bugs were found.

| Part | Status | Where / tests |
|---|---|---|
| Project skeleton, full DB schema (V1), security, layout, 403/404/413 pages | ✅ done | `SecurityConfig`, `V1__init.sql`, `layout.html`, `error/` |
| File upload/download foundation | ✅ done | `document/` · `FileStorageTest` |
| Project state machine (§6) | ✅ done | `ProjectStatus`, `Project.changeStatus` · `ProjectStatusTest` |
| Reference SQL for every UC (§5b) | ✅ done | all queries run against the real schema |
| **UC00** Register + login/logout | ✅ done | `auth/` · `RegistrationServiceTest`, `AuthFlowTest` |
| **UC01** Create Project Profile | ✅ done | `project/ProjectService`, `ProjectController`, `project/new.html` · `ProjectServiceTest`, `ProjectControllerTest` |
| **UC02** Store Project Solution Detail | ✅ done | `solution/`, `solution/form.html` · `SolutionServiceTest`, `SolutionAndDetailWebTest` |
| **UC03** Check Project Detail | ✅ done | `/projects` list + `/projects/{id}` detail + PDF download done (`ProjectDetailController`, `DocumentController`). Assigned engineers (with skills, assigned by/at) + PM in charge done. Test schedule (all rounds, latest first, *Upcoming* badge) done. Customer requirements (priority, due date, status, decided by/at, reject reason, attachments) done; approved requirement attachments are listed under Documents (checked with UC07). *Review Requirement* (Sale in charge) and *Complete Project* (PM in charge, confirmation dialog) buttons. List with search = F3 |
| **UC04** Assign Engineer to Project | ✅ done | `project/AssignEngineerService`, `AssignEngineerController`, `ProjectEngineer`, `project/assign.html`, `js/assign.js`, `notification/` (rows only, bell is F2) · `AssignEngineerServiceTest`, `AssignEngineerWebTest`. Profile pictures from F4. After an approved requirement only the PM in charge may re-confirm |
| **UC05** Set Project Test Calendar Notification | ✅ done | `testschedule/` (`TestSchedule`, `TestScheduleService`, `TestScheduleController`), `testschedule/form.html`, *Set Test Date* button on UC03, `NotificationService.notifyAt` + `TEST_SCHEDULED`/`TEST_REMINDER` (rows only, bell is F2) · `TestScheduleServiceTest`, `TestScheduleWebTest` |
| **UC06** Add Customer New Requirement | ✅ done | `requirement/` (`Requirement`, `Priority`, `RequirementStatus`, `RequirementService`, `RequirementController`), `requirement/form.html`, *Add Requirement* button on UC03 (only PM in charge, `TESTING`, test time passed), `REQUIREMENT_PENDING` notification to Sale in charge (row only, bell is F2), attachments = `ProjectDocument` linked to the requirement · `RequirementServiceTest`, `RequirementWebTest` |
| **UC07** Approve New Project Requirement | ✅ done | `requirement/ApprovalService`, `ApprovalController`, `ApprovalForm`, `requirement/decide.html` (`/requirements/{id}/decision`); step 1 list = SALE dashboard "Requirements waiting for my approval", plus *Review* on UC03. Hidden `version` field + `@Version` stop double decisions. `REQUIREMENT_APPROVED` (PM + Presale + engineers) / `REQUIREMENT_REJECTED` (PM) notifications (rows only, bell is F2) · `ApprovalServiceTest`, `ApprovalWebTest` |
| Complete Project | ✅ done | `project/CompleteProjectService`, `CompleteProjectController` (`POST /projects/{id}/complete`), confirmation dialog on UC03, "available from …" hint for the PM before the week is over, link on PM dashboard. `PROJECT_COMPLETED` to Sale, Presale, engineers · `CompleteProjectServiceTest`, `CompleteProjectWebTest` |
| **F1** Dashboard per role | ✅ done | `dashboard/DashboardController`, `dashboard.html`. Every role: "Latest notifications" (5 newest unread, *See all*). SALE: counts of my projects by status (each tile opens the F3 list with `saleId` + `status`) + *Create Project* + "Requirements waiting for my approval" (*Review*) + "My projects". PRESALE: "Need solution" (with *Add Solution*) + other projects. PM: "Need engineers" (with *Assign Engineers*) + "My projects in Testing" (test Upcoming/Done, *Add Requirement* when done, *Complete Project* or "can be completed from …") + "My requirements" (status + reject reason) + "My projects (PM in charge)". TECH: "Need test date" (with *Set Test Date*) + "Upcoming tests" + "My assigned projects" · `DashboardWebTest` + dashboard checks in each UC web test |
| **F2** Notification bell | ✅ done | `notification/NotificationController` (`GET /notifications`, `POST /notifications/{id}/open`, `POST /notifications/read-all`), `NotificationBellAdvice` (`unreadCount` for the navbar on every page, error pages too), `NotificationService` (inbox/count/open/mark all), `notification/list.html`, `notification(n)` fragment in `layout.html` · `NotificationWebTest` |
| **F3** Search & filter project list | ✅ done | `project/ProjectSearch` (URL params, lenient), `ProjectListService` (row buttons), `ProjectRepository.search`, `project/list.html` · `ProjectSearchTest`, `ProjectListWebTest` |
| **F4** Edit my profile (+ picture) | ✅ done | `employee/ProfileService`, `ProfileController`, `NameForm`/`PasswordForm`/`PictureForm`/`SkillForm`, `employee/profile.html`; avatar download `GET /employees/{id}/avatar` (`DocumentController`); `auth/CurrentUserSession`; navbar picture + link to `/profile` · `ProfileWebTest`, `ProfilePictureTest` |

Update this table and tick §9 whenever a part is finished.

### Known bugs (to fix)
None right now. Add each bug found here; fix it with a regression test, then delete it from this list.

---

## 1. Purpose

Web app for a system-integration company. Replaces email/document/LINE hand-offs between
departments with one **Project Profile** per customer project that every role reads and writes.

| Pain point | How the app solves it |
|---|---|
| 1 Customer meeting/test appointments get mixed up | UC05 test schedule + notifications |
| 3 Sale unavailable mid-deal, slow hand-off | Profile records persons in charge; everyone can read it (UC03) |
| 5 PM doesn't know engineers' skills | Skills entered at register/profile; filter in UC04 |
| 7 Delays from mid-project requirements | Formal requirement flow with status (UC06 → UC07) |
| 14 Project details lost in hand-over | Single Project Profile + solution PDFs (UC02, UC03) |

---

## 2. Actors (roles)

Every user is an **Employee** with **exactly one role**, chosen at registration (UC00):

| Role (enum) | Main responsibilities |
|---|---|
| **SALE** | Create project profile (UC01), approve/reject new requirements (UC07) |
| **PRESALE** | Store solution details + PDFs (UC02) |
| **PM** | Assign engineers (UC04), add customer new requirements (UC06) |
| **TECH** (Tech Engineer) | Set test schedule (UC05); has skills |

All four can Check Project Detail (UC03), use Dashboard, Notifications, Search and Profile (§4b).
Purchasing/Stock appears in the To-Be flow but is **not** a user of the app (§8).

---

## 3. To-Be business process (summary)

1. Sale accepts a project → **creates Project Profile** (UC01) → `NewProject`.
2. Presale designs solution; customer confirms → **Presale stores solution + PDFs** (UC02) → `WaitingForAssignEngineer`.
   *(Equipment purchasing/stock happens outside the app.)*
3. PM checks detail (UC03), plans → **assigns engineers by skill** (UC04) → `Working`.
4. Engineers install and test internally (fix → retest, outside the app).
5. Tech Engineer **sets the customer test date** (UC05) → `Testing`.
6. After the customer test has taken place (test date/time passed):
   - OK → UAT + handover → **PM clicks *Complete Project*** → `Finish`.
   - Customer wants more → **PM adds new requirement** (UC06) → `WaitingForApprove` →
     **Sale approves** (UC07) → back to `NewProject` (Presale updates solution, repeat from step 2),
     or **rejects** → project `Finish`.

---

## 4. Use cases

| UC | Name | Actor(s) |
|---|---|---|
| UC00 | Register Employee *(team notes)* | Anyone not logged in |
| UC01 | Create Project Profile | SALE |
| UC02 | Store Project Solution Detail | PRESALE |
| UC03 | Check Project Detail | SALE, PRESALE, PM, TECH |
| UC04 | Assign Engineer to Project | PM |
| UC05 | Set Project Test Calendar Notification | TECH |
| UC06 | Add Customer New Requirement | PM |
| UC07 | Approve New Project Requirement | SALE |

### UC00 Register Employee — anyone
1. Enter **Name**.
2. Choose **exactly one Role**: Tech Engineer, PM, Sale, Presale.
3. Enter **Email** (any valid address; becomes the login username).
4. Enter **Password** + **Confirm Password**.
5. If role = Tech Engineer: prompt **"Please Assign Your Skills :"** (e.g. `CCNA`). Each *Add skill*
   click adds it to a list and shows the prompt again, until **Submit**.
6. Validate: name required; role is one of four; email valid + unique (case-insensitive); password
   ≥ 8 chars and equals confirm; Tech ≥ 1 skill, no duplicates (case-insensitive); non-Tech → no skills saved.
7. Save employee (BCrypt hash), role, skills → redirect to login.
- Class demo: anyone may register with any role, no admin approval.

### UC01 Create Project Profile — SALE
- Post: project created with status `NewProject`, `est_budget` NULL.
- **Required:** Project Name, Sale in Charge (employee with role SALE, defaults to current user),
  Presale in Charge (employee with role PRESALE), Customer Name, Customer Email (valid format),
  Project Site (address fields, §5).

### UC02 Store Project Solution Detail — PRESALE
- Pre: project in `NewProject` and current user is the project's **Presale in charge**. Post: `WaitingForAssignEngineer`.
1. List projects → select → load.
2. Enter **Start Date**, **End Date** (≥ start), **Project Objective**, **Estimated Budget** (number ≥ 0) — all required.
3. Upload **Solution PDF** and **Product Requirement PDF** — PDF only (check content, not just extension), ≤ 50 MB each.
4. Save data + files → update status.
- When coming back after an approved requirement (UC07), existing values are pre-filled; uploading
  a new PDF adds a new version (old files kept).

### UC03 Check Project Detail — all roles (read-only)
1. List projects (with F3 search) → select.
2. System checks project exists (404 otherwise) and shows: name, status, **Sale in Charge**,
   **Presale in Charge**, PM, assigned engineers, **Customer Name**, customer email, **Project Site**,
   dates, objective, budget, test schedule, requirements with their status.
3. Download buttons for **Solution PDF**, Product Requirement PDF, and **approved requirement attachments**.
- Never modifies anything.

### UC04 Assign Engineer to Project — PM
- Pre: project in `WaitingForAssignEngineer`, ≥ 1 Tech Engineer exists. Post: `Working`.
1. List projects → select.
2. System shows **engineer list: every Tech Engineer with their skills** (+ profile picture).
3. PM **filters by skill** → list shows only engineers having that skill.
4. PM selects ≥ 1 engineer → save to `project_engineer`; current PM saved as project PM (first round only).
   After an approved requirement only the **PM in charge** (`pm_id`) may re-confirm; `pm_id` never changes.
5. Notify each newly assigned engineer.
- No duplicate assignment of the same engineer to the same project.

### UC05 Set Project Test Calendar Notification — TECH
- Pre: project in `Working` and current user is **assigned** to it. Post: `Testing`.
1. List my assigned projects → select.
2. Enter **test date/time** (must be in the future) + **test details**.
3. Save schedule → status `Testing`.
4. Create notifications for the project's **Sale, PM and assigned engineers**: one immediately
   ("test scheduled") and one reminder with `send_at` = 1 day before the test.
- Sale and PM can view the schedule (UC03).

### UC06 Add Customer New Requirement — PM
- Pre: current user is the project's **PM in charge** (`pm_id`), project in `Testing` **and the customer test is finished** (latest test schedule's `test_at`
  ≤ now). Before that, the *Add Requirement* action is hidden and the service rejects it.
  Post: requirement `PENDING`, project `WaitingForApprove`.
1. List projects (ID, name, customer, status) → select → show details.
2. Enter **Title**, **Detail** (required), **Priority** (LOW/MEDIUM/HIGH), **Customer Due Date**
   (required, today or later), **Related Feature** (optional).
3. Optional attachments: **PDF, JPG or PNG** (check content), ≤ 50 MB each.
4. Save with ID, created_at, created_by, status `PENDING` → notify the project's Sale in charge.

### UC07 Approve New Project Requirement — SALE
- Pre: requirement `PENDING`, project `WaitingForApprove`, current user is the project's **Sale in charge**.
1. List **pending requirements of my projects** → select one.
2. System re-checks it is still `PENDING`, shows detail + attachments.
3. Choose **Approve** or **Reject** (reason **required** for reject).
4. Save decision (decided_by, decided_at, reason) → update requirement status.
5. Notify PM (title, decision, reason). If approved, also notify the Presale in charge (to update the solution) and the assigned engineers; attachments
   become visible as approved documents in UC03.
6. Project status: Approved → `NewProject`; Rejected → `Finish`.

### Complete Project — PM *(handover; `TESTING → FINISH`, no UC number in the diagrams)*
- Pre: project in `Testing`, current user is the project's **PM in charge**, and **one week has passed since the
  customer test** (latest `test_at` + 7 days ≤ now) with no new requirement (a new requirement moves the project out of
  `Testing`, §8). Before that the button is hidden and the PM sees "available from …".
  PM clicks **Complete Project** on the project page and confirms.
- Post: project `Finish` (read-only). Notify the project's Sale, Presale and assigned engineers.

---

## 4b. Extra features (team-approved)

Same role (§7) and state (§6) rules apply.

### F1 Dashboard per role — `/dashboard`, landing page after login
| Role | Shows |
|---|---|
| SALE | My projects (count by status) · **requirements waiting for my approval** · *Create Project* button |
| PRESALE | My projects in `NewProject` (**need solution**) · my other projects |
| PM | Projects in `WaitingForAssignEngineer` (**need engineers**) · my projects in `Testing` · my requirements' results |
| TECH | My assigned projects · those in `Working` (**need test date**) · upcoming test dates |
Plus latest unread notifications. Every item links to the matching page.

### F2 Notification bell
- Bell in the navbar on every page with **unread count** (only mine, `read_at` NULL and `send_at` ≤ now).
- List of my notifications, newest first; clicking one marks it read and opens the project (UC03).
- *Mark all as read*. A user can only see/mark their own notifications.
- Sources: UC04 (assigned), UC05 (scheduled + reminder), UC06 (to Sale), UC07 (to PM; on approve also Presale + engineers).
- In-app only (no email).

### F3 Search & filter project list
- Search box: project ID, project name, customer name (case-insensitive, partial match).
- Filters: status, Sale in charge, *only my projects*. Combined with AND, newest first, 20 per page,
  filters kept when paging.
- All roles see all projects; row action buttons appear only if role + state allow.

### F4 Edit my profile — `/profile`, own account only
- Edit **name**.
- Change **password**: current password + new + confirm (UC00 rules).
- Change **profile picture**: JPG or PNG only (check content), ≤ 2 MB; replaces the old one;
  stored under `uploads/avatars/`. No picture → default avatar. Shown in the navbar, profile page and UC04 engineer list.
- Tech Engineer: add skills (same prompt) and remove own skills; ≥ 1 must remain.
- **Cannot** change role, email, or ID (ignore/reject those fields if submitted).

---

## 5. Data model

### ER diagram (as drawn)
- **Employee** (<u>EmployeeID</u>, Name) → Sales, Tech Engineer (+Skills), PM, Presale.
  Diagram says overlapping; **decided: one role per employee**.
- **Project** (<u>ProjectID</u>, ProjectName, EstBudget, Status, EmployeeEnlist, Date {PeriodDate,
  TestDate}, Detail {BaseDetail, RequirementDetail}, Address {BuildingNumber, VillageNumber, Alley,
  Soi, Street, Subdistrict, District, Province, Zipcode}).
- Relationships to Project: Sales Create 1:M, Sales ApproveRequirement 1:M, all roles CheckProject,
  Presale StoreProjectSolutionDetail 1:M, PM AssignEngineerToProject 1:M, PM AddCustomerNewRequirement 1:M,
  Tech SetCalendarNotification M:N.

### Tables (Flyway `V1__init.sql`) — agree names with the teammates writing queries
```
employee(employee_id PK, name, email UNIQUE, password_hash,
         role VARCHAR CHECK IN ('SALE','PRESALE','PM','TECH') NOT NULL,
         profile_picture_path NULL, created_at)
engineer_skill(id PK, employee_id FK, skill, UNIQUE(employee_id, skill))

project(project_id PK, project_name, customer_name, customer_email,
        sale_id FK, presale_id FK, pm_id FK NULL,
        objective NULL, est_budget NULL, start_date NULL, end_date NULL,
        status VARCHAR CHECK IN (§6 values) NOT NULL, created_by FK, created_at, updated_at,
        building_no, village_no NULL, alley NULL, soi NULL, street NULL,
        subdistrict, district, province, zipcode)
project_engineer(project_id FK, employee_id FK, assigned_by FK, assigned_at,
                 PRIMARY KEY(project_id, employee_id))
project_document(id PK, project_id FK, requirement_id FK NULL,
                 type CHECK IN ('SOLUTION','PRODUCT_REQUIREMENT','REQUIREMENT_ATTACHMENT'),
                 original_name, file_path, mime, size_bytes, uploaded_by FK, uploaded_at)
project_test_schedule(id PK, project_id FK, test_at, detail, created_by FK, created_at)
requirement(requirement_id PK, project_id FK, title, detail,
            priority CHECK IN ('LOW','MEDIUM','HIGH'), due_date, related_feature NULL,
            status CHECK IN ('PENDING','APPROVED','REJECTED'), reject_reason NULL,
            created_by FK, created_at, decided_by FK NULL, decided_at NULL)
notification(id PK, recipient_id FK, project_id FK NULL, type, message,
             send_at, read_at NULL, created_at)
```
The actual schema is `V1__init.sql`; it also has a `version` column on `project` and `requirement`
for optimistic locking (`@Version`), which prevents double decisions in UC07. Never edit an applied
migration — add `V2__...sql` instead (changing even a comment breaks Flyway on existing demo data; V1's
header comment still mentions PostgreSQL — leave it). Use `VARCHAR + CHECK` for status-like columns. Files: store with a
generated name (UUID) under `uploads/projects/{projectId}/` or `uploads/avatars/`; never use the
user's filename as the path.

---

## 5b. Use-case queries (teammates' narrative queries, corrected to the real schema)

Source: teammates' *Use-Case Narrative* queries. These are the **reference SQL** for the report and
for checking the code; the app itself uses JPA repositories that must do the same thing.
`:name` = bound parameter (never paste user input into SQL). `:me` = logged-in employee ID.

### Problems found in the original queries (all fixed below)
| # | Problem | Where | Fix |
|---|---|---|---|
| 1 | Tables that don't exist: `ADDRESS`, `EMPLOYEEENLIST`/`EnlistEmployee`, `TechEngineer`, `Date`, `ProjectProfile`, `RequirmentDetail` | UC01, UC03–UC07 | Address is columns on `project`; Sale/Presale/PM are `project.sale_id/presale_id/pm_id`; engineers in `project_engineer`; skills in `engineer_skill`; test date in `project_test_schedule`; requirements in `requirement` |
| 2 | Rows looked up by `ProjectName` (not unique) | UC02, UC03 | Always use `project_id` |
| 3 | `WHERE ProjectID = ProjectID` — always true, returns every row | UC03 5.3.1, 5.4.1 | `WHERE project_id = :projectId` |
| 4 | JOINs with no project filter → returns Sales/Presales of **all** projects | UC03 5.1.1, 5.2.1 | Join on `p.sale_id` and filter `p.project_id = :projectId` |
| 5 | UC07 `UPDATE ... WHERE status='รออนุมัติ'` with no requirement ID → approves/rejects **every** pending requirement; `UPDATE ... JOIN` is not valid SQL | UC07 7.1 | `UPDATE requirement ... WHERE requirement_id = :id AND status = 'PENDING'` |
| 6 | `UPDATE Project SET TechnicianID` — one column can't hold many engineers, and UPDATE can't add a first value to a missing row | UC04 7.1, UC05 4.1 | `INSERT INTO project_engineer` per engineer; `INSERT INTO project_test_schedule` |
| 7 | Syntax errors: `VAULES`, `==`, `Est.Budget` (dot in a name), missing quotes, `'ee.PMID'` (a text literal, not a column) | UC01, UC04, UC06 | Fixed |
| 8 | Thai status values `'รออนุมัติ'`, `'อนุมัติ/ปฎิเสธ'` | UC06, UC07 | App is English-only; values are `PENDING` / `APPROVED` / `REJECTED` (DB CHECK) |
| 9 | Status changes missing (project never moves to the next state) | UC02, UC04–UC07 | Added `UPDATE project SET status = ... WHERE status = <expected>` |
| 10 | Budget inserted in UC01 | UC01 4.1 | Budget is set in UC02 (§8) |
| 11 | `AttachmentFile` column on requirement; files saved in table rows | UC06 8 | Files go to `project_document` (path + metadata) |
| 12 | Missing queries: file insert/download, notifications, requirement title, checks | UC02–UC07 | Added |

The `AND status = '<expected>'` in every status UPDATE is the state-machine guard (§6): if it
updates 0 rows, the project was not in the right state and the action must fail.

### UC00 Register
```sql
SELECT COUNT(*) FROM employee WHERE email = :email;                 -- email already normalized (lower-case)
INSERT INTO employee (name, email, password_hash, role, created_at)
VALUES (:name, :email, :bcryptHash, :role, CURRENT_TIMESTAMP);
INSERT INTO engineer_skill (employee_id, skill) VALUES (:newEmployeeId, :skill);   -- once per skill, TECH only
```

### UC01 Create Project Profile — SALE
```sql
-- dropdowns for persons in charge
SELECT employee_id, name FROM employee WHERE role = 'SALE'    ORDER BY name;
SELECT employee_id, name FROM employee WHERE role = 'PRESALE' ORDER BY name;

INSERT INTO project (project_name, customer_name, customer_email, sale_id, presale_id, status,
                     building_no, village_no, alley, soi, street, subdistrict, district, province, zipcode,
                     created_by, created_at, updated_at)
VALUES (:projectName, :customerName, :customerEmail, :saleId, :presaleId, 'NEW_PROJECT',
        :buildingNo, :villageNo, :alley, :soi, :street, :subdistrict, :district, :province, :zipcode,
        :me, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
```

### UC02 Store Project Solution Detail — PRESALE
```sql
-- 1.1 projects waiting for a solution
SELECT project_id, project_name, customer_name, status
FROM project
WHERE status = 'NEW_PROJECT'
  AND presale_id = :me                         -- only the Presale in charge
ORDER BY created_at DESC;

-- 3.1 load the selected project
SELECT * FROM project WHERE project_id = :projectId;

-- 8 save solution data + move state
UPDATE project
SET start_date = :startDate, end_date = :endDate, objective = :objective, est_budget = :estBudget,
    status = 'WAITING_FOR_ASSIGN_ENGINEER', updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE project_id = :projectId AND status = 'NEW_PROJECT';

-- 8 save each PDF (type = 'SOLUTION' or 'PRODUCT_REQUIREMENT')
INSERT INTO project_document (project_id, type, original_name, file_path, mime, size_bytes, uploaded_by, uploaded_at)
VALUES (:projectId, :type, :originalName, :filePath, 'application/pdf', :sizeBytes, :me, CURRENT_TIMESTAMP);
```

### UC03 Check Project Detail — all roles, read-only
```sql
-- 1.1 project list with F3 search + filters (each filter is skipped when its parameter is NULL), 20 per page
SELECT p.project_id, p.project_name, p.customer_name, p.status, s.name AS sale_name, p.created_at
FROM project p JOIN employee s ON s.employee_id = p.sale_id
WHERE (:status IS NULL OR p.status = :status)
  AND (:saleId IS NULL OR p.sale_id = :saleId)
  AND (:text IS NULL OR LOWER(p.project_name) LIKE :text ESCAPE '!'      -- :text = '%' || lower(search) || '%'
       OR LOWER(p.customer_name) LIKE :text ESCAPE '!' OR p.project_id = :id)
  AND (:me IS NULL OR :me IN (p.sale_id, p.presale_id, p.pm_id)          -- "only my projects"
       OR EXISTS (SELECT 1 FROM project_engineer pe WHERE pe.project_id = p.project_id AND pe.employee_id = :me))
ORDER BY p.created_at DESC, p.project_id DESC
LIMIT 20 OFFSET :offset;

-- 3.1 exists?  (0 rows → 404)
SELECT EXISTS (SELECT 1 FROM project WHERE project_id = :projectId);

-- 4.1 + 5.1–5.4 project with persons in charge and site (address is on project)
SELECT p.*, s.name AS sale_name, ps.name AS presale_name, pm.name AS pm_name
FROM project p
JOIN employee s       ON s.employee_id  = p.sale_id
JOIN employee ps      ON ps.employee_id = p.presale_id
LEFT JOIN employee pm ON pm.employee_id = p.pm_id
WHERE p.project_id = :projectId;

-- assigned engineers
SELECT e.employee_id, e.name FROM project_engineer pe
JOIN employee e ON e.employee_id = pe.employee_id
WHERE pe.project_id = :projectId ORDER BY e.name;

-- test schedule
SELECT test_at, detail FROM project_test_schedule WHERE project_id = :projectId ORDER BY test_at DESC;

-- requirements
SELECT requirement_id, title, priority, due_date, status, reject_reason, created_at
FROM requirement WHERE project_id = :projectId ORDER BY created_at DESC;

-- 7 downloadable documents: solution + product requirement + attachments of APPROVED requirements
SELECT d.id, d.type, d.original_name, d.uploaded_at
FROM project_document d
LEFT JOIN requirement r ON r.requirement_id = d.requirement_id
WHERE d.project_id = :projectId
  AND (d.type IN ('SOLUTION', 'PRODUCT_REQUIREMENT') OR r.status = 'APPROVED')
ORDER BY d.uploaded_at DESC;
```

### UC04 Assign Engineer to Project — PM
```sql
-- 1.1 projects needing engineers that I may assign (no PM yet, or I am the PM in charge)
SELECT project_id, project_name, customer_name FROM project
WHERE status = 'WAITING_FOR_ASSIGN_ENGINEER' AND (pm_id IS NULL OR pm_id = :me) ORDER BY created_at;

-- 3.1 all Tech Engineers with skills
SELECT e.employee_id, e.name, e.profile_picture_path, s.skill
FROM employee e LEFT JOIN engineer_skill s ON s.employee_id = e.employee_id
WHERE e.role = 'TECH' ORDER BY e.name, s.skill;

-- 5.1 filter by skill (case-insensitive)
SELECT DISTINCT e.employee_id, e.name
FROM employee e JOIN engineer_skill s ON s.employee_id = e.employee_id
WHERE e.role = 'TECH' AND LOWER(s.skill) = LOWER(:skill)
ORDER BY e.name;

-- 7.0 allowed?  pm_id IS NULL (first round) or pm_id = :me (re-confirm); otherwise refuse
-- 7.1 assign (once per selected engineer not already on the project)
INSERT INTO project_engineer (project_id, employee_id, assigned_by, assigned_at)
VALUES (:projectId, :engineerId, :me, CURRENT_TIMESTAMP);

UPDATE project SET pm_id = COALESCE(pm_id, :me), status = 'WORKING', updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE project_id = :projectId AND status = 'WAITING_FOR_ASSIGN_ENGINEER' AND (pm_id IS NULL OR pm_id = :me);

INSERT INTO notification (recipient_id, project_id, type, message, send_at, created_at)
VALUES (:engineerId, :projectId, 'ASSIGNED', :message, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
```

### UC05 Set Project Test Calendar Notification — TECH
```sql
-- 1.1 my assigned projects ready to test
SELECT p.project_id, p.project_name, p.customer_name
FROM project p JOIN project_engineer pe ON pe.project_id = p.project_id
WHERE pe.employee_id = :me AND p.status = 'WORKING';

-- 4.1 save test schedule (:testAt must be in the future) + move state
INSERT INTO project_test_schedule (project_id, test_at, detail, created_by, created_at)
VALUES (:projectId, :testAt, :detail, :me, CURRENT_TIMESTAMP);

UPDATE project SET status = 'TESTING', updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE project_id = :projectId AND status = 'WORKING';

-- 5 notify Sale, PM and assigned engineers: now + reminder (:reminderAt = test_at − 1 day)
--   recipients:
SELECT sale_id AS recipient_id FROM project WHERE project_id = :projectId
UNION SELECT pm_id FROM project WHERE project_id = :projectId AND pm_id IS NOT NULL
UNION SELECT employee_id FROM project_engineer WHERE project_id = :projectId;
--   for each recipient:
INSERT INTO notification (recipient_id, project_id, type, message, send_at, created_at)
VALUES (:recipientId, :projectId, 'TEST_SCHEDULED', :message, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (:recipientId, :projectId, 'TEST_REMINDER',  :message, :reminderAt,       CURRENT_TIMESTAMP);
```

### UC06 Add Customer New Requirement — PM
```sql
-- 1.1 my projects whose customer test is finished
SELECT p.project_id, p.project_name, p.customer_name, p.status
FROM project p
WHERE p.status = 'TESTING'
  AND p.pm_id = :me                                              -- only the PM in charge
  AND (SELECT MAX(t.test_at) FROM project_test_schedule t WHERE t.project_id = p.project_id) <= CURRENT_TIMESTAMP;

-- 3 project details (engineers: see UC03 "assigned engineers")
SELECT p.project_name, p.customer_name, pm.name AS pm_name, p.status, p.start_date, p.end_date, p.objective
FROM project p LEFT JOIN employee pm ON pm.employee_id = p.pm_id
WHERE p.project_id = :projectId;

-- 3.7 earlier requirements of this project
SELECT requirement_id, title, detail, priority, status, created_at
FROM requirement WHERE project_id = :projectId ORDER BY created_at DESC;

-- 8 save requirement
INSERT INTO requirement (project_id, title, detail, priority, due_date, related_feature, status, created_by, created_at)
VALUES (:projectId, :title, :detail, :priority, :dueDate, :relatedFeature, 'PENDING', :me, CURRENT_TIMESTAMP);

-- 8 attachments (optional, PDF/JPG/PNG)
INSERT INTO project_document (project_id, requirement_id, type, original_name, file_path, mime, size_bytes, uploaded_by, uploaded_at)
VALUES (:projectId, :requirementId, 'REQUIREMENT_ATTACHMENT', :originalName, :filePath, :mime, :sizeBytes, :me, CURRENT_TIMESTAMP);

-- 10 move state
UPDATE project SET status = 'WAITING_FOR_APPROVE', updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE project_id = :projectId AND status = 'TESTING';

-- 9 notify the Sale in charge
INSERT INTO notification (recipient_id, project_id, type, message, send_at, created_at)
SELECT sale_id, project_id, 'REQUIREMENT_PENDING', :message, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM project WHERE project_id = :projectId;
```

### UC07 Approve New Project Requirement — SALE
```sql
-- 1.1 pending requirements of MY projects
SELECT r.requirement_id, r.title, r.priority, r.due_date, r.created_at, p.project_id, p.project_name
FROM requirement r JOIN project p ON p.project_id = r.project_id
WHERE r.status = 'PENDING' AND p.sale_id = :me
ORDER BY r.created_at;

-- 4.1 still pending + I am the Sale in charge?  (no row → refuse)
SELECT r.requirement_id, r.version
FROM requirement r JOIN project p ON p.project_id = r.project_id
WHERE r.requirement_id = :requirementId AND r.status = 'PENDING' AND p.sale_id = :me;

-- 5.1 requirement detail + attachments
SELECT * FROM requirement WHERE requirement_id = :requirementId;
SELECT id, original_name, mime FROM project_document WHERE requirement_id = :requirementId;

-- 7.1 save decision (:decision = 'APPROVED' or 'REJECTED'; :reason required when REJECTED)
UPDATE requirement
SET status = :decision, reject_reason = :reason, decided_by = :me, decided_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE requirement_id = :requirementId AND status = 'PENDING' AND version = :version;

-- 8 move project state: APPROVED → NEW_PROJECT, REJECTED → FINISH
UPDATE project SET status = :newStatus, updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE project_id = :projectId AND status = 'WAITING_FOR_APPROVE';

-- 9/10 notify PM (+ Presale in charge and assigned engineers when approved) — same INSERT INTO notification pattern as UC05
```

### F4 Edit my profile — own account only (:me from the login, never from the form)
```sql
UPDATE employee SET name = :name WHERE employee_id = :me;
UPDATE employee SET password_hash = :newBcryptHash WHERE employee_id = :me;      -- after checking the current password
UPDATE employee SET profile_picture_path = :path WHERE employee_id = :me;        -- 'avatars/<uuid>.png|.jpg'
INSERT INTO engineer_skill (employee_id, skill) VALUES (:me, :skill);           -- TECH only, not a duplicate
DELETE FROM engineer_skill WHERE id = :skillId AND employee_id = :me
  AND (SELECT COUNT(*) FROM engineer_skill WHERE employee_id = :me) > 1;         -- keep at least one
```

### Complete Project — PM
```sql
UPDATE project SET status = 'FINISH', updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE project_id = :projectId AND status = 'TESTING'
  AND pm_id = :me                                                 -- only the PM in charge
  AND (SELECT MAX(t.test_at) FROM project_test_schedule t WHERE t.project_id = :projectId)
      <= DATEADD('DAY', -7, CURRENT_TIMESTAMP);                   -- one week after the customer test (§8)
```

---

## 6. Project state machine

```
[start] --UC01--> NEW_PROJECT
NEW_PROJECT --UC02--> WAITING_FOR_ASSIGN_ENGINEER
WAITING_FOR_ASSIGN_ENGINEER --UC04--> WORKING
WORKING --UC05--> TESTING
TESTING --UC06 (only after test_at has passed)--> WAITING_FOR_APPROVE
TESTING --PM "Complete Project" (only 1 week after test_at)--> FINISH
WAITING_FOR_APPROVE --UC07 approve--> NEW_PROJECT
WAITING_FOR_APPROVE --UC07 reject--> FINISH
```
- Only these transitions are legal; the service checks the current state before every change and
  returns an error otherwise. Implement in one place (`ProjectStatus.moveTo`).
- `FINISH` is terminal: read-only.
- Requirement status (`PENDING/APPROVED/REJECTED`) is separate from project status.
- UI labels: New Project, Waiting for Engineer Assignment, Working, Testing, Waiting for Approval, Finished.

---

## 7. CRUD matrix (official CRUD Table)

| Function | Sales | TechEngineer | PM | Presale | Project |
|---|---|---|---|---|---|
| CreateProject (UC01) | C | | | | U |
| StoreProjectSolution (UC02) | | | | C | U |
| CheckProject (UC03) | R | R | R | R | |
| AssignEngineerToProject (UC04) | | R | CR | | U |
| SetTestDate (UC05) | R | CR | R | | U |
| AddNewRequirement (UC06) | R | | C | | U |
| ApproveNewRequirement (UC07) | U | | | | U |

Meaning: UC03 never writes. In UC04 engineers are read (and notified). In UC05 Sale/PM read the
schedule. In UC06 Sale reads the requirement. In UC07 Sale updates the requirement.

Added (not in official table): UC00 creates Employee + skills. Complete Project: PM updates Project (→ `FINISH`). F1/F3 read only. F2 reads own
notifications and updates own `read_at`. F4 updates own Employee (name, password, picture) and
creates/deletes own skills.

**No deletes anywhere**, except an engineer removing their own skill (F4). No delete endpoints.

---

## 8. Decisions & open questions

**Decided**
| Topic | Decision |
|---|---|
| UC02 "Active/Inactive" status | Use §6 states. |
| UC07 narrative says PM reviews | Typo — Sale reviews and decides. |
| Presale R on CheckProject (CRUD) vs narrative | Follow CRUD: Presale can use UC03. |
| Sales R on SetTestDate (CRUD) vs narrative | Follow CRUD: Sale sees schedule + gets notified. |
| Stock/Purchasing notification (To-Be) | Out of scope. |
| Engineer calendar / availability | **Removed** — not built. |
| Roles | One role per employee. |
| Register | Name, role, email, password + confirm; Tech adds skills. Any valid email. Self-register any role (class demo). |
| Language | English only. |
| Files | On disk, metadata in DB. |
| Database | **Local H2 file only** (`./data`), for a laptop demo. PostgreSQL was **removed** from the project (drivers, profile, settings) and must not be brought back. |
| Notifications | In-app only; no customer email. |
| Budget | Set in UC02 (NULL after UC01). |
| Which PM owns a project | Any PM can do UC04 on a project with no PM yet; that PM becomes `pm_id` and **keeps it**. After an approved requirement only that PM in charge may re-confirm (others: button hidden, 403). Decided by the user 2026-10-06. |
| Approved requirement → `NEW_PROJECT` | Assigned engineers are kept; after UC02 again, the PM in charge re-confirms in UC04 (can add engineers). |
| Reject in UC07 | Project → `FINISH` (as drawn). |
| When a new requirement can be added | Only in `TESTING` **after the customer test is finished** (`test_at` passed). |
| Who finishes a project (handover) | PM, button *Complete Project* (`TESTING → FINISH`). |
| UC06 attachments | PDF, JPG, PNG; ≤ 50 MB each. |
| Upload size limits | 50 MB per file, 110 MB per upload in total (`spring.servlet.multipart.*`; UC02's two PDFs fit). UC06 attachments that are each allowed but together exceed 110 MB are refused as a whole; the 413 page and the UC06 form name both limits. Chosen 2026-10-06. |
| Who may do UC02 | Only the project's **Presale in charge** (`presale_id`). |
| Who may do UC06 | Only the project's **PM in charge** (`pm_id`, the PM who did UC04). |
| Who may Complete Project | Only a **PM** — implemented as the project's **PM in charge** (`pm_id`), same as UC06. |
| When Complete Project is allowed | Only when the customer has **no new requirement within one week after the test date**: project still `TESTING` and latest `test_at` + 7 days ≤ now (`CompleteProjectService.REQUIREMENT_WINDOW_DAYS`). Decided by the user 2026-10-06. UC06 is not limited to that week. |
| UC07 step 1 list | On the SALE dashboard ("Requirements waiting for my approval") and as *Review* on UC03; no separate page. |
| Reason on approve (UC07) | Only a rejection stores a reason (`reject_reason`); text typed in on approve is ignored. |
| Presale on UC07 approve | The Presale in charge is notified too (`REQUIREMENT_APPROVED`, "please update the solution"), besides PM and engineers. Decided by the user 2026-10-06. |
| UC05 reminder when the test is < 1 day away | "1 day before" is already past → the reminder's `send_at` = now (never in the past, never skipped), so it arrives together with "test scheduled". Confirmed by the user. |
| F3 "Only my projects" | Projects where I am Sale, Presale or PM in charge, or an assigned engineer (same meaning for every role). Chosen while building F3 (2026-10-06). |
| F3 search by project ID | A number in the search box (optionally `#12`) matches that exact project ID; name and customer are partial matches. Unknown filter values in the URL are ignored (whole list), a page past the end shows the last page. |
| F4 forms | Name, password, picture and skills are separate forms on `/profile`. Removing a skill is `POST /profile/skills/{id}/remove` (the only delete, §7). A wrong or missing current password blocks the password change. |
| F2 details | Bell → `/notifications` page (no dropdown). Newest first = by `send_at` (a reminder counts as new when it arrives), 20 per page, lenient `?page=` like F3. Opening one is a **POST** (it writes `read_at`), marks it read and goes to its project (or back to the list when it has none). Someone else's or a not-yet-sent notification → 404. *Mark all as read* only touches my sent ones. A future reminder appears by itself when `send_at` passes, so no scheduler is needed. Chosen while building F2 (2026-10-06). |
| F1 counts / notifications | SALE counts include all six statuses (0 too), each linking to the F3 list. Every dashboard shows the 5 newest unread notifications. Chosen while building F1 (2026-10-06). |
| Who may download UC06 attachments | Approved requirement → every logged-in employee (UC03). Pending/rejected → only the project's Sale in charge (needs them for UC07) and PM in charge (added them). Confirmed by the user. |

**OPEN** — none right now. Add new questions here instead of guessing.

---

## 9. Bug-check checklist

**Access control**
`[x]` = done and tested. `[ ]` with *(done for …)* = partly done, finish when the remaining parts are built.

- [x] Only `/login`, `/register`, static assets are public; everything else needs login. *(`/h2-console` is also open, for viewing the local demo data.)*
- [x] Each UC only for its role(s) (§4) — enforced in the service, not just hidden buttons. *(UC00–UC07 + Complete Project + F1–F4)*
- [x] UC02 only by the project's Presale in charge; UC04 re-confirm only by the PM in charge; UC05 only by engineers assigned to that project; UC06 only by the project's PM in charge; UC07 only by the project's Sale in charge (check by opening another person's project URL directly). *(all done + tested)*
- [x] Permissions match §7; UC03 never writes; no delete endpoints (except own skill). *(UC03 + F1 + F3 pages are GET-only; F2 only writes own `read_at` via POST; the only delete is F4 `POST /profile/skills/{id}/remove` on own skills)*
- [x] Passwords only as BCrypt hashes, never logged. CSRF protection on all forms. *(re-check for every new form)*
- [x] Files/avatars can't be fetched without login; file IDs from the URL can't escape `uploads/` (no path traversal). *(project documents, requirement attachments and avatars: the path always comes from the DB; tested)*

**State machine**
- [x] Every mutating action checks current state and does exactly one §6 transition. *(state machine done + tested; UC01 creates in `NEW_PROJECT`; UC02 only from `NEW_PROJECT` → `WAITING_FOR_ASSIGN_ENGINEER`; UC04 only from `WAITING_FOR_ASSIGN_ENGINEER` → `WORKING`; UC05 only from `WORKING` → `TESTING`; UC06 only from `TESTING` after the test time → `WAITING_FOR_APPROVE`; UC07 only from `WAITING_FOR_APPROVE` → `NEW_PROJECT` / `FINISH`; Complete only from `TESTING` one week after the test → `FINISH`)*
- [x] Nothing changes on a `FINISH` project. *(every action checks its start state; tested for UC06 and Complete on a finished project)*
- [x] UC07 re-checks `PENDING` at decision time (no double decisions; use optimistic locking/version).

**Validation (server-side, with error messages shown on the form)**
- [x] UC00: name, one of four roles, valid unique email (case-insensitive), password ≥ 8 = confirm, Tech ≥ 1 unique skill, non-Tech saves no skills. *(password also ≤ 72 UTF-8 bytes, BCrypt's limit — `employee.Passwords`; same rule for the F4 new password)*
- [x] UC01: six required fields; Sale in charge has role SALE; Presale in charge has role PRESALE; email format.
- [x] UC02: dates present, end ≥ start; objective; budget ≥ 0; both PDFs, real PDF content, ≤ 50 MB.
- [x] UC04: ≥ 1 engineer, all with role TECH, no duplicates. *(zero new engineers allowed only when the project already has some — re-confirm after an approved requirement, §8)*
- [x] UC05: test date in the future; details present.
- [x] UC06: only when project is `TESTING` and the test time has passed; title, detail, priority, due date ≥ today; attachments PDF/JPG/PNG ≤ 50 MB.
- [x] Complete Project: only the project's PM in charge, only from `TESTING`, only one week after the test; notifies Sale, Presale, engineers.
- [x] UC07: reject needs a non-empty reason.
- [x] F4: current password correct; picture JPG/PNG content ≤ 2 MB; role/email unchanged even if form is tampered.

**Data & display**
- [x] UC03 shows all fields listed in §4 and download links for solution, product requirement and approved attachments.
- [x] UC04 list shows every engineer's skills + picture; filter returns only matching engineers.
- [x] Requirement stores created_by/at and decided_by/at/reason.
- [x] Uploads saved with generated names; original name kept for download. *(avatars: `avatars/<uuid>.png|.jpg`, old file deleted after the new one is saved)*

**Notifications**
- [x] UC04 → each new engineer. UC05 → Sale, PM, engineers (now + reminder). UC06 → Sale in charge. UC07 → PM (+ Presale and engineers if approved). Complete → Sale, Presale, engineers. *(all done + tested; messages over 1000 chars are cut to fit)*
- [x] Badge counts only my unread notifications with `send_at` ≤ now; I can't open/mark others' (change ID in URL). *(404 for others' and not-yet-sent ones; tested)*

**Extra features**
- [x] F1: each role gets its own dashboard; counts match data. *(SALE counts by status tested against data; latest unread notifications on every dashboard)*
- [x] F3: search ID/name/customer case-insensitive; filters AND; paging keeps filters; buttons respect role + state.
- [x] F4: own profile only; can't remove last skill; duplicate skills rejected; default avatar when none.

**General**
- [x] All UI text English.
- [x] Bad URLs (`/projects/abc`) and server errors show the app's error page, never a stack trace or Java message. *(re-check JSON with curl after adding endpoints. Accepted exception: a URL Tomcat rejects before the app sees it, e.g. an encoded slash `/documents/..%2F..%2Fpom.xml`, gets Tomcat's bare "HTTP Status 400" page — no details shown, and allowing such URLs through would weaken Tomcat's protection)*
- [x] Every form shows all its errors in one submit (annotation + business rules). *(UC00–UC07 + F4; F1–F3 have no input forms that save; follow the `validate(...)` pattern in new forms)*
- [x] Demo data in `./data` survives an app restart and a `git pull`; reset works by deleting `data/` + `uploads/`. *(checked 2026-10-06 on a scratch copy: data and files survived a restart, even a forced kill; since 2026-10-07 both folders are committed to Git on purpose (see §0 Demo data); after deleting them Flyway rebuilt the schema and the whole flow worked)*
