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
 * Created on Feb 3, 2014 by dietzc
 */
package org.knime.semisupervised.nodes.lp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.knime.base.util.kdtree.KDTree;
import org.knime.base.util.kdtree.NearestNeighbour;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.util.ThreadPool;
import org.knime.paruni.Utils;

/**
 *
 * @author dietzc
 */
public class LabelPropagation {

    private double alpha;

    private int numIterations;

    /**
     * @param _numIterations
     */
    public LabelPropagation(final int _numIterations, final double _alpha) {
        numIterations = _numIterations;
        alpha = _alpha;
    }

    /**
     * @param unknowns
     * @param labeled
     * @param monitor
     */
    public void execute(final List<DataPoint> unknowns, final List<DataPoint> labeled, final ExecutionMonitor monitor) {

        // for multi threading
        final ThreadPool subPool = ThreadPool.currentPool().createSubPool(100);
        final ArrayList<Future<Void>> futures = new ArrayList<Future<Void>>();
        try {
            for (int iteration = 0; iteration < numIterations; iteration++) {
                for (final DataPoint u : unknowns) {

                    final Callable<Void> callable = new Callable<Void>() {

                        @Override
                        public final Void call() {
                            u.updateUsingNeighbors(1);
                            return null;
                        }
                    };

                    futures.add(subPool.submit(callable));
                }

                for (final DataPoint labeledPoint : labeled) {
                    final Callable<Void> callable = new Callable<Void>() {

                        @Override
                        public final Void call() {
                            labeledPoint.updateUsingNeighbors(1);
                            return null;
                        }
                    };

                    futures.add(subPool.submit(callable));

                }

                // waiting for results
                for (final Future<Void> future : futures) {
                    future.get();
                }

                // adjust posteriors
                for (DataPoint unlabeledPoint : unknowns) {
                    unlabeledPoint.putDistributionAt(0, unlabeledPoint.dist(1));
                }

                for (DataPoint labeledPoint : labeled) {
                    labeledPoint
                            .putDistributionAt(0, Utils.combine(labeledPoint.dist(0), labeledPoint.dist(1), 1 - alpha));
                }

                try {
                    monitor.checkCanceled();
                } catch (CanceledExecutionException e) {
                    return;
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param u
     * @param tree
     * @param numNeighbors
     */
    public static void findAndSetNeighbors(final DataPoint point, final KDTree<DataPoint> tree, final int numNeighbors) {
        final List<NearestNeighbour<DataPoint>> neighbors = tree.getKNearestNeighbours(point.vector, numNeighbors);

        double[] weights = new double[neighbors.size()];
        int k = 0;
        double total = 0;
        for (final NearestNeighbour<DataPoint> neighbor : neighbors) {
            total += neighbor.getDistance();
            weights[k++] = neighbor.getDistance();
        }

        k = 0;
        // normalize them to make it a distribution
        for (final NearestNeighbour<DataPoint> neighbor : neighbors) {
            point.addNeigbor(neighbor.getData(), weights[k++] /= total);

        }
    }
}
