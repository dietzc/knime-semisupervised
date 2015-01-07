package org.knime.semisupervised.nodes.ssng;

import org.knime.core.data.IntValue;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnNameSelection;

/**
 *
 */
public class SSNGNodeFactory extends NodeFactory<SSNGNodeModel> {

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
    public NodeView<SSNGNodeModel> createNodeView(final int viewIndex, final SSNGNodeModel nodeModel) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SSNGNodeModel createNodeModel() {
        return new SSNGNodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    protected NodeDialogPane createNodeDialogPane() {
        return new DefaultNodeSettingsPane() {
            {
                addDialogComponent(new DialogComponentColumnNameSelection(SSNGNodeModel.createIdxCol(),
                        "Global Idx Column ", 0, IntValue.class));

            }
        };
    }
}
