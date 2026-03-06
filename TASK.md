## Must fix

- ~~When switching Month to week, it should saved the month page and switch back to that page when rechanging it. It must be the same when changing from week to month. Save current week, Switch to month (use today date for month as first occurence) and then swtich to saved week when switch back~~ ✅
- ~~Physiologic and hsitoric pages are not implemented in COACH Page.~~ ✅
- ~~A coach could be an athlete as well. A Toggle  must be present to switch from one to another. AThlete se, main dahsboard, assistant, PMC and Calendar. When on Coach, see assitant and coach, and extract Zone Dashboard in a separated Tabs~~ ✅
- ~~For the overdue, what I mean is to display only overdue element from today to seven days in the past~~ ✅
- ~~Tss, IF, timing close to title in analysed workout (from fit) must be bigger~~ ✅
- ~~Modification of RPE in front end must trigger a call in back end (use Debounce). The call must then calculate a metrics (Tss,IF,...) if possible and include in PMC~~ ✅
- ~~The User info must be display in the top right of the top bar~~ ✅
- ~~The creation of TAG from the coach, must be : only one input for the tag name, and use that input value for the tag assiciation with the code~~ ✅
- ~~In Coahc dashboard, the PMC graph of the athlete must be display at least 30 days in the future and 30 days in the past. Coach must have the possiblity to go to the PMC page of the athlete to see the estimation and other timeline/analysis (A button must be there in this case to switch back to athelte page on coach dahsboard)~~ ✅
- ~~Tss Value in week load in calendar are difficult to read. Make it bigger~~ ✅
- ~~When using IA, Tags are linked to my training, but Tags to training must only be possible for coach and it must be explicilty asked (like assign to group/tag xxx)~~ ✅
- ~~Add a button in calendar to reset to current Month or week~~ ✅
- ~~The list of scheduled training of the athelete in Coach dashboard must be displayed week by week nd it should be possible to navigate between week~~ ✅
- ~~In the Physiologic (in Coach), I want to see the three default zone in swim, bike and run and also the Custom Zone I have defined~~ ✅
- ~~TAG management must be in a separated Tab in the top bar (ONLY for coach), Rename, remove athlete,..., When a tag is removed from an athlete it do not delete all the training but it will not be possible to see future training;  Find the best way to do it.~~ ✅
- ~~Add the physiology tab also in the athlete page (as a main page)~~ ✅
- ~~Migrate the TAG creation from coach Dashboard to TAG Page. Make the tag creation more explicit, With One input for the tag name, one for the max use, and a button to create the tag.~~ ✅
- ~~Bug with User info display in top right of top bar. I want that the user display with lougout button from main dashborad was moved in top right of top bar~~ ✅
- ~~Bug With Drag and drop of Workout in Calendar. Drag works but drop is not possible and does not work~~ ✅
- ~~Bug with Chat history session link and retrieval of history.~~ ✅
- ~~Bugs with PMC display, completly flat (I have not .fit, But I have manual completed workout, and scheduled Workout)~~ ✅
- ~~On Physiology Page, I want to see all 3 default zone in Swim bike, run. Coggan has 7 zones. A percentage of reference value in the zone display/graph~~ ✅
- ~~There is an issue with the calendar in month view, the page is larger and the 3% of the  rigth page is not visible~~ ✅
- In Coach Page, the Zone Dashboard is still in the same page as the Athelte page. Create a seperated tab 'like for TAGS'
- When refresh the page, we always arrive in the main dashboard, but we should just stay on the page/component we are. Even when coach, I can see the dashboard.
- When connecting for the first Time with google or strava, user must say if coach or athlete, FTP, CSS, and hreshold pace in run. Only Coach/athlete is mandatory
- API http://localhost:8080/api/sessions/699f5bb89268b9733f42e4d2 return 403 and CORS error, same for http://localhost:8080/api/schedule/69ab2c0b34bf549c8b87fa4b/reschedule
- Day in week view in calendar must be locally scrollable/ OR Find a way to display more than 2 training
- The link of complete fit workout works but the display of the action is ugly and not visible at all. It the linked training, a link to the compelted training mist be abalable and a icon visible in the calednar for this workout
- The sport distribution in physiology must be display before the zones in coach dashboard when looking at athlete
- Training Creation: In blocks, distance or duration must be setted, never both. The other is extrapolted in backend not by the IA

## Nice too have
- **Retrieve FIT files from Strava** — Implement an endpoint + service to fetch FIT activity files from Strava for the authenticated user. Requires the user to have granted all metric scopes (`activity:read_all`). Parse the FIT data and ingest relevant metrics (power, HR, cadence, TSS) into the session/history store.

## Later

- Remove the dev connector