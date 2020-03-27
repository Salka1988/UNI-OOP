package at.tugraz.oo2.client;

import at.tugraz.oo2.commands.DataObject;
import at.tugraz.oo2.commands.DataObjectBuilder;
import at.tugraz.oo2.helpers.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class RRHandlerThread extends Thread {

    private List<DataObject> responseList;
    private ClientConnection connection;
    private boolean isRunning = true;

    public RRHandlerThread(ClientConnection connection) {
        responseList = Collections.synchronizedList(new ArrayList<>());

        this.connection = connection;
    }

    public synchronized void addRequest(DataObject object) {
        try {
            connection.sendObject(object);
        }
        catch (Exception e) {
            Log.ERROR("Couldn't put object into queue", false);
            e.printStackTrace();
        }
    }

    DataObject getResponse(DataObject dataObject) {
            synchronized (responseList) {
                while(true) {
                    if (!isRunning) {
                        return null;
                    }

                    if(responseList.contains(dataObject)) {
                        Iterator i = responseList.iterator(); // Must be in synchronized block
                        while (i.hasNext()) {
                            DataObject current = (DataObject)i.next();
                            if(current.equals(dataObject)) {
                                responseList.remove(current);
                                //Log.INFO("Returning result", false);
                                return current;
                            }
                        }
                    }
                    else {
                        try {
                            responseList.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
    }

public int jobsDone = 0;
    public void run() {
        while (isRunning) {
            DataObject obj = connection.receiveObject();
            try {
                if(obj != null && obj.command == DataObjectBuilder.Command.SIMJOB) {    //TODO: obj != null dodano
                    SimWorker worker = new SimWorker(connection, obj, this);
                    worker.start();
                } else {
                    synchronized (responseList) {
                        responseList.add(obj);
                        responseList.notifyAll();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void turnROff()
    {
        Log.INFO("Jobs finished: " + jobsDone, false);
        isRunning = false;
        synchronized (responseList) {
            responseList.notifyAll();
        }
    }

}
