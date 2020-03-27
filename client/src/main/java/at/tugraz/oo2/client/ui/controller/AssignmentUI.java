package at.tugraz.oo2.client.ui.controller;

import at.tugraz.oo2.client.ClientConnection;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;


public class AssignmentUI extends TabPane {

	public AssignmentUI(ClientConnection clientConnection) {
		this.setDisable(true);
		Tab liveTab = new Tab("Live data");
		Tab lineChartTab = new Tab("Line chart");
		Tab scatterTab = new Tab("Scatter");
		Tab liveChartTab = new Tab("Live Chart");
		Tab jobStatusTab = new Tab("Job status");
		Tab clusterVisualization = new Tab("Cluster Visualization");
		Tab sketchbasedSearch = new Tab("Sketch Search");
		getTabs().add(liveTab);
		getTabs().add(lineChartTab);
		getTabs().add(scatterTab);
		getTabs().add(liveChartTab);
		getTabs().add(jobStatusTab);
		getTabs().add(sketchbasedSearch);
		getTabs().add(clusterVisualization);

		liveTab.setContent(new LiveUI(clientConnection));
		liveTab.setClosable(false);
		lineChartTab.setContent(new LineChartUI(clientConnection));
		lineChartTab.setClosable(false);
		scatterTab.setContent(new ScatterUI(clientConnection));
		scatterTab.setClosable(false);
		liveChartTab.setContent(new LiveChartUI((clientConnection)));
		liveChartTab.setClosable(false);
		jobStatusTab.setContent(new JobStatusUI((clientConnection)));
		jobStatusTab.setClosable(false);
		clusterVisualization.setContent(new ClusterUI(clientConnection, this));
		clusterVisualization.setClosable(false);
		sketchbasedSearch.setContent(new SketchUI(clientConnection, this));
		sketchbasedSearch.setClosable(false);

		this.getSelectionModel().select(liveTab);
		setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
		clientConnection.addConnectionOpenedListener(this::onConnectionOpened);
		clientConnection.addConnectionClosedListener(this::onConnectionClosed);
		clientConnection.addConnectionBrokeListener(this::onConnectionClosed);
	}

	private void onConnectionOpened() {
		this.setDisable(false);
	}

	private void onConnectionClosed() {
		this.setDisable(true);
	}

}
