## DONE

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
- ~~In Coach Page, the Zone Dashboard is still in the same page as the Athelte page. Create a seperated tab 'like for TAGS'~~ ✅
- ~~When refresh the page, we always arrive in the main dashboard, but we should just stay on the page/component we are. Even when coach, I can see the dashboard.~~ ✅
- ~~When connecting for the first Time with google or strava, user must say if coach or athlete, FTP, CSS, and hreshold pace in run. Only Coach/athlete is mandatory~~ ✅
- ~~API http://localhost:8080/api/sessions/699f5bb89268b9733f42e4d2 return 403 and CORS error, same for http://localhost:8080/api/schedule/69ab2c0b34bf549c8b87fa4b/reschedule~~ ✅
- ~~Day in week view in calendar must be locally scrollable/ OR Find a way to display more than 2 training~~ ✅
- ~~The link of complete fit workout works but the display of the action is ugly and not visible at all. It the linked training, a link to the compelted training mist be abalable and a icon visible in the calednar for this workout~~ ✅
- ~~The sport distribution in physiology must be display before the zones in coach dashboard when looking at athlete~~ ✅
- ~~Training Creation: In blocks, distance or duration must be setted, never both. The other is extrapolted in backend not by the IA~~ ✅
- ~~The Dashboard, must be a real summary dashboard, currenlty, it is a training done and plan dhasboard. First Split the training dashboard in two others 1. The Trainings I have 2. The completed Training (Only .fit)
    Secondly modify the current dashboard, to display the main metric of the athlete. Keep the Overdue and Week panel to display the training to do this week and overdue training Some metrics on the current week, like Bike/run/Swim total duration/distance/TSS/... Find anything relevant
    On the new Main dashboard, add also to the latest .fit workout done of the athelte. Propose in your plan other improvements you could do~~ ✅ 
- ~~The Physiology in Coach Dashboard shows only Cycling and Custom ZOnes. First Custom zone must dislay the value and the percentage based on the referenced value of the athlete. Secondly, Only Cycling default zones are visibles, not the SWIM or Running defualt ZONE. Thridly, you can make 3 columns, one for each deufalt zones,then diplays the cusotme zones below~~ ✅
- ~~Make the PMC graph a sperated TAB in the COach Dashboard~~ ✅
- ~~The History page does not show the data in the Hsitory Tab i Coach Dahsboard~~ ✅
- ~~The link .fit workout to scheduled workout in Calendar, is still not visible enough. Make a modal/Dialog for that~~ ✅
- ~~Remove the +register button in Coach atheltee page, Add the redeem code in TAGS page in each corresponding Tab~~ ✅
- ~~In the tab, there is an ahtlete list. I want that when click on the ahtlete I am redirect the coach Page witht this athlete selected.~~ ✅
- ~~I want real metric value (Fitness, fatigue, form) of the athlete in the Athlete ese coach page. Make the tendency icon relly based on real value (Use latest 10 days)~~ ✅
- ~~When assign a Workout to an athelte, there is issue with the Change detection. As it is no zone (like Change detection on PUSH) Fix the issue~~ ✅
- ~~In the calendar, the workout name must be truncated if too long.~~ ✅
- ~~Make the filter for sport and training type a little bigger~~ ✅
- ~~Add Sport filter in History as well and add a Time range search element~~ ✅
- ~~Make the font a little bigger when displaying the block info (on graph and tooltip)~~ ✅
- ~~In PMC, make the fatigue zone less visible. The lines are challenging to read. FInd a smart way to improve that. Keep a solid line instead of dot/dash. The change of background is enough to understand~~ ✅
- ~~The goal page is not professional enough. The Goal on the calendar is not visible enough. Find a smart way to display it~~ ✅
- ~~In the calendar, when clicked on a planned workout, The modal must have a button to display the details of the workout. A link to the Training page with the workout selected must also be present (in the details part of the modal)~~ ✅
- ~~The details steps of workout in calendar display pace (probably running) instead of Power. In this case, no time or distance estimation must be done.~~ ✅
- ~~The estimation of distance or duration for steps is not correct. It must be done at retrieve and not during save because it depends on the user reference value.~~ ✅
- ~~The display of the goal in PMC needs also to be improved~~ ✅
- ~~Complete and skip button must be icon instead button in calendar week view~~ ✅
- ~~Drop down input in zones (sport and reference) have white  background. Almost everywhere in the app actually.~~ ✅
- ~~When custom reference, an input should appear to insert the name of the reference. The reference can be read only (It mean that the value is only setted by the coach) and athlete that have this zone will have new physiology (based on reference value). If~~ ✅
- ~~When creating a tag, the input must have tooltip and description. The tag creation must in the same time create the code.  The tag card must display the CODE and remove the GENERATE Invite as the CODE will be generated. TAg and code must be related~~ ✅
- ~~After Tag refactoring into group, Tag page (that need to be changed to group page) need to work again. In this page, as a coach, I want to see all group, and club group that I created with the invite code. The code need to be generated at group creation.~~ ✅
- ~~Feed in club must be more compact, with simple line event, without Icon, but you can do color for event type~~ ✅
- ~~After Creating a tag, the redeem code is the name of the group but it should be a random CODE. The copied box is hidden when clik on code~~ ✅
- ~~The custom zone into the Physiology page do not show athlete value, even if he has the value of the reference value~~ ✅


## TODO

- In Coach dashboard, athlete training scheduled list the last day of the week is dipslayed on the next wek. for example if I want to see monday 1 to sunday 7, I see sunday 31 to Saturday 6 and sunday 7 is display on 8 - 15 week. Fix tht
- Custom reference value name are not saved in ZONE DASHBOARD when clicking on save button. Athlete do not see the new bow to set the new reference value for the zones system of his coach
- The athlete must see the Custom zones of his coaches (Coaches from Clubs and coaches from Group). Distinct zone only. If zones are duplicate because I have same coach in clu and group, display only one. Zone is disable (same way as other zone) if reference value is not set (custom or already existing)

## Nice too have
- **Retrieve FIT files from Strava** — Implement an endpoint + service to fetch FIT activity files from Strava for the authenticated user. Requires the user to have granted all metric scopes (`activity:read_all`). Parse the FIT data and ingest relevant metrics (power, HR, cadence, TSS) into the session/history store.

## Later

- Remove the dev connector