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
- Tss Value in week load in calendar are difficult to read. Make it bigger
- When using IA, Tags are linked to my training, but Tags to training must only be possible for coach and it must be explicilty asked (like assign to group/tag xxx)
- Add a button in calendar to reset to current Month or week
- The list of scheduled training must be displayed week by week nd it should be possible to navigate between week
- In the Physiologic, I want to see the 3 default zone in swil, bike and run and also the Custom Zone I have defined
- TAG management must be in a separated Tab in the top bar (ONLY for coach), Rename, remove athlete,..., When a tag is removed from an athlete it do not delete all the training but it will not be possible to see future training;  Find the best way to do it.


## Nice too have
- **Retrieve FIT files from Strava** — Implement an endpoint + service to fetch FIT activity files from Strava for the authenticated user. Requires the user to have granted all metric scopes (`activity:read_all`). Parse the FIT data and ingest relevant metrics (power, HR, cadence, TSS) into the session/history store.
- AGENT in AI instead of one unique chat
- 
