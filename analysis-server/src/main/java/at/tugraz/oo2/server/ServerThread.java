package at.tugraz.oo2.server;

import at.tugraz.oo2.Util;
import at.tugraz.oo2.commands.DataObject;
import at.tugraz.oo2.commands.DataObjectBuilder;
import at.tugraz.oo2.data.DataSeries;
import at.tugraz.oo2.data.LiveData;
import at.tugraz.oo2.data.MatchedCurve;
import at.tugraz.oo2.data.Sensor;
import at.tugraz.oo2.helpers.Log;
import at.tugraz.oo2.server.Cache.CacheObject;
import at.tugraz.oo2.server.Cache.ListCacheObject;
import at.tugraz.oo2.server.Cache.NowCacheObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerThread extends Thread {

    private final Socket clientSocket;
    private final AnalysisServer analysisServer;
    private ObjectOutputStream outputToClientStream;
    private ObjectInputStream inputFromClientStream;
    private ExecutorService executorService;
    public AtomicInteger jobCounter;
    public LinkedBlockingQueue<DataObject> pendingJobs;
    private List<DataObject> myJobs; // TODO: toma 2
    public final List<DataObject> doneJobs;
    public long id;

    public ServerThread(Socket clientSocket, AnalysisServer analysisServer, long id) {
        this.clientSocket = clientSocket;
        this.analysisServer = analysisServer;
        this.executorService = Executors.newFixedThreadPool(100);
        this.id = id;
        this.pendingJobs = new LinkedBlockingQueue<>();
        this.doneJobs = new ArrayList<>();
        this.jobCounter = new AtomicInteger(0);
        this.myJobs = new ArrayList<>();
    }

    private synchronized void writeToClient(DataObject obj) {
        try {
            outputToClientStream.writeObject(obj);
            outputToClientStream.flush();
        } catch (IOException e) {
            Log.WARN("Couldn't write response to socket!", true);
            e.printStackTrace();
        }
    }

    public void run() {
        try {
            outputToClientStream = new ObjectOutputStream(clientSocket.getOutputStream());
            inputFromClientStream = new ObjectInputStream(clientSocket.getInputStream());

            boolean shouldListen = true;

            while (shouldListen) {
                DataObject command = (DataObject) inputFromClientStream.readObject();
                Log.INFO("Command: "+ command.command, true);
                switch (command.command)
                {
                    case LS:
                        DataObject lsObject = command.getCopy();
                        executorService.submit(()-> {

                            ListCacheObject listCacheObject = new ListCacheObject(lsObject);
                            lsObject.sensorList = analysisServer.cacheManager.getCachedSensorList(listCacheObject);

                            writeToClient(lsObject);
                        });
                        break;
                    case NOW:
                        DataObject nowObject = command.getCopy();
                        executorService.submit(() -> {

                            NowCacheObject nowCacheObject = new NowCacheObject(nowObject);
                            nowObject.dataPoint = analysisServer.cacheManager.getCachedCurrentData(nowCacheObject);

                            writeToClient(nowObject);
                        });
                        break;
                    case DATA:
                        DataObject dataSObject = command.getCopy();
                        executorService.submit(() -> {

                            CacheObject cacheObject = new CacheObject(dataSObject);
                            dataSObject.dataSeries = analysisServer.cacheManager.getCachedData(cacheObject);

                            //take subseries if smaller range than one in cache
                            dataSObject.dataSeries = dataSObject.dataSeries.subSeries(dataSObject.from, dataSObject.to);

                            //scale to interval
                            Log.INFO("Scaling", true);
                            try{
                                dataSObject.dataSeries = dataSObject.dataSeries.scale(dataSObject.interval);
                                writeToClient(dataSObject);
                            }
                            catch (Exception e) {
                                dataSObject.setErrorMessage("Couldn't scale to the interval! " + e.getMessage() + "\n\nFallback to default interval of 5min!");
                                Log.ERROR(dataSObject.getErrorMessage(), true);
                                writeToClient(dataSObject);
                            }
                        });
                        break;
                    case LIVE:
                        DataObject liveObject = command.getCopy();
                        executorService.submit(() -> {
                            getForLiveData(liveObject);
                            writeToClient(liveObject);
                        });
                        break;
                    case SIM:
                        DataObject simObject = command.getCopy();
                        executorService.submit(() -> {
                            splitSimilaritySearch(simObject);
                            writeToClient(simObject);
                        });
                        break;
                    case SIMJOB:
                        DataObject simJobObject = command.getCopy();
                        executorService.submit(() -> {
                            ServerThread origin = null;
                           synchronized (analysisServer.clients) {
                               origin = analysisServer.clients.get(simJobObject.job.origin);
                           }

                           if(origin != null) {
                               Log.INFO("Job finished by worker #" + simJobObject.job.worker + " from origin #" + origin.id, true);
                               synchronized (origin.doneJobs) {
                                   origin.doneJobs.add(simJobObject);
                                   this.jobCounter.decrementAndGet();
                                   origin.doneJobs.notifyAll();
                               }
                           }
                           else {
                               Log.WARN("No origin with id= " + simJobObject.job.origin + " found!", true);
                           }
                        });
                        break;
                    case CLOSE:
                        Log.INFO("Closing connection on client side. Thread should close as well and stop running!", true);
                        shouldListen = false;

                        synchronized (analysisServer.clients) {
                            analysisServer.clients.remove(this.id);
                            if(!this.myJobs.isEmpty()) {

                                for(DataObject ende: this.myJobs) {
                                    ServerThread current = analysisServer.clients.get(ende.job.origin);

                                    if(current != null) {
                                        synchronized (current.doneJobs) {
                                            current.pendingJobs.add(ende);
                                            current.doneJobs.notifyAll();
                                        }

                                    }
                                }
                            }
                        }
                        executorService.shutdown();
                        break;

                    case CLUSTER:
                        Log.INFO("Cluster request received...", true);
                        DataObject clusterObject = command.getCopyForClustering();
                        executorService.submit(() -> {

                            CacheObject cacheObject = new CacheObject(clusterObject);
                            clusterObject.dataSeries = analysisServer.cacheManager.getCachedData(cacheObject);

                            analysisServer.influxConnection.getClusters(clusterObject);
                            writeToClient(clusterObject);
                        });
                        break;


                    default: Log.WARN("Command not found",true);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            Log.ERROR("Something went wrong when trying to handle client request in thread: " + this.getId() + "\nMessage: " + e.getMessage(), true);
            try {
                outputToClientStream.close();
                inputFromClientStream.close();
            } catch (IOException s) {
                Log.ERROR("Couldn't close client socket/streams!", true);
            }
        } finally {
            try {
                outputToClientStream.close();
                inputFromClientStream.close();
            } catch (IOException e) {
                Log.ERROR("Couldn't close client socket/streams!", true);
            }

        }

    }

    private void getForLiveData(DataObject dataObject) {
        List<LiveData> list = new ArrayList<>();

        List<Future<LiveData>> futures = new ArrayList<>();
        ListCacheObject listCacheObject = new ListCacheObject(dataObject);

        dataObject.sensorList = analysisServer.cacheManager.getCachedSensorList(listCacheObject);

        if (dataObject.getErrorMessage() != null) {
            return;
        }

        List<Sensor> sensorList = dataObject.sensorList;

        sensorList.forEach(sensor -> {
            Future<LiveData> future = executorService.submit(() -> {
                LiveData liveData = new LiveData(sensor.getLocation(), sensor.getMetric());
                DataObject sensorObject = new DataObjectBuilder().setSensor(sensor).getDataObject();

                NowCacheObject nowCacheObject = new NowCacheObject(sensorObject);
                sensorObject.dataPoint = analysisServer.cacheManager.getCachedCurrentData(nowCacheObject);
                
                liveData.setDataPoint(sensorObject.dataPoint);
                return liveData;
            });

            futures.add(future);
        });

        futures.forEach(liveDataFuture -> {
            try {
                LiveData liveData = liveDataFuture.get();
                list.add(liveData);
            } catch (InterruptedException | ExecutionException e) {
                Log.ERROR("Couldn't get future!", true);
            }
        });

        dataObject.liveDataList = list;
    }

    // gets all data series for sim.from - sim.to range from all sensors that have given metric
    private LinkedBlockingQueue<DataObject> fetchAllData(DataObject dataObject) {

        LinkedBlockingQueue<DataObject> information = new LinkedBlockingQueue<>();
        List<Future<DataObject>> futures = new ArrayList<>();

        this.analysisServer.influxConnection.getAllSensors(dataObject);
        List<Sensor> sensorList = dataObject.sensorList;

        sensorList.forEach(sensor -> {
            if(sensor.getMetric().equals(dataObject.job.simData.metric)) {
                Future<DataObject> future = executorService.submit(() -> {
                    DataObject seriesObject = new DataObject(DataObjectBuilder.Command.SIMJOB, sensor, dataObject.job.simData.from, dataObject.job.simData.to, Util.EPOCH); //TODO: myb all cn be null
                    seriesObject.job = dataObject.copySimJob();

                    CacheObject cacheObject = new CacheObject(seriesObject);
                    seriesObject.dataSeries = analysisServer.cacheManager.getCachedData(cacheObject);

                    // interpolate missing points and normalize
                    seriesObject.dataSeries = seriesObject.dataSeries.interpolate();
                    // seriesObject.dataSeries = seriesObject.dataSeries.normalize();

                    return seriesObject;
                });

                futures.add(future);
            }
        });


        futures.forEach(myFuture -> {
            try {
                DataObject obj = myFuture.get();
                information.add(obj);
            } catch (InterruptedException | ExecutionException e) {
                Log.ERROR("Couldn't get future!", true);
            }
        });

        return information;
    }

    private void splitSimilaritySearch(DataObject dataObject) {

        Log.INFO("Starting split for similarity search..", true);

        // normalize ref curve 0-1
        DataSeries.normalize(dataObject.job.simData.ref);

        // fetch data from all sensors with given interval
       pendingJobs = fetchAllData(dataObject);
       long jobsToDo = this.pendingJobs.size();

        Log.INFO("Amount of jobs to do: " + jobsToDo, true);

        sendJobs();

        //slep
        try {
            synchronized (this.doneJobs) {
                while (true) {
                    if(this.doneJobs.size() != jobsToDo) {
                        if(this.pendingJobs.peek() != null) {
                            Log.INFO("More jobs to be sent, sending now..", true);
                            sendJobs();
                        }
                        Log.INFO("All clients occupied, jobs left = " + pendingJobs.size() + ", going to sleep..", true);
                        this.doneJobs.wait();
                    }
                    else {
                        Log.INFO("All sim jobs done, returning to main client..", true);
                        packData(dataObject);
                        cleanupSimJob();
                        return;
                    }
                }
            }
        }
        catch (InterruptedException e) {
            Log.ERROR("Something went wrong when handling sim jobs", true);
        }
    }

    private void sendJobs() {
        try {
            synchronized (analysisServer.clients) {
                for (long threadID: analysisServer.clients.keySet()) {
                    if(this.pendingJobs.peek() == null) break;

                    while (analysisServer.clients.get(threadID).jobCounter.get() < AnalysisServer.MAX_JOBS_PER_CLIENT) {
                        Log.INFO("Sending job #" + analysisServer.clients.get(threadID).jobCounter.get() + " to client  #" + threadID, true);

                        if(this.pendingJobs.peek() != null) {
                            DataObject jobObject = this.pendingJobs.take();
                            jobObject.job.origin = this.id;

                            jobObject.job.worker = threadID;
                            analysisServer.clients.get(threadID).writeToClient(jobObject);
                            analysisServer.clients.get(threadID).jobCounter.incrementAndGet();
                            analysisServer.clients.get(threadID).myJobs.add(jobObject);
                        }
                        else {
                            break;
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            Log.ERROR("Couldn't obtain job to take", true);
            e.printStackTrace();
        }
    }

    private void packData(DataObject simObj) {
        Log.INFO(this.doneJobs.size() + " jobs done! Packing up final response..", true);

        List<MatchedCurve> allErrors = new ArrayList<>();
        for(DataObject obj: this.doneJobs) { allErrors.addAll(obj.job.result); }

        Collections.sort(allErrors, Comparator.comparingDouble(MatchedCurve::getError));
        simObj.job.result = new ArrayList<>(allErrors.subList(0, simObj.job.simData.maxResultCount));
    }

    private void cleanupSimJob() {
        this.doneJobs.clear();
        this.pendingJobs.clear();
    }
}
