package at.tugraz.oo2.client;

import at.tugraz.oo2.Util;
import at.tugraz.oo2.commands.DataObject;
import at.tugraz.oo2.data.DataSeries;
import at.tugraz.oo2.data.MatchedCurve;
import at.tugraz.oo2.helpers.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class SimWorker extends Thread {

    public DataObject simJob;
    public RRHandlerThread rrHandler;
    public ClientConnection clientConnection;
    private DataSeries originalSeries;

    public SimWorker(ClientConnection clientConnection, DataObject dataObject, RRHandlerThread rrHandler) {
        this.clientConnection = clientConnection;
        this.simJob = dataObject;
        this.rrHandler = rrHandler;
    }

    private CompletableFuture<MatchedCurve> computeDistance(DataSeries dataSeries) {
        return CompletableFuture.supplyAsync(() -> {

            double error = dataSeries.similarity(simJob.job.simData.ref);
            MatchedCurve ret = new MatchedCurve(simJob.sensor, dataSeries, error);
            ret.setOriginalSeries(originalSeries.subSeries(dataSeries.getMinTime(), dataSeries.getMaxTime()).scale(dataSeries.getInterval()));
            return ret;
        });
    }

    @Override
    public void run() {
        Log.INFO("Sim Worker started. Data:" + simJob.job.toString(), false);

        simJob.job.result = new ArrayList<>();
        originalSeries = new DataSeries(simJob.dataSeries.getMinTime(),
                simJob.dataSeries.getInterval(),
                simJob.dataSeries.getData(),
                simJob.dataSeries.getPresent());

        simJob.dataSeries = simJob.dataSeries.normalize();

        List<Future<MatchedCurve>> futures = new ArrayList<>();
        // future for each sub series, then collect results in a list and send back to server
        long size = simJob.job.simData.minSize;

        for (; size <= simJob.job.simData.maxSize; size+= Util.MIN_TO_MS) {
            long interval = size / (simJob.job.simData.ref.length - 1);
            if (interval % Util.EPOCH != 0) {
                //Log.WARN("Window size of " + size / Util.MIN_TO_MS + " min is not divisible by 5 min!", false);
                continue;
            }

            //Log.INFO("Window size of " + size / Util.MIN_TO_MS + " min CAN be used!", false);

            long startTime = simJob.job.simData.from;
            long endTime = startTime + (interval * simJob.job.simData.ref.length);

            while (endTime <= simJob.job.simData.to) {

                DataSeries subSeries = simJob.dataSeries.subSeries(startTime, endTime);
                subSeries = subSeries.scale(interval);

                if(subSeries.getValueCount() != simJob.job.simData.ref.length) {
                    Log.WARN("Scaled series to interval of " + interval / Util.MIN_TO_MS + " min is different than the reference curve and cannot be compared! " +subSeries.getValueCount(), false);
                    continue;
                }

                if (!subSeries.hasGaps()) {
                    futures.add(computeDistance(subSeries));
                }

                startTime += Util.EPOCH;
                endTime = startTime + (interval * simJob.job.simData.ref.length);
            }
        }

        futures.forEach(myFuture -> {
            try {
                MatchedCurve obj = myFuture.get();
                simJob.job.result.add(obj);
            } catch (InterruptedException | ExecutionException e) {
                Log.ERROR("Couldn't get future!", true);
            }
        });

        Log.INFO("Got total of " + simJob.job.result.size() + " results", false);
        Collections.sort(simJob.job.result, Comparator.comparingDouble(MatchedCurve::getError));

        if(simJob.job.result.size() > simJob.job.simData.maxResultCount) {
            simJob.job.result = new ArrayList<>(simJob.job.result.subList(0, simJob.job.simData.maxResultCount));
        }

        clientConnection.callSimJobHandler(simJob.getCopy());
        rrHandler.addRequest(simJob);
    }
}