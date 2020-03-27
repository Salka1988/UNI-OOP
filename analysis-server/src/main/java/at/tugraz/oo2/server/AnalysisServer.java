package at.tugraz.oo2.server;

import at.tugraz.oo2.commands.SimJob;
import at.tugraz.oo2.helpers.Log;
import at.tugraz.oo2.server.Cache.CacheManager;
import com.google.common.cache.CacheBuilder;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class will hold the implementation of your server and handle connecting and connected clients.
 */
public final class AnalysisServer {
	/**
	 * The maximum number of similarity search jobs a client can handle in parallel. Only
	 * relevant for the second assignment.
	 */
	public static final int MAX_JOBS_PER_CLIENT = 5;

	private final int serverPort;

	public InfluxConnection influxConnection;
	public CacheManager cacheManager;
	public final Map<Long, ServerThread> clients;
	private long id = 1;

	public AnalysisServer(InfluxConnection influxConnection, int serverPort) {
		this.serverPort = serverPort;
		this.influxConnection = influxConnection;
		this.clients = new HashMap<>();
	}

	public void run() {
		try {
			ServerSocket serverSocket = new ServerSocket(serverPort);
			Log.INFO("Server socket has been created!", true);

			cacheManager = new CacheManager(this);
			Log.INFO("Starting cache manager!", true);

			while(true) {
				Socket socket = serverSocket.accept();
				Log.INFO("New client socket has been created!", true);
				ServerThread serverThread = new ServerThread(socket, this, id);
				synchronized (clients) {
					clients.put(serverThread.id, serverThread);
				}
				id++;

				serverThread.start();

			}
		}
		catch (IOException e) {
			Log.INFO("Something went wrong when accepting new client connections!", true);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
