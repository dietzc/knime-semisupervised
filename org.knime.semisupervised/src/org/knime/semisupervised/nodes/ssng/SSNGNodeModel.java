package org.knime.semisupervised.nodes.ssng;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.IntValue;
import org.knime.core.data.RowKey;
import org.knime.core.data.collection.CollectionDataValue;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.util.MutableDouble;
import org.knime.core.util.Pair;
import org.knime.paruni.NodeTools;
import org.knime.paruni.Utils;

/**
 * @author dietzc, University of Konstanz
 */
public class SSNGNodeModel extends NodeModel {

    static ExecutorService EXECUTIONSERVICE = Executors.newFixedThreadPool(100);

    private SettingsModelString m_idxColModel = createIdxCol();

    private int m_idxColIdx;

    private ArrayList<String> m_universes;

    static SettingsModelString createIdxCol() {
        return new SettingsModelString("idx_col_idx", "");
    }

    /**
     * InPort 1 = All unlabeled
     *
     * InPort 2 = Newly labeled
     *
     * OutPort 1 = Scored
     *
     * OutPort 2 = Induced DontKnows
     */
    public SSNGNodeModel() {
        super(1, 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {

        m_idxColIdx = NodeTools.silentOptionalAutoColumnSelection(inSpecs[0], m_idxColModel, IntValue.class);

        // tmp
        final Map<String, List<Integer>> universeIdxMap = extractUniverseIdxMap(inSpecs[0]);
        m_universes = new ArrayList<String>(universeIdxMap.keySet());

        return new DataTableSpec[]{createResSpec()};
    }

    /**
     * @param universeIdxMap
     * @param universeIdxMap
     * @param universes
     * @return
     */
    private DataTableSpec createResSpec() {

        final DataColumnSpec[] resSpec = new DataColumnSpec[3];

        resSpec[0] = new DataColumnSpecCreator("Estimate Active", DoubleCell.TYPE).createSpec();
        resSpec[1] = new DataColumnSpecCreator("Estimate Inactive", DoubleCell.TYPE).createSpec();
        resSpec[2] = new DataColumnSpecCreator("AL Score", DoubleCell.TYPE).createSpec();

        return new DataTableSpec(resSpec);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec)
            throws Exception {

        if (m_idxColIdx == -1) {
            m_idxColIdx =
                    NodeTools.silentOptionalAutoColumnSelection(inData[0].getSpec(), m_idxColModel, IntValue.class);
        }

        // parameters should be set via dialog later
        final double minCertaintyNGMembers = 0.5;
        final int numNeighbors = 50;
        //        final double alpha = 0.5;

        // extract all points and their neighbors in the according universe
        final Map<String, List<Integer>> universeIdxMap = extractUniverseIdxMap(inData[0].getDataTableSpec());

        final HashMap<String, SSNGPoint[]> pointsPerUniverse = new HashMap<>();
        final HashMap<RowKey, List<SSNGPoint>> globalPointMap = new HashMap<>();

        for (final String universe : m_universes) {
            pointsPerUniverse.put(universe,
                                  extractPoints(inData[0], universeIdxMap.get(universe), globalPointMap, universe));
        }

        // Create NGs for Active
        final HashMap<Integer, SSNG> ngs = new HashMap<>();
        for (final Entry<RowKey, List<SSNGPoint>> entry : globalPointMap.entrySet()) {

            int u = 0;
            for (final SSNGPoint pointInUniverse : entry.getValue()) {

                if (pointInUniverse.dist()[0] >= 0.5) {
                    // create NG for point...
                    ngs.put((pointInUniverse.getIdx() * m_universes.size()) + u, new SSNG(pointInUniverse,
                            pointsPerUniverse.get(pointInUniverse.universe()), minCertaintyNGMembers, 0, numNeighbors));
                } else {
                    ngs.put((pointInUniverse.getIdx() * m_universes.size()) + u, new SSNG(pointInUniverse,
                            pointsPerUniverse.get(pointInUniverse.universe()), minCertaintyNGMembers, 1, numNeighbors));
                }

                u++;
            }
        }

        // now select the best NGS and remove all dependend ones
        final HashSet<Integer> covered = new HashSet<>();
        ArrayList<SSNG> workingList = new ArrayList<>(ngs.values());

        Collections.sort(workingList);
        final Set<SSNG> selected = new HashSet<>();

        while (ngs.size() != 0) {
            selected.add(workingList.get(0));
            //            covered.add(workingList.get(0).centerIdx());
            covered.addAll(workingList.get(0).containedPoints());

            for (final int i : covered) {
                for (int u = 0; u < m_universes.size(); u++) {
                    ngs.remove((i * m_universes.size()) + u);
                }
            }

            exec.checkCanceled();
            covered.clear();

            System.out.println(ngs.size());

            Collections.sort(workingList = new ArrayList<>(ngs.values()));
        }

        final HashMap<RowKey, List<Pair<double[], Double>>> results = new HashMap<>();
        final BufferedDataContainer containerP0 = exec.createDataContainer(createResSpec());

        for (final SSNG ng : selected) {
            final SSNGPoint[] points = pointsPerUniverse.get(ng.getUniverse());
            final double coverage = ng.coverage();

            for (int i : ng.containedPoints()) {

                List<Pair<double[], Double>> list = results.get(points[i].getKey());
                if (list == null) {
                    list = new ArrayList<>();
                    results.put(points[i].getKey(), list);
                }

                list.add(new Pair<>(points[i].dist(), coverage));
            }
        }

        final HashMap<RowKey, Double> tmpScoreMap = new HashMap<>();
        for (final Entry<RowKey, List<SSNGPoint>> entry : globalPointMap.entrySet()) {
            final List<Pair<double[], Double>> res = results.get(entry.getKey());
            double totalCoverage = 0;
            double maxCoverage = 0;
            double[] resVector = null;

            for (final Pair<double[], Double> pair : res) {
                totalCoverage += pair.getSecond();
                maxCoverage = Math.max(pair.getSecond(), maxCoverage);
            }

            for (final Pair<double[], Double> pair : res) {
                if (resVector == null) {
                    resVector = new double[pair.getFirst().length];
                }

                for (int i = 0; i < resVector.length; i++) {
                    resVector[i] += pair.getFirst()[i] * pair.getSecond() / totalCoverage;
                }
            }

            res.clear();
            res.add(new Pair<>(resVector, 0.0d));

            // now we define the scores for each point in each universe. for now simply add everything up
            double discoveryWeight = 0;
            for (final SSNGPoint p : entry.getValue()) {
                discoveryWeight += p.getDiscoveryWeight();
            }

            tmpScoreMap.put(entry.getKey(), Utils.entropy(resVector) + (1 - maxCoverage)
                    + (1 - discoveryWeight / m_universes.size()));

        }

        // now: smooth score over universes

        final HashMap<RowKey, Double> scoreMap = new HashMap<>();
        for (final Entry<RowKey, List<SSNGPoint>> entry : globalPointMap.entrySet()) {
            double finalScore = 0;
            for (final SSNGPoint point : entry.getValue()) {
                double tmp = 0;
                for (final Pair<Integer, MutableDouble> pair : point.neighbors()) {
                    tmp +=
                            tmpScoreMap.get(pointsPerUniverse.get(point.universe())[pair.getFirst()].getKey())
                                    * pair.getSecond().doubleValue();
                }
                finalScore = Math.max(finalScore, tmp);
            }

            scoreMap.put(entry.getKey(), finalScore);
        }

        for (final Entry<RowKey, List<SSNGPoint>> entry : globalPointMap.entrySet()) {
            final List<Pair<double[], Double>> res = results.get(entry.getKey());

            double[] resVector = res.get(0).getFirst();

            final DataCell[] cells = new DataCell[3];

            if (resVector == null) {
                cells[0] = new DoubleCell(0);
                cells[1] = new DoubleCell(0);
                cells[2] = new DoubleCell(0);
            } else {
                for (int i = 0; i < resVector.length; i++) {
                    cells[i] = new DoubleCell(resVector[i]);
                }
            }

            cells[2] = new DoubleCell(scoreMap.get(entry.getKey()));

            containerP0.addRowToTable(new DefaultRow(entry.getKey(), cells));
        }

        containerP0.close();

        return new BufferedDataTable[]{containerP0.getTable()};
    }

    /**
     * @param dataTableSpec
     * @return
     */
    private Map<String, List<Integer>> extractUniverseIdxMap(final DataTableSpec dataTableSpec) {
        final HashMap<String, List<Integer>> universeIdxMap = new HashMap<String, List<Integer>>();

        for (int i = 0; i < dataTableSpec.getNumColumns(); i++) {
            final DataColumnSpec columnSpec = dataTableSpec.getColumnSpec(i);

            final String universeName = columnSpec.getProperties().getProperty("universe_name");

            if (universeName != null && universeIdxMap.get(universeName) == null) {
                universeIdxMap.put(universeName, new ArrayList<Integer>());
            }

            if (universeName != null && columnSpec.getType().isCollectionType()) {
                universeIdxMap.get(universeName).add(i);
            }

            if (universeName != null && columnSpec.getType().isCompatible(DoubleValue.class)) {
                universeIdxMap.get(universeName).add(i);
            }
        }

        return universeIdxMap;
    }

    /**
     * @param allDists
     * @return
     */
    public static double[] aggregateAvg(final double[] weights, final double[][] allDists) {
        // if we dont have any distribution exception should be thrown
        double[] res = new double[allDists[0].length];
        for (int u = 0; u < allDists.length; u++) {

            for (int d = 0; d < allDists[u].length; d++) {
                res[d] += weights[u] * allDists[u][d];
            }
        }

        return res;
    }

    private SSNGPoint[] extractPoints(final BufferedDataTable table, final List<Integer> indices,
                                      final Map<RowKey, List<SSNGPoint>> globalPointMap, final String universe)
            throws CanceledExecutionException {

        final int numPoints = table.getRowCount();

        final SSNGPoint[] allPoints = new SSNGPoint[numPoints];
        final Iterator<DataRow> it = table.iterator();
        while (it.hasNext()) {
            final DataRow row = it.next();
            final int globalIdx = ((IntValue)row.getCell(m_idxColIdx)).getIntValue();
            int neighborWeightsIdx = -1, neighborIndicesIdx = -1;

            int activityIndicator = -1;
            int inactivityIndicator = -1;

            for (final int i : indices) {
                if (table.getDataTableSpec().getColumnSpec(i).getType().isCollectionType()) {
                    if (table.getDataTableSpec().getColumnSpec(i).getType().getCollectionElementType()
                            .isCompatible(IntValue.class)) {
                        neighborIndicesIdx = i;
                    } else {
                        neighborWeightsIdx = i;
                    }
                } else if (activityIndicator == -1) {
                    activityIndicator = i;
                } else {
                    inactivityIndicator = i;
                }
            }

            double estimateActive = ((DoubleValue)row.getCell(activityIndicator)).getDoubleValue();
            double estimateInactive = ((DoubleValue)row.getCell(inactivityIndicator)).getDoubleValue();

            //            double uncertainty = Math.min(1 - estimateActive, 1 - estimateInactive);
            double total = estimateActive + estimateInactive;

            final SSNGPoint point =
                    new SSNGPoint(row.getKey(), globalIdx, new double[]{total == 0 ? 0 : estimateActive / total,
                            total == 0 ? 0 : estimateInactive / total}, universe);

            point.setDiscoveryWeight(Math.max(estimateActive, estimateInactive));
            allPoints[globalIdx] = point;

            List<SSNGPoint> globalList = globalPointMap.get(row.getKey());
            if (globalList == null) {
                globalPointMap.put(row.getKey(), globalList = new ArrayList<>());
            }

            globalList.add(point);

            final Iterator<DataCell> relationWeights =
                    ((CollectionDataValue)row.getCell(neighborWeightsIdx)).iterator();
            final Iterator<DataCell> neighborIndices =
                    ((CollectionDataValue)row.getCell(neighborIndicesIdx)).iterator();

            total = 0;
            while (relationWeights.hasNext()) {
                final int idx = ((IntValue)neighborIndices.next()).getIntValue();
                final double weight = ((DoubleValue)relationWeights.next()).getDoubleValue();

                if (idx == globalIdx) {
                    continue;
                }

                point.addNeighbor(idx, new MutableDouble(weight));
                total += weight;
            }

            // needs to be normalized
            for (final Pair<Integer, MutableDouble> pair : point.neighbors()) {
                pair.getSecond().setValue(pair.getSecond().doubleValue() / total);
            }
        }

        return allPoints;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // Nothing to do since now
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        // Nothing to do here
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        // Nothing to do here
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_idxColModel.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_idxColModel.validateSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_idxColModel.loadSettingsFrom(settings);
    }
}
