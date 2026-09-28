# CLAUDE.md — Project Management Program (Project SA)

This file is the **functional spec** for the app, derived from the System Analysis diagrams in
`Business Process.drawio.xml` (pages: *Clean To-Be*, *Use-Case*, *Copy of Use-Case Narrative*,
*ER Diagram*, *CRUD Table*, *State Diagram*). Read it before writing code, and re-check the code
against it when hunting for bugs (see **§9 Bug-check checklist**).

When the diagrams contradict each other, follow the resolution in **§8 Known gaps & decisions**.
If something is not covered here, ask the user instead of inventing behavior.

---

## 0. Tech stack

_Not decided yet — the repo is empty. Fill this in once chosen (language, framework, DB, how to
run, how to test)._

---

## 1. Purpose

A web app for a system-integration company that replaces email/document/LINE hand-offs between
departments. It keeps one **Project Profile** per customer project that every role reads from and
writes to, so information is not lost when work passes between people.

Pain points (from the As-Is analysis) the app must solve:

| # | Pain point | How the app solves it |
|---|---|---|
| 1 | Meeting/test appointments with customer get mixed up | UC05 test calendar + notifications |
| 3 | Sale goes out of service mid-deal, hand-off is slow | Project Profile records Sale in charge (and co-sale); anyone can read it (UC03) |
| 5 | PM doesn't know each engineer's skills | Engineer skills stored; filter by skill in UC04 |
| 6 | PM doesn't know engineers' schedules | Engineer calendar visible when assigning (UC04) |
| 7 | Project delays when requirements are added mid-project | Formal new-requirement flow with status (UC06 → UC07) |
| 11 | Sales has to keep checking stock manually | Stock sends a notification to Sale when equipment is complete |
| 14 | Project details get lost when work is handed over | Single Project Profile + solution PDFs (UC02, UC03) |

---

## 2. Actors (roles)

All actors are **Employees** (one login/account model with a role).

| Actor | Thai lane name | Main responsibilities in the app |
|---|---|---|
| **Sale** (Sale / Sale_Co) | Sale | Create project profile, approve/reject new requirements, check project |
| **Presale Engineer** | Presale Engineer | Store solution details + PDFs |
| **PM** | PM | Check project, assign engineers, add customer new requirements |
| **Tech Engineer** | Technical Engineer | Check project, set test calendar + notifications; has skills & calendar |
| **Purchasing / Stock** | จัดซื้อ / Stock | Not a use-case actor, but in To-Be it notifies Sale when equipment is complete (see §8) |

Role-based access is required: each use case may only be executed by its listed actor(s).

---

## 3. To-Be business process (Clean To-Be)

1. **Sale** finds a project → decides whether to accept it (No → keep looking).
2. Yes → **Sale creates a new Project Profile** (UC01).
3. **Presale** designs the solution and project plan.
4. **Customer confirms** the project? No → back to step 1. Yes → project starts.
5. **Presale stores the solution in the app** (UC02) and tells Sale which equipment is needed.
   - Sale informs Purchasing/Stock → Stock checks internal stock → if missing, Purchasing orders by
     spec and re-checks → when complete, **the system notifies Sale** → Sale informs PM & Engineers.
6. **PM checks project detail** (UC03) → plans work to match the solution.
7. **PM assigns engineers** (UC04) — decision support: check engineer **skills** and **calendar**.
   (Engineers keep their calendar and skill details up to date.)
8. PM and engineers plan together → **check project detail** → install equipment on site.
9. **Tech Engineer tests the system.** Problem? → engineer fixes → test again.
10. No problem → **Tech Engineer sets the customer test calendar** (UC05).
11. Does the system meet customer needs?
    - Yes → UAT + handover documents → **deliver project** (end).
    - No, system problem → engineer fixes → back to testing.
    - No, customer wants more → **PM records the new requirement** in the project and sets status
      **รออนุมัติ (Pending approval)** (UC06) → **Sale checks project detail** and **approves** (UC07)
      → back to Presale to redesign solution (step 3/5).

---

## 4. Use cases (from Use-Case diagram + narratives)

System boundary: **Project Management Program**.

| UC | Name | Actor(s) |
|---|---|---|
| UC01 | Create Project Profile | Sale |
| UC02 | Store Project Solution Detail | Presale Engineer |
| UC03 | Check Project Detail | Sale, PM, Tech Engineer, Presale (per CRUD Table) |
| UC04 | Assign Engineer to Project | PM |
| UC05 | Set Project Test Calendar Notification | Tech Engineer |
| UC06 | Add Customer New Requirement | PM |
| UC07 | Approve New Project Requirement | Sale |

### UC01 Create Project Profile — Sale
- **Pre:** Project accepted from customer. **Post:** Project Profile exists, waiting for solution details. State → `NewProject`.
- Flow: Sale clicks *Create Project Profile* → fills basic info → system validates → system creates profile.
- **Required fields (validation 3.1–3.6):** Project Name, Sale in Charge, Presale Engineer in Charge,
  Customer Name, Customer Email, Project Site.
- Narrative DB hint: `INSERT INTO PROJECT (ProjectID, ProjectName, EstBudget, Status)`.

### UC02 Store Project Solution Detail — Presale Engineer
- **Pre:** Project Profile exists (UC01). **Post:** solution details saved to the profile. State → `WaitingForAssignEngineer`.
- Flow:
  1. System lists projects. 2. User selects a project. 3. System loads it.
  4. User fills solution details. 5. System validates: **Start Date / End Date**, **Project Objective**, **Estimated Budget** (all required).
  6. User uploads **Solution** and **Product Requirement** documents.
  7. System validates files: **PDF only, ≤ 50 MB each**.
  8. System saves data + files to the selected project. 9. System updates project status.

### UC03 Check Project Detail — Sale, PM, Tech Engineer
- **Pre:** Profile created by Sale and solution filled by Presale. **Post:** user can see project details.
- Flow: system lists projects → user selects → system checks the project **exists** and its status →
  shows basic info: **Sale in Charge, Presale Engineer in Charge, Customer Name, Project Site** →
  user clicks *Project Solutions* icon → system serves the solution PDF for download.
- Must also show approved new-requirement PDFs (UC07 step 11).

### UC04 Assign Engineer to Project — PM
- **Pre:** Profile exists and engineers exist in the system. **Post:** Tech Engineer(s) linked to the project. State → `Working`.
- Flow: list projects → PM selects project → system shows company engineers → PM **filters by skill**
  → system shows matching engineers → PM selects engineer(s) → system saves them to the project.
- To-Be also requires showing **engineer calendar/availability** when choosing (pain point 6).

### UC05 Set Project Test Calendar Notification — Tech Engineer
- **Pre:** Project has engineers (UC04) and is ready to test (`Working`). **Post:** test schedule saved; notifications sent to relevant people on the scheduled date. State → `Testing`.
- Flow: list projects → engineer selects project → sets **test date/time + test details** →
  system saves schedule → system **notifies PM and team** (description also says customer) ahead of / at the set time.

### UC06 Add Customer New Requirement — PM
- **Pre:** Project exists and is in progress (`Testing` per state diagram). **Post:** requirement saved with status **Pending approval**, waiting for Sale. Project state → `WaitingForApprove`.
- Flow:
  1. System lists projects showing **Project ID, Project Name, Customer Name, Project Status**.
  2. PM selects project.
  3. System shows details: project name, customer name, PM, assigned engineers, current status, start date, solution detail.
  4. PM enters requirement: **title**, **detail**, **priority/urgency**, **customer due date**, related feature/area *(optional)*.
  5. System validates: title & detail not empty, date valid.
  6. PM uploads supporting docs *(optional)*: spec files, sample images, customer documents.
  7. System validates file type and size.
  8. System saves requirement with **Requirement ID, created timestamp, created by (PM), status = Pending**.
  9. System notifies **Sale** (in-app and/or email).
  10. Requirement status = "รออนุมัติ" (Pending).

### UC07 Approve New Project Requirement — Sale
- **Pre:** Requirement exists (UC06) with status Pending. **Post:** requirement Approved or Rejected; status updated.
- Flow:
  1. System lists **pending requirements belonging to this Sale's projects**.
  2. Sale selects project. 3. Sale selects requirement.
  4. System checks the requirement is still **Pending**.
  5. System shows the requirement documents.
  6. Sale chooses **Approve** or **Reject** — **reason is required when rejecting**.
  7. System saves the decision. 8. System updates requirement status.
  9. System notifies **PM**: requirement name, decision, reason (if rejected).
  10. If approved: add requirement to the project's scope/work list and notify related PM/Engineers.
  11. Add the requirement PDF to *Check Project Detail* (UC03).
- Project state: Approved → `NewProject`; Rejected → `Finish` (see §6, §8).

---

## 5. Data model (ER Diagram → suggested relational schema)

### ER as drawn
- **Employee** (<u>EmployeeID</u>, Name) — supertype with **partial, overlapping (P,O)** specialization into
  **Sales**, **Tech Engineer** (+ Skills), **PM**, **Presale**. → An employee may have more than one role, and some employees have none of these roles.
- **Project** (<u>ProjectID</u>, ProjectName, EstBudget, Status, EmployeeEnlist,
  Date {PeriodDate, TestDate} *(multivalued composite)*,
  Detail {BaseDetail, RequirementDetail} *(multivalued composite)*,
  Address {BuildingNumber, VillageNumber, Alley, Soi, Street, Subdistrict, District, Province, Zipcode} *(composite)*).
- Relationships (all to Project):

| Relationship | Employee side | Cardinality |
|---|---|---|
| Create | Sales | 1 : M |
| ApproveRequirement | Sales | 1 : M |
| CheckProject | Sales (1), PM (1), Tech Engineer (M) | — : M |
| StoreProjectSolutionDetail | Presale | 1 : M |
| AssignEngineerToProject | PM | 1 : M |
| AddCustomerNewRequirement | PM | 1 : M |
| SetCalendarNotification | Tech Engineer | M : M |

### Suggested tables (normalize multivalued/composite attrs; include fields the narratives require)
```
employee(employee_id PK, name, email, password_hash, ...)
employee_role(employee_id FK, role ENUM('SALE','PRESALE','PM','TECH'))      -- overlapping roles
engineer_skill(employee_id FK, skill)                                         -- Tech Engineer Skills
engineer_calendar(id PK, employee_id FK, start_at, end_at, detail)            -- pain point 6

project(project_id PK, project_name, customer_name, customer_email,
        sale_id FK, co_sale_id FK NULL, presale_id FK, pm_id FK NULL,
        objective, est_budget, start_date, end_date,
        status ENUM(see §6), created_by FK, created_at,
        -- address (Project Site)
        building_no, village_no, alley, soi, street, subdistrict, district, province, zipcode)
project_engineer(project_id FK, employee_id FK, assigned_by FK, assigned_at)  -- EmployeeEnlist, M:N
project_document(id PK, project_id FK, requirement_id FK NULL,
                 type ENUM('SOLUTION','PRODUCT_REQUIREMENT','REQUIREMENT_ATTACHMENT'),
                 file_path, mime, size_bytes, uploaded_by FK, uploaded_at)
project_test_schedule(id PK, project_id FK, test_at, detail, created_by FK)   -- Date.TestDate
requirement(requirement_id PK, project_id FK, title, detail, priority, due_date, related_feature NULL,
            status ENUM('PENDING','APPROVED','REJECTED'), reject_reason NULL,
            created_by FK (PM), created_at, decided_by FK (Sale) NULL, decided_at NULL)
notification(id PK, recipient_id FK, project_id FK, type, message, send_at, read_at NULL)
```

---

## 6. Project state machine (State Diagram)

```
[start] --CreateProjectProfile (UC01)--> NewProject
NewProject --StoreProjectSolution (UC02)--> WaitingForAssignEngineer
WaitingForAssignEngineer --AssignEngineer (UC04)--> Working
Working --SetProjectCalendarTestDate (UC05)--> Testing
Testing --AddCustomerRequirement (UC06)--> WaitingForApprove
Testing --NoCustomerRequirement (handover)--> Finish
WaitingForApprove --ApproveNewRequirement (UC07 approve)--> NewProject
WaitingForApprove --NotApproveNewRequirement (UC07 reject)--> Finish
Finish --> [end]
```

Rules:
- **Only these transitions are legal.** Every action must check the current state and reject otherwise.
- `Finish` is terminal — no edits except read (UC03).
- Requirement status (`PENDING/APPROVED/REJECTED`) is **separate** from project status.

---

## 7. CRUD matrix (official CRUD Table)

C = Create, R = Read, U = Update, D = Delete. Columns are the actors (what each role does in the
function) plus the **Project** record (what happens to it).

| Function | Sales | TechEngineer | PM | Presale | Project |
|---|---|---|---|---|---|
| CreateProject (UC01) | C | | | | U |
| StoreProjectSolution (UC02) | | | | C | U |
| CheckProject (UC03) | R | R | R | R | |
| AssignEngineerToProject (UC04) | | R | CR | | U |
| SetTestDate (UC05) | R | CR | R | | U |
| AddNewRequirement (UC06) | R | | C | | U |
| ApproveNewRequirement (UC07) | U | | | | U |

What each cell means for the code:
- **CreateProject:** Sale creates the profile; project record is written (status → `NewProject`).
- **StoreProjectSolution:** Presale creates the solution details and documents; project updated (status → `WaitingForAssignEngineer`).
- **CheckProject:** read-only for **all four roles, including Presale**. Must never modify the project.
- **AssignEngineerToProject:** PM reads engineers (skills/calendar) and creates the assignment; engineers are read (they see they're assigned); project updated (status → `Working`).
- **SetTestDate:** Tech Engineer creates and reads the test schedule; **Sales and PM can read it** (they're notified); project updated (status → `Testing`).
- **AddNewRequirement:** PM creates the requirement; **Sales reads it** (notified and sees it in the pending list); project updated (status → `WaitingForApprove`).
- **ApproveNewRequirement:** Sale updates the requirement (approve/reject + reason); project updated (status → `NewProject` or `Finish`).

**No function deletes (D) anything.** Do not add delete endpoints or hard deletes unless the user asks.

---

## 8. Known gaps & decisions (diagrams disagree)

| Issue | Decision |
|---|---|
| UC02 step 9 says status "Active / Inactive" | Use the §6 state machine; UC02 moves `NewProject → WaitingForAssignEngineer`. |
| UC05 pre "ready to test", UC06 pre "in progress" | = `Working` for UC05, `Testing` for UC06. |
| UC07 step 5 says "show to **PM** to check", step 7 "decision of **PM**" | Typo — it is **Sale** who reviews and decides. |
| UC03 narrative has two "Step 3" | Step 3a: check project exists/status; 3b: load project. |
| Rejected requirement → state diagram goes to `Finish`; To-Be has no reject branch | Follow state diagram, but **confirm with user** before implementing (rejecting one requirement ending the whole project may be unintended). |
| Approved requirement → back to `NewProject` → must re-assign engineers? | Keep existing engineers; Presale re-stores solution, then PM re-confirms assignment. Confirm with user. |
| CRUD Table gives **Presale R on CheckProject**, but the UC03 narrative and Use-Case diagram list only Sale, PM, Tech | Follow the CRUD Table: Presale can also use UC03 (read-only). |
| CRUD Table gives **Sales R on SetTestDate**, but UC05 narrative only notifies "PM and team" | Follow the CRUD Table: Sales can view the test schedule and gets the notification too. |
| Stock/Purchasing notification (pain point 11) is in To-Be but no use case / actor / CRUD row | Not in scope unless user says so; if built, Stock role creates a "equipment complete" notification to Sale. |
| UC06 file type/size limits unspecified | Reuse UC02 limits (≤ 50 MB); allow PDF + images. Confirm with user. |
| ER `Skills`, `EmployeeEnlist` drawn single-valued | Implement as multivalued tables (`engineer_skill`, `project_engineer`). |
| UC01 query mentions `Status`, `EstBudget` but form doesn't collect budget | Budget is set in UC02; UC01 creates with status `NewProject`, budget NULL. |

---

## 9. Bug-check checklist

Use this when reviewing the code for bugs:

**Access control**
- [ ] Each UC is restricted to its actor(s) (§4); a user with multiple roles gets the union.
- [ ] UC07 only lists/decides requirements on projects where the user is the Sale in charge.
- [ ] Permissions match the §7 CRUD Table exactly (e.g. CheckProject never writes; nothing deletes).

**State machine**
- [ ] Every mutating action verifies the current project state and performs exactly the §6 transition.
- [ ] No action is possible on a `Finish` project except reading.
- [ ] UC07 re-checks requirement is `PENDING` at decision time (no double approve/reject, race-safe).

**Validation**
- [ ] UC01: all six required fields; customer email format valid.
- [ ] UC02: start/end date present and end ≥ start; objective present; budget present and numeric ≥ 0.
- [ ] UC02 files: PDF only (check MIME/magic bytes, not just extension), ≤ 50 MB; both Solution and Product Requirement handled.
- [ ] UC06: title & detail non-empty; due date valid; attachment type/size checked.
- [ ] UC07: reject requires a non-empty reason.
- [ ] Server-side validation exists (not only in the UI).

**Data integrity**
- [ ] Project list in UC06 shows ID, name, customer, status.
- [ ] UC03 shows Sale in charge, Presale in charge, customer name, site, and downloadable solution PDF + approved requirement PDFs.
- [ ] UC04 skill filter returns only engineers with that skill; calendar/availability is shown; no duplicate assignment of the same engineer.
- [ ] Requirement stores ID, created_at, created_by, status; decision stores decided_by/at.
- [ ] Uploaded files are linked to the correct project and can't be downloaded by unauthorized users.

**Notifications**
- [ ] UC05 notifies PM, the project's Sale, and assigned engineers (and customer if implemented) at the scheduled time; Sale and PM can view the schedule.
- [ ] UC06 notifies the project's Sale.
- [ ] UC07 notifies PM with name/decision/reason; on approve also notifies assigned engineers.

**General**
- [ ] Thai text stored and rendered correctly (UTF-8 end to end).
- [ ] No hard deletes (§7).
