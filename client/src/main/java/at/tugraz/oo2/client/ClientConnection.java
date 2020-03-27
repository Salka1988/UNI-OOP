package at.tugraz.oo2.client;

import at.tugraz.oo2.commands.DataObject;
import at.tugraz.oo2.commands.DataObjectBuilder;
import at.tugraz.oo2.commands.SimData;
import at.tugraz.oo2.commands.SimJob;
import at.tugraz.oo2.data.*;
import at.tugraz.oo2.helpers.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Used for managing the connection to the server and for sending requests.
 */
public final class ClientConnection implements AutoCloseable {

	private LinkedBlockingQueue<ConnectionEventHandler> connectionClosedEventHandlers;
	private LinkedBlockingQueue<ConnectionEventHandler> connectionOpenedEventHandlers;
	private LinkedBlockingQueue<ConnectionEventHandler> connectionBrokeEventHandlers;
	private LinkedBlockingQueue<SimJobAdded> simJobAddedLinkedBlockingQueue;
	private ObjectOutputStream outputStream;
	private ObjectInputStream inputStream;
	private Socket clientSocket;
	public RRHandlerThread rrHandler;
	private boolean closedWithDisconnect = false;

	public ClientConnection() {
		connectionClosedEventHandlers = new LinkedBlockingQueue<>();
		connectionOpenedEventHandlers = new LinkedBlockingQueue<>();
		connectionBrokeEventHandlers = new LinkedBlockingQueue<>();
		simJobAddedLinkedBlockingQueue = new LinkedBlockingQueue<>();
	}

	/**
	 * Establishes a connection to the server.
	 */
	public void connect(String url, int port) throws IOException {
		closedWithDisconnect = false;
		// initializing the client socket and streams should be done only once
		this.clientSocket = new Socket(url, port);
		outputStream = new ObjectOutputStream(this.clientSocket.getOutputStream());
		inputStream = new ObjectInputStream(this.clientSocket.getInputStream());

		this.rrHandler = new RRHandlerThread(this);
		rrHandler.start();

		connectionOpenedEventHandlers.forEach(ConnectionEventHandler::apply);
	}

	/**
	 * Registers a handler that will be called when the connection is opened.
	 */
	public void addConnectionClosedListener(ConnectionEventHandler eventHandler) {
		connectionClosedEventHandlers.add(eventHandler);
	}

	/**
	 * Registers a handler that will be called when the connection is closed either by
	 * the client itself or by the server.
	 */
	public void addConnectionOpenedListener(ConnectionEventHandler eventHandler) {
		connectionOpenedEventHandlers.add(eventHandler);
	}

	/**
	 * Registers a handler that will be called when the connection is broke
	 * e.g. when the server is not available or similar
	 */
	public void addConnectionBrokeListener(ConnectionEventHandler eventHandler) {
		connectionBrokeEventHandlers.add(eventHandler);
	}

	public void setSimJobAddedHandler(SimJobAdded event) {
		simJobAddedLinkedBlockingQueue.add(event);
	}

	public void callSimJobHandler(DataObject dataObject) {
		simJobAddedLinkedBlockingQueue.forEach(simJobAdded -> simJobAdded.onSimAdded(dataObject));
	}

	@Override
	public void close() {
		try {
			Log.INFO("Close method called, closing socket and streams!", false);
			inputStream.close();
			outputStream.close();
			clientSocket.close();
			rrHandler.turnROff();

			connectionClosedEventHandlers.forEach(ConnectionEventHandler::apply);
		} catch (IOException e) {
			Log.ERROR("Socket couldn't close successfully!", false);
//			e.printStackTrace();
		}
	}

	/*
	 * Tells the server that we are going to close the connection
	 * Handles the problem when closing the connection without sending any data beforehand
	 *
	 * This method is only called from GUI, the flag 'closedWithDisconnect' will indicate
	 * if the onConnectionBroke listener should be notified
	 * */
	public void closeServerThread() {
		try{
			closedWithDisconnect = true;
			if (outputStream != null) {
				synchronized (outputStream) {
					DataObject cmd = new DataObjectBuilder().setComand(DataObjectBuilder.Command.CLOSE).getDataObject();
					outputStream.writeObject(cmd);
					this.close();
				}
			}
		}
		catch (Exception e) {
			Log.ERROR("Socket already closed!", false);
		}
	}

	public void sendObject(DataObject dataObject) {
		try {
			outputStream.writeObject(dataObject);
		}
		catch (IOException e) {
//			e.printStackTrace();
			if (!closedWithDisconnect) {
				connectionBrokeEventHandlers.forEach(ConnectionEventHandler::apply);
			}
			Log.WARN("Couldn't write object to stream!", false);
			close();
		}
	}

	public synchronized DataObject receiveObject() {
		try {
			return (DataObject) inputStream.readObject();
		}
		catch (IOException | ClassNotFoundException e) {
			Log.WARN("Couldn't read object from stream!", false);
			if (!closedWithDisconnect) {
				connectionBrokeEventHandlers.forEach(ConnectionEventHandler::apply);
			}
			close();
		}
		return null;
	}

	/**
	 * Returns a future holding a list of all known sensors.
	 */
	public CompletableFuture<List<Sensor>> querySensors() {
		return CompletableFuture.supplyAsync(() -> {
			DataObject requestObject = new DataObjectBuilder().setComand(DataObjectBuilder.Command.LS).getDataObject();

			rrHandler.addRequest(requestObject);
			DataObject responseObject = rrHandler.getResponse(requestObject);

			if (!checkForErrors(responseObject)) return null;
			List<Sensor> list = responseObject.sensorList;

			return list;
		});
	}

	/**
	 * Returns a data point containing the last reading of the given sensor.
	 */
	public CompletableFuture<DataPoint> queryValue(Sensor sensor) {
		return CompletableFuture.supplyAsync(() -> {
			DataObject requestObject = new DataObjectBuilder().setComand(DataObjectBuilder.Command.NOW).setSensor(sensor).getDataObject();

			rrHandler.addRequest(requestObject);
			DataObject responseObject = rrHandler.getResponse(requestObject);

			if (!checkForErrors(responseObject)) return null;
			DataPoint dataPoint = responseObject.dataPoint;

			return dataPoint;
		});

	}

	/**
	 * Returns a data series containing the queried data.
	 */
	public CompletableFuture<DataSeries> queryData(Sensor sensor, long from, long to, long interval) {
		return CompletableFuture.supplyAsync(() -> {
			DataObject requestObject = new DataObjectBuilder().setComand(DataObjectBuilder.Command.DATA).setSensor(sensor).
					setFrom(from).setTo(to).setInterval(interval).getDataObject();

			rrHandler.addRequest(requestObject);
			DataObject responseObject = rrHandler.getResponse(requestObject);

			if (!checkForErrors(responseObject)) return null;
			return responseObject.dataSeries;
		});
	}

	public CompletableFuture<List<LiveData>> getLiveData() {
		return CompletableFuture.supplyAsync(() -> {
			DataObject requestObject = new DataObjectBuilder().setComand(DataObjectBuilder.Command.LIVE).getDataObject();
			rrHandler.addRequest(requestObject);
			DataObject response = rrHandler.getResponse(requestObject);
			if (!checkForErrors(response)) return null;
			List<LiveData> liveDataList = response.liveDataList;
//			Log.INFO("" + liveDataList.size(), false);
			return liveDataList;
		});
	}

	/**
	 * Second assignment.
	 */
	public CompletableFuture<List<ClusterDescriptor>> getClustering(Sensor sensor, long from, long to, long intervalClusters, long intervalPoints, int numberOfClusters) {
		return CompletableFuture.supplyAsync(() -> {
			Log.INFO("Cluster request sent to server...", false);
			DataObject requestObject = new DataObjectBuilder().setComand(DataObjectBuilder.Command.CLUSTER).setSensor(sensor).
					setFrom(from).setTo(to).setInterval(intervalPoints).getDataObject();
			requestObject.intervalPoints = intervalPoints;
			requestObject.intervalClusters = intervalClusters;
			requestObject.numberOfClusters = numberOfClusters;

			rrHandler.addRequest(requestObject);
			DataObject response = rrHandler.getResponse(requestObject);
			if (!checkForErrors(response)) return null;
			Log.INFO("Cluster response received from server...", false);
			return response.clusters;
		});
	}

	/**
	 * Second assignment.
	 */
	public CompletableFuture<List<MatchedCurve>> getSimilarity(String metric, long from, long to, long minSize, long maxSize, int maxResultCount, double[] ref) {
		return CompletableFuture.supplyAsync(() -> {

			SimJob job = new SimJob(-1, -1, new SimData(metric, from, to, minSize, maxSize, maxResultCount, ref));

			DataObject requestObject = new DataObjectBuilder()
					.setComand(DataObjectBuilder.Command.SIM)
					.getDataObject();

			requestObject.job = job;

			rrHandler.addRequest(requestObject);
			DataObject response = rrHandler.getResponse(requestObject);

			if (!checkForErrors(response)) return null;

			Log.INFO("Result size: " + response.job.result.size(), false);
			return response.job.result;
		});
	}

	@FunctionalInterface
	public interface ConnectionEventHandler {
		void apply();
	}

	@FunctionalInterface
	public interface SimJobAdded {
		void onSimAdded(DataObject dataObject);
	}


	public boolean checkForErrors(DataObject dataObject) {
		if (dataObject.getErrorMessage() != null) {
			System.out.println(Instant.now().toString());
			System.out.println(Log.ANSI_RED + dataObject.getErrorMessage() + Log.ANSI_RESET);
            connectionBrokeEventHandlers.forEach(ConnectionEventHandler::apply);
			return false;
		}
		return true;
	}
}