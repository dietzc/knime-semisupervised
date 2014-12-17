package org.knime.paruni.semisupervised.nodes.activesearch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.knime.core.node.defaultnodesettings.SettingsModelDouble;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.util.Pair;
import org.knime.core.util.ThreadPool;
import org.knime.paruni.NodeTools;

/**
 * @author dietzc, University of Konstanz
 */
public class ActiveSearchOnGraphsNodeModel extends NodeModel {

    public static ExecutorService EXECUTIONSERVICE = Executors.newFixedThreadPool(100);

    private SettingsModelString m_classColModel = createClassColModel();

    private SettingsModelString m_idxColModel = createIdxCol();

    //    private SettingsModelString m_nnRelationWeightsColModel = createNNRelationWeightsColModel();

    //    private SettingsModelString m_nnIndicesColModel = createNNIndicesColModel();

    private SettingsModelString m_priorColModel = createPriorColModel();

    private SettingsModelDouble m_etaNodeModel = createEtaModel();

    private SettingsModelDouble m_priorWeightModel = createPriorWeightModel();

    //    private int m_nnRelationWeightsIdx;

    //    int m_nnIndicesIdx;

    int m_classIdx, m_priorIdx, m_idxColIdx;

    private ArrayList<String> m_universes;

    static SettingsModelString createClassColModel() {
        return new SettingsModelString("class_col_idx", "");
    }

    static SettingsModelString createIdxCol() {
        return new SettingsModelString("idx_col_idx", "");
    }

    static SettingsModelString createNNRelationWeightsColModel() {
        return new SettingsModelString("nn_relation_weight_model", "");
    }

    static SettingsModelString createNNIndicesColModel() {
        return new SettingsModelString("nn_indices_model", "");
    }

    static SettingsModelString createPriorColModel() {
        return new SettingsModelString("priors", "");
    }

    static SettingsModelDouble createEtaModel() {
        return new SettingsModelDouble("eta", 1.0);
    }

    static SettingsModelDouble createPriorWeightModel() {
        return new SettingsModelDouble("priorWeight", 1.0);
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
    public ActiveSearchOnGraphsNodeModel() {
        super(1, 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {

        m_classIdx = NodeTools.silentOptionalAutoColumnSelection(inSpecs[0], m_classColModel, IntValue.class);

        //        m_nnRelationWeightsIdx =
        //                NodeTools.silentOptionalAutoColumnSelection(inSpecs[0], m_nnRelationWeightsColModel,
        //                                                            CollectionDataValue.class);
        //        m_nnIndicesIdx =
        //                NodeTools.silentOptionalAutoColumnSelection(inSpecs[0], m_nnIndicesColModel, CollectionDataValue.class);

        m_priorIdx = NodeTools.silentOptionalAutoColumnSelection(inSpecs[0], m_priorColModel, DoubleValue.class);

        m_idxColIdx = NodeTools.silentOptionalAutoColumnSelection(inSpecs[0], m_idxColModel, IntValue.class);

        // tmp
        final Map<String, List<Integer>> universeIdxMap = new HashMap<String, List<Integer>>();

        final DataTableSpec dataTableSpec = inSpecs[0];
        for (int i = 0; i < inSpecs[0].getNumColumns(); i++) {
            final DataColumnSpec columnSpec = dataTableSpec.getColumnSpec(i);

            final String universeName = columnSpec.getProperties().getProperty("universe_name");

            if (universeName != null && universeIdxMap.get(universeName) == null) {
                universeIdxMap.put(universeName, new ArrayList<Integer>());
            }

            if (universeName != null && columnSpec.getType().isCollectionType()) {
                universeIdxMap.get(universeName).add(i);
            }
        }

        m_universes = new ArrayList<String>(universeIdxMap.keySet());

        return new DataTableSpec[]{createResSpec()};
    }

    /**
     * @param universeIdxMap
     * @param universes
     * @return
     */
    private DataTableSpec createResSpec() {
        DataColumnSpec[] dataColumnSpecs = new DataColumnSpec[(2 * m_universes.size()) + 2];

        int i = 0;
        for (final String universe : m_universes) {
            dataColumnSpecs[i] = new DataColumnSpecCreator("Estimate [" + universe + "]", DoubleCell.TYPE).createSpec();
            dataColumnSpecs[i + 1] =
                    new DataColumnSpecCreator("Certainty [" + universe + "]", DoubleCell.TYPE).createSpec();

            i += 2;
        }

        dataColumnSpecs[(2 * m_universes.size())] =
                new DataColumnSpecCreator("Estimate [Max]", DoubleCell.TYPE).createSpec();
        dataColumnSpecs[(2 * m_universes.size()) + 1] =
                new DataColumnSpecCreator("Certainty [Max]", DoubleCell.TYPE).createSpec();
        return new DataTableSpec(dataColumnSpecs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec)
            throws Exception {
        if (m_classIdx == -1) {
            m_classIdx =
                    NodeTools.silentOptionalAutoColumnSelection(inData[0].getSpec(), m_classColModel, IntValue.class);
        }

        //        if (m_nnRelationWeightsIdx == -1) {
        //            m_nnRelationWeightsIdx =
        //                    NodeTools.silentOptionalAutoColumnSelection(inData[0].getSpec(), m_nnRelationWeightsColModel,
        //                                                                CollectionDataValue.class);
        //        }
        //
        if (m_idxColIdx == -1) {
            m_idxColIdx =
                    NodeTools.silentOptionalAutoColumnSelection(inData[0].getSpec(), m_idxColModel, IntValue.class);
        }

        if (m_priorIdx == -1) {
            m_priorIdx =
                    NodeTools
                            .silentOptionalAutoColumnSelection(inData[0].getSpec(), m_priorColModel, DoubleValue.class);
        }

        // tmp
        final Map<String, List<Integer>> universeIdxMap = new HashMap<String, List<Integer>>();

        final DataTableSpec dataTableSpec = inData[0].getDataTableSpec();
        for (int i = 0; i < inData[0].getDataTableSpec().getNumColumns(); i++) {
            final DataColumnSpec columnSpec = dataTableSpec.getColumnSpec(i);

            final String universeName = columnSpec.getProperties().getProperty("universe_name");

            if (universeName != null && universeIdxMap.get(universeName) == null) {
                universeIdxMap.put(universeName, new ArrayList<Integer>());
            }

            if (universeName != null && columnSpec.getType().isCollectionType()) {
                universeIdxMap.get(universeName).add(i);
            }
        }

        final HashMap<String, Pair<ActiveSearchOnGraphsPoint[], double[][]>> pointsMap = new HashMap<>();
        final HashMap<RowKey, List<ActiveSearchOnGraphsPoint>> globalPointMap = new HashMap<>();

        for (final String universe : m_universes) {
            final int idxA = universeIdxMap.get(universe).get(0);
            final int idxB = universeIdxMap.get(universe).get(1);

            final Pair<ActiveSearchOnGraphsPoint[], double[][]> pointsInUniverse;
            if (inData[0].getDataTableSpec().getColumnSpec(idxA).getType().getCollectionElementType()
                    .isCompatible(IntValue.class)) {
                pointsInUniverse = extractPoints(inData[0], idxB, idxA, globalPointMap);
            } else {
                pointsInUniverse = extractPoints(inData[0], idxA, idxB, globalPointMap);
            }

            pointsMap.put(universe, pointsInUniverse);
        }

        final ActiveSearchOnGraphs[] algo = new ActiveSearchOnGraphs[m_universes.size()];

        int a = 0;
        for (final Entry<String, Pair<ActiveSearchOnGraphsPoint[], double[][]>> entry : pointsMap.entrySet()) {
            algo[a++] =
                    new ActiveSearchOnGraphs(m_priorWeightModel.getDoubleValue(), entry.getValue().getFirst(), entry
                            .getValue().getSecond());
        }

        final List<Future<Void>> futures = new ArrayList<>();

        // store all points in unlabeled

        for (a = 0; a < m_universes.size(); a++) {
            final int proxy = a;
            final Callable<Void> callable = new Callable<Void>() {

                @Override
                public Void call() throws Exception {
                    algo[proxy].propagate(exec);
                    return null;
                }
            };

            futures.add(ThreadPool.currentPool().submit(callable));
        }

        for (final Future<Void> future : futures) {
            future.get();
        }

        final BufferedDataContainer containerP0 = exec.createDataContainer(createResSpec());

        for (final List<ActiveSearchOnGraphsPoint> points : globalPointMap.values()) {
            double max = 0;
            double maxCertainty = 0;
            final DoubleCell[] cells = new DoubleCell[(2 * points.size()) + 2];
            for (int i = 0; i < points.size() * 2; i += 2) {
                max = Math.max(points.get(i).getEstimate(), max);
                maxCertainty = Math.max(points.get(i).getCertainty(), maxCertainty);
                cells[i] = new DoubleCell(points.get(i).getEstimate());
                cells[i + 1] = new DoubleCell(points.get(i).getCertainty());
            }

            cells[(2 * points.size())] = new DoubleCell(max);
            cells[(2 * points.size()) + 1] = new DoubleCell(maxCertainty);

            containerP0.addRowToTable(new DefaultRow(points.get(0).getKey(), cells));
        }

        containerP0.close();

        return new BufferedDataTable[]{containerP0.getTable()};
    }

    private Pair<ActiveSearchOnGraphsPoint[], double[][]>
            extractPoints(final BufferedDataTable table, final int universeRelationsIdx, final int universeIndicesIdx,
                          final Map<RowKey, List<ActiveSearchOnGraphsPoint>> globalPointMap)
                    throws CanceledExecutionException {

        final int numPoints = table.getRowCount();

        final ActiveSearchOnGraphsPoint[] allPoints = new ActiveSearchOnGraphsPoint[numPoints];
        final double[][] relationWeightMatrix = new double[numPoints][numPoints];

        final Iterator<DataRow> it = table.iterator();
        while (it.hasNext()) {
            final DataRow row = it.next();
            final int globalIdx = ((IntValue)row.getCell(m_idxColIdx)).getIntValue();
            final int clazz = ((IntValue)row.getCell(m_classIdx)).getIntValue();
            final ActiveSearchOnGraphsPoint point;
            if (clazz == -1) {
                point =
                        new ActiveSearchOnGraphsPoint(row.getKey(), globalIdx,
                                ((DoubleValue)row.getCell(m_priorIdx)).getDoubleValue());
            } else {
                point =
                        new ActiveSearchOnGraphsPoint(row.getKey(), globalIdx, clazz == 1.0 ? true : false,
                                m_etaNodeModel.getDoubleValue());
            }

            allPoints[globalIdx] = point;

            List<ActiveSearchOnGraphsPoint> globalList = globalPointMap.get(row.getKey());
            if (globalList == null) {
                globalPointMap.put(row.getKey(), globalList = new ArrayList<>());
            }

            globalList.add(point);

            final Iterator<DataCell> relationWeights =
                    ((CollectionDataValue)row.getCell(universeRelationsIdx)).iterator();
            final Iterator<DataCell> indices = ((CollectionDataValue)row.getCell(universeIndicesIdx)).iterator();

            while (relationWeights.hasNext()) {
                final int idx = ((IntValue)indices.next()).getIntValue();
                final double weight = ((DoubleValue)relationWeights.next()).getDoubleValue();

                if (idx == globalIdx) {
                    continue;
                }

                point.addNeighbor(idx);
                relationWeightMatrix[globalIdx][idx] = weight;
            }
        }

        return new Pair<ActiveSearchOnGraphsPoint[], double[][]>(allPoints, relationWeightMatrix);
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
        m_classColModel.saveSettingsTo(settings);
        m_etaNodeModel.saveSettingsTo(settings);
        m_priorColModel.saveSettingsTo(settings);
        m_priorWeightModel.saveSettingsTo(settings);
        m_idxColModel.saveSettingsTo(settings);
        //        m_nnRelationWeightsColModel.saveSettingsTo(settings);
        //        m_nnIndicesColModel.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_classColModel.validateSettings(settings);
        m_etaNodeModel.validateSettings(settings);
        m_priorColModel.validateSettings(settings);
        m_priorWeightModel.validateSettings(settings);
        m_idxColModel.validateSettings(settings);
        //        m_nnRelationWeightsColModel.validateSettings(settings);
        //       m_nnIndicesColModel.validateSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_classColModel.loadSettingsFrom(settings);
        m_etaNodeModel.loadSettingsFrom(settings);
        m_priorColModel.loadSettingsFrom(settings);
        m_priorWeightModel.loadSettingsFrom(settings);
        m_idxColModel.loadSettingsFrom(settings);
        //      m_nnRelationWeightsColModel.loadSettingsFrom(settings);
        //        m_nnIndicesColModel.loadSettingsFrom(settings);
    }
}
