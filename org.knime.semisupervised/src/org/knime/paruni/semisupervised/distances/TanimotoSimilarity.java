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
package org.knime.paruni.semisupervised.distances;

import org.knime.base.distance.Distance;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataValue;
import org.knime.core.data.vector.bitvector.BitVectorValue;
import org.knime.core.data.vector.bitvector.DenseBitVectorCellFactory;
import org.knime.core.data.vector.bitvector.SparseBitVectorCell;
import org.knime.core.data.vector.bitvector.SparseBitVectorCellFactory;
import org.knime.core.node.InvalidSettingsException;

public final class TanimotoSimilarity extends Distance {

    public static final TanimotoSimilarity INSTANCE = new TanimotoSimilarity();

    public TanimotoSimilarity() {
    }

    /** {@inheritDoc} */
    @Override
    public double distance(final DataRow row1, final DataRow row2) {
        if (row1.getNumCells() < 1 || row2.getNumCells() < 1) {
            throw new IllegalArgumentException("Invalid length (both " + "should be 1) " + row1.getNumCells() + " and "
                    + row2.getNumCells());
        }
        DataCell cell1 = row1.getCell(0);
        DataCell cell2 = row2.getCell(0);
        if (cell1.isMissing()) {
            throw new IllegalArgumentException("Cell in row \"" + row1.getKey() + "\" is missing.");
        }
        if (cell2.isMissing()) {
            throw new IllegalArgumentException("Cell in row \"" + row2.getKey() + "\" is missing.");
        }
        if (!(cell1 instanceof BitVectorValue)) {
            throw new IllegalArgumentException("No bit vector: " + cell1);
        }
        if (!(cell2 instanceof BitVectorValue)) {
            throw new IllegalArgumentException("No bit vector: " + cell2);
        }
        BitVectorValue b1 = (BitVectorValue)cell1;
        BitVectorValue b2 = (BitVectorValue)cell2;
        long nominator;
        if (cell1 instanceof SparseBitVectorCell) {
            nominator = SparseBitVectorCellFactory.and(b1, b2).cardinality();
        } else {
            nominator = DenseBitVectorCellFactory.and(b1, b2).cardinality();
        }
        long denominator = b1.cardinality() + b2.cardinality() - nominator;
        if (denominator > 0) {
            return 1 - (1.0 - nominator / (double)denominator);
        } else {
            return 0.0;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void validateInput(final DataTableSpec spec) throws InvalidSettingsException {
        if (spec.getNumColumns() != 1) {
            throw new InvalidSettingsException("Invalid number of columns for " + "distance calculation: "
                    + spec.getNumColumns());
        }
        DataColumnSpec c = spec.getColumnSpec(0);
        if (!c.getType().isCompatible(BitVectorValue.class)) {
            throw new InvalidSettingsException("Can't use column \"" + c.getName()
                    + "\" for distance calculation, wrong type: " + c.getType());
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return "Tanimoto Similarity (1-Tanimoto)";
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public Class<? extends DataValue>[] getCompatibleDataValues() {
        return new Class[]{BitVectorValue.class};
    }
}
