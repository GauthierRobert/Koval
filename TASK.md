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
- In Coach dashboard, athlete training scheduled list the last day of the week is dipslayed on the next wek. for example if I want to see monday 1 to sunday 7, I see sunday 31 to Saturday 6 and sunday 7 is display on 8 - 15 week. Fix tht
- Custom reference value name are not saved in ZONE DASHBOARD when clicking on save button. Athlete do not see the new box to set the new reference value in settings for the zones system of his coach. Remove the input to set the value in the physiology custom zone part
- In training library, the CLub are well displayed, the groups are duplicated (for owner). And athlete cannot see trainings of their groups o Clubs if they are not the owner
_ Training assign should be more clear, on training dashboard do not change, just me it clear that is it self assignment
  On Coach manement page, where I see single athlete, I must be able to assign a training to only one athlete. In that case, the athlete see it in its library
  On group management, I should have a button to assign a training. A modal open to generate a training, or use an already existing training in my library. As a coach, I must be able to choice the assignment date, the participant, the complet group or jsut some.
  On club management, as a Coach from the club, I should be able to add
  All training, assign to someone, though the coach management, group management, or club management, should be visible in the training libraray of the convern althete.
  To simplify the behavior, athlete should have in another table, the list of training id not created by himself but send by other, with the orign, Coach name, group name, club name;
- The leaflet map are empty in Race details were we should see the GPX map of the race
- A button simulate must be present in public race as well (disabled with explication if gpx are not present (Bike/RUN for triathlon))
- Modify the IA chat example to more reflect the actual TOOL and combination of TOOLS in the app. Make 5 examples
- In the simulation result, in the leaflet issue, the segment have void before and after and seems to overlap when hover. It is strange, try to fix that.
- In Member List; Tag should be displayed aside the member name, instead of the bottom of the card.
- Group name in Club member management page in group managemenbt section, the  is cropped, I only see the first letter, I want to see all the name.
- I must be able to modify GPX of race clicking on already added gpx on race Button is only limited to small Icon
- Change getRaceGoals. It must retrieve all the future goals of the members with all the participants in the clubs

- Sessions linked to Club group, should only be visible to club group member, unless the condition open to all is activated and respected
- The set Custom value should belong the the updateSettings api, as well at the set of the value should be displayed in the setting page where athlete set all the other reference. Hide 5k/10/semi/mararthon reference in running, a toggle can be there to show other reference value. Customs must be displayed in any case.
- Session reminder in coach dashboard must be in main dashboard instead (but only for coach)
- When add a training of a session, the chance to add an already existing training (of my own) should be possible. In that case, the club id is added into the club ids of the training. A training could then have multiple Clubs/group link
- Cancelling a session (single event only even for recurrent session) should be possible for coach/Admin/owner. An explication can be added on the reason. This trigger a notificatio  to all club members
- the create training ia modal display groups and user but there is issues: user are all selected, when click on group, it should select the group, and the user in the groups.
- The drag and drop of workout in calendar do not work well, moved training ghost box remain in old place;
- Delete session and recurring session (with possible of deleting recurring session or only single event of recurring session. Inspire of the edit behaviour)
- RaceGoals in club do not retrieve the goals of the members of the clubs.
- Display in a "tooltip" The member that participate in a RACE (in the club race page).
- Retrieve FIT files from Strava** — Implement an endpoint + service to fetch FIT activity files from Strava for the authenticated user. Requires the user to have granted all metric scopes (`activity:read_all`). Parse the FIT data and ingest relevant metrics (power, HR, cadence, TSS) into the session/history store.
- Zones are display label, lower-higher, pace with lower-higher, but pace must be displayed higher-lower, as it is inverse of intensity,more instense, lower the pace is.
- ~~ANDROID: Remove from route the IA assistant and History for now. Add //TODO Temporary~~ ✅
- ~~ANDROID: In physiology, when % = 0 do not display value /km /400m,...~~ ✅
- ~~ANDROID: Display the default zones in physiology~~ ✅
- ~~ANDROID: Compact training card in Trainings lib~~ ✅
- ~~ANDROID: Compact training filters~~ ✅
- ~~ANDROID: Remove Complete/skip/delete/usage from calendar workout cards~~ ✅
- ~~ANDROID: Move leave button at the place of the "joined" label in club session in calendar view. Remove the Joined label and add an icon below the sporticon to say I participate.~~ ✅
- ~~ANDROID: Add trainings title (with link to see the training) linked to the club session~~ ✅
- ~~ANDROID: Compact club session in calendar view.~~ ✅
- ~~ANDROID: Add waiting list feature in calendar session view~~ ✅
- ~~ANDROID: Move the user setting (logout, name) in a profile tab (aside Zones, History,Training,...)~~ ✅
- ~~ANDROID: Add reference value of the athlete in the training view (if 75% FTP, display 75% as it is and at the left part, display XXX W, the calculated value based on reference value)~~ ✅
- ~~ANDROID: Add possibility to see graphs value when over it.~~ ✅
- ~~ANDROID: Instead of My Training / Club filter, use My Training, [Name of Club 1], [Name of club 2], Name of Coach Group 1, ... Like in angular~~ ✅
- ~~ANDROID: User should be able to join Club/Group. Add this feature in profile/settings tab~~ ✅
- remove old way to edit blocks
- In training builder, sets do not work correctly, Intensity of rest is never used, it will always be a PAUSE with 0 intensity. It must be one or the other. Default Rest intensity must be 60%. A check box could help to choose between Passive rest (PAUSE, zero intensity) or active rest (steady intensity). Make it cleared in angular, how to group elements, that one element can be a set with pause>/steady intensity. The way to group element hidden and not clear
- Add way to link trainings into plan, I mean populate training with plans
- Disable all element in front end concerning plan for now (add //TODO temporary) Also, disable plans tools/prompt for IA for now
- Add CGU that user must accept at least once. It must be displayed in angular or android based on first connection.
- Google login in android redirect to localhost:4200 in dev mode. I suppose it does not wokr in production as well; Need to fix. Add redirect to mobile, new endpoint
- In Block Mode, the graphs in analyse workout still follows the unique element value when mouse is hover the graphs and not the block value
- Display description in a new line in the training lirbary details workout
- When set have zero rest, the display show a zero as set summary. It must be more clear.
- Club feed can be readded but it need to be improve. A void adding everything in it, only usefully information (Fixed next goal with athlète engagements), coach announcement (send notification), Propose a design for that, must I combine both, session list with details in the same page when clicked. I want in the feed that when an athlete as complete a COACH Session, that there is a new fixed feed event until next complete coach session. In this feed event, I want to see live reloading (SSE) of user number and user list (clickable) completing the workout. In this event, I can give Starva Kudos to all athlete that complet thee workout, it is a "like" that automaticcaly gives kudos to athelte that have activity on strava and that is listed in the complete workout event. This kind of event must be also applicable to completed Races. Use Queue or something efficient in that kind of case.
  Any member of the club can propose a new group bike ride, open swim session, or open run session. It is the same as a session. But different category. It is open session. And must be visible in a another tabs.
  Open session (three sport, must add rdv point with rdv hours, GPX if applicable.  athlete should also be able to create single session
  (like group ride, zwift ride,... in that case notification is created as well),
  Add GPX linkage functionality to some sessions when group ride (recurring session (always the same GPX), open session or single session)
- Icon is not found http://localhost:4200/media/assets/leaflet/marker-icon.png, marker-shadow.png:1  
  GET http://localhost:4200/media/assets/leaflet/marker-shadow.png 404 (Not Found)
  marker-icon-2x.png:1  GET http://localhost:4200/media/assets/leaflet/marker-icon-2x.png 404 (Not Found)
- GPX not well displayed in rides sessions
- When schedule a training in the workout library, no input for schedule date is visible and then no scheduled date is sent.
- When clicking on feed small session card leave or join, all the historic and future sessions appears not only from the week.
- In the feed right side panel,  add next goal of the club and separate recurring session and open session
- I want that the memory of the conversation is more than 8, but each message need to be compacted in 1 or 2 lines, for instance, i user created I have a huge context with training details, I want Training create : title with id.
- Button to switch from % to values in training lib does not work any more. I have this issue when click (maybe not related) : workout-visualization.component.html:221 NG0956: The configured tracking expression (track by identity) caused re-creation of the entire collection of size 6. This is an expensive operation requiring destruction and subsequent creation of DOM nodes, directives, components etc. Please review the "track expression" and make sure that it uniquely identifies items in a collection. Find more at https://v21.angular.dev/errors/NG0956
- notification.service.ts:64 Failed to get FCM token: AbortError: Failed to execute 'subscribe' on 'PushManager': Subscription failed - no active Service Worker at async _NotificationService.requestPermissionAndRegisterToken (notification.service.ts:52:21)
# TO VERIFY

## TO FIX
- Colour of Zone need to be changed, (in Zone distribution, Zone display in analysis, zone creation,...) must use colour that goes from purple (lowest) to dark red (highest). Default Zone Color must use the same system. You can assuse that the mean of the lowest zone is 0% then purple and the mean of the highest zone is 100% then dark red.

# TO IMPLEMENT
- SnapShot simulation race instead of save simulation race, it must be an action of user and parameters must be saved (including front end params)

# Club
- Add car sharing in club. Athlete can propose their car sharing in the club. Athlete can ask for car sharing and other athlete can accept. 
  A car sharing must be link to a club session or a race.  The athlete that propose must specify the number of seats and number of bike place. 
  The athlete that ask must specify if it is only him or also for his bike. Create new club tab for that. 

## Nice too have

## Later

- Remove the dev connector