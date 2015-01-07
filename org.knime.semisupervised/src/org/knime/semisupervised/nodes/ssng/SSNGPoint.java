package org.knime.semisupervised.nodes.ssng;

/*
 * ------------------------------------------------------------------------
 *
 *  Copyright (C) 2003 - 2014
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
 * Created on 03.04.2014 by Christian Dietz
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.knime.core.data.RowKey;
import org.knime.core.util.MutableDouble;
import org.knime.core.util.Pair;

/**
 *
 * @author Christian Dietz
 */
class SSNGPoint {

    private final List<Pair<Integer, MutableDouble>> neighbors = new ArrayList<>();

    private final int globalIdx;

    private final RowKey key;

    private final double[] dist;

    private final String universe;

    private double discoveryWeight;

    public SSNGPoint(final RowKey _key, final int _idx, final double[] _dist, final String _universe) {
        this.globalIdx = _idx;
        this.key = _key;
        this.dist = _dist;
        this.universe = _universe;
    }

    public List<Pair<Integer, MutableDouble>> neighbors() {
        return neighbors;
    }

    public void neighbors(final double minActivity, final SSNGPoint[] allPoints, final Set<Integer> totalSet,
                          final int _idx, final int numNeighbors) {
        for (Pair<Integer, MutableDouble> neighborPair : neighbors) {
            if (allPoints[neighborPair.getFirst()].dist[_idx] > minActivity
                    && !totalSet.contains(neighborPair.getFirst())) {

                if (totalSet.size() == numNeighbors) {
                    return;
                }

                totalSet.add(neighborPair.getFirst());

                if (totalSet.size() == numNeighbors) {
                    return;
                }

                allPoints[neighborPair.getFirst()].neighbors(minActivity, allPoints, totalSet, _idx, numNeighbors);
            }
        }
    }

    /**
     * @param _idx
     */
    public void addNeighbor(final int _idx, final MutableDouble weight) {
        neighbors.add(new Pair<>(_idx, weight));
    }

    /**
     * @return
     */
    public final int getIdx() {
        return globalIdx;
    }

    /**
     * @return the key
     */
    public RowKey getKey() {
        return key;
    }

    /**
     * @return
     */
    public double[] dist() {
        return dist;
    }

    public String universe() {
        return universe;
    }

    /**
     * @param d
     */
    public void setDiscoveryWeight(final double d) {
        this.discoveryWeight = d;
    }

    /**
     * @return the discoveryWeight
     */
    public double getDiscoveryWeight() {
        return discoveryWeight;
    }
}
