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
package org.knime.paruni.semisupervised.nodes.ssakde;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.knime.base.util.kdtree.KDTree;
import org.knime.base.util.kdtree.NearestNeighbour;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.util.ThreadPool;
import org.knime.paruni.DataPoint;
import org.knime.paruni.Utils;

/**
 *
 * @author dietzc
 */
public class SSAKDE {

    private final int numIterations;

    private final double kernelSmoothing;

    private final List<String> classes;

    private final double initSigma;

    private final int numNeighbors;

    private final String defUniverseName;

    /**
     * @param _numIterations
     * @param _kernelSmoothing
     * @param _initSigma
     * @param _numNeighbors
     * @param _classes
     * @param _defUniverseName
     */
    public SSAKDE(final int _numIterations, final double _kernelSmoothing, final double _initSigma,
                  final int _numNeighbors, final List<String> _classes, final String _defUniverseName) {
        numIterations = _numIterations;
        defUniverseName = _defUniverseName;
        kernelSmoothing = _kernelSmoothing;
        initSigma = _initSigma;
        numNeighbors = _numNeighbors;
        classes = _classes;
    }

    /**
     * @param allPoints
     * @param unknowns
     * @param kernels
     * @param monitor
     */
    public void execute(final KDTree<DataPoint> allPoints, final List<DataPoint> unknowns,
                        final List<DataPoint> kernels, final ExecutionMonitor monitor) {

        // estimate sigmas

        final HashMap<DataPoint, Double> sigmas = new HashMap<DataPoint, Double>();
        double normalizationConstant = 0;
        for (final DataPoint unknown : unknowns) {
            final double res = calcSigmaFactor(allPoints, unknown);
            normalizationConstant += res;
            sigmas.put(unknown, res);
        }

        for (final DataPoint kernel : kernels) {
            final double res = calcSigmaFactor(allPoints, kernel);
            normalizationConstant += res;
            sigmas.put(kernel, res);
        }

        // normalize sigmas and init distribution
        for (final DataPoint kernel : kernels) {
            sigmas.put(kernel, (sigmas.get(kernel).doubleValue()) / normalizationConstant * initSigma * unknowns.size());
            kernel.assignDistributionAt(defUniverseName, 0, initProbDistForLabeled(kernel));
        }

        for (final DataPoint unknown : unknowns) {
            sigmas.put(unknown,
                       (sigmas.get(unknown).doubleValue()) / normalizationConstant * initSigma * unknowns.size());
            unknown.assignDistributionAt(defUniverseName, 0, initProbDist(kernels, unknown, sigmas));

        }
        // for multi threading
        final ThreadPool subPool = ThreadPool.currentPool().createSubPool(100);
        final ArrayList<Future<Void>> futures = new ArrayList<Future<Void>>();
        try {
            for (int iteration = 0; iteration < numIterations; iteration++) {
                for (final DataPoint u : unknowns) {

                    final Callable<Void> callable = new Callable<Void>() {

                        @Override
                        public final Void call() {
                            u.assignDistributionAt(defUniverseName, 1, estimateDensities(unknowns, kernels, u, sigmas));
                            return null;
                        }
                    };

                    futures.add(subPool.submit(callable));
                }

                for (final DataPoint point : kernels) {
                    final Callable<Void> callable = new Callable<Void>() {

                        @Override
                        public final Void call() {
                            point.assignDistributionAt(defUniverseName, 1,
                                                       estimateDensities(unknowns, kernels, point, sigmas));
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
                for (DataPoint unlabeled : unknowns) {
                    unlabeled.assignDistributionAt(defUniverseName, 0, unlabeled.dist(defUniverseName, 1));
                }

                for (DataPoint labeled : kernels) {
                    labeled.assignDistributionAt(defUniverseName,
                                                 0,
                                                 Utils.combine(labeled.dist(defUniverseName, 0),
                                                               labeled.dist(defUniverseName, 1), 1 - kernelSmoothing));
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
     * @param sigmaMap
     * @param tree
     * @param normalizationConstant
     * @param p
     * @return
     */
    private double calcSigmaFactor(final KDTree<DataPoint> tree, final DataPoint p) {

        double sdegree = 0;
        for (NearestNeighbour<DataPoint> nn : tree.getKNearestNeighbours(p.vector(defUniverseName), numNeighbors)) {
            final double distance = nn.getDistance();
            sdegree += distance;
        }

        return sdegree;
    }

    /**
     * @param unlabeled
     * @return
     */
    private double[] estimateDensities(final List<DataPoint> unknown, final List<DataPoint> kernels,
                                       final DataPoint testPoint, final Map<DataPoint, Double> sigmas) {

        final double[] probDistribution = new double[classes.size()];

        double total = 0;

        for (DataPoint p : unknown) {
            if (unknown == p) {
                continue;
            }

            for (int i = 0; i < classes.size(); i++) {
                final double response =
                        Utils.gaussRespone(p.vector(defUniverseName), testPoint.vector(defUniverseName), sigmas.get(p));

                // estimate weightedDist
                final double weightedDist = p.dist(defUniverseName, 0)[i] * response;
                probDistribution[i] += weightedDist;
                total += weightedDist;
            }
        }

        for (DataPoint p : kernels) {
            for (int i = 0; i < classes.size(); i++) {
                final double response =
                        Utils.gaussRespone(p.vector(defUniverseName), testPoint.vector(defUniverseName), sigmas.get(p));

                // estimate weightedDist
                final double weightedDist = p.dist(defUniverseName, 0)[i] * response;
                probDistribution[i] += weightedDist;
                total += weightedDist;
            }
        }

        // normalize responses
        for (int d = 0; d < probDistribution.length; d++) {
            probDistribution[d] /= total;
        }

        return probDistribution;
    }

    /**
     * @param labeled
     * @return
     */
    private double[] initProbDistForLabeled(final DataPoint labeled) {
        final double[] probDistribution = new double[classes.size()];
        probDistribution[classes.indexOf(labeled.classLabel())] = 1;
        return probDistribution;
    }

    private double[] initProbDist(final List<DataPoint> kernels, final DataPoint unlabeled,
                                  final HashMap<DataPoint, Double> sigmaMap) {

        // if we have no labels yet, prob distribution is 1 for known everywhere.
        if (kernels.size() == 0) {
            return new double[]{1};
        }

        final double[] probDistribution = new double[classes.size()];
        double total = 0;
        for (DataPoint kernel : kernels) {
            final double response =
                    Utils.gaussRespone(kernel.vector(defUniverseName), unlabeled.vector(defUniverseName),
                                       sigmaMap.get(kernel));
            probDistribution[classes.indexOf(kernel.classLabel())] += response;
            total += response;
        }

        for (int d = 0; d < probDistribution.length; d++) {
            probDistribution[d] /= total;
        }

        return probDistribution;
    }

}
