package org.knime.semisupervised.nodes.activesearch;

import org.knime.core.data.DoubleValue;
import org.knime.core.data.IntValue;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnNameSelection;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;

/**
 *
 */
public class ActiveSearchOnGraphsNodeFactory extends NodeFactory<ActiveSearchOnGraphsNodeModel> {

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
    public NodeView<ActiveSearchOnGraphsNodeModel> createNodeView(final int viewIndex,
                                                                  final ActiveSearchOnGraphsNodeModel nodeModel) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ActiveSearchOnGraphsNodeModel createNodeModel() {
        return new ActiveSearchOnGraphsNodeModel();
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
                        ActiveSearchOnGraphsNodeModel.createClassColModel(), "Class Column ", 0, IntValue.class));

                addDialogComponent(new DialogComponentColumnNameSelection(ActiveSearchOnGraphsNodeModel.createIdxCol(),
                        "Global Idx Column ", 0, IntValue.class));

                addDialogComponent(new DialogComponentColumnNameSelection(
                        ActiveSearchOnGraphsNodeModel.createPriorColModel(), "Column of Priors", 0, DoubleValue.class));

                addDialogComponent(new DialogComponentNumber(ActiveSearchOnGraphsNodeModel.createEtaModel(), "Eta", 0.5));

                addDialogComponent(new DialogComponentNumber(ActiveSearchOnGraphsNodeModel.createPriorWeightModel(),
                        "Prior Weight", 0.95));

            }
        };
    }
}
