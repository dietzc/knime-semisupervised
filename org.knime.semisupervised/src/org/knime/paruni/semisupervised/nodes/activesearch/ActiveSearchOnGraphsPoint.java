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
package org.knime.paruni.semisupervised.nodes.activesearch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.knime.core.data.RowKey;
import org.knime.core.util.MutableDouble;

/**
 *
 * @author Christian Dietz
 */
class ActiveSearchOnGraphsPoint {

    private final double isActive;

    private double estimate;

    private double prior;

    private List<Integer> neighbors = new ArrayList<>();

    private Map<String, HashSet<ActiveSearchOnGraphsPoint>> neighborhoods = new HashMap<>();

    private Map<String, MutableDouble> relations = new HashMap<>();

    private final int idx;

    private RowKey key;

    private double eta;

    private double certainty;

    public ActiveSearchOnGraphsPoint(final RowKey _key, final int _idx, final boolean _isActive, final double _eta) {
        this.idx = _idx;
        this.key = _key;
        this.isActive = _isActive ? 1 : 0;
        this.prior = isActive;
        this.eta = _eta;
    }

    public ActiveSearchOnGraphsPoint(final RowKey _key, final int _idx, final double _prior) {
        this.idx = _idx;
        this.key = _key;
        this.isActive = -1;
        this.prior = _prior;
        this.eta = Double.NaN;
    }

    public final boolean isActive() {
        return isActive == 1;
    }

    public final boolean isLabeled() {
        return isActive > -1;
    }

    public final double getPrior() {
        return prior;
    }

    public final void setActivityEstimate(final double _estimate) {
        this.estimate = _estimate;
    }

    public final double getEstimate() {
        return estimate;
    }

    public List<Integer> neighborIndices() {
        return neighbors;
    }

    public double rate(final int lvl, final ActiveSearchOnGraphsPoint[] allPoints, final double alpha) {

        double total = 0;
        for (final int i : neighbors) {
            if (i == idx) {
                continue;
            }
            if (lvl == 0) {
                total += allPoints[i].getEstimate();
            } else {
                total += allPoints[i].rate(lvl - 1, allPoints, alpha);
            }

            if (Double.isNaN(total)) {
                System.out.println("?");
            }
        }

        total /= (neighbors.size() - 1);

        return alpha * estimate + (1 - alpha) * (total);
    }

    /**
     * @param idx
     */
    public void addNeighbor(final int idx) {
        neighbors.add(idx);
    }

    /**
     * @return
     */
    public final int getIdx() {
        return idx;
    }

    /**
     * @param d
     */
    public void setPrior(final double _prior) {
        this.prior = _prior;
    }

    public Set<ActiveSearchOnGraphsPoint> resolveNeighborhood(final int lvl,
                                                              final ActiveSearchOnGraphsPoint[] allPoints,
                                                              final String universe) {

        HashSet<ActiveSearchOnGraphsPoint> neighborhood = neighborhoods.get(universe + lvl);
        if (neighborhood == null) {
            neighborhood = new HashSet<>();
            neighborhoods.put(universe + lvl, neighborhood);

            for (final int i : neighbors) {
                if (i == idx) {
                    continue;
                }
                if (lvl > 0) {
                    neighborhood.addAll(allPoints[idx].resolveNeighborhood(lvl - 1, allPoints, universe));
                }

                neighborhood.add(allPoints[idx]);
            }
        }

        return neighborhood;
    }

    /**
     * @param relation
     * @param string
     */
    public void setRelationWeight(final double relation, final String universe) {
        relations.put(universe, new MutableDouble(relation));
    }

    /**
     * @param string
     * @return
     */
    public double getRelation(final String universe) {
        return relations.get(universe).doubleValue();
    }

    /**
     * @return the key
     */
    public RowKey getKey() {
        return key;
    }

    /**
     *
     */
    public void normalizeRelations() {
        double total = 0;
        for (final Entry<String, MutableDouble> entry : relations.entrySet()) {
            total += entry.getValue().doubleValue();
        }

        for (final Entry<String, MutableDouble> entry : relations.entrySet()) {
            entry.getValue().setValue(entry.getValue().doubleValue() / total);
        }
    }

    public void setEta(final double _eta) {
        if (!isLabeled()) {
            throw new IllegalArgumentException("we can only set eta on labeled points");
        }

        this.eta = _eta;
    }

    public double getEta() {
        return eta;
    }

    /**
     * @param d
     */
    public void setCertainty(final double certainty) {
        this.certainty = certainty;
    }

    /**
     * @return the certainty
     */
    public double getCertainty() {
        return certainty;
    }
}
