package org.knime.paruni.semisupervised.nodes.ssakde;

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
public class SSAKDENodeFactory extends NodeFactory<SSAKDENodeModel> {

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
    public NodeView<SSAKDENodeModel> createNodeView(final int viewIndex, final SSAKDENodeModel nodeModel) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SSAKDENodeModel createNodeModel() {
        return new SSAKDENodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    protected NodeDialogPane createNodeDialogPane() {
        return new DefaultNodeSettingsPane() {
            {

                addDialogComponent(new DialogComponentColumnNameSelection(SSAKDENodeModel.createClassColModel(),
                        "Class Column (Port 1)", 1, StringValue.class));

                addDialogComponent(new DialogComponentNumber(SSAKDENodeModel.createNumNeighborsModel(),
                        "Number of Neighbors", 1));

                addDialogComponent(new DialogComponentNumber(SSAKDENodeModel.createNumIterationsModel(),
                        "Number of Iterations", 1));

                addDialogComponent(new DialogComponentNumber(SSAKDENodeModel.createKernelSmoothingModel(),
                        "Number of Kernel Smoothing", 0.05));

                addDialogComponent(new DialogComponentNumber(SSAKDENodeModel.createInitSigmaModel(), "Sigma", 0.05));

            }
        };
    }
}
