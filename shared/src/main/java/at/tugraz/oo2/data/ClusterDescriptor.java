package at.tugraz.oo2.data;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * Represents a cluster returned by the k-means algorithm. It contains an average series along with its
 * existing member series.
 */
public final class ClusterDescriptor implements Serializable {
	private final double[] average;
	private final List<DataSeries> members;
	private final List<DataSeries> nonNormalizedMembers;

	public ClusterDescriptor(double[] average, List<DataSeries> members, List<DataSeries> nonNormalizedMembers) {
		this.average = average;
		this.members = members;
		this.nonNormalizedMembers = nonNormalizedMembers;
	}

	public double[] getAverage() {
		return average;
	}

	public List<DataSeries> getMembers() {
		return members;
	}

	public List<DataSeries> getNonNormalizedMembers() {
		return nonNormalizedMembers;
	}

	/**
	 * Computes the error of a single member series.
	 */
	public double getErrorOf(DataSeries member) {
		return member.similarity(average);
	}

	/**
	 * Computes the total error within this cluster.
	 */
	public double getClusterError() {
		return members.stream().mapToDouble(this::getErrorOf).sum();
	}

	/**
	 * Computes the total error of a cluster set.
	 */
	public static double getTotalError(Collection<ClusterDescriptor> clusters) {
		return clusters.stream().mapToDouble(ClusterDescriptor::getClusterError).sum();
	}
}
