package org.knime.semisupervised.nodes.lp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.knime.base.util.kdtree.KDTree;
import org.knime.base.util.kdtree.KDTreeBuilder;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.RowKey;
import org.knime.core.data.StringValue;
import org.knime.core.data.container.ColumnRearranger;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.StringCell;
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
import org.knime.core.node.defaultnodesettings.SettingsModelInteger;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.paruni.NodeTools;

/**
 * @author dietzc, University of Konstanz
 */
public class LabelPropagationNodeModel extends NodeModel {

    static final int LABELED_INDEX = 0;

    static final int UNLABELED_INDEX = 1;

    private int m_classIdx;

    private SettingsModelString m_classColModel = createClassColModel();

    private SettingsModelInteger m_numNeighborsModel = createNumNeighborsModel();

    private SettingsModelInteger m_numIterations = createNumIterationsModel();

    private SettingsModelDouble m_alphaModel = createKernelSmoothingModel();

    private List<String> m_classLabels;

    private ColumnRearranger m_featureSpecUnlabeled;

    private ColumnRearranger m_featureSpecLabeled;

    private ArrayList<DataPoint> m_unlabeledPoints;

    private ArrayList<DataPoint> m_labeledPoints;

    private HashMap<RowKey, DataPoint> m_allPoints;

    private KDTree<DataPoint> m_tree;

    static SettingsModelString createClassColModel() {
        return new SettingsModelString("class_col_idx", "");
    }

    static SettingsModelInteger createNumIterationsModel() {
        return new SettingsModelInteger("num_iterations", 50);
    }

    static SettingsModelDouble createKernelSmoothingModel() {
        return new SettingsModelDouble("kernel_smoothing", 0.0);
    }

    static SettingsModelInteger createNumNeighborsModel() {
        return new SettingsModelIntegerBounded("neighbors", 5, -1, Integer.MAX_VALUE);
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
    public LabelPropagationNodeModel() {
        super(2, 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {
        m_classIdx = -1;
        m_classIdx = NodeTools.silentOptionalAutoColumnSelection(inSpecs[1], m_classColModel, StringValue.class);

        ArrayList<String> unlabeledIndices = new ArrayList<String>();
        for (int i = 0; i < inSpecs[UNLABELED_INDEX].getNumColumns(); i++) {
            if (inSpecs[UNLABELED_INDEX].getColumnSpec(i).getType().isCompatible(DoubleValue.class)) {
                unlabeledIndices.add(inSpecs[UNLABELED_INDEX].getColumnSpec(i).getName());
            }
        }

        ArrayList<String> labeledIndices = new ArrayList<String>();
        for (int i = 0; i < inSpecs[LABELED_INDEX].getNumColumns(); i++) {
            if (inSpecs[LABELED_INDEX].getColumnSpec(i).getType().isCompatible(DoubleValue.class)) {
                labeledIndices.add(inSpecs[LABELED_INDEX].getColumnSpec(i).getName());
            }
        }

        m_classLabels = new ArrayList<String>();
        for (DataCell value : inSpecs[LABELED_INDEX].getColumnSpec(m_classIdx).getDomain().getValues()) {
            m_classLabels.add(((StringCell)value).getStringValue());
        }

        m_featureSpecUnlabeled =
                createFilterRearranger(inSpecs[UNLABELED_INDEX],
                                       unlabeledIndices.toArray(new String[unlabeledIndices.size()]));

        m_featureSpecLabeled =
                createFilterRearranger(inSpecs[LABELED_INDEX],
                                       labeledIndices.toArray(new String[labeledIndices.size()]));

        return new DataTableSpec[]{createResSpec()};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec)
            throws Exception {

        if (m_classIdx == -1) {
            m_classIdx =
                    NodeTools
                            .silentOptionalAutoColumnSelection(inData[1].getSpec(), m_classColModel, StringValue.class);
        }

        // extract the points
        m_allPoints = new HashMap<RowKey, DataPoint>();

        m_unlabeledPoints = new ArrayList<DataPoint>();

        m_labeledPoints = new ArrayList<DataPoint>();

        // store all points in unlabeled
        extractUnlabeledPoints(inData[UNLABELED_INDEX], m_featureSpecUnlabeled, exec);

        extractUnlabeledPoints(inData[LABELED_INDEX], m_featureSpecLabeled, exec);

        KDTreeBuilder<DataPoint> treeBuilder = new KDTreeBuilder<DataPoint>(m_unlabeledPoints.get(0).vector.length);

        // since now all points are stored in unlabeled
        for (final DataPoint unlabeled : m_unlabeledPoints) {
            treeBuilder.addPattern(unlabeled.vector, unlabeled);
        }

        m_tree = treeBuilder.buildTree();

        // construct neighbor graph and initialize weights
        for (final DataPoint p : m_unlabeledPoints) {
            LabelPropagation.findAndSetNeighbors(p, m_tree, m_numNeighborsModel.getIntValue());
            p.putDistributionAt(0, new double[m_classLabels.size()]);
        }

        for (final DataPoint p : m_labeledPoints) {
            LabelPropagation.findAndSetNeighbors(p, m_tree, m_numNeighborsModel.getIntValue());
            double[] dist = new double[m_classLabels.size()];
            dist[m_classLabels.indexOf(p.classLabel)] = 1;
            p.putDistributionAt(0, dist);
        }

        // add the newly labeled rows
        handleNewlyLabeledKeys(inData[LABELED_INDEX], exec);

        LabelPropagation algo = new LabelPropagation(m_numIterations.getIntValue(), m_alphaModel.getDoubleValue());

        algo.execute(m_unlabeledPoints, m_labeledPoints, exec);

        final BufferedDataContainer container = exec.createDataContainer(createResSpec());
        for (final DataPoint unlabeled : m_unlabeledPoints) {
            DoubleCell[] classes = new DoubleCell[m_classLabels.size()];

            for (int i = 0; i < m_classLabels.size(); i++) {
                classes[i] = new DoubleCell(unlabeled.dist(0)[i]);
            }
            container.addRowToTable(new DefaultRow(unlabeled.rowKey, classes));
        }

        container.close();

        return new BufferedDataTable[]{container.getTable()};
    }

    private void extractUnlabeledPoints(final BufferedDataTable table, final ColumnRearranger rearranger,
                                        final ExecutionContext exec) throws CanceledExecutionException {

        final Iterator<DataRow> rowFeatureIt = exec.createColumnRearrangeTable(table, rearranger, exec).iterator();
        final Iterator<DataRow> rowCompleteIt = table.iterator();

        while (rowFeatureIt.hasNext()) {
            DataRow row = rowCompleteIt.next();
            DataPoint p = new DataPoint(rowFeatureIt.next(), null);

            m_unlabeledPoints.add(p);
            m_allPoints.put(row.getKey(), p);
        }
    }

    private void handleNewlyLabeledKeys(final BufferedDataTable table, final ExecutionContext exec) {

        final Iterator<DataRow> rowCompleteIt = table.iterator();

        String clazz = null;
        while (rowCompleteIt.hasNext()) {
            DataRow row = rowCompleteIt.next();
            if (m_classIdx != -1) {

                clazz = ((StringValue)row.getCell(m_classIdx)).getStringValue();
                if (!m_classLabels.contains(clazz)) {
                    m_classLabels.add(clazz);
                }
            }
            DataPoint dataPoint = m_allPoints.get(row.getKey());
            dataPoint.setLabel(clazz);

            m_labeledPoints.add(dataPoint);
            m_unlabeledPoints.remove(dataPoint);
        }
    }

    private DataTableSpec createResSpec() {
        DataColumnSpec[] dataColumnSpecs = new DataColumnSpec[m_classLabels.size()];

        for (int i = 0; i < m_classLabels.size(); i++) {
            dataColumnSpecs[i] = new DataColumnSpecCreator(m_classLabels.get(i), DoubleCell.TYPE).createSpec();
        }

        return new DataTableSpec(dataColumnSpecs);
    }

    /*
     * Simple column filter
     */
    private ColumnRearranger createFilterRearranger(final DataTableSpec in, final String... columns) {
        ColumnRearranger c = new ColumnRearranger(in);
        c.keepOnly(columns);
        return c;
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
        m_numNeighborsModel.saveSettingsTo(settings);
        m_alphaModel.saveSettingsTo(settings);
        m_numIterations.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_classColModel.validateSettings(settings);
        m_numNeighborsModel.validateSettings(settings);
        m_alphaModel.validateSettings(settings);
        m_numIterations.validateSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_classColModel.loadSettingsFrom(settings);
        m_numNeighborsModel.loadSettingsFrom(settings);
        m_alphaModel.loadSettingsFrom(settings);
        m_numIterations.loadSettingsFrom(settings);
    }
}
