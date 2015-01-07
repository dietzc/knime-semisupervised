/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by
 *  University of Konstanz, Germany and
 *  KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Sep 29, 2008 (wiswedel): created
 */
package org.knime.semisupervised.distances;

import org.knime.base.distance.Distance;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataValue;
import org.knime.core.data.DoubleValue;
import org.knime.core.node.InvalidSettingsException;

public final class EuclideanSimilarity extends Distance {

    public EuclideanSimilarity() {
    }

    /** {@inheritDoc} */
    @Override
    public double distance(final DataRow row1, final DataRow row2) {
        if (row1.getNumCells() != row2.getNumCells()) {
            throw new IllegalArgumentException("Invalid length: " + row1.getNumCells() + " vs. " + row2.getNumCells());
        }
        double dis = 0.0;
        for (int i = 0; i < row1.getNumCells(); i++) {
            DataCell cell1 = row1.getCell(i);
            DataCell cell2 = row2.getCell(i);
            if (cell1.getType().isCompatible(DoubleValue.class) && cell2.getType().isCompatible(DoubleValue.class)) {
                if (cell1.isMissing()) {
                    throw new IllegalArgumentException("Cell in row \"" + row1.getKey() + "\" is missing.");
                }
                if (cell2.isMissing()) {
                    throw new IllegalArgumentException("Cell in row \"" + row2.getKey() + "\" is missing.");
                }
                double d = ((DoubleValue)cell1).getDoubleValue();
                d -= ((DoubleValue)cell2).getDoubleValue();
                dis += d * d;
            }
        }
        return Math.exp(-Math.sqrt(dis));
    }

    /** {@inheritDoc} */
    @Override
    public void validateInput(final DataTableSpec spec) throws InvalidSettingsException {
        if (spec.getNumColumns() <= 0) {
            throw new InvalidSettingsException("No columns for distance calculation");
        }
        for (DataColumnSpec c : spec) {
            if (!c.getType().isCompatible(DoubleValue.class)) {
                throw new InvalidSettingsException("Can't use column \"" + c.getName()
                        + "\" for distance calculation, wrong type: " + c.getType());
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return "Euclidean Similarity (e^(-distance))";
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public Class<? extends DataValue>[] getCompatibleDataValues() {
        return new Class[]{DoubleValue.class};
    }
}
