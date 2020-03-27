### To turn on log messages please change the 'logSwitch' variable in ~/shared/src/main/java/at/tugraz/oo2/helpers/Log.java from 'false' to 'true'

Design decisions:
    Assignment1:
        
        -server distributes every request from client into a new thread
        -client works over a single connection per instance opened
        -client writes requests immediately and then has a running thread that
            reads responses from the sockets until close and saves them in a list
        -after a response has been added to the list, all of the instances waiting on 
        the response are woken up and they check if that is their respective response 
            that they have been waiting for, if not, they go back to sleep
        - once a response is received it is taken from the list and sent back to client 
            for further processing
        -like this we can process multiple client requests without them blocking 
            each other

Bonus Tasks description:

Live Data as chart: 

    - displayed in new tab called Live Chart
    - has line chart graph for every metric, and shows last cca 5mins
    - starts with creating new connection
    - you can scroll up/down to see different graphs
    
Line Chart multiple sensors:

    - you can view same metric for multiple sensors on one line chart graph
    - when selecting multiple sensors form the list, metrics are updated accordingly
    - this means that if one metric is present in only one location/sensor it will 
        not be included, as only metrics that are common for all locations/sensors
        are taken

History of queries

    - every query in line chart and scatter is saved in a file
    - we added a button called "Show search history" in both where you can see
        all past queries made and choose one to load it to be queried by clicking 
        "Reuse" button
    - if chosen data will be loaded in side panel and you can click draw
    - in popup windows you can also clear whole history
    