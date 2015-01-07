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
package org.knime.semisupervised.nodes.activesearch;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;

/**
 *
 * @author dietzc
 */
public class ActiveSearchOnGraphs {

    private final double priorWeight;

    private final int numPoints;

    private final double priorDPrimeFactor;

    private final double priorAFactor;

    private final ActiveSearchOnGraphsPoint[] points;

    private final double[][] distanceMatrix;

    private final ExecutorService executorService = ActiveSearchOnGraphsNodeModel.EXECUTIONSERVICE;

    /**
     * @param _numIterations
     * @param _minActivation
     */
    public ActiveSearchOnGraphs(final double _priorWeight, final ActiveSearchOnGraphsPoint[] _points,
                                final double[][] _distanceMatrix) {
        this.priorWeight = _priorWeight;
        this.numPoints = _points.length;

        // Some pre-computations.
        this.priorAFactor = 1.0 / (1.0 + priorWeight);
        this.priorDPrimeFactor = priorWeight / (1.0 + priorWeight);
        this.points = _points;
        this.distanceMatrix = _distanceMatrix;

        // Matrix A

        // normalization factor
        for (int i = 0; i < numPoints; i++) {
            double normConstant = 0;
            for (int j = 0; j < numPoints; j++) {
                normConstant += _distanceMatrix[i][j];
            }
            for (int j = 0; j < numPoints; j++) {
                _distanceMatrix[i][j] /= normConstant;
            }
        }

    }

    /**
     *
     *
     * @param unlabaled
     * @param labeled
     * @param monitor
     * @param distanceMatrix
     * @throws CanceledExecutionException
     */
    public void propagate(final ExecutionMonitor monitor) throws CanceledExecutionException {

        //        final double certaintySmoothing = 0.0005;
        //        final double certaintySmoothingA = 1.0 / (1.0 + certaintySmoothing);
        //        final double certaintySmoothingDPrime = certaintySmoothing / (1.0 + certaintySmoothing);

        // A
        final double[][] A = new double[numPoints][numPoints];
        for (int i = 0; i < numPoints; i++) {
            final boolean isLabeled = points[i].isLabeled();

            for (int j = 0; j < numPoints; j++) {
                A[i][j] =
                        isLabeled ? (1 - points[i].getEta()) * distanceMatrix[i][j] : priorAFactor
                                * distanceMatrix[i][j];
            }
        }

        //        // A_CERTAINTY
        //        final double[][] A_CERTAINTY = new double[numPoints][numPoints];
        //        for (int i = 0; i < numPoints; i++) {
        //            final boolean isLabeled = points[i].isLabeled();
        //
        //            for (int j = 0; j < numPoints; j++) {
        //                A_CERTAINTY[i][j] = isLabeled ? 0.0 : certaintySmoothingA * distanceMatrix[i][j];
        //            }
        //        }

        final double[] D_PRIME = new double[numPoints];

        // D'
        for (int i = 0; i < numPoints; i++) {
            D_PRIME[i] = points[i].isLabeled() ? points[i].getEta() : priorDPrimeFactor;
        }

        //        final double[] D_PRIME_CERTAINTY = new double[numPoints];

        //        // D' CERTAINTY
        //        for (int i = 0; i < numPoints; i++) {
        //            D_PRIME_CERTAINTY[i] = points[i].isLabeled() ? 1 : certaintySmoothingDPrime;
        //        }

        final double[] pseudoRes = new double[numPoints];
        for (int i = 0; i < numPoints; i++) {
            pseudoRes[i] = points[i].getPrior();
        }

        //        final double[] pseudoResCertainty = new double[numPoints];
        //        for (int i = 0; i < numPoints; i++) {
        //            pseudoResCertainty[i] = points[i].isLabeled() ? 1 : 0;
        //        }

        double[] res = new double[numPoints];
        //        double[] resCertainty = new double[numPoints];

        // now the iterative multiplication
        int t = 0;
        boolean converged = false;
        while (!converged) {

            final double[] newRes = new double[numPoints];
            //            final double[] newResCertainty = new double[numPoints];

            //            final double[] finalResCertainty = resCertainty;
            final double[] finalRes = res;

            // multiply
            final ArrayList<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < numPoints; i++) {

                final int finalI = i;
                final Callable<Void> callable = new Callable<Void>() {

                    @Override
                    public Void call() throws Exception {

                        for (final int j : points[finalI].neighborIndices()) {
                            newRes[finalI] += finalRes[j] * A[finalI][j];
                            //                            newResCertainty[finalI] += finalResCertainty[j] * A_CERTAINTY[finalI][j];
                        }

                        // some up all pseudo label nodes
                        newRes[finalI] += pseudoRes[finalI] * D_PRIME[finalI];

                        // set certainty
                        //                        newResCertainty[finalI] += pseudoResCertainty[finalI] * D_PRIME_CERTAINTY[finalI];

                        return null;
                    }
                };

                futures.add(executorService.submit(callable));
            }

            for (final Future<Void> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }

            res = newRes;
            //            resCertainty = newResCertainty;
            if (++t > 500) {
                break;
            }

            monitor.checkCanceled();
        }

        for (int i = 0; i < numPoints; i++) {
            points[i].setActivityEstimate(res[i]);
            points[i].setCertainty(0);
        }
    }
}
