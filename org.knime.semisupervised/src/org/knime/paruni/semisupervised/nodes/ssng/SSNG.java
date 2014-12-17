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
 * Created on Apr 24, 2014 by dietzc
 */
package org.knime.paruni.semisupervised.nodes.ssng;

import java.util.HashSet;

/**
 *
 * @author dietzc
 */
public class SSNG implements Comparable<SSNG> {

    private final HashSet<Integer> neighborSet;

    private final SSNGPoint[] allPoints;

    private String universe;

    private boolean dirty = true;

    private double coverage;

    private SSNGPoint center;

    //    private HashMap<Integer, Double> m_resMap;

    private final int distIdx;

    private int numNeighbors;

    /**
     * @param idx
     * @param _numNeighbors
     * @param pointInUniverse
     * @param ssngPoints
     */
    public SSNG(final SSNGPoint _center, final SSNGPoint[] _allPoints, final double minActivity, final int idx,
                final int _numNeighbors) {
        this.neighborSet = new HashSet<>();
        this.allPoints = _allPoints;
        this.center = _center;
        this.numNeighbors = _numNeighbors;
        this.distIdx = idx;
        this.universe = _center.universe();

        neighborSet.add(_center.getIdx());
        _center.neighbors(minActivity, _allPoints, neighborSet, idx, numNeighbors);
    }

    /**
     * Score is as coding size of neighborgram AND some the certainty. the higher the score the better.
     *
     * @return
     */
    public double coverage() {

        if (dirty) {
            coverage = 0;

            for (int i : neighborSet) {
                if (distIdx == 0) {
                    coverage += allPoints[i].dist()[distIdx];
                } else {
                    coverage += (1 - allPoints[i].dist()[distIdx]);
                }
            }

            coverage /= numNeighbors;

            dirty = false;
        }
        // best score = all points have 1.0
        return coverage;
    }

    public int centerIdx() {
        return center.getIdx();
    }

    public boolean contains(final int idx) {
        return neighborSet.contains(idx);
    }

    public HashSet<Integer> containedPoints() {
        return neighborSet;
    }

    /**
     * @return the universe
     */
    public String getUniverse() {
        return universe;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(final SSNG o) {
        final double s1 = coverage();
        final double s2 = o.coverage();

        if (s1 == s2) {
            return 0;
        } else if (s1 < s2) {
            return 1;
        }
        return -1;
    }

    /**
     * @param covered
     */
    public void removeAndUpdate(final HashSet<Integer> covered) {
        neighborSet.removeAll(covered);
        if (neighborSet.isEmpty()) {
            System.out.println("?");
        }
        dirty = true;
    }
}
