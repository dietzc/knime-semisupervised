package org.knime.semisupervised.nodes.lp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.knime.core.data.DataRow;
import org.knime.core.data.RowKey;
import org.knime.core.util.Pair;
import org.knime.paruni.Utils;

/**
 * Simple data point in euclidean space
 *
 * @author Christian Dietz
 */
class DataPoint {

    public final HashMap<Integer, double[]> assignedDistributions;

    public final double[] vector;

    public final RowKey rowKey;

    public String classLabel = "?";

    public double tmp;

    public double uncertainty;

    public double uncertainty2;

    private List<Pair<DataPoint, Double>> neighbors;

    public DataPoint(final DataRow filteredRow, final String label) {
        this.rowKey = filteredRow.getKey();
        this.classLabel = label;
        this.vector = Utils.toDoubleArray(filteredRow);
        this.assignedDistributions = new HashMap<Integer, double[]>();
        this.neighbors = new ArrayList<Pair<DataPoint, Double>>();
    }

    public DataPoint(final DataPoint point) {
        this.rowKey = point.rowKey;
        this.classLabel = point.classLabel;
        this.vector = point.vector;
        this.assignedDistributions = new HashMap<Integer, double[]>();
        this.neighbors = point.neighbors;
    }

    public final void putDistributionAt(final int idx, final double[] dist) {
        assignedDistributions.put(idx, dist);
    }

    /**
     * @param label new label of the point. actually calling this makes only sense if "?" before
     */
    public final void setLabel(final String label) {
        classLabel = label;
    }

    /**
     * @param idx
     * @return distribution
     */
    public final double[] dist(final int idx) {
        return assignedDistributions.get(idx);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof DataPoint) {
            return ((DataPoint)obj).rowKey.getString().equalsIgnoreCase(rowKey.getString());
        } else {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /**
     *
     */
    public void updateUsingNeighbors(final int distributionIdxToStore) {
        final double[] res = new double[assignedDistributions.get(0).length];

        for (Pair<DataPoint, Double> pair : neighbors) {
            final double weight = pair.getSecond();
            final double[] dist = pair.getFirst().dist(0);
            double total = 0;
            for (int d = 0; d < res.length; d++) {
                res[d] += weight * dist[d];
                total += res[d];
            }

            if (total != 0) {
                for (int d = 0; d < res.length; d++) {
                    res[d] /= total;
                }
            }
        }
        assignedDistributions.put(distributionIdxToStore, res);
    }

    /**
     * @param neighbor
     */
    public void addNeigbor(final DataPoint neighbor, final double weight) {
        neighbors.add(new Pair<DataPoint, Double>(neighbor, weight));
    }
}
