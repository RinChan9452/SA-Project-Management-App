# Test accounts

Accounts for checking the app by hand, one or two for every role. They are registered in the database
(`./data`), which is committed to Git together with `./uploads`, so another PC gets them with `git pull` (stop the app
first). Only after a reset do you need the script in [Recreate the accounts](#recreate-the-accounts).

## Start the app

```powershell
.\mvnw spring-boot:run
```

Open http://localhost:8080 and log in with an email below.

## Accounts

Password for every account: **`password123`**

| Role | Name | Email | Skills | Use it to |
|---|---|---|---|---|
| Sale | Loser | `sale1@example.com` | | Create projects (UC01), approve or reject requirements (UC07) |
| Sale | Ryo Supersale | `sale2@example.com` | | Check that another Sale cannot decide Sale One's requirements, test edit profile |
| Presale Engineer | Presale One | `presale1@example.com` | | Store the solution and PDFs (UC02) |
| Presale Engineer | Agnes Digital | `presale2@example.com` | | Check that a Presale who is not in charge is refused |
| Project Manager | PM One | `pm1@example.com` | | Assign engineers (UC04), add requirements (UC06), Complete Project |
| Project Manager | PM Two | `pm2@example.com` | | Check that only the PM in charge can continue a project |
| Tech Engineer | Aozaki The engineer | `tech1@example.com` | CCNA, Linux | Set the customer test date (UC05) |
| Tech Engineer | Tech Two | `tech2@example.com` | CCNA, Python | Skill filter in UC04; when not assigned, cannot set the test date |
| Tech Engineer | Sono-G The engineer | `sono@gmail.com` | CCIE, Linux, C++ | Test tech engineer register
| Tech Engineer | Tobimaru The engineer | `kumachan@cute.gmail.com` | CCNA, Linux | Test tech engineer register

Tip: use a second browser or a private window to be logged in as two people at the same time.

## Suggested test run

One full round through every use case. **Bold** names are the account to log in with.

1. **Sale One**: *Create Project*. Sale in charge = Sale One, Presale in charge = Presale One, fill in the customer and
   site fields. The project opens as **New Project**.
2. **Presale One**: Dashboard, *Need solution*, *Add Solution*. Enter dates, objective and budget, and upload two PDF
   files (any real PDF; a renamed picture is refused). The project is now **Waiting for Engineer Assignment**.
   - Check: **Presale Two** sees no *Add Solution* button, and the URL `/projects/<ID>/solution` shows *Access denied*.
3. **PM One**: Dashboard, *Need engineers*, *Assign Engineers*. Try the skill filter (*Python* shows only Tech Two,
   *CCNA* shows both). Select **Tech One only** and save. The project is **Working** and PM One is now its PM in
   charge. Tech One gets a notification (bell in the navbar).
4. **Tech One**: Dashboard, *Need test date*, *Set Test Date*. Pick a time **about 5 minutes from now**. The project
   is **Testing**. Sale One, PM One and Tech One get *Test scheduled* and *Test reminder* (the reminder comes at once
   because the test is less than 1 day away).
   - Check: **Tech Two** is not assigned, so there is no *Set Test Date* button.
5. Wait until the test time has passed. **PM One**: open the project, *Add Requirement* (title, detail, priority, due
   date; attachments are optional: PDF, JPG or PNG). The project is **Waiting for Approval** and Sale One gets a
   notification.
   - Check: before the test time the button is hidden, and **PM Two** never sees it.
6. **Sale One**: Dashboard, *Requirements waiting for my approval*, *Review*. Then either:
   - *Approve*: the project goes back to **New Project**. PM One, Presale One and Tech One are notified, and the
     attachments are now visible to everyone on the project page.
   - *Reject* with a reason: the project is **Finished** (read-only) and PM One is notified.
   - Check: **Sale Two** sees no *Review* button.
7. After an approval, do the round again: **Presale One** saves the solution (fields are pre-filled, PDFs are now
   optional), **PM One** opens *Assign Engineers* and clicks *Save Engineers* (PM Two cannot), and **Tech One** sets a
   new test date. The project is **Testing** again.
8. **PM One**: *Complete Project* is allowed only 7 days after the customer test. To try it now, see
   [Skip the 7-day wait](#skip-the-7-day-wait). After completing, the project is **Finished** and Sale One,
   Presale One and Tech One are notified.

Also try:
- **Projects** page (F3): search by project ID, name or customer; filter by status, Sale in charge, *Only my projects*.
- **Bell** (F2): open a notification (marks it read and opens the project), *Mark all as read*.
- **My profile** (click your name in the navbar, F4): change name, password, picture (JPG or PNG, at most 2 MB);
  Tech Engineers can add and remove skills (at least one must stay).

## Skip the 7-day wait

Complete Project needs the customer test to be at least 7 days in the past. To test it right away, move the test date
of one project back:

1. With the app running, open http://localhost:8080/h2-console
2. JDBC URL `jdbc:h2:file:./data/projectsa`, user `sa`, password empty, then *Connect*.
3. Run this, with `<ID>` replaced by the project number (shown as "Project #<ID>" on the project page):

   ```sql
   UPDATE project_test_schedule SET test_at = DATEADD('DAY', -8, LOCALTIMESTAMP) WHERE project_id = <ID>;
   ```

4. Refresh the project page as **PM One**: *Complete Project* is now there.

This changes only that project's test date.


