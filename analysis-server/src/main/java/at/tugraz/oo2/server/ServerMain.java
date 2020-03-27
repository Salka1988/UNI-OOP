package at.tugraz.oo2.server;

import org.junit.internal.TextListener;
import org.junit.runner.JUnitCore;

/**
 * Used to start the server. The actual implementation should be in other classes.
 */
public final class ServerMain {

	public static void main(String... args)  {
		// run unit tests before starting server
		// you may want to disable Log switch in Log.java to have a clean output
		JUnitCore junit = new JUnitCore();
		junit.addListener(new TextListener(System.out));
		junit.run(InfluxConnectionTest.class);

		if (args.length == 5) {
			final String influxUrl = args[0];
			final String influxDatabaseName = args[1];
			final String influxUser = args[2];
			final String influxPassword = args[3];
			final int serverPort = Integer.parseUnsignedInt(args[4]);
			final InfluxConnection influxConnection = new InfluxConnection(influxUrl, influxDatabaseName, influxUser, influxPassword);
			final AnalysisServer server = new AnalysisServer(influxConnection, serverPort);
			server.run();
			// control flow never reaches here
			// also not here
		} else {
			printUsage();
		}
	}

	private static void printUsage() {
		System.out.println("Usage:");
		System.out.println("  ./server.jar <influx url> <influx database name> <server username> <server password> <server port> - Starts the server");
	}
}
