package org.knime.semisupervised.nodes.lp;

import org.knime.core.data.StringValue;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnNameSelection;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;

/**
 *
 */
public class LabelPropagationNodeFactory extends NodeFactory<LabelPropagationNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNrNodeViews() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDialog() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<LabelPropagationNodeModel> createNodeView(final int viewIndex,
                                                              final LabelPropagationNodeModel nodeModel) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LabelPropagationNodeModel createNodeModel() {
        return new LabelPropagationNodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    protected NodeDialogPane createNodeDialogPane() {
        return new DefaultNodeSettingsPane() {
            {

                addDialogComponent(new DialogComponentColumnNameSelection(
                        LabelPropagationNodeModel.createClassColModel(), "Class Column (Port 1)", 1, StringValue.class));

                addDialogComponent(new DialogComponentNumber(LabelPropagationNodeModel.createNumNeighborsModel(),
                        "Number of Neighbors", 1));

                addDialogComponent(new DialogComponentNumber(LabelPropagationNodeModel.createNumIterationsModel(),
                        "Number of Iterations", 1));

                addDialogComponent(new DialogComponentNumber(LabelPropagationNodeModel.createKernelSmoothingModel(),
                        "Number of Kernel Smoothing", 0.05));

            }
        };
    }
}
