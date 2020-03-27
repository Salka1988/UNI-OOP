# Design decisions: Assignment 2

### Cluster visualization
- the user selects a sensor (location and metric), begin and end date, interval clusters (in minutes), points per cluster, and the number of clusters, then clicks on the `cluster` button to issue a cluster request
- list of all clusters is presented in a table view, which containts the cluster number, cluster error, and member count
- on the left side, a scroll pane containts each cluster represented as a line chart
- a double click on a table view entry opens a new tab which shows cluster details
- if a tab is already opened, the user is redirected to that opened tab, so it won't loose scrolling state
- at the top of the tab screen, a label displays the total cluster error
- each member is presented as a line chart in a scroll pane, and each chart has the member error as title

### Distributed Sim
- SIM command is sent to server with necessary information
- server queries all sensors with given metric and saves it in a list of jobs
- server sends out jobs to clients on per sensor basis, where every client gets a job that contains one sensor
- client gets a job and starts a SimWorker thread
- thread checks from minwindow to maxwindow size for every subseries possible similiarities, and sorts maxrefcount errors ascendingly and then sends it back to server
- server sleeps until some job is done, or until client is interrupted and returns CLOSE command
- server sends only 5 jobs per client and sleeps unti it can send more, or until all jobs are done and it has to pack up final response
- when all jobs are done server packs up final response and sends it back to requesting client.

### Sketch Based Search and Visualization
- the user selects a metric, start date, end date, min. window size, max. window size, and max. result count. On the right side, a drawing board is placed with which the user is able to sketch a curve for which the similiraty search operation should be executed.
- Upon receiving results, the results are presented in a table view, which displays the location, error, and the number of data points of each result.
- Upon clicking on an item in the table view, a new tab opens where the curve from the similarity search result is represented in its own line chart.
- Lines and points can be deleted from the drawing board by pressing the right mouse click button.
- There is also a button on the left grid that deletes the whole sketch from the drawing board.

# Bonus Tasks Description for Assignment 2:

### Writing cached data to disk
- cacheManager is expanded with new service which starts at a given time and creates persistant cache
- cacheInThread and cacheOutThread are responsible for importing cache to running aplication or exporting cache at given time through service.
- all data that we are sending to persistant cache are stored in JSON format 
- data classes that we need in cache are expanded with `toJsonOut()` function
- JSON format files are easy for parsing, exporting and importing
- When importing JSON file is passed to a JSONObject and when adding it to "the cache" the cache menager is checking it for duplicates 

### Caching sensor List and live data
- in cacheManager are two new caches, one for live data one for sensor list
- liveData is cached and saved for the next 5seconds, after it expired it will be re-queried and re-cached
- sensor list is cached and saved for next 10minutes, after it expires it will be re-queried and re-cached

### Job status tab
- New tab named Job status
- has a list of finished jobs by this client
- list contains: sensor data, timespan, client from which the job came, min and max window size
- new entries are added to the list whenever a new job has been finished
- implemented in class [JobStatusUI.java](https://student.cgv.tugraz.at/oop2_2019/003/blob/master/client/src/main/java/at/tugraz/oo2/client/ui/controller/JobStatusUI.java).

### Unit tests
- There are 4 Unit tests that are executed before starting the Analysis Server. ([ServerMain.java](https://student.cgv.tugraz.at/oop2_2019/003/blob/master/analysis-server/src/main/java/at/tugraz/oo2/server/ServerMain.java))
- The file where test are implemented is called [InfluxConnectionTest.java](https://student.cgv.tugraz.at/oop2_2019/003/blob/master/analysis-server/src/main/java/at/tugraz/oo2/server/InfluxConnectionTest.java).
- We test 4 features:
1. Get all sensors or the `ls` command. We test if the command gives a proper response upon a wrong scenario (without influx connection), and upon a good scenario (with influx connection).
2. Get latest sensor value or the `now` command. We test if the command gives a proper response upon a valid request (existing sensor), and upon an invalid request (non-existing sensor). 
3. Get data series or the `data` command. We test if the command gives a proper response upon a valid request that should return data, upon a valid request that should return no data, and upon an invalid request that should return a proper error message.
4. Get clusters or the `cluster` command. We test if the command gives a proper response upon a valid request with real data series, and upon a valid request with empty data series.